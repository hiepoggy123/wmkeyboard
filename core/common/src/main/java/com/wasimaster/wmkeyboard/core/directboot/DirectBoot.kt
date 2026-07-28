package com.wasimaster.wmkeyboard.core.directboot

import android.content.Context
import android.os.UserManager

/**
 * Direct boot: the window between the device powering on and the user typing
 * their PIN for the first time. On a file-based-encryption device (every phone
 * since Android 7) the app's normal storage — `filesDir`, DataStore, the
 * clipboard and snippet files — is *credential encrypted* and simply is not
 * readable until then. Touching it throws.
 *
 * A keyboard that cannot run in that window is a keyboard the user cannot use
 * to type their own PIN or answer a lock-screen reply, so the platform quietly
 * swaps in a different IME instead. Declaring `directBootAware` on the service
 * (see the manifest) says we can cope; this object is how we cope.
 *
 * The rules that follow from it:
 *
 *  * [isUserUnlocked] is the one gate. False means "no credential-encrypted
 *    storage" — not "the keyguard is showing". A phone that has been unlocked
 *    once and then locked again is unlocked as far as storage is concerned;
 *    for the lock-*screen* privacy setting see `KeyboardUiState.deviceLocked`.
 *  * Anything read before unlock has to live in *device protected* storage
 *    ([deviceContext]), which is available from boot. That storage is not
 *    covered by the user's credential, so it holds the keyboard's appearance
 *    and behaviour — never their typing, their clips, or their API keys.
 *  * The unlock is a live event: the service can be created locked and still be
 *    running minutes later when the user unlocks. Everything gated here has to
 *    be re-established at that point rather than at the next process start.
 */
object DirectBoot {

    /**
     * Whether credential-encrypted storage (`filesDir`, the settings DataStore)
     * can be read right now. Defaults to true if the platform has no
     * [UserManager] at all, since the alternative is a keyboard that
     * permanently believes it is locked.
     */
    fun isUserUnlocked(context: Context): Boolean =
        (context.getSystemService(Context.USER_SERVICE) as? UserManager)?.isUserUnlocked ?: true

    /**
     * A context whose storage lives in the device-protected area: readable from
     * boot, before any credential has been entered, and therefore *not*
     * protected by it.
     */
    fun deviceContext(context: Context): Context =
        context.createDeviceProtectedStorageContext() ?: context
}
