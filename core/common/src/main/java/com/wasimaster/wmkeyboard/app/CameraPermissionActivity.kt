package com.wasimaster.wmkeyboard.app

/**
 * Trampoline that shows the camera disclosure and then the system
 * camera-permission dialog on behalf of the IME (services cannot request
 * runtime permissions). It finishes as soon as the dialog is answered; the
 * camera panel re-checks the permission when the keyboard regains focus.
 */
class CameraPermissionActivity : PermissionRequestActivity() {
    override val disclosure = PermissionDisclosures.CAMERA
}
