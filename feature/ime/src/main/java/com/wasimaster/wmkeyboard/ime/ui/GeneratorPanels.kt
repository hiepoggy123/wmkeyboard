package com.wasimaster.wmkeyboard.ime.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wasimaster.wmkeyboard.core.prediction.DictionaryLoader
import com.wasimaster.wmkeyboard.core.tools.PasswordGen
import com.wasimaster.wmkeyboard.core.tools.QrCodeGen
import com.wasimaster.wmkeyboard.ime.KeyboardUiState
import com.wasimaster.wmkeyboard.ime.PwSettingAction
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---- password / passphrase generator ----

/**
 * Passphrase material, filtered once from the bundled English dictionary
 * (no separate wordlist ships with the app).
 */
private object PassphraseWords {
    @Volatile private var cached: List<String>? = null

    suspend fun load(context: Context): List<String> = cached ?: withContext(Dispatchers.IO) {
        val words = runCatching {
            context.assets.open("dictionaries/en.txt").use { stream ->
                PasswordGen.buildWordlist(
                    DictionaryLoader.loadEntries(stream).asSequence().map { it.first },
                )
            }
        }.getOrDefault(emptyList())
        cached = words
        words
    }
}

/**
 * Password / passphrase generator. Every option persists (the same
 * controls live in the tool's settings); entropy is a rough bits estimate.
 * Nothing generated here is stored anywhere.
 */
@Composable
internal fun PasswordPanel(
    state: KeyboardUiState,
    onSetting: (PwSettingAction) -> Unit,
    onInsert: (String) -> Unit,
) {
    val kb = LocalKbTheme.current
    val settings = state.settings
    val context = LocalContext.current
    val random = remember { SecureRandom() }
    var regenerateKey by remember { mutableIntStateOf(0) }

    val wordlist by produceState(initialValue = emptyList(), Unit) {
        value = PassphraseWords.load(context)
    }

    val passphraseSpec = PasswordGen.PassphraseSpec(
        words = settings.passwordGenerator.ppWordCount,
        separator = settings.passwordGenerator.ppSeparator,
        capitalize = settings.passwordGenerator.ppCapitalize,
        includeDigit = settings.passwordGenerator.ppIncludeDigit,
    )
    val passwordSpec = PasswordGen.PasswordSpec(
        length = settings.passwordGenerator.pwLength,
        upper = settings.passwordGenerator.pwUppercase,
        digits = settings.passwordGenerator.pwDigits,
        symbols = settings.passwordGenerator.pwSymbols,
        excludeAmbiguous = settings.passwordGenerator.pwExcludeAmbiguous,
    )
    val passphraseMode = settings.passwordGenerator.pwPassphraseMode
    val generated = remember(passphraseMode, passwordSpec, passphraseSpec, wordlist, regenerateKey) {
        if (passphraseMode) PasswordGen.passphrase(wordlist, passphraseSpec, random)
        else PasswordGen.password(passwordSpec, random)
    }
    val entropy = if (passphraseMode) {
        PasswordGen.passphraseEntropyBits(passphraseSpec, wordlist.size)
    } else {
        PasswordGen.passwordEntropyBits(passwordSpec)
    }

    // The Password/Passphrase tabs live in the FullBleedTool header next to
    // the back button; the body starts at the generated result.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(kb.chip)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                generated.ifEmpty { "…" },
                color = kb.modifierKeyText,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { regenerateKey++ }, modifier = Modifier.size(30.dp)) {
                Icon(
                    Icons.Outlined.Refresh,
                    contentDescription = "Regenerate",
                    tint = kb.accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            ToolPanelChip("Copy") {
                if (generated.isNotEmpty()) {
                    val clipboard =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Password", generated))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            }
            Spacer(Modifier.width(4.dp))
            ToolPanelChip("Insert") { if (generated.isNotEmpty()) onInsert(generated) }
        }
        Text(
            "≈$entropy bits of entropy",
            color = kb.secondaryText,
            fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.End)
                .padding(bottom = 2.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (!passphraseMode) {
                StepperRow(
                    label = "Length",
                    value = settings.passwordGenerator.pwLength,
                    onChange = { onSetting(PwSettingAction.Length(it)) },
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ToolPanelChip("A–Z", selected = settings.passwordGenerator.pwUppercase) {
                        onSetting(PwSettingAction.Upper(!settings.passwordGenerator.pwUppercase))
                    }
                    ToolPanelChip("0–9", selected = settings.passwordGenerator.pwDigits) {
                        onSetting(PwSettingAction.Digits(!settings.passwordGenerator.pwDigits))
                    }
                    ToolPanelChip("#!&", selected = settings.passwordGenerator.pwSymbols) {
                        onSetting(PwSettingAction.Symbols(!settings.passwordGenerator.pwSymbols))
                    }
                    ToolPanelChip("No look-alikes", selected = settings.passwordGenerator.pwExcludeAmbiguous) {
                        onSetting(PwSettingAction.ExcludeAmbiguous(!settings.passwordGenerator.pwExcludeAmbiguous))
                    }
                }
            } else {
                StepperRow(
                    label = "Words",
                    value = settings.passwordGenerator.ppWordCount,
                    onChange = { onSetting(PwSettingAction.Words(it)) },
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val separators = listOf("-", ".", "_", " ", "")
                    val next = separators[
                        (separators.indexOf(settings.passwordGenerator.ppSeparator) + 1).mod(separators.size),
                    ]
                    ToolPanelChip(
                        "Sep: ${if (settings.passwordGenerator.ppSeparator.isEmpty()) "none" else "“${settings.passwordGenerator.ppSeparator}”"}",
                    ) { onSetting(PwSettingAction.Separator(next)) }
                    ToolPanelChip("Capitalize", selected = settings.passwordGenerator.ppCapitalize) {
                        onSetting(PwSettingAction.Capitalize(!settings.passwordGenerator.ppCapitalize))
                    }
                    ToolPanelChip("Add digit", selected = settings.passwordGenerator.ppIncludeDigit) {
                        onSetting(PwSettingAction.IncludeDigit(!settings.passwordGenerator.ppIncludeDigit))
                    }
                }
            }
        }
    }
}

@Composable
private fun StepperRow(label: String, value: Int, onChange: (Int) -> Unit) {
    val kb = LocalKbTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = kb.secondaryText, fontSize = 12.sp, modifier = Modifier.width(52.dp))
        ToolPanelChip("−") { onChange(value - 1) }
        Text(
            "$value",
            color = kb.modifierKeyText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(36.dp),
        )
        ToolPanelChip("+") { onChange(value + 1) }
    }
}

