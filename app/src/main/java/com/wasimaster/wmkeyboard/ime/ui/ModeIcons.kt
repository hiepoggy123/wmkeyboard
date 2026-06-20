package com.wasimaster.wmkeyboard.ime.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The icons a keyboard mode can wear, as stable ids. `KeyboardMode.icon`
 * stores the id, never the vector, so the stored modes survive an icon being
 * swapped for a better-looking one — and an id that no longer exists falls
 * back to [DefaultModeIcon] instead of crashing.
 *
 * Ordered as the picker shows them: the ones that fit a mode ("chat",
 * "writing", "passwords") first, then the general-purpose ones.
 */
object ModeIcons {
    val catalog: List<Pair<String, ImageVector>> = listOf(
        "tune" to Icons.Outlined.Tune,
        "chat" to Icons.Outlined.ChatBubbleOutline,
        "forum" to Icons.Outlined.Forum,
        "write" to Icons.Outlined.EditNote,
        "note" to Icons.Outlined.StickyNote2,
        "article" to Icons.Outlined.Article,
        "book" to Icons.AutoMirrored.Outlined.MenuBook,
        "mail" to Icons.Outlined.MailOutline,
        "lock" to Icons.Outlined.Lock,
        "shield" to Icons.Outlined.Shield,
        "key" to Icons.Outlined.VpnKey,
        "web" to Icons.Outlined.Language,
        "search" to Icons.Outlined.Search,
        "code" to Icons.Outlined.Code,
        "terminal" to Icons.Outlined.Terminal,
        "work" to Icons.Outlined.WorkOutline,
        "school" to Icons.Outlined.School,
        "science" to Icons.Outlined.Science,
        "home" to Icons.Outlined.Home,
        "person" to Icons.Outlined.PersonOutline,
        "group" to Icons.Outlined.Group,
        "call" to Icons.Outlined.Call,
        "shopping" to Icons.Outlined.ShoppingCart,
        "money" to Icons.Outlined.Payments,
        "calendar" to Icons.Outlined.CalendarMonth,
        "game" to Icons.Outlined.SportsEsports,
        "music" to Icons.Outlined.MusicNote,
        "video" to Icons.Outlined.Videocam,
        "camera" to Icons.Outlined.PhotoCamera,
        "palette" to Icons.Outlined.Palette,
        "travel" to Icons.Outlined.Flight,
        "fitness" to Icons.Outlined.FitnessCenter,
        "cloud" to Icons.Outlined.Cloud,
        "bolt" to Icons.Outlined.Bolt,
        "star" to Icons.Outlined.StarOutline,
        "favorite" to Icons.Outlined.FavoriteBorder,
    )

    private val byId: Map<String, ImageVector> = catalog.toMap()

    /** What a mode with no icon of its own shows. */
    val DefaultModeIcon: ImageVector = Icons.Outlined.Tune

    /** The vector for [id], or [DefaultModeIcon] for null and unknown ids. */
    fun icon(id: String?): ImageVector = id?.let { byId[it] } ?: DefaultModeIcon
}
