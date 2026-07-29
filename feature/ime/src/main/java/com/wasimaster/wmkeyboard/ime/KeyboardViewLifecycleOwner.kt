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
        dispatch(Lifecycle.Event.ON_CREATE)
    }

    fun onResume() {
        dispatch(Lifecycle.Event.ON_RESUME)
    }

    fun onPause() {
        dispatch(Lifecycle.Event.ON_PAUSE)
    }

    fun onDestroy() {
        dispatch(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }

    /**
     * DESTROYED is the end of the line — LifecycleRegistry throws on any move
     * out of it. The platform walks the service through a full input teardown
     * from inside InputMethodService.onDestroy, so onFinishInputView (and with
     * it ON_PAUSE) can arrive after the service has already said goodbye. That
     * throw propagates out of handleStopService and takes the process with it.
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
