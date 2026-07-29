package com.wasimaster.wmkeyboard.app

/**
 * Trampoline that shows the calendar disclosure and then the system
 * calendar-permission dialog on behalf of the IME (services cannot request
 * runtime permissions). It finishes as soon as the dialog is answered; the
 * calendar panel re-checks the permission when the keyboard regains focus.
 */
class CalendarPermissionActivity : PermissionRequestActivity() {
    override val disclosure = PermissionDisclosures.CALENDAR
}
