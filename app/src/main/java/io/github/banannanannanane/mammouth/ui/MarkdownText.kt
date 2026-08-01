package io.github.banannanannanane.mammouth.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.banannanannanane.mammouth.R

/**
 * Renders the subset of Markdown that chat answers actually use: fenced code
 * blocks, headings, bullets, bold, italic and inline code. Anything else is
 * shown verbatim rather than swallowed.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    val blocks = remember(text) { parseBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> CodeBlock(block)
                is MarkdownBlock.Prose -> Text(
                    text = annotate(block.text),
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MarkdownBlock.Code) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 2.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = block.language.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(block.code)) }) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = block.code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
            )
        }
    }
}

private sealed interface MarkdownBlock {
    data class Prose(val text: String) : MarkdownBlock
    data class Code(val code: String, val language: String?) : MarkdownBlock
}

private fun parseBlocks(input: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val prose = StringBuilder()
    val code = StringBuilder()
    var inCode = false
    var language: String? = null

    fun flushProse() {
        val text = prose.toString().trim('\n')
        if (text.isNotBlank()) blocks += MarkdownBlock.Prose(text)
        prose.clear()
    }

    input.lines().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks += MarkdownBlock.Code(code.toString().trimEnd('\n'), language)
                code.clear()
                language = null
                inCode = false
            } else {
                flushProse()
                language = line.trimStart().removePrefix("```").trim().takeIf { it.isNotBlank() }
                inCode = true
            }
        } else if (inCode) {
            code.append(line).append('\n')
        } else {
            prose.append(line).append('\n')
        }
    }

    // An unterminated fence happens constantly while streaming.
    if (inCode && code.isNotEmpty()) blocks += MarkdownBlock.Code(code.toString().trimEnd('\n'), language)
    flushProse()

    return blocks.ifEmpty { listOf(MarkdownBlock.Prose(input)) }
}

private fun annotate(text: String): AnnotatedString = buildAnnotatedString {
    val lines = text.lines()
    lines.forEachIndexed { index, rawLine ->
        val trimmed = rawLine.trimStart()
        when {
            trimmed.startsWith("#") -> {
                val level = trimmed.takeWhile { it == '#' }.length
                pushStyle(
                    SpanStyle(
                        fontWeight = FontWeight.Bold,
                        fontSize = when (level) {
                            1 -> 20.sp
                            2 -> 18.sp
                            else -> 16.sp
                        },
                    ),
                )
                appendInline(trimmed.dropWhile { it == '#' }.trim())
                pop()
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") -> {
                append("  •  ")
                appendInline(trimmed.drop(2))
            }
            trimmed.startsWith("> ") -> {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendInline(trimmed.drop(2))
                pop()
            }
            else -> appendInline(rawLine)
        }
        if (index != lines.lastIndex) append('\n')
    }
}

/** Applies inline emphasis markers, leaving unmatched markers as literal text. */
private fun AnnotatedString.Builder.appendInline(line: String) {
    var index = 0
    while (index < line.length) {
        when {
            line.startsWith("**", index) -> {
                val end = line.indexOf("**", index + 2)
                if (end > index + 2) {
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    appendInline(line.substring(index + 2, end))
                    pop()
                    index = end + 2
                } else {
                    append(line[index]); index++
                }
            }
            line[index] == '`' -> {
                val end = line.indexOf('`', index + 1)
                if (end > index + 1) {
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp))
                    append(line.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(line[index]); index++
                }
            }
            line[index] == '*' || line[index] == '_' -> {
                val marker = line[index]
                val end = line.indexOf(marker, index + 1)
                if (end > index + 1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    appendInline(line.substring(index + 1, end))
                    pop()
                    index = end + 1
                } else {
                    append(line[index]); index++
                }
            }
            else -> {
                append(line[index]); index++
            }
        }
    }
}
