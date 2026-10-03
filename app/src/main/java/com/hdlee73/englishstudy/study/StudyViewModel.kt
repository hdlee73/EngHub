package com.hdlee73.englishstudy.study

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hdlee73.englishstudy.dictionary.SavedWordsRepository
import com.hdlee73.englishstudy.dictionary.WordEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StudyStage { SETUP, STUDY, DONE }

data class FlashcardUiState(
    val stage: StudyStage = StudyStage.SETUP,
    val savedCount: Int = 0,
    val masteredCount: Int = 0,
    val deckCounts: Map<DeckFilter, Int> = emptyMap(),
    val filter: DeckFilter = DeckFilter.TO_LEARN,
    val shuffle: Boolean = true,
    /** true: the word is on the front and the meaning on the back; false: the other way round. */
    val frontIsWord: Boolean = true,
    val card: WordEntry? = null,
    val flipped: Boolean = false,
    val total: Int = 0,
    val known: Int = 0,
    val remaining: Int = 0,
    val missedAnswers: Int = 0,
    val missedWords: List<WordEntry> = emptyList()
)

enum class QuizStage { SETUP, QUESTION, RESULT }

data class QuizUiState(
    val stage: QuizStage = QuizStage.SETUP,
    val savedCount: Int = 0,
    /** Saved words that have an example sentence the quiz can use. */
    val eligibleCount: Int = 0,
    val requestedCount: Int = 10,
    val question: QuizQuestion? = null,
    val index: Int = 0,
    val total: Int = 0,
    val selected: Int? = null,
    val correct: Int = 0,
    val wrong: List<QuizQuestion> = emptyList()
) {
    /** 5 / 10 / 20 questions, plus "all" when that is a different number. */
    val countOptions: List<Int> get() = (listOf(5, 10, 20).filter { it < eligibleCount } + listOf(eligibleCount)).filter { it > 0 }
    val effectiveCount: Int get() = minOf(requestedCount, eligibleCount)
}

/** Flashcard and quiz sessions over the saved words. All rules live in the tested session classes. */
class StudyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SavedWordsRepository.get(application)
    private val progress: ProgressStore = PrefsProgressStore(application)

    private val _flash = MutableStateFlow(FlashcardUiState())
    val flashcards: StateFlow<FlashcardUiState> = _flash.asStateFlow()
    private val _quiz = MutableStateFlow(QuizUiState())
    val quiz: StateFlow<QuizUiState> = _quiz.asStateFlow()

    private var flashSession: FlashcardSession? = null
    private var quizSession: QuizSession? = null

    init {
        viewModelScope.launch { repository.words.collect { refreshSetup() } }
    }

    private fun words(): List<WordEntry> = repository.words.value

    /** Counts shown on the setup screens; also reflects words saved or deleted while the app is open. */
    fun refreshSetup() {
        val all = words()
        val map = progress.all()
        _flash.update {
            it.copy(
                savedCount = all.size,
                masteredCount = all.count { w -> (map[progressKey(w.word)] ?: WordProgress()).mastered },
                deckCounts = DeckFilter.values().associateWith { f -> FlashcardDeck.count(all, map, f) }
            )
        }
        _quiz.update { it.copy(savedCount = all.size, eligibleCount = QuizBuilder.eligible(all).size) }
    }

    // ---- flashcards ----

    fun setFilter(filter: DeckFilter) = _flash.update { it.copy(filter = filter) }
    fun setShuffle(shuffle: Boolean) = _flash.update { it.copy(shuffle = shuffle) }
    fun setFrontIsWord(value: Boolean) = _flash.update { it.copy(frontIsWord = value) }

    fun startFlashcards() {
        val s = _flash.value
        val deck = FlashcardDeck.build(words(), progress.all(), s.filter, s.shuffle)
        if (deck.isEmpty()) return
        beginFlashcards(deck)
    }

    private fun beginFlashcards(deck: List<WordEntry>) {
        flashSession = FlashcardSession(deck)
        publishFlash(StudyStage.STUDY)
    }

    private fun publishFlash(stage: StudyStage) {
        val session = flashSession
        _flash.update {
            it.copy(
                stage = stage, card = session?.current, flipped = false,
                total = session?.total ?: 0, known = session?.known ?: 0, remaining = session?.remaining ?: 0,
                missedAnswers = session?.missedAnswers ?: 0, missedWords = session?.missedCards.orEmpty()
            )
        }
    }

    fun flip() = _flash.update { if (it.stage == StudyStage.STUDY) it.copy(flipped = !it.flipped) else it }

    fun answerCard(knows: Boolean) {
        val session = flashSession ?: return
        val card = session.answer(knows) ?: return
        progress.update(card.word) { Leitner.afterCard(it, knows, System.currentTimeMillis()) }
        publishFlash(if (session.finished) StudyStage.DONE else StudyStage.STUDY)
        if (session.finished) refreshSetup()
    }

    /** A new round with only the cards missed in the round that just ended. */
    fun retryMissedCards() {
        val ids = _flash.value.missedWords.map { it.id }.toSet()
        val deck = words().filter { it.id in ids }
        if (deck.isEmpty()) return
        beginFlashcards(deck)
    }

    fun endFlashcards() {
        flashSession = null
        _flash.update { it.copy(stage = StudyStage.SETUP, card = null, flipped = false) }
        refreshSetup()
    }

    // ---- quiz ----

    fun setQuizCount(count: Int) = _quiz.update { it.copy(requestedCount = count) }

    fun startQuiz() {
        val count = _quiz.value.effectiveCount
        if (count <= 0) return
        beginQuiz(QuizBuilder.build(words(), count))
    }

    private fun beginQuiz(questions: List<QuizQuestion>) {
        if (questions.isEmpty()) return
        quizSession = QuizSession(questions)
        publishQuiz()
    }

    private fun publishQuiz() {
        val session = quizSession ?: return
        _quiz.update {
            it.copy(
                stage = if (session.finished) QuizStage.RESULT else QuizStage.QUESTION,
                question = session.current, index = session.index, total = session.total,
                selected = session.selected, correct = session.correctCount, wrong = session.wrong
            )
        }
    }

    fun chooseAnswer(choice: Int) {
        val session = quizSession ?: return
        val question = session.current ?: return
        val right = session.choose(choice) ?: return
        progress.update(question.word) { Leitner.afterQuiz(it, right, System.currentTimeMillis()) }
        publishQuiz()
    }

    fun nextQuestion() {
        val session = quizSession ?: return
        session.next()
        publishQuiz()
        if (session.finished) refreshSetup()
    }

    /** A new quiz made only from the words answered wrongly. */
    fun retryWrong() {
        val ids = _quiz.value.wrong.map { it.wordId }.toSet()
        val entries = words().filter { it.id in ids }
        beginQuiz(QuizBuilder.build(entries, entries.size))
    }

    fun endQuiz() {
        quizSession = null
        _quiz.update { it.copy(stage = QuizStage.SETUP, question = null, selected = null) }
        refreshSetup()
    }
}
