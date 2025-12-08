package com.example.killquestion.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

object PdfExportManager {

    // 颜色定义 (RGB)
    private val COLOR_PRIMARY = Color.rgb(45, 106, 79) // 深绿
    private val COLOR_PRIMARY_LIGHT = Color.rgb(232, 245, 233) // 浅绿背景
    private val COLOR_TEXT_PRIMARY = Color.rgb(31, 41, 55) // 深灰字
    private val COLOR_BG_GRAY = Color.rgb(241, 245, 249) // 页面底色

    fun exportAndShare(context: Context, cards: List<Pair<String, String>>) {
        try {
            val file = generatePdf(context, cards)
            shareFile(context, file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun generatePdf(context: Context, cards: List<Pair<String, String>>): File {
        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 宽
        val pageHeight = 842 // A4 高

        // --- 画笔设置 ---
        // 1. 页面背景画笔
        val pageBgPaint = Paint().apply { color = COLOR_BG_GRAY; style = Paint.Style.FILL }

        // 2. 卡片背景画笔 (纯白 + 阴影模拟)
        val cardBgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
            setShadowLayer(12f, 0f, 6f, Color.LTGRAY)
        }

        // 3. 标题栏背景画笔
        val headerBgPaint = Paint().apply { color = COLOR_PRIMARY_LIGHT }

        // 4. 序号圆圈画笔
        val indexBgPaint = Paint().apply { color = COLOR_PRIMARY; isAntiAlias = true }

        // 5. 序号文字
        val indexTextPaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // 6. 标题文字
        val titlePaint = TextPaint().apply {
            color = COLOR_PRIMARY
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        // 7. 内容文字 (关键：支持 Spannable)
        val contentPaint = TextPaint().apply {
            color = COLOR_TEXT_PRIMARY
            textSize = 16f
            isAntiAlias = true
        }

        // 8. 署名文字
        val footerPaint = Paint().apply {
            color = Color.GRAY
            textSize = 12f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val margin = 40f
        val cardWidth = pageWidth - margin * 2
        val cardHeight = pageHeight - margin * 2

        cards.forEachIndexed { index, (title, rawContent) ->
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // 1. 填充页面淡灰底色 (提升质感)
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), pageBgPaint)

            // 2. 绘制卡片大白底 (圆角)
            val cardRect = RectF(margin, margin, margin + cardWidth, margin + cardHeight)
            canvas.drawRoundRect(cardRect, 20f, 20f, cardBgPaint)

            // 3. 绘制标题栏 (卡片上部的淡绿色区域)
            // 技巧：先保存画布，裁剪出上半圆角
            canvas.save()
            canvas.clipRect(margin, margin, margin + cardWidth, margin + 100f)
            canvas.drawRoundRect(cardRect, 20f, 20f, headerBgPaint)
            canvas.restore()

            // 4. 绘制序号
            val circleX = margin + 40f
            val circleY = margin + 50f
            canvas.drawCircle(circleX, circleY, 18f, indexBgPaint)
            val fontMetrics = indexTextPaint.fontMetrics
            val baseline = (fontMetrics.descent - fontMetrics.ascent) / 2 - fontMetrics.descent
            canvas.drawText("${index + 1}", circleX, circleY + baseline, indexTextPaint)

            // 5. 绘制标题 (自动换行)
            val titleLayout = StaticLayout.Builder.obtain(title, 0, title.length, titlePaint, (cardWidth - 100).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            canvas.save()
            canvas.translate(margin + 80f, margin + 35f) // 标题位置
            titleLayout.draw(canvas)
            canvas.restore()

            // 6. 绘制分割线
            val dividerY = margin + 100f
            val dividerPaint = Paint().apply { color = Color.rgb(230, 230, 230); strokeWidth = 2f }
            canvas.drawLine(margin, dividerY, margin + cardWidth, dividerY, dividerPaint)

            // 7. 处理并绘制内容 (支持粗体！)
            // 将 Markdown 转换为 Android 的 SpannableString
            val styledContent = parseMarkdownToSpannable(rawContent)

            val contentLayout = StaticLayout.Builder.obtain(styledContent, 0, styledContent.length, contentPaint, (cardWidth - 60).toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(10f, 1f) // 增加行间距
                .build()

            canvas.save()
            canvas.translate(margin + 30f, dividerY + 30f) // 内容起始位置
            contentLayout.draw(canvas)
            canvas.restore()

            // 8. 绘制底部装饰条
            val bottomBarHeight = 10f
            canvas.save()
            canvas.clipRect(margin, margin + cardHeight - bottomBarHeight, margin + cardWidth, margin + cardHeight)
            val bottomPaint = Paint().apply { color = COLOR_PRIMARY }
            canvas.drawRoundRect(cardRect, 20f, 20f, bottomPaint)
            canvas.restore()

            // 9. 署名 (页面最下方)
            canvas.drawText("- Designed by 邝梓濠 -", pageWidth / 2f, pageHeight - 15f, footerPaint)

            pdfDocument.finishPage(page)
        }

        val dir = File(context.cacheDir, "pdfs").apply { mkdirs() }
        val file = File(dir, "智能弱点分析卡片_${System.currentTimeMillis()}.pdf")
        pdfDocument.writeTo(FileOutputStream(file))
        pdfDocument.close()

        return file
    }

    // [核心辅助方法]：解析 Markdown (**text**) 为 SpannableString (Bold style)
    private fun parseMarkdownToSpannable(text: String): CharSequence {
        // 1. 处理列表符号，把 "- " 变成 "• "
        var processedText = text.replace(Regex("^[-*]\\s+"), "• ") // 替换开头的
        processedText = processedText.replace(Regex("\n[-*]\\s+"), "\n• ") // 替换换行后的

        // 2. 移除标题符号 #
        processedText = processedText.replace("#", "")

        val builder = SpannableStringBuilder()

        // 正则匹配 **bold**
        val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
        var lastIndex = 0

        boldRegex.findAll(processedText).forEach { matchResult ->
            // 添加普通文本
            builder.append(processedText.substring(lastIndex, matchResult.range.first))

            // 添加粗体文本
            val boldText = matchResult.groupValues[1]
            val start = builder.length
            builder.append(boldText)
            val end = builder.length

            // 设置粗体样式
            builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

            lastIndex = matchResult.range.last + 1
        }

        // 添加剩余文本
        if (lastIndex < processedText.length) {
            builder.append(processedText.substring(lastIndex))
        }

        return builder
    }

    private fun shareFile(context: Context, file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "分享学习卡片")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}