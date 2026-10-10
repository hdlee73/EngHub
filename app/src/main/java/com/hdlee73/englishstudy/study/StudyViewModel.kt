package com.hdlee73.englishstudy.study

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hdlee73.englishstudy.dictionary.SavedWordsRepository
import com.hdlee73.englishstudy.dictionary.WordEntry
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.hdlee73.englishstudy.speaking.data.DatasetStore
import com.hdlee73.englishstudy.speaking.model.SentencePair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** Id of the source that stands for the words saved in the dictionary. */
const val SAVED_SOURCE = "saved"

/** Something to study: the saved words, or one speaking dataset. [count] is how many cards or questions it offers. */
data class StudySource(val id: String, val label: String, val count: Int)

enum class StudyStage { SETUP, STUDY, DONE }

data class FlashcardUiState(
    val stage: StudyStage = StudyStage.SETUP,
    val sources: List<StudySource> = listOf(StudySource(SAVED_SOURCE, "저장 단어", 0)),
    val sourceId: String = SAVED_SOURCE,
    /** Cards in the chosen source. */
    val cardCount: Int = 0,
    val masteredCount: Int = 0,
    val deckCounts: Map<DeckFilter, Int> = emptyMap(),
    val filter: DeckFilter = DeckFilter.TO_LEARN,
    val shuffle: Boolean = true,
    /** true: the word (or English sentence) is on the front and the meaning on the back; false: the other way round. */
    val frontIsWord: Boolean = true,
    val card: WordEntry? = null,
    val flipped: Boolean = false,
    val total: Int = 0,
    val known: Int = 0,
    val remaining: Int = 0,
    val missedAnswers: Int = 0,
    val missedWords: List<WordEntry> = emptyList(),
    val message: String? = null
)

enum class QuizStage { SETUP, QUESTION, RESULT }

