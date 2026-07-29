package com.wasimaster.wmkeyboard.app

/**
 * Trampoline for READ_MEDIA_IMAGES (API 33+) or READ_EXTERNAL_STORAGE (API < 33),
 * used by the clipboard's user-screenshots option: the disclosure first, then
 * the system dialog. Services cannot request runtime permissions, and the
 * settings screen starts this too so both entry points show the same text.
 */
class ImagesPermissionActivity : PermissionRequestActivity() {
    override val disclosure = PermissionDisclosures.IMAGES
}
