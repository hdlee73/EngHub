package com.hdlee73.englishstudy.docvoice.core

import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.zip.ZipInputStream

/** EPUB (a zip of XHTML chapters) to plain text, chapters in reading order and paragraphs separated by blank lines. */
object Epub {
    fun extract(data: ByteArray): String {
        val files = HashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(data)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) files[entry.name] = zip.readBytes()
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            throw ExtractException("EPUB 파일을 열지 못했습니다. (${e.message})")
        }
        val container = files["META-INF/container.xml"]?.toString(Charsets.UTF_8) ?: throw ExtractException("EPUB 형식이 아닙니다.")
        val opfPath = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(container)?.groupValues?.get(1)
            ?: throw ExtractException("EPUB 목차 정보를 찾지 못했습니다.")
        val opf = files[opfPath]?.toString(Charsets.UTF_8) ?: throw ExtractException("EPUB 목차 정보를 찾지 못했습니다.")
        val base = opfPath.substringBeforeLast('/', "")
        val manifest = HashMap<String, String>()
        Regex("<item\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf).forEach { m ->
            val id = attr(m.value, "id")
            val href = attr(m.value, "href")
            if (id != null && href != null) manifest[id] = href
        }
        val order = Regex("<itemref\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(opf).mapNotNull { attr(it.value, "idref") }.mapNotNull { manifest[it] }.toList()
            .ifEmpty { manifest.values.filter { it.endsWith(".xhtml", true) || it.endsWith(".html", true) || it.endsWith(".htm", true) } }
        val out = StringBuilder()
        for (href in order) {
            val path = resolve(base, URLDecoder.decode(href.substringBefore('#'), "UTF-8"))
            val html = files[path]?.toString(Charsets.UTF_8) ?: continue
            val text = htmlToText(html)
            if (text.isNotBlank()) out.append(text).append("\n\n")
        }
        return out.toString()
    }

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", RegexOption.IGNORE_CASE).find(tag)?.let { it.groupValues[1].ifEmpty { it.groupValues[2] } }

    private fun resolve(base: String, href: String): String {
        val parts = ArrayList<String>()
        (if (base.isEmpty()) href else "$base/$href").split('/').forEach { seg ->
            when (seg) {
                "", "." -> {}
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(seg)
            }
        }
        return parts.joinToString("/")
    }

    internal fun htmlToText(html: String): String {
        var t = html
            .replace(Regex("<head\\b.*?</head>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<(script|style)\\b.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("\\s+"), " ")
        t = t.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(p|div|h[1-6]|li|tr|blockquote|section|article|title)\\s*>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<[^>]*>"), "")
        t = decodeEntities(t)
        return t.lines().joinToString("\n") { it.trim() }.replace(Regex("\\n{3,}"), "\n\n").trim()
    }

    private fun decodeEntities(s: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(s) { m ->
        val e = m.groupValues[1]
        when {
            e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
            e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
            else -> when (e) {
                "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"; "nbsp" -> " "
                "mdash" -> "\u2014"; "ndash" -> "\u2013"; "hellip" -> "\u2026"
                "lsquo" -> "\u2018"; "rsquo" -> "\u2019"; "ldquo" -> "\u201C"; "rdquo" -> "\u201D"
                else -> m.value
            }
        }
    }
}
