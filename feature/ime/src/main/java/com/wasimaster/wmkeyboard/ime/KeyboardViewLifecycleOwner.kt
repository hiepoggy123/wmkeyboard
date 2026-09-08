package com.wasimaster.wmkeyboard.ime

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * ComposeView requires ViewTree owners that a plain InputMethodService
 * window doesn't provide. This owner is attached to the IME's decor view
 * and driven from the service's lifecycle callbacks.
 */
class KeyboardViewLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            dispatch(Lifecycle.Event.ON_CREATE)
        }
    }

    /** The keyboard window is up and taking input. */
    fun onResume() {
        climbTo(Lifecycle.State.RESUMED)
    }

    /** Input is finished, but the window may still be on screen. */
    fun onPause() {
        descendTo(Lifecycle.State.STARTED)
    }

    /**
     * The keyboard window is gone.
     *
     * Worth dispatching on its own rather than folding into [onPause]: ON_STOP
     * is what pauses the window's Recomposer frame clock and what stops every
     * `repeatOnLifecycle(STARTED)` collector a panel started. Without it a
     * dismissed keyboard keeps recomposing and keeps its collectors — media
     * sessions, network watchers, clipboard — running for as long as the
     * process lives.
     */
    fun onStop() {
        descendTo(Lifecycle.State.CREATED)
    }

    fun onDestroy() {
        descendTo(Lifecycle.State.CREATED)
        dispatch(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    /**
     * Walks up to [target] one event at a time.
     *
     * LifecycleRegistry would sync the intermediate steps itself, but only from
     * a state it can reach [target] from: asked to resume an owner still sitting
     * at INITIALIZED it throws rather than inventing the ON_CREATE nobody sent.
     * The service can land here that way — the platform shows a window before
     * the first onStartInputView on some OEM builds.
     */
    private fun climbTo(target: Lifecycle.State) {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        if (lifecycleRegistry.currentState == Lifecycle.State.INITIALIZED) {
            dispatch(Lifecycle.Event.ON_CREATE)
        }
        if (target.isAtLeast(Lifecycle.State.STARTED) &&
            lifecycleRegistry.currentState == Lifecycle.State.CREATED
        ) {
            dispatch(Lifecycle.Event.ON_START)
        }
        if (target.isAtLeast(Lifecycle.State.RESUMED) &&
            lifecycleRegistry.currentState == Lifecycle.State.STARTED
        ) {
            dispatch(Lifecycle.Event.ON_RESUME)
        }
    }

    /**
     * Walks down to [target], and only down.
     *
     * handleLifecycleEvent(ON_PAUSE) on an owner that never resumed moves it
     * *up* to STARTED, because the registry reads the event as a destination
     * rather than as a step. A hide that quietly starts the thing it is meant
     * to be winding down is how collectors end up running with no window.
     */
    private fun descendTo(target: Lifecycle.State) {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        if (!target.isAtLeast(Lifecycle.State.RESUMED) &&
            lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            dispatch(Lifecycle.Event.ON_PAUSE)
        }
        if (!target.isAtLeast(Lifecycle.State.STARTED) &&
            lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)
        ) {
            dispatch(Lifecycle.Event.ON_STOP)
        }
    }

    /**
     * DESTROYED is the end of the line — LifecycleRegistry throws on any move
     * out of it. The platform walks the service through a full input teardown
     * from inside InputMethodService.onDestroy, so onFinishInputView (and with
     * it ON_PAUSE/ON_STOP) can arrive after the service has already said
     * goodbye. That throw propagates out of handleStopService and takes the
     * process with it.
     */
    private fun dispatch(event: Lifecycle.Event) {
        if (lifecycleRegistry.currentState == Lifecycle.State.DESTROYED) return
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    fun attachTo(view: View) {
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
    }
}
