package com.wasimaster.wmkeyboard.app

import android.os.Build

/**
 * Trampoline for WRITE_EXTERNAL_STORAGE, used by the save-to-gallery option on
 * API 24-28 (services cannot request runtime permissions). On API 29+ scoped
 * storage lets an app write its own media without any permission, so there is
 * nothing to disclose and nothing to ask for: the base class finishes straight
 * away on a null disclosure and the save proceeds unguarded.
 */
class StoragePermissionActivity : PermissionRequestActivity() {
    override val disclosure: PermissionDisclosure? =
        PermissionDisclosures.STORAGE.takeIf { Build.VERSION.SDK_INT < Build.VERSION_CODES.Q }
}
