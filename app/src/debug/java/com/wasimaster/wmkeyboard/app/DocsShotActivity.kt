package com.wasimaster.wmkeyboard.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat

/**
 * Debug-only host for documentation screenshots. Never shipped: lives in the
 * debug source set and is started from adb, e.g.
 *
 *   adb shell am start -n com.wasimaster.wmkeyboard/.app.DocsShotActivity \
 *       --es mode field --es kind email --es theme dark --es hint "Email"
 *
 * Extras:
 *   mode   field (default) | chat | blank
 *   kind   text | email | uri | password | number | phone | search | multiline
 *   action none | send | search | go | done | next
 *   hint   field hint text
 *   text   prefilled field text
 *   theme  dark (default) | light
 *   bg     default | white
 */
class DocsShotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mode = intent.getStringExtra("mode") ?: "field"
        val kind = intent.getStringExtra("kind") ?: "text"
        val action = intent.getStringExtra("action") ?: "none"
        val hint = intent.getStringExtra("hint") ?: defaultHint(kind)
        val prefill = intent.getStringExtra("text") ?: ""
        val dark = (intent.getStringExtra("theme") ?: "dark") == "dark"
        val whiteBg = intent.getStringExtra("bg") == "white"

        setContent {
            MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
                val bg = when {
                    whiteBg -> Color.White
                    else -> MaterialTheme.colorScheme.surface
                }
                Surface(modifier = Modifier.fillMaxSize(), color = bg) {
                    when (mode) {
                        "chat" -> ChatScreen()
                        "blank" -> BlankScreen()
                        else -> FieldScreen(kind, action, hint, prefill)
                    }
                }
            }
        }
    }
}

private fun defaultHint(kind: String): String = when (kind) {
    "email" -> "Email address"
    "uri" -> "Web address"
    "password" -> "Password"
    "number" -> "Number"
    "phone" -> "Phone number"
    "search" -> "Search"
    else -> "Type here"
}

@Composable
private fun FieldScreen(kind: String, action: String, hint: String, prefill: String) {
    val value = rememberSaveable { mutableStateOf(prefill) }
    val keyboardType = when (kind) {
        "email" -> KeyboardType.Email
        "uri" -> KeyboardType.Uri
        "password" -> KeyboardType.Password
        "number" -> KeyboardType.Number
        "phone" -> KeyboardType.Phone
        else -> KeyboardType.Text
    }
    val imeAction = when (action) {
        "send" -> ImeAction.Send
        "search" -> ImeAction.Search
        "go" -> ImeAction.Go
        "done" -> ImeAction.Done
        "next" -> ImeAction.Next
        else -> ImeAction.Default
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 96.dp))
        OutlinedTextField(
            value = value.value,
            onValueChange = { value.value = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(hint) },
            singleLine = kind != "multiline",
            minLines = if (kind == "multiline") 3 else 1,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            visualTransformation = if (kind == "password") {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
        )
    }
}

@Composable
private fun BlankScreen() {
    // A visually silent focus target so the keyboard can still be summoned.
    Box(modifier = Modifier.fillMaxSize()) {
        val value = remember { mutableStateOf("") }
        androidx.compose.foundation.text.BasicTextField(
            value = value.value,
            onValueChange = { value.value = it },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 1.dp).fillMaxWidth(),
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
        )
    }
}

private sealed interface ChatItem {
    data class Msg(val text: String, val mine: Boolean) : ChatItem
    data class Pic(val bitmap: Bitmap) : ChatItem
}

@Composable
private fun ChatScreen() {
    val items = remember {
        mutableStateListOf<ChatItem>(
            ChatItem.Msg("Hey! Trip photos are up, take a look when you can.", mine = false),
            ChatItem.Msg("Just saw them — the lake one is amazing.", mine = true),
            ChatItem.Msg("Right? Same spot as last year.", mine = false),
        )
    }
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
        ) {
            items.forEach { item ->
                when (item) {
                    is ChatItem.Msg -> Bubble(item.text, item.mine)
                    is ChatItem.Pic -> Image(
                        bitmap = item.bitmap.asImageBitmap(),
                        contentDescription = "Shared image",
                        modifier = Modifier
                            .align(Alignment.End)
                            .widthIn(max = 220.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(16.dp),
                            )
                            .padding(4.dp),
                    )
                }
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            factory = {
                CommitContentEditText(it) { bitmap -> items.add(ChatItem.Pic(bitmap)) }.apply {
                    hint = "Message"
                    setPadding(48, 32, 48, 32)
                }
            },
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 8.dp))
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Text(
            text = text,
            color = if (mine) colors.onPrimaryContainer else colors.onSurfaceVariant,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    if (mine) colors.primaryContainer else colors.surfaceVariant,
                    RoundedCornerShape(18.dp),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/**
 * EditText that accepts commitContent (stickers, GIFs, images) from the IME —
 * the piece a plain Compose TextField can't provide. Received images are
 * decoded and handed to [onImage] for display as a chat bubble.
 */
@SuppressLint("ViewConstructor", "AppCompatCustomView")
private class CommitContentEditText(
    context: Context,
    private val onImage: (Bitmap) -> Unit,
) : EditText(context) {

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs) ?: return null
        EditorInfoCompat.setContentMimeTypes(outAttrs, arrayOf("image/*"))
        return InputConnectionCompat.createWrapper(ic, outAttrs) { inputContentInfo, flags, _ ->
            if (Build.VERSION.SDK_INT >= 25 &&
                flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0
            ) {
                try {
                    inputContentInfo.requestPermission()
                } catch (_: Exception) {
                    return@createWrapper false
                }
            }
            val shown = showUri(inputContentInfo.contentUri)
            if (Build.VERSION.SDK_INT >= 25) inputContentInfo.releasePermission()
            shown
        }
    }

    private fun showUri(uri: Uri): Boolean = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.let { bitmap ->
                post { onImage(bitmap) }
                true
            } ?: false
        } ?: false
    } catch (_: Exception) {
        false
    }
}
