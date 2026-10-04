package com.hdlee73.englishstudy.reading

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hdlee73.englishstudy.dictionary.MachineTranslation
import com.hdlee73.englishstudy.dictionary.SavedWordsRepository
import com.hdlee73.englishstudy.dictionary.WordEntry
import com.hdlee73.englishstudy.translate.Direction
import com.hdlee73.englishstudy.translate.OnlineTranslator
import com.hdlee73.englishstudy.translate.httpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** What the open article shows: the English text, the text with its Korean translation, or the key expressions. */
enum class ReadingMode { TEXT, TRANSLATION, EXPRESSIONS }

/** A key expression with its explanation; [meaning] and [sentenceKo] are null while they are being fetched. */
data class ExpressionItem(
    val expression: String,
    val sentence: String,
    val meaning: String? = null,
    val sentenceKo: String? = null,
    val saved: Boolean = false
)

data class ReadingUiState(
    val loaded: Boolean = false,
    val dateLabel: String = "",
    /** The two texts of the day: intermediate and advanced. */
    val today: List<ReadingArticle> = emptyList(),
    /** True when today's texts come from the web (news), false for the texts bundled with the app. */
    val fromWeb: Boolean = false,
    val fetching: Boolean = false,
    /** Ids of today's texts that have been opened. */
    val readIds: Set<String> = emptySet(),
    val openId: String? = null,
    val mode: ReadingMode = ReadingMode.TEXT,
    val translating: Boolean = false,
    /** Korean text per paragraph of the open article; a missing index is not translated yet. */
    val translations: Map<Int, String> = emptyMap(),
    val expressions: List<ExpressionItem> = emptyList(),
    val loadingExpressions: Boolean = false,
    val message: String? = null,
    /** The translation of the words the learner selected in the open article. */
    val snippet: Snippet? = null
) {
    val open: ReadingArticle? get() = today.firstOrNull { it.id == openId }
    val showTranslation: Boolean get() = mode == ReadingMode.TRANSLATION
}

/** A selected passage and its Korean translation ([korean] is null while loading or when it failed). */
data class Snippet(val text: String, val korean: String?, val loading: Boolean)

class ReadingViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("reading", Context.MODE_PRIVATE)
    private val repository = SavedWordsRepository.get(application)
    private val translator = ReadingTranslator()
    private val snippetTranslator = OnlineTranslator()
    private val _state = MutableStateFlow(ReadingUiState())
    val state: StateFlow<ReadingUiState> = _state.asStateFlow()

    private var library: List<ReadingArticle> = emptyList()
    private var day = LocalDate.now().toEpochDay()
    private var translateJob: Job? = null
    private var snippetJob: Job? = null
    private var expressionJob: Job? = null
    private var fetchJob: Job? = null

    init { refresh() }

    /** Loads today's texts (from the cache, else the web, else the bundled library); call again when the tab is shown. */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            if (library.isEmpty()) {
                library = runCatching {
                    getApplication<Application>().assets.open("reading_articles.txt").bufferedReader(Charsets.UTF_8).use { ReadingLibrary.parse(it.readText()) }
                }.getOrDefault(emptyList())
            }
            val date = LocalDate.now()
            day = date.toEpochDay()
            val cachedToday = loadDaily(day)
            val list = cachedToday ?: bundledForToday()
            val read = prefs.getStringSet("read_$day", emptySet()).orEmpty()
            _state.update {
                // Keep an article open across a refresh unless the day has moved on.
                val stillOpen = it.openId?.takeIf { id -> list.any { a -> a.id == id } }
                it.copy(
                    loaded = true, today = list, fromWeb = cachedToday != null, readIds = read.filter { id -> list.any { a -> a.id == id } }.toSet(),
                    dateLabel = "${date.monthValue}월 ${date.dayOfMonth}일", openId = stillOpen
                )
            }
            prune()
            if (cachedToday == null && fetchJob?.isActive != true) fetchToday(day)
        }
    }

    /** The bundled texts for the day, the easier one first, as a stand-in while offline. */
    private fun bundledForToday(): List<ReadingArticle> {
        val two = DailyReading.pick(day, library, 2).sortedBy { Readability.grade(it.paragraphs.joinToString(" ")) }
        return two.mapIndexed { i, a -> a.copy(level = if (i == 0) "중급" else "고급", credit = "앱에 들어 있는 학습용 글") }
    }

    // ---- downloaded news ----

    private fun fetchToday(forDay: Long) {
        fetchJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(fetching = true) }
            val fetched = runCatching { downloadDaily(forDay) }.getOrNull()
            if (fetched != null && forDay == day) {
                storeDaily(forDay, fetched)
                _state.update {
                    val stillOpen = it.openId?.takeIf { id -> fetched.any { a -> a.id == id } }
                    it.copy(today = fetched, fromWeb = true, fetching = false, openId = stillOpen,
                        readIds = it.readIds.filter { id -> fetched.any { a -> a.id == id } }.toSet())
                }
            } else {
                _state.update { it.copy(fetching = false) }
            }
        }
    }

    /**
     * Takes the next unread Wikinews articles in order (newest first, never the same one twice), trims them to reading
     * length and keeps the easiest as the intermediate text and the hardest as the advanced one.
     */
    private fun downloadDaily(forDay: Long): List<ReadingArticle>? {
        val seen = loadSeen()
        val titles = cachedTitles(forDay) ?: return null
        val candidates = ArrayList<Triple<String, List<String>, Double>>()
        var requests = 0
        for (title in titles) {
            if (title in seen) continue
            if (candidates.size >= 5 || requests >= 14) break
            requests++
            val extract = httpGet(WikinewsSource.extractUrl(title))?.let { WikinewsSource.parseExtract(it) } ?: continue
            val trimmed = WikinewsSource.trim(WikinewsSource.paragraphs(extract.second))
            if (trimmed == null) { seen += title; continue }
            candidates += Triple(title, trimmed, Readability.grade(trimmed.joinToString(" ")))
        }
        if (candidates.size < 2) { saveSeen(seen); return null }
        val easy = candidates.minByOrNull { it.third }!!
        val hard = candidates.filter { it !== easy }.maxByOrNull { it.third }!!
        seen += easy.first; seen += hard.first
        saveSeen(seen)
        fun article(n: Int, c: Triple<String, List<String>, Double>, level: String) =
            ReadingArticle("w${forDay}_$n", "뉴스", c.first, c.second, level, WikinewsSource.CREDIT, WikinewsSource.pageUrl(c.first))
        return listOf(article(1, easy, "중급"), article(2, hard, "고급"))
    }

    private fun cachedTitles(forDay: Long): List<String>? {
        if (prefs.getLong("wn_titles_day", -1) == forDay) {
            val cached = runCatching { JSONArray(prefs.getString("wn_titles", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) return cached
        }
        val titles = httpGet(WikinewsSource.titlesUrl())?.let { WikinewsSource.parseTitles(it) }.orEmpty()
        if (titles.isEmpty()) return null
        prefs.edit().putString("wn_titles", JSONArray(titles).toString()).putLong("wn_titles_day", forDay).apply()
        return titles
    }

    private fun loadSeen(): MutableSet<String> = runCatching {
        val a = JSONArray(prefs.getString("wn_seen", "[]"))
        (0 until a.length()).mapTo(LinkedHashSet()) { a.getString(it) }
    }.getOrDefault(LinkedHashSet())

    private fun saveSeen(seen: Set<String>) {
        prefs.edit().putString("wn_seen", JSONArray(seen.toList().takeLast(400)).toString()).apply()
    }

    private fun storeDaily(forDay: Long, list: List<ReadingArticle>) {
        val array = JSONArray()
        list.forEach { a ->
            array.put(JSONObject().put("id", a.id).put("title", a.title).put("level", a.level).put("credit", a.credit).put("url", a.url)
                .put("topic", a.topic).put("paragraphs", JSONArray(a.paragraphs)))
        }
        prefs.edit().putString("daily_$forDay", array.toString()).apply()
    }

    private fun loadDaily(forDay: Long): List<ReadingArticle>? = runCatching {
        val array = JSONArray(prefs.getString("daily_$forDay", null) ?: return null)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val p = o.getJSONArray("paragraphs")
            ReadingArticle(o.getString("id"), o.getString("topic"), o.getString("title"), (0 until p.length()).map { p.getString(it) },
                o.getString("level"), o.getString("credit"), o.getString("url"))
        }.takeIf { it.size >= 2 }
    }.getOrNull()

    /** Forgets what belongs to earlier days: read marks, downloaded texts, their translations and expressions. */
    private fun prune() {
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            val old = when {
                key.startsWith("read_") -> key.removePrefix("read_").toLongOrNull()?.let { it != day } ?: false
                key.startsWith("daily_") -> key.removePrefix("daily_").toLongOrNull()?.let { it < day - 1 } ?: false
                key.startsWith("tr_w") || key.startsWith("ex_w") -> key.substringAfter('w').substringBefore('_').toLongOrNull()?.let { it < day - 1 } ?: false
                else -> false
            }
            if (old) editor.remove(key)
        }
        editor.apply()
    }

    // ---- reading ----

    fun open(id: String) {
        val read = (prefs.getStringSet("read_$day", emptySet()).orEmpty() + id).toSet()
        prefs.edit().putStringSet("read_$day", read).apply()
        translateJob?.cancel(); expressionJob?.cancel()
        _state.update {
            it.copy(openId = id, readIds = it.readIds + id, mode = ReadingMode.TEXT, translating = false, translations = cached(id),
                expressions = emptyList(), loadingExpressions = false, snippet = null)
        }
    }

    fun close() {
        translateJob?.cancel(); snippetJob?.cancel(); expressionJob?.cancel()
        _state.update {
            it.copy(openId = null, mode = ReadingMode.TEXT, translating = false, translations = emptyMap(), expressions = emptyList(),
                loadingExpressions = false, snippet = null)
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Translates the selected passage into Korean. */
    fun translateSnippet(text: String) {
        snippetJob?.cancel()
        _state.update { it.copy(snippet = Snippet(text, null, true)) }
        snippetJob = viewModelScope.launch(Dispatchers.IO) {
            val korean = snippetTranslator.translate(text, Direction.EN_KO)
            _state.update {
                if (it.snippet?.text != text) it
                else it.copy(snippet = Snippet(text, korean, false), message = if (korean == null) "번역을 가져오지 못했습니다. 인터넷 연결을 확인해 주세요." else it.message)
            }
        }
    }

    fun clearSnippet() {
        snippetJob?.cancel()
        _state.update { if (it.snippet == null) it else it.copy(snippet = null) }
    }

    /** Switches between the English text, the translation and the key expressions. */
    fun setMode(mode: ReadingMode) {
        val article = _state.value.open ?: return
        translateJob?.cancel()
        _state.update { it.copy(mode = mode, translating = false, snippet = null) }
        when (mode) {
            ReadingMode.TRANSLATION -> translateAll(article)
            ReadingMode.EXPRESSIONS -> loadExpressions(article)
            ReadingMode.TEXT -> Unit
        }
    }

    /** The first time, every paragraph is translated online and kept for next time. */
    private fun translateAll(article: ReadingArticle) {
        if (_state.value.translations.size >= article.paragraphs.size) return
        _state.update { it.copy(translating = true) }
        translateJob = viewModelScope.launch(Dispatchers.IO) {
            var failed = false
            for ((index, paragraph) in article.paragraphs.withIndex()) {
                if (_state.value.translations.containsKey(index)) continue
                val korean = translator.translate(paragraph)
                if (korean == null) { failed = true; break }
                _state.update { if (it.openId == article.id) it.copy(translations = it.translations + (index to korean)) else it }
            }
            val done = _state.value.translations
            if (_state.value.openId == article.id && done.size >= article.paragraphs.size) store(article.id, article.paragraphs.size, done)
            _state.update {
                it.copy(
                    translating = false,
                    message = if (failed) "번역을 가져오지 못했습니다. 인터넷 연결을 확인하고 다시 눌러 주세요." else it.message
                )
            }
        }
    }

    // ---- key expressions ----

    private fun loadExpressions(article: ReadingArticle) {
        if (_state.value.expressions.isNotEmpty()) return
        val base = KeyExpressions.extract(article.paragraphs)
        val saved = cachedExpressions(article.id)
        val items = base.map { e -> saved[e.expression] ?: ExpressionItem(e.expression, e.sentence) }
        _state.update { it.copy(expressions = items, loadingExpressions = items.any { i -> i.meaning == null }) }
        if (items.none { it.meaning == null }) return
        expressionJob?.cancel()
        expressionJob = viewModelScope.launch(Dispatchers.IO) {
            for ((index, item) in items.withIndex()) {
                if (item.meaning != null) continue
                val meaning = httpGet(MachineTranslation.url(item.expression, true))?.let { MachineTranslation.meaning(it, item.expression)?.text }
                val sentenceKo = item.sentenceKo ?: translator.translate(item.sentence)
                _state.update {
                    if (it.openId != article.id || index >= it.expressions.size) it
                    else it.copy(expressions = it.expressions.toMutableList().also { list -> list[index] = list[index].copy(meaning = meaning ?: list[index].meaning, sentenceKo = sentenceKo ?: list[index].sentenceKo) })
                }
            }
            val finished = _state.value.expressions
            if (_state.value.openId == article.id && finished.all { it.meaning != null }) storeExpressions(article.id, finished)
            _state.update {
                it.copy(
                    loadingExpressions = false,
                    message = if (finished.any { i -> i.meaning == null }) "일부 설명을 가져오지 못했습니다. 인터넷 연결을 확인해 주세요." else it.message
                )
            }
        }
    }

    /** Saves a key expression with its meaning and the sentence it came from to the word list. */
    fun saveExpression(index: Int) {
        val article = _state.value.open ?: return
        val item = _state.value.expressions.getOrNull(index) ?: return
        val meaning = item.meaning ?: return
        val entry = WordEntry(
            word = item.expression, ipa = "", korean = meaning, english = "",
            examples = item.sentence + "\t" + item.sentenceKo.orEmpty(),
            source = "리딩 · ${article.title}" + if (article.credit.isNotBlank()) " (${article.credit})" else ""
        )
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repository.save(entry)
            _state.update {
                val list = it.expressions.toMutableList()
                if (ok && index < list.size) list[index] = list[index].copy(saved = true)
                it.copy(expressions = list, message = if (ok) "‘${item.expression}’ 표현을 단어장에 저장했습니다." else "저장하지 못했습니다.")
            }
        }
    }

    private fun cachedExpressions(id: String): Map<String, ExpressionItem> = runCatching {
        val a = JSONArray(prefs.getString("ex_$id", null) ?: return emptyMap())
        (0 until a.length()).map { a.getJSONObject(it) }.associate { o ->
            o.getString("e") to ExpressionItem(o.getString("e"), o.getString("s"), o.getString("m"), o.getString("k"))
        }
    }.getOrDefault(emptyMap())

    private fun storeExpressions(id: String, items: List<ExpressionItem>) {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("e", it.expression).put("s", it.sentence).put("m", it.meaning.orEmpty()).put("k", it.sentenceKo.orEmpty())) }
        prefs.edit().putString("ex_$id", a.toString()).apply()
    }

    private fun cached(id: String): Map<Int, String> = runCatching {
        val array = JSONArray(prefs.getString("tr_$id", null) ?: return emptyMap())
        (0 until array.length()).associateWith { array.getString(it) }
    }.getOrDefault(emptyMap())

    private fun store(id: String, size: Int, translations: Map<Int, String>) {
        val array = JSONArray()
        for (i in 0 until size) array.put(translations[i].orEmpty())
        prefs.edit().putString("tr_$id", array.toString()).apply()
    }
}
