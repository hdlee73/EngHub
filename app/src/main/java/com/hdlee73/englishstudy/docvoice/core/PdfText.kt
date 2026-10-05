package com.hdlee73.englishstudy.docvoice.core

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

object PdfText {
    @Volatile private var inited = false

    fun reader(context: Context): (ByteArray) -> String = { data ->
        if (!inited) {
            PDFBoxResourceLoader.init(context.applicationContext)
            inited = true
        }
        try {
            PDDocument.load(data).use { doc ->
                if (doc.isEncrypted) throw ExtractException("암호가 걸린 PDF 는 읽을 수 없습니다.")
                PDFTextStripper().apply { sortByPosition = true }.getText(doc)
            }
        } catch (e: ExtractException) {
            throw e
        } catch (e: Exception) {
            throw ExtractException("PDF 를 읽지 못했습니다. (${e.message})")
        }
    }
}