// ---- QR code generator ----

/**
 * QR code of content the user types into the panel itself (keystrokes reroute
 * into the shared search buffer via [PanelMode.hasMediaSearch], so the key
 * rows show below). It opens seeded with the field text as a convenience, but
 * from there it is independent of the focused field. Send commits the code as
 * a PNG.
 */
@Composable
internal fun QrGeneratorPanel(
    state: KeyboardUiState,
    onSend: () -> Unit,
) {
    val kb = LocalKbTheme.current
    val content = state.mediaQuery
    // Preview renders small and cheap; the inserted PNG uses the size setting.
    val bitmap = remember(content, state.settings.qrEcc) {
        QrCodeGen.bitmap(content, sizePx = 384, ecc = state.settings.qrEcc.name)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        // Editable content line with a faked caret — keys reroute into the
        // buffer, so the platform draws no cursor of its own.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(kb.chip)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchQueryText(
                query = content.replace("\n", " "),
                placeholder = "Type QR content on the keys below…",
                active = state.mediaSearchActive,
                textColor = kb.suggestionText,
                placeholderColor = kb.secondaryText,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (bitmap == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (content.length > QrCodeGen.MAX_CHARS) {
                            "Too much text for one QR code (${content.length} characters)."
                        } else {
                            "Type on the keys below to build a QR code."
                        },
                        color = kb.secondaryText,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            } else {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR code",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Color.White)
                        .padding(4.dp),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    ToolPanelChip("Send as image") { onSend() }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${content.length} chars · ECC ${state.settings.qrEcc.name}",
                        color = kb.secondaryText,
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}
