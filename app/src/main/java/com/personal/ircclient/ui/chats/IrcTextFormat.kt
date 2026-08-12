package com.personal.ircclient.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

val IrcColorMap = mapOf(
    "00" to Color(0xFFFFFFFF), "0" to Color(0xFFFFFFFF),  // White
    "01" to Color(0xFF000000), "1" to Color(0xFF000000),  // Black
    "02" to Color(0xFF00007F), "2" to Color(0xFF00007F),  // Blue
    "03" to Color(0xFF009300), "3" to Color(0xFF009300),  // Green
    "04" to Color(0xFFFF0000), "4" to Color(0xFFFF0000),  // Red
    "05" to Color(0xFF7F0000), "5" to Color(0xFF7F0000),  // Brown
    "06" to Color(0xFF9C009C), "6" to Color(0xFF9C009C),  // Purple
    "07" to Color(0xFFFC7F00), "7" to Color(0xFFFC7F00),  // Orange
    "08" to Color(0xFFFFFF00), "8" to Color(0xFFFFFF00),  // Yellow
    "09" to Color(0xFF00FC00), "9" to Color(0xFF00FC00),  // Light Green
    "10" to Color(0xFF009393),                          // Teal
    "11" to Color(0xFF00FFFF),                          // Cyan
    "12" to Color(0xFF0000FC),                          // Light Blue
    "13" to Color(0xFFFF00FF),                          // Pink
    "14" to Color(0xFF7F7F7F),                          // Grey
    "15" to Color(0xFFD2D2D2)                           // Light Grey
)

fun stripIrcColors(text: String): String {
    return text.replace(Regex("\u0003\\d{0,2}(,\\d{1,2})?|\u0002|\u001f|\u001d|\u000f|\u0016"), "")
}

fun parseIrcColors(text: String, settings: com.personal.ircclient.data.local.entities.SettingsEntity? = null): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        var isBold = false
        var isItalic = false
        var isUnderline = false
        var currentColor: Color? = null
        var currentBg: Color? = null
        
        val urlRegex = Regex("(https?://[\\w\\d:#@%/;$()~_?\\+-=\\.&]+)", RegexOption.IGNORE_CASE)

        while (i < text.length) {
            val char = text[i]
            when (char) {
                '\u0002' -> { isBold = !isBold; i++ }
                '\u001d' -> { isItalic = !isItalic; i++ }
                '\u001f' -> { isUnderline = !isUnderline; i++ }
                '\u000f' -> { 
                    isBold = false; isItalic = false; isUnderline = false
                    currentColor = null; currentBg = null; i++ 
                }
                '\u0003' -> {
                    i++
                    val match = Regex("^(\\d{1,2})(,(\\d{1,2}))?").find(text.substring(i))
                    if (match != null) {
                        val fgCode = match.groupValues[1]
                        val bgCode = match.groupValues[3]
                        currentColor = IrcColorMap[fgCode.padStart(2, '0')]
                        if (bgCode.isNotEmpty()) currentBg = IrcColorMap[bgCode.padStart(2, '0')]
                        i += match.value.length
                    } else {
                        currentColor = null; currentBg = null
                    }
                }
                else -> {
                    val urlMatch = urlRegex.find(text.substring(i))
                    if (urlMatch != null && urlMatch.range.start == 0) {
                        val url = urlMatch.value
                        pushStringAnnotation(tag = "URL", annotation = url)
                        withStyle(SpanStyle(color = Color(0xFF2196F3), textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                            append(url)
                        }
                        pop()
                        i += url.length
                    } else {
                        withStyle(
                            SpanStyle(
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                                textDecoration = if (isUnderline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
                                color = currentColor ?: Color.Unspecified,
                                background = currentBg ?: Color.Transparent
                            )
                        ) {
                            append(char)
                        }
                        i++
                    }
                }
            }
        }
    }
}

@Composable
fun FormattingTools(
    onFormatClick: (String) -> Unit,
    onColorClick: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(onClick = { onFormatClick("\u0002") }) { Icon(Icons.Default.FormatBold, "Bold") }
            IconButton(onClick = { onFormatClick("\u001d") }) { Icon(Icons.Default.FormatItalic, "Italic") }
            IconButton(onClick = { onFormatClick("\u001f") }) { Icon(Icons.Default.FormatUnderlined, "Underline") }
            IconButton(onClick = { onFormatClick("\u000f") }) { Icon(Icons.Default.FormatClear, "Reset") }
        }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(IrcColorMap.keys.toList().filter { it.length == 2 }.distinct()) { code ->
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(IrcColorMap[code]!!, shape = MaterialTheme.shapes.small)
                        .clickable { onColorClick(code) }
                )
            }
        }
    }
}
