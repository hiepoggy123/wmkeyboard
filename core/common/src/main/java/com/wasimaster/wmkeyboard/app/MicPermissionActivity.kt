package com.wasimaster.wmkeyboard.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Invisible trampoline that shows the system microphone-permission dialog
 * on behalf of the IME (services cannot request runtime permissions). It
 * finishes as soon as the dialog is answered; the voice panel re-checks
 * the permission when the keyboard regains focus.
 */
class MicPermissionActivity : ComponentActivity() {

    private val request =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request.launch(android.Manifest.permission.RECORD_AUDIO)
    }
}
