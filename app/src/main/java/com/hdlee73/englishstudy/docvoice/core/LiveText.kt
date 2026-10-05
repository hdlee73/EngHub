package com.hdlee73.englishstudy.docvoice.core

/** 스트리밍 인식기의 날것 출력(영어는 대문자·구두점 없음)을 읽기 좋게 다듬는다. */
object LiveText {
    private val CONTRACTION = Regex("\\bi(?=('(m|ve|ll|d)\\b|\\b))")

    fun format(raw: String, lang: String, final: Boolean): String {
        var t = raw.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return ""
        if (lang == "en") {
            t = t.lowercase()
            t = CONTRACTION.replace(t, "I")
            t = t.replaceFirstChar { it.uppercase() }
        }
        if (final && t.last() !in ".?!。！？…") t += "."
        return t
    }
}
