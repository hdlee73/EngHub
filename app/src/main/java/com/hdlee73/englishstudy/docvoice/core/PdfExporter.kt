package com.hdlee73.englishstudy.docvoice.core

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream

/** 받아쓰기 결과를 PDF 로 저장 (NanumGothic 내장 → 한글/영문 모두 깨지지 않음). */
object PdfExporter {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 56f

    fun export(
        context: Context,
        paragraphs: List<Paragraph>,
        title: String,
        includeTime: Boolean,
        showSpeaker: Boolean,
    ): ByteArray {
        val face = try {
            Typeface.createFromAsset(context.assets, "fonts/NanumGothic-Regular.ttf")
        } catch (_: Exception) {
            Typeface.DEFAULT
        }
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = 11f; color = Color.parseColor("#4A4560") }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = 8.5f; color = Color.parseColor("#9A90C0") }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = 18f; color = Color.parseColor("#6B5BB5"); isFakeBoldText = true }
        val rule = Paint().apply { color = Color.parseColor("#E6E1F5"); strokeWidth = 1.2f }
        val contentW = (W - MARGIN * 2).toInt()
        val lineH = body.fontSpacing * 1.25f

        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var canvas = android.graphics.Canvas()
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            canvas = page!!.canvas
            y = MARGIN
        }
        newPage()

        if (title.isNotEmpty()) {
            canvas.drawText(title, MARGIN, y + head.textSize, head)
            y += head.textSize + 10f
            canvas.drawLine(MARGIN, y, W - MARGIN, y, rule)
            y += 18f
        }

        for (p in paragraphs) {
            val prefix = Exporter.prefixOf(p, includeTime, showSpeaker)
            if (prefix.isNotEmpty()) {
                if (y + small.fontSpacing + lineH > H - MARGIN) newPage()
                canvas.drawText(prefix, MARGIN, y + small.textSize, small)
                y += small.fontSpacing + 2f
            }
            val layout = StaticLayout.Builder.obtain(p.text, 0, p.text.length, body, contentW)
                .setLineSpacing(0f, 1.25f)
                .setBreakStrategy(android.text.Layout.BREAK_STRATEGY_SIMPLE)
                .build()
            for (i in 0 until layout.lineCount) {
                if (y + lineH > H - MARGIN) newPage()
                val s = layout.getLineStart(i)
                val e = layout.getLineEnd(i)
                canvas.drawText(p.text, s, e, MARGIN, y + body.textSize, body)
                y += lineH
            }
            y += 12f
        }
        page?.let { doc.finishPage(it) }
        val out = ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }
}