data class QuizUiState(
    val stage: QuizStage = QuizStage.SETUP,
    val sources: List<StudySource> = listOf(StudySource(SAVED_SOURCE, "저장 단어", 0)),
    val sourceId: String = SAVED_SOURCE,
    /** Questions the chosen source can offer (saved words need an example sentence; sentences need a Korean translation). */
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

/** Flashcard and quiz sessions over the saved words or a speaking dataset. All rules live in the tested session classes. */
class StudyViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SavedWordsRepository.get(application)
    private val datasetStore = DatasetStore(application)
    private val flashStore = FlashDatasetStore(application)
    private val progress: ProgressStore = PrefsProgressStore(application)

    private val _flash = MutableStateFlow(FlashcardUiState())
    val flashcards: StateFlow<FlashcardUiState> = _flash.asStateFlow()
    private val _quiz = MutableStateFlow(QuizUiState())
    val quiz: StateFlow<QuizUiState> = _quiz.asStateFlow()

    private var flashSession: FlashcardSession? = null
    private var quizSession: QuizSession? = null

    // The flashcard lists the learner added (own word lists, not shared with the speaking tab).
    @Volatile private var flashNames: Map<String, String> = emptyMap()
    @Volatile private var flashCards: Map<String, List<WordEntry>> = emptyMap()
    // The speaking datasets that have Korean translations, used by the quiz; loaded in the background.
    @Volatile private var datasetNames: Map<String, String> = emptyMap()
    @Volatile private var datasetPairs: Map<String, List<SentencePair>> = emptyMap()
    @Volatile private var datasetQuizCounts: Map<String, Int> = emptyMap()

    init {
        // The everyday-word list lets the quiz blank hard words instead of arbitrary ones.
        viewModelScope.launch(Dispatchers.IO) {
            if (KeyWordPicker.frequent.isEmpty()) KeyWordPicker.frequent = runCatching {
                application.assets.open("common_words.txt").bufferedReader().useLines { lines -> lines.map { it.trim() }.filter { it.isNotEmpty() }.toHashSet() }
            }.getOrDefault(emptySet())
            refreshSetup()
        }
        viewModelScope.launch { repository.words.collect { refreshSetup() } }
    }

    private fun words(): List<WordEntry> = repository.words.value

    /** Counts shown on the setup screens; also reflects words saved and datasets added while the app is open. */
    fun refreshSetup() {
        updateSetup()
        viewModelScope.launch(Dispatchers.IO) {
            loadDatasets()
            updateSetup()
        }
    }

    private fun loadDatasets() {
        val names = LinkedHashMap<String, String>()
        val pairs = LinkedHashMap<String, List<SentencePair>>()
        val quizCounts = LinkedHashMap<String, Int>()
        // The "saved words" dataset of the speaking tab only repeats the saved words, so it is not offered again.
        for (dataset in datasetStore.list().filter { it.id != DatasetStore.SAVED_WORDS_ID }) {
            // Sentences without a Korean translation cannot be studied here (nothing to put on the other side).
            val loaded = runCatching { datasetStore.load(dataset) }.getOrNull().orEmpty()
                .filter { it.korean.isNotBlank() && it.english.isNotBlank() }
                .distinctBy { it.english.trim().lowercase(Locale.ROOT) }
            if (loaded.isEmpty()) continue
            names[dataset.id] = dataset.name.substringBeforeLast('.')
            pairs[dataset.id] = loaded
            quizCounts[dataset.id] = SentenceQuizBuilder.eligible(loaded).size
        }
        datasetNames = names; datasetPairs = pairs; datasetQuizCounts = quizCounts

        val fNames = LinkedHashMap<String, String>()
        val fCards = LinkedHashMap<String, List<WordEntry>>()
        for (dataset in flashStore.list()) {
            val loaded = runCatching { flashStore.load(dataset) }.getOrNull().orEmpty()
                .distinctBy { it.english.trim().lowercase(Locale.ROOT) }
            fNames[dataset.id] = dataset.name.substringBeforeLast('.')
            fCards[dataset.id] = loaded.mapIndexed { i, p ->
                WordIpa.split(p.english).let { (word, ipa) -> WordEntry(id = i + 1L, word = word, ipa = ipa, korean = p.korean.trim(), english = "", examples = "") }
            }
        }
        flashNames = fNames; flashCards = fCards
    }

    private fun cardsFor(sourceId: String): List<WordEntry> =
        if (sourceId == SAVED_SOURCE) words() else flashCards[sourceId].orEmpty()

    private fun updateSetup() {
        val all = words()
        val map = progress.all()
        val flashSources = listOf(StudySource(SAVED_SOURCE, "저장 단어", all.size)) +
            flashCards.map { (id, cards) -> StudySource(id, flashNames[id].orEmpty(), cards.size) }
        _flash.update { state ->
            val id = state.sourceId.takeIf { chosen -> flashSources.any { it.id == chosen } } ?: SAVED_SOURCE
            val cards = if (id == SAVED_SOURCE) all else flashCards[id].orEmpty()
            state.copy(
                sources = flashSources, sourceId = id, cardCount = cards.size,
                masteredCount = cards.count { (map[progressKey(it.word)] ?: WordProgress()).mastered },
                deckCounts = DeckFilter.values().associateWith { f -> FlashcardDeck.count(cards, map, f) }
            )
        }
        val quizSources = listOf(StudySource(SAVED_SOURCE, "저장 단어", QuizBuilder.eligible(all).size)) +
            datasetQuizCounts.filterValues { it > 0 }.map { (id, count) -> StudySource(id, datasetNames[id].orEmpty(), count) }
        _quiz.update { state ->
            val id = state.sourceId.takeIf { chosen -> quizSources.any { it.id == chosen } } ?: SAVED_SOURCE
            state.copy(sources = quizSources, sourceId = id, eligibleCount = quizSources.first { it.id == id }.count)
        }
    }

    // ---- flashcards ----

    fun clearFlashMessage() = _flash.update { it.copy(message = null) }

    /** Adds the chosen Excel / CSV files as flashcard word lists. */
    fun importFlashDatasets(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = getApplication<Application>().contentResolver
            var added = 0
            var lastError: String? = null
            var lastId: String? = null
            for (uri in uris) {
                runCatching {
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "words.xlsx"
                    runCatching { resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    flashStore.import(uri, name)
                }.onSuccess { added++; lastId = it.id }.onFailure { lastError = it.message ?: "파일을 읽지 못했습니다." }
            }
            loadDatasets()
            lastId?.let { id -> _flash.update { it.copy(sourceId = id) } }
            updateSetup()
            _flash.update {
                it.copy(message = when {
                    added == 0 -> lastError ?: "파일을 읽지 못했습니다."
                    lastError != null -> "${added}개를 추가했습니다. 일부 파일은 읽지 못했습니다: $lastError"
                    else -> "${added}개 단어장을 추가했습니다."
                })
            }
        }
    }

    fun deleteFlashDataset(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            flashStore.delete(id)
            // Progress of its cards stays in the store; it is harmless and returns if the same list is added again.
            loadDatasets()
            updateSetup()
        }
    }

    fun setFlashSource(id: String) { _flash.update { it.copy(sourceId = id) }; updateSetup() }
    fun setFilter(filter: DeckFilter) = _flash.update { it.copy(filter = filter) }
    fun setShuffle(shuffle: Boolean) = _flash.update { it.copy(shuffle = shuffle) }
    fun setFrontIsWord(value: Boolean) = _flash.update { it.copy(frontIsWord = value) }

    fun startFlashcards() {
        val s = _flash.value
        val deck = FlashcardDeck.build(cardsFor(s.sourceId), progress.all(), s.filter, s.shuffle)
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
        if (session.finished) updateSetup()
    }

    /** A new round with only the cards missed in the round that just ended. */
    fun retryMissedCards() {
        val ids = _flash.value.missedWords.map { it.id }.toSet()
        val deck = cardsFor(_flash.value.sourceId).filter { it.id in ids }
        if (deck.isEmpty()) return
        beginFlashcards(deck)
    }

    fun endFlashcards() {
        flashSession = null
        _flash.update { it.copy(stage = StudyStage.SETUP, card = null, flipped = false) }
        updateSetup()
    }

    // ---- quiz ----

    fun setQuizSource(id: String) { _quiz.update { it.copy(sourceId = id) }; updateSetup() }
    fun setQuizCount(count: Int) = _quiz.update { it.copy(requestedCount = count) }

    fun startQuiz() {
        val s = _quiz.value
        val count = s.effectiveCount
        if (count <= 0) return
        beginQuiz(
            if (s.sourceId == SAVED_SOURCE) QuizBuilder.build(words(), count)
            else SentenceQuizBuilder.build(datasetPairs[s.sourceId].orEmpty(), count)
        )
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
        if (question.trackProgress) progress.update(question.word) { Leitner.afterQuiz(it, right, System.currentTimeMillis()) }
        publishQuiz()
    }

    fun nextQuestion() {
        val session = quizSession ?: return
        session.next()
        publishQuiz()
        if (session.finished) updateSetup()
    }

    /** A new quiz made only from the questions answered wrongly. */
    fun retryWrong() {
        val wrong = _quiz.value.wrong
        val ids = wrong.filter { it.trackProgress }.map { it.wordId }.toSet()
        // Saved words get a fresh example sentence; dataset sentences are asked again with the choices reshuffled.
        val fromSaved = QuizBuilder.build(words().filter { it.id in ids }, ids.size)
        val fromDataset = wrong.filterNot { it.trackProgress }.map { it.reshuffled() }
        beginQuiz((fromSaved + fromDataset).shuffled())
    }

    fun endQuiz() {
        quizSession = null
        _quiz.update { it.copy(stage = QuizStage.SETUP, question = null, selected = null) }
        updateSetup()
    }
}
