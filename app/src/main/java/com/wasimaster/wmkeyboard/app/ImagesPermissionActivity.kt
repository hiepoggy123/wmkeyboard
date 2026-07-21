package com.wasimaster.wmkeyboard.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Invisible trampoline for READ_MEDIA_IMAGES (API 33+) or READ_EXTERNAL_STORAGE (API < 33),
 * used by the clipboard user screenshots option. Services cannot request runtime permissions.
 */
class ImagesPermissionActivity : ComponentActivity() {

    private val request =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        request.launch(permission)
    }
}
