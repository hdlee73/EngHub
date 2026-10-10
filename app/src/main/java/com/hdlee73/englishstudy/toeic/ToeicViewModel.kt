package com.hdlee73.englishstudy.toeic

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A finished day: [day] is the epoch day, [score] the number of correct answers out of [total]. */
data class ToeicDay(val day: Long, val score: Int, val total: Int)

data class ToeicUiState(
    val loaded: Boolean = false,
    val day: Long = 0,
    val dateLabel: String = "",
    val questions: List<ToeicQuestion> = emptyList(),
    /** The chosen answer (index of the choice) per question answered so far, in order. */
    val answers: List<Int> = emptyList(),
    /** True from "start" until the learner leaves the questions; the questions are shown only while this is true. */
    val inSession: Boolean = false,
    /** True right after an answer, while the explanation is shown. */
    val showingFeedback: Boolean = false,
    val history: List<ToeicDay> = emptyList()
) {
    val total: Int get() = questions.size
    val finished: Boolean get() = total > 0 && answers.size >= total
    val current: ToeicQuestion? get() = questions.getOrNull(if (showingFeedback) answers.size - 1 else answers.size)
    val score: Int get() = answers.indices.count { questions.getOrNull(it)?.answer == answers[it] }
    fun sectionScore(section: String): Pair<Int, Int> {
        val idx = questions.indices.filter { questions[it].section == section }
        return idx.count { answers.getOrNull(it) == questions[it].answer } to idx.size
    }
    /** Consecutive days up to today (or yesterday) on which all questions were answered. */
    val streak: Int
        get() {
            val days = history.map { it.day }.toSet()
            var d = if (day in days) day else day - 1
            var n = 0
            while (d in days) { n++; d-- }
            return n
        }
}

class ToeicViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("toeic", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(ToeicUiState())
    val state: StateFlow<ToeicUiState> = _state.asStateFlow()
    private var bank: List<ToeicQuestion> = emptyList()

    init { refresh() }

    /** Loads (or, after midnight, renews) the day's questions; call again when the tab is shown. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            if (bank.isEmpty()) {
                bank = runCatching {
                    getApplication<Application>().assets.open("toeic_bank.txt").bufferedReader(Charsets.UTF_8).use { ToeicBank.parse(it.readText()) }
                }.getOrDefault(emptyList())
            }
            val date = LocalDate.now()
            val day = date.toEpochDay()
            if (_state.value.loaded && _state.value.day == day) return@launch
            val questions = ToeicBank.today(day, bank)
            val answers = loadAnswers(day).take(questions.size)
            _state.update {
                ToeicUiState(
                    loaded = true, day = day, dateLabel = "${date.monthValue}월 ${date.dayOfMonth}일", questions = questions,
                    answers = answers, history = loadHistory()
                )
            }
        }
    }

    fun start() = _state.update { it.copy(inSession = true, showingFeedback = false) }

    fun choose(index: Int) {
        val s = _state.value
        val q = s.questions.getOrNull(s.answers.size) ?: return
        if (s.showingFeedback || index !in q.options.indices) return
        val answers = s.answers + index
        saveAnswers(s.day, answers)
        var history = s.history
        if (answers.size >= s.total) {
            val score = answers.indices.count { s.questions[it].answer == answers[it] }
            history = (history.filter { it.day != s.day } + ToeicDay(s.day, score, s.total)).sortedBy { it.day }.takeLast(60)
            saveHistory(history)
        }
        _state.update { it.copy(answers = answers, showingFeedback = true, history = history) }
    }

    fun next() = _state.update { it.copy(showingFeedback = false) }

    /** Leaves the questions (the answers so far are kept). */
    fun pause() = _state.update { it.copy(inSession = false, showingFeedback = false) }

    /** Throws away today's answers and starts over with the same questions. */
    fun restart() {
        val s = _state.value
        saveAnswers(s.day, emptyList())
        _state.update { it.copy(answers = emptyList(), inSession = true, showingFeedback = false) }
    }

    private fun loadAnswers(day: Long): List<Int> =
        prefs.getString("ans_$day", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }

    private fun saveAnswers(day: Long, answers: List<Int>) {
        val editor = prefs.edit().putString("ans_$day", answers.joinToString(","))
        // Answers of earlier days are not needed again.
        prefs.all.keys.filter { it.startsWith("ans_") && it.removePrefix("ans_").toLongOrNull()?.let { d -> d < day - 1 } == true }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun loadHistory(): List<ToeicDay> = prefs.getString("history", "").orEmpty().split(';').mapNotNull { item ->
        val p = item.split(':').mapNotNull { it.toLongOrNull() }
        if (p.size == 3) ToeicDay(p[0], p[1].toInt(), p[2].toInt()) else null
    }

    private fun saveHistory(list: List<ToeicDay>) {
        prefs.edit().putString("history", list.joinToString(";") { "${it.day}:${it.score}:${it.total}" }).apply()
    }
}
