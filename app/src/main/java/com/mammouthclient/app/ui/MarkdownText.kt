package com.mammouthclient.app.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mammouthclient.app.data.AppContainer
import com.mammouthclient.app.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Rendu Markdown sans dépendance : blocs de code (avec copie), titres, listes,
 * citations, gras/italique, code en ligne, liens cliquables et images.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(text) { splitBlocks(text) }
    val uriHandler = LocalUriHandler.current

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeBlock(block)
                is MdBlock.Picture -> MarkdownImage(block.url)
                is MdBlock.Quote -> Row {
                    Box(
                        modifier = Modifier
                            .size(width = 3.dp, height = 20.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = inlineAnnotated(block.text),
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        color = color,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }

                is MdBlock.Paragraph -> {
                    val annotated = inlineAnnotated(block.text)
                    Text(
                        text = annotated,
                        style = MaterialTheme.typography.bodyLarge,
                        color = color,
                        modifier = Modifier.clickable {
                            annotated.getStringAnnotations("URL", 0, annotated.length)
                                .firstOrNull()
                                ?.let { runCatching { uriHandler.openUri(it.item) } }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = block.language.ifBlank { "code" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(block.code)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copier le code",
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Text(
            text = block.code,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        )
    }
}

/** Affiche une image locale ou distante (résultats de génération, illustrations). */
@Composable
fun MarkdownImage(url: String, modifier: Modifier = Modifier) {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        val loaded = loadImageBitmap(url)
        if (loaded == null) failed = true else image = loaded
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val current = image
        when {
            current != null -> Image(
                bitmap = current,
                contentDescription = null,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )

            failed -> Text(
                text = "Image indisponible",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(16.dp)
            )

            else -> CircularProgressIndicator(
                modifier = Modifier.padding(16.dp).size(22.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

private suspend fun loadImageBitmap(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
    runCatching {
        if (!url.startsWith("http")) {
            FileUtils.loadBitmap(url.removePrefix("file://"))?.asImageBitmap()
        } else {
            AppContainer.api().downloadBytes(url)?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }
    }.getOrNull()
}

internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
    data class Picture(val url: String) : MdBlock
    data class Quote(val text: String) : MdBlock
}

private val IMAGE_LINE = Regex("""^!\[[^\]]*]\(([^)\s]+)[^)]*\)\s*$""")

internal fun splitBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val buffer = StringBuilder()
    val quoteBuffer = StringBuilder()
    val codeBuffer = StringBuilder()
    var inCode = false
    var language = ""

    fun flushParagraph() {
        val content = buffer.toString().trim('\n')
        if (content.isNotBlank()) blocks += MdBlock.Paragraph(content)
        buffer.setLength(0)
    }

    fun flushQuote() {
        val content = quoteBuffer.toString().trim('\n')
        if (content.isNotBlank()) blocks += MdBlock.Quote(content)
        quoteBuffer.setLength(0)
    }

    text.lines().forEach { line ->
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                if (inCode) {
                    blocks += MdBlock.Code(language, codeBuffer.toString().trimEnd('\n'))
                    codeBuffer.setLength(0)
                    language = ""
                    inCode = false
                } else {
                    flushQuote()
                    flushParagraph()
                    language = trimmed.removePrefix("```").trim()
                    inCode = true
                }
            }

            inCode -> codeBuffer.append(line).append('\n')

            IMAGE_LINE.matches(trimmed) -> {
                flushQuote()
                flushParagraph()
                IMAGE_LINE.find(trimmed)?.groupValues?.getOrNull(1)?.let {
                    blocks += MdBlock.Picture(it)
                }
            }

            trimmed.startsWith("> ") -> {
                flushParagraph()
                quoteBuffer.append(trimmed.removePrefix("> ")).append('\n')
            }

            else -> {
                flushQuote()
                buffer.append(line).append('\n')
            }
        }
    }

    if (inCode && codeBuffer.isNotEmpty()) {
        blocks += MdBlock.Code(language, codeBuffer.toString().trimEnd('\n'))
    }
    flushQuote()
    flushParagraph()

    return blocks.ifEmpty { listOf(MdBlock.Paragraph(text)) }
}

internal fun inlineAnnotated(source: String): AnnotatedString = buildAnnotatedString {
    source.lines().forEachIndexed { index, rawLine ->
        if (index > 0) append('\n')

        var line = rawLine
        var headingStyle: SpanStyle? = null
        val heading = Regex("^(#{1,6})\\s+").find(line)
        if (heading != null) {
            val level = heading.groupValues[1].length
            line = line.removeRange(heading.range)
            headingStyle = SpanStyle(
                fontWeight = FontWeight.Bold,
                fontSize = when (level) {
                    1 -> 22.sp
                    2 -> 20.sp
                    3 -> 18.sp
                    else -> 16.sp
                }
            )
        }
        val listPrefix = line.trimStart()
        if (listPrefix.startsWith("- ") || listPrefix.startsWith("* ")) {
            val indent = line.takeWhile { it == ' ' }
            line = "$indent•  " + listPrefix.drop(2)
        }
        if (line.trim() == "---" || line.trim() == "___") line = "─────────"

        if (headingStyle != null) {
            withStyle(headingStyle) { appendInline(line) }
        } else {
            appendInline(line)
        }
    }
}

private val INLINE_PATTERN = Regex(
    """(\[([^\]]+)]\((https?://[^)\s]+)\))|(\*\*(.+?)\*\*)|(`([^`]+?)`)|(\*(.+?)\*)|(https?://[^\s)]+)"""
)

private fun AnnotatedString.Builder.appendInline(line: String) {
    var cursor = 0
    INLINE_PATTERN.findAll(line).forEach { match ->
        if (match.range.first > cursor) append(line.substring(cursor, match.range.first))
        when {
            // [texte](url)
            match.groupValues[1].isNotEmpty() -> {
                pushStringAnnotation("URL", match.groupValues[3])
                withStyle(
                    SpanStyle(
                        color = Color(0xFF3B82F6),
                        textDecoration = TextDecoration.Underline
                    )
                ) { append(match.groupValues[2]) }
                pop()
            }

            match.groupValues[4].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[5]) }

            match.groupValues[6].isNotEmpty() ->
                withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                ) { append(match.groupValues[7]) }

            match.groupValues[8].isNotEmpty() ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[9]) }

            // URL brute
            else -> {
                pushStringAnnotation("URL", match.value)
                withStyle(
                    SpanStyle(
                        color = Color(0xFF3B82F6),
                        textDecoration = TextDecoration.Underline
                    )
                ) { append(match.value) }
                pop()
            }
        }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}
