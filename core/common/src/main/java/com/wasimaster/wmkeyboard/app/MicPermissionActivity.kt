package com.wasimaster.wmkeyboard.app

/**
 * Trampoline that shows the microphone disclosure and then the system
 * microphone-permission dialog on behalf of the IME (services cannot request
 * runtime permissions). It finishes as soon as the dialog is answered; the
 * voice panel re-checks the permission when the keyboard regains focus.
 */
class MicPermissionActivity : PermissionRequestActivity() {
    override val disclosure = PermissionDisclosures.MICROPHONE
}
