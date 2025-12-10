package com.shuati.shanghanlun.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.shuati.shanghanlun.ui.theme.TextPrimary
import com.shuati.shanghanlun.ui.theme.ZenGreenPrimary

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    fontSize: TextUnit = 16.sp,
    lineHeight: TextUnit = 28.sp
) {
    val styledText = remember(markdown) {
        buildAnnotatedString {
            val lines = markdown.split("\n")
            lines.forEachIndexed { index, line ->
                val currentLine = line.trim()

                // 1. 处理标题 (#, ##)
                if (currentLine.startsWith("#")) {
                    val level = currentLine.takeWhile { it == '#' }.length
                    val content = currentLine.removePrefix("#".repeat(level)).trim()

                    withStyle(
                        style = SpanStyle(
                            fontWeight = FontWeight.Black,
                            fontSize = fontSize * (1.5f - level * 0.1f),
                            color = ZenGreenPrimary
                        )
                    ) {
                        appendMarkdownContent(content, isHeader = true)
                    }
                }
                // 2. 处理列表 (- , * )
                else if (currentLine.startsWith("- ") || currentLine.startsWith("* ")) {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = ZenGreenPrimary)) {
                        append("• ")
                    }
                    appendMarkdownContent(currentLine.substring(2), baseColor = color)
                }
                // 3. 普通文本
                else {
                    appendMarkdownContent(currentLine, baseColor = color)
                }

                if (index < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    Text(
        text = styledText,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight
    )
}

fun AnnotatedString.Builder.appendMarkdownContent(
    text: String,
    baseColor: Color = TextPrimary,
    isHeader: Boolean = false
) {
    val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
    var lastIndex = 0

    boldRegex.findAll(text).forEach { matchResult ->
        val normalText = text.substring(lastIndex, matchResult.range.first)
        if (normalText.isNotEmpty()) {
            if (!isHeader) {
                withStyle(style = SpanStyle(color = baseColor)) {
                    append(normalText)
                }
            } else {
                append(normalText)
            }
        }

        val boldContent = matchResult.groupValues[1]
        withStyle(
            style = SpanStyle(
                fontWeight = FontWeight.ExtraBold,
                color = if (isHeader) ZenGreenPrimary else Color(0xFF1F2937)
            )
        ) {
            append(boldContent)
        }

        lastIndex = matchResult.range.last + 1
    }

    if (lastIndex < text.length) {
        val remainingText = text.substring(lastIndex)
        if (!isHeader) {
            withStyle(style = SpanStyle(color = baseColor)) {
                append(remainingText)
            }
        } else {
            append(remainingText)
        }
    }
}