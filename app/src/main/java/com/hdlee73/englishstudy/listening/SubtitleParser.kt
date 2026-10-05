package com.hdlee73.englishstudy.listening

import com.hdlee73.englishstudy.R

import android.content.Context
import android.net.Uri

data class Cue(val startMs: Long, val endMs: Long, val text: String)

/** Reads .srt / .vtt (timed blocks) and .lrc (one timestamp per line) subtitle files. */
object SubtitleParser {
    private val BOM = 0xFEFF.toChar().toString()
    private val timing = Regex(
        """(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})\s*-->\s*(?:(\d+):)?(\d{1,2}):(\d{2})[,.](\d{1,3})"""
    )
    private val lrcLine = Regex("""^\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\]\s*(.*)$""")
    private val tags = Regex("""<[^>]+>|\{\\[^\}]*\}""")
    private val blockSplit = Regex("""\n\s*\n""")
    private val spaces = Regex("""\s+""")

    /** Returns null when the file cannot be read; an empty list when nothing in it looks like subtitles. */
    fun load(context: Context, uri: String): List<Cue>? {
        val raw = runCatching {
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { it.readBytes() }
        }.getOrNull() ?: return null
        return runCatching { parse(String(raw, Charsets.UTF_8).removePrefix(BOM)) }.getOrNull()
    }

    fun parse(text: String): List<Cue> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        return if (normalized.contains("-->")) parseTimed(normalized) else parseLrc(normalized)
    }

    private fun ms(hours: String, minutes: String, seconds: String, fraction: String): Long {
        val frac = (fraction + "00").take(3).toLong()
        return (hours.ifEmpty { "0" }.toLong() * 3600 + minutes.toLong() * 60 + seconds.toLong()) * 1000 + frac
    }

    private fun parseTimed(text: String): List<Cue> {
        val cues = mutableListOf<Cue>()
        text.split(blockSplit).forEach { block ->
            val lines = block.lines()
            val timingIndex = lines.indexOfFirst { timing.containsMatchIn(it) }
            if (timingIndex < 0) return@forEach
            val m = timing.find(lines[timingIndex]) ?: return@forEach
            val g = m.groupValues
            val body = lines.drop(timingIndex + 1).joinToString(" ") { it.trim() }
                .replace(tags, "").replace(spaces, " ").trim()
            if (body.isNotEmpty()) cues += Cue(ms(g[1], g[2], g[3], g[4]), ms(g[5], g[6], g[7], g[8]), body)
        }
        return cues.sortedBy { it.startMs }
    }

    private fun parseLrc(text: String): List<Cue> {
        val starts = mutableListOf<Pair<Long, String>>()
        text.lines().forEach { line ->
            val m = lrcLine.find(line.trim()) ?: return@forEach
            val g = m.groupValues
            val body = g[4].trim()
            if (body.isNotEmpty()) starts += ms("0", g[1], g[2], g[3]) to body
        }
        starts.sortBy { it.first }
        return starts.mapIndexed { i, (start, body) ->
            Cue(start, if (i + 1 < starts.size) starts[i + 1].first else start + 5_000L, body)
        }
    }
}
