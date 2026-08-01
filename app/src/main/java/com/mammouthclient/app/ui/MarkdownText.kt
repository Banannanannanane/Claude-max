package com.mammouthclient.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Rendu Markdown minimaliste et sans dépendance : blocs de code délimités par ```,
 * titres `#`, gras `**…**`, italique `*…*` et code en ligne `` `…` ``.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    val blocks = remember(text) { splitBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MdBlock.Code -> CodeBlock(block)
                is MdBlock.Paragraph -> Text(
                    text = inlineAnnotated(block.text),
                    style = MaterialTheme.typography.bodyLarge,
                    color = color
                )
            }
        }
    }
}

@Composable
private fun CodeBlock(block: MdBlock.Code) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {
        if (block.language.isNotBlank()) {
            Text(
                text = block.language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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

internal sealed interface MdBlock {
    data class Paragraph(val text: String) : MdBlock
    data class Code(val language: String, val code: String) : MdBlock
}

internal fun splitBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val buffer = StringBuilder()
    var inCode = false
    var language = ""
    val codeBuffer = StringBuilder()

    fun flushParagraph() {
        val content = buffer.toString().trim('\n')
        if (content.isNotBlank()) blocks += MdBlock.Paragraph(content)
        buffer.setLength(0)
    }

    text.lines().forEach { line ->
        if (line.trimStart().startsWith("```")) {
            if (inCode) {
                blocks += MdBlock.Code(language, codeBuffer.toString().trimEnd('\n'))
                codeBuffer.setLength(0)
                language = ""
                inCode = false
            } else {
                flushParagraph()
                language = line.trimStart().removePrefix("```").trim()
                inCode = true
            }
        } else if (inCode) {
            codeBuffer.append(line).append('\n')
        } else {
            buffer.append(line).append('\n')
        }
    }

    if (inCode && codeBuffer.isNotEmpty()) {
        // Bloc de code non refermé (réponse en cours de streaming).
        blocks += MdBlock.Code(language, codeBuffer.toString().trimEnd('\n'))
    }
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
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ")) {
            val indent = line.takeWhile { it == ' ' }
            line = "$indent•  " + line.trimStart().drop(2)
        }

        if (headingStyle != null) {
            withStyle(headingStyle) { appendInline(line) }
        } else {
            appendInline(line)
        }
    }
}

private val INLINE_PATTERN = Regex("(\\*\\*(.+?)\\*\\*)|(`([^`]+?)`)|(\\*(.+?)\\*)")

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(line: String) {
    var cursor = 0
    INLINE_PATTERN.findAll(line).forEach { match ->
        if (match.range.first > cursor) append(line.substring(cursor, match.range.first))
        when {
            match.groupValues[1].isNotEmpty() ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groupValues[2]) }

            match.groupValues[3].isNotEmpty() ->
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                ) { append(match.groupValues[4]) }

            else ->
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groupValues[6]) }
        }
        cursor = match.range.last + 1
    }
    if (cursor < line.length) append(line.substring(cursor))
}
