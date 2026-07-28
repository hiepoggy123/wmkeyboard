package com.wasimaster.wmkeyboard.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Invisible trampoline for WRITE_EXTERNAL_STORAGE, used by the save-to-gallery
 * option on API 24-28 (services cannot request runtime permissions). On API
 * 29+ the permission does not exist for this purpose, so it finishes straight
 * away and the save proceeds unguarded.
 */
class StoragePermissionActivity : ComponentActivity() {

    private val request =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            finish()
            return
        }
        request.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}
