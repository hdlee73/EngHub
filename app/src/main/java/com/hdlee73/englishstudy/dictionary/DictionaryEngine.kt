package com.hdlee73.englishstudy.dictionary

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger

/** What the search screen shows right now: a status line, optionally an entry card and spelling suggestions. */
internal data class SearchUpdate(
    val entry: WordEntry?,
    val status: String,
    val canSave: Boolean,
    val suggestions: List<String>,
    val suggestionTitle: String,
    val searching: Boolean
)

/**
 * The dictionary lookup pipeline of the dictionary app, without any UI: reviewed entries and the bundled
 * offline dictionary first, then corpus examples, then the online dictionaries, and an automatic
 * translation only as the last resort. Results are reported through [publish], possibly from a
 * background thread, and a newer search silently cancels the previous one.
 */
internal class DictionaryEngine(context: Context, private val publish: (SearchUpdate) -> Unit) {
    private val searchIo = Executors.newFixedThreadPool(2)
    private val io = Executors.newSingleThreadExecutor()
    private val translateIo = Executors.newFixedThreadPool(3)
    private val translationCache = ConcurrentHashMap<String, String>()
    private val lookupSequence = AtomicInteger(0)
    private val resultCache = mutableMapOf<String, WordEntry>()
    private val requestContext = ThreadLocal<Int>()
    private val connections = ConcurrentHashMap<Int, HttpURLConnection>()
    private val exampleCorpus = BilingualExamples(context.applicationContext)
    private val glossary = LocalGlossary(context.applicationContext)
    private var searchTask: Future<*>? = null
    @Volatile private var closed = false

    init { searchIo.execute { glossary.prewarm() } }

    private data class OnlineResult(val english: String, val korean: String, val examples: List<String>, val allDefinitions: String, val parts: List<String>, val ipa: String = "")

    /** Drops the search in progress; its result is no longer reported. */
    fun cancel() {
        lookupSequence.incrementAndGet()
        searchTask?.cancel(true)
    }

    fun lookup(q: String) {
        if (closed) return
        val requestId = lookupSequence.incrementAndGet()
        searchTask?.cancel(true)
        // Disconnect old requests away from the UI thread, including blocked reads.
        connections.entries.filter { it.key != requestId }.forEach { (id, connection) ->
            io.execute { connection.disconnect(); connections.remove(id, connection) }
        }
        publish(SearchUpdate(null, "‘$q’ 검색 중…", false, emptyList(), "", searching = true))
        // Expressions are not cached: their list of similar expressions is looked up fresh each time.
        val cached = ReviewedEntries.lookup(q) ?: if (' ' in q) null else synchronized(resultCache) { resultCache[q.lowercase(Locale.ROOT)] }
        if (cached != null) {
            publish(SearchUpdate(cached, "검색 결과", true, emptyList(), "", searching = false))
            return
        }
        searchTask = searchIo.submit {
            requestContext.set(requestId)
            fun stale() = requestId != lookupSequence.get() || Thread.currentThread().isInterrupted
            fun present(entry: WordEntry?, text: String, canSave: Boolean = true,
                        suggestions: List<String> = emptyList(), suggestionTitle: String = "혹시 이 단어인가요?") {
                if (requestId != lookupSequence.get() || closed) return
                publish(SearchUpdate(entry, text, canSave, suggestions, suggestionTitle, searching = false))
            }

            // 1) Offline: exact headword, or the base word of an inflected form (went → go).
            val local = glossary.lookup(q)
            val baseForm = if (local == null) WordForms.find(q) { glossary.contains(it) } else null
            val baseLocal = baseForm?.let { glossary.lookup(it.base) }
            val alsoForm = if (local != null) WordForms.irregular(q)?.let { form -> glossary.lookup(form.base)?.let { form to it } } else null
            // Rows such as "asked: ask의 과거형" get the base word's real senses underneath.
            val pointer = local?.let { WordForms.pointerBase(it.korean) }?.let { base -> glossary.lookup(base) }
            val rawOfflineKorean = when {
                local != null && pointer != null -> local.korean.lines().filter { it.isNotBlank() }
                    .joinToString("\n") { "[변화형] " + it.replace(Regex("^\\s*\\d+[.)]\\s*"), "") } + "\n" + StudyMeanings.limit(pointer.korean)
                local != null -> (alsoForm?.let { (form, base) -> form.note(base.korean) + "\n" } ?: "") + MeaningQuality.compact(local.korean)
                baseForm != null && baseLocal != null -> baseForm.korean(MeaningQuality.compact(baseLocal.korean))
                else -> null
            }
            // For an expression the automatic translation of the whole phrase leads (a reverse index of a
            // Korean dictionary can only guess at idioms); the dictionary's own words follow it.
            val phraseAuto = if (rawOfflineKorean != null && ' ' in q) autoMeaning(q, requestId) else null
            val offlineKorean = if (phraseAuto != null) MeaningQuality.merge(phraseAuto.text, rawOfflineKorean) else rawOfflineKorean
            val similarPhrases = if (' ' in q) glossary.similar(q) else emptyList()
            val offlineCredit = when {
                local != null -> localMeaningCredit(local, q)
                baseLocal != null -> localMeaningCredit(baseLocal, baseForm!!.base)
                else -> ""
            } + if (phraseAuto != null) "\n" + MachineTranslation.CREDIT_MEANING else ""
            val exampleCandidates = exampleCorpus.lookup(q)
            // Examples are chosen to fit the Korean meanings shown, most common sense first.
            val humanExamples = ExampleSense.pick(exampleCandidates, offlineKorean.orEmpty())
            val humanExampleText = humanExamples.joinToString("\n") { "${it.english}\t${it.korean}" }
            val humanCredit = if (humanExamples.isEmpty()) "" else humanExamples.joinToString("\n") { it.credit } + "\n문장 모음: ManyThings / Tatoeba"
            val phraseCandidates = if (offlineKorean == null) glossary.suggest(q).filter { !it.equals(q, true) } else emptyList()
            if (stale()) return@submit

            if (offlineKorean != null) {
                val englishLocal = local?.english.orEmpty()
                val initial = WordEntry(word = q, ipa = "", korean = offlineKorean,
                    english = englishLocal.ifBlank { "영어 풀이를 불러오는 중…" }, examples = humanExampleText,
                    source = listOf(offlineCredit, humanCredit).filter { it.isNotBlank() }.joinToString("\n"))
                if (englishLocal.isNotBlank() && humanExamples.isNotEmpty()) {
                    // Complete offline entry: no network needed.
                    synchronized(resultCache) { resultCache[q.lowercase(Locale.ROOT)] = initial }
                    present(initial, "검색 결과 · 기기 내 사전", suggestions = similarPhrases, suggestionTitle = "비슷한 표현")
                    // The bundled data has no pronunciation; add it when the online dictionary answers.
                    val ipa = try { fetchDictionary(q).ipa } catch (_: Exception) { "" }
                    if (ipa.isNotBlank() && !stale()) {
                        val withIpa = initial.copy(ipa = ipa)
                        synchronized(resultCache) { resultCache[q.lowercase(Locale.ROOT)] = withIpa }
                        present(withIpa, "검색 결과 · 기기 내 사전", suggestions = similarPhrases, suggestionTitle = "비슷한 표현")
                    }
                    return@submit
                }
                present(initial, "한글 뜻 표시됨 · 영어 풀이와 예문을 불러오는 중…", canSave = false)
            } else if (phraseCandidates.isNotEmpty()) {
                present(null, "기기 내 사전 후보를 표시했습니다 · 온라인 사전 확인 중…",
                    suggestions = phraseCandidates, suggestionTitle = "이 표현을 찾으세요?")
            }

            // 2) Online: English definitions, examples and (if still needed) a Korean meaning.
            // The automatic dictionary is asked in parallel for the main sense of headwords (palm → 야자나무).
            val autoFuture = if (local != null)
                translateIo.submit<MachineMeaning?> { autoMeaning(q, requestId) } else null
            val online = try { fetchDictionary(q) } catch (e: Exception) {
                if (stale()) return@submit
                if (offlineKorean != null) {
                    val englishLocal = local?.english.orEmpty()
                    present(WordEntry(word = q, ipa = "", korean = offlineKorean,
                        english = englishLocal.ifBlank { "영어 풀이를 불러오지 못했습니다." }, examples = humanExampleText,
                        source = listOf(offlineCredit, humanCredit).filter { it.isNotBlank() }.joinToString("\n")),
                        "기기 내 사전 뜻을 표시했습니다. 영어 풀이는 인터넷 연결 후 다시 검색해 주세요.",
                        suggestions = similarPhrases, suggestionTitle = "비슷한 표현")
                } else {
                    // Not in the English dictionary API either (phrases, names, rare words): try the
                    // automatic dictionary before giving up.
                    val auto = autoMeaning(q, requestId)
                    if (stale()) return@submit
                    if (auto != null) {
                        val entry = WordEntry(word = q, ipa = "", korean = auto.text, english = "", examples = humanExampleText,
                            source = listOf(MachineTranslation.CREDIT_MEANING, humanCredit).filter { it.isNotBlank() }.joinToString("\n"))
                        synchronized(resultCache) { resultCache[q.lowercase(Locale.ROOT)] = entry }
                        present(entry, "검색 결과 · 한글 뜻은 자동 번역입니다", suggestions = similarPhrases, suggestionTitle = "비슷한 표현")
                        return@submit
                    }
                    val suggestions = (phraseCandidates + glossary.spellingCandidates(q) +
                        (try { fetchSuggestions(q) } catch (_: Exception) { emptyList() })).distinct().take(6)
                    present(null, if (suggestions.isNotEmpty()) "‘$q’에 대한 결과가 없습니다. 철자를 확인하거나 아래 단어를 선택해 보세요."
                        else lookupErrorMessage(e), suggestions = suggestions)
                }
                return@submit
            }
            if (stale()) return@submit

            // Korean meanings come from dictionary records first. Only when no curated dictionary
            // has an answer is a clearly labeled automatic translation used.
            val formOf = InflectedForms.find(online.allDefinitions)?.takeIf { !it.base.equals(q, true) }
            val formBaseKorean = formOf?.let { ReviewedEntries.lookup(it.base)?.korean ?: glossary.lookup(it.base)?.korean }
            val formBaseLocal = formOf?.let { glossary.lookup(it.base) }
            var usedAutoMeaning = false
            var supplementCredit = ""
            val koreanBase: String? = when {
                offlineKorean != null -> {
                    // "wanted" is a headword (adjective) and also the past tense of "want".
                    val note = if (local != null && pointer == null && alsoForm == null && formOf != null && formBaseKorean != null)
                        BaseForm(formOf.base, formOf.form).note(formBaseKorean) + "\n" else ""
                    var text = note + offlineKorean
                    if (local != null) {
                        // Each dictionary lists only the senses it has Korean words for (palm had just
                        // the verb, then only the hand senses). Add words the Wiktionary translations
                        // know, then the automatic dictionary's top words for the English entry's main
                        // part of speech and for any part of speech still missing.
                        val extras = MeaningMerge.extras(online.korean, null, text, 2).toMutableList()
                        if (extras.isNotEmpty()) supplementCredit = "보충 뜻: Wiktionary 한국어 어휘 번역 (CC BY-SA 4.0)"
                        val missing = MeaningMerge.missingParts(MeaningMerge.combine(text, extras, emptyList()), online.parts)
                        val wanted = missing + listOfNotNull(online.parts.firstOrNull()?.let { MeaningMerge.koreanPart(it) })
                        val auto = if (wanted.isEmpty()) null else try { autoFuture?.get(4, java.util.concurrent.TimeUnit.SECONDS) }
                            catch (_: Exception) { autoFuture?.cancel(true); null }
                        val more = auto?.let {
                            MeaningMerge.extras(it.text, wanted, text + " " + extras.joinToString(" "), 2, maxWords = 2, topWords = 3)
                        }.orEmpty()
                        if (more.isNotEmpty()) { extras += more; supplementCredit = listOf(supplementCredit, MachineTranslation.CREDIT_SUPPLEMENT).filter { it.isNotBlank() }.joinToString("\n") }
                        text = MeaningMerge.combine(text, extras, online.parts)
                    }
                    text
                }
                formOf != null -> {
                    val baseGloss = formBaseKorean ?: online.korean.ifBlank { null }
                        ?: autoMeaning(formOf.base, requestId)?.also { usedAutoMeaning = true }?.text
                    formOf.korean(baseGloss)
                }
                online.korean.isNotBlank() -> online.korean
                else -> autoMeaning(q, requestId)?.also { usedAutoMeaning = true }?.text
            }
            if (stale()) return@submit
            // The examples may use a sense the lists lack (palm tree → 야자나무): add it from the
            // compound's own dictionary entry so the meanings and the example agree.
            var korean = koreanBase
            if (koreanBase != null && offlineKorean != null && local != null && pointer == null) {
                val shown = ExampleSense.pick(exampleCandidates, koreanBase)
                val compound = ExampleSense.compoundSenses(shown, q, koreanBase) { glossary.lookup(it)?.korean }
                if (compound.isNotEmpty()) {
                    korean = MeaningMerge.combine(koreanBase, compound, online.parts)
                    supplementCredit = listOf(supplementCredit, "예문 속 복합어 뜻: 한국어 위키낱말사전 (CC BY-SA 4.0)").filter { it.isNotBlank() }.joinToString("\n")
                }
            }
            val spelling = if (korean == null) (glossary.spellingCandidates(q) + phraseCandidates).distinct().take(5) else emptyList()
            val english = local?.english?.ifBlank { null } ?: online.english

            // Every example gets a Korean line: corpus pairs are human translations, online English
            // sentences are translated automatically.
            var machineExamples = false
            var missingTranslations = 0
            // Re-pick with the final meaning list so the examples show its first senses.
            val finalHuman = if (korean != null) ExampleSense.pick(exampleCandidates, korean) else humanExamples
            val finalHumanText = finalHuman.joinToString("\n") { "${it.english}\t${it.korean}" }
            val finalHumanCredit = if (finalHuman.isEmpty()) "" else finalHuman.joinToString("\n") { it.credit } + "\n문장 모음: ManyThings / Tatoeba"
            val examples = if (finalHuman.isNotEmpty()) finalHumanText else {
                val translated = translateSentences(online.examples, requestId)
                online.examples.zip(translated).joinToString("\n") { (sentence, ko) ->
                    if (ko == null) missingTranslations++ else machineExamples = true
                    "$sentence\t${ko.orEmpty()}"
                }
            }
            if (stale()) return@submit
            val koreanCredit = when {
                offlineKorean != null -> listOf(offlineCredit, supplementCredit).filter { it.isNotBlank() }.joinToString("\n")
                formOf != null && formBaseLocal != null -> localMeaningCredit(formBaseLocal, formOf.base)
                usedAutoMeaning -> MachineTranslation.CREDIT_MEANING
                online.korean.isNotBlank() -> "한글 의미: Wiktionary 한국어 어휘 번역 (CC BY-SA 4.0)"
                else -> ""
            }
            val entry = WordEntry(word = q, ipa = online.ipa, examples = examples,
                korean = korean ?: "기기 내 사전과 온라인 사전에 한글 뜻풀이가 없습니다." +
                    (if (spelling.isNotEmpty()) " 철자를 확인하거나 아래 추천 단어를 눌러 보세요." else " 아래 네이버 사전에서 확인해 주세요."),
                english = english,
                source = listOf(koreanCredit,
                    if (local?.english.isNullOrBlank()) "영영 풀이: FreeDictionaryAPI / Wiktionary (CC BY-SA 4.0)" else "",
                    finalHumanCredit.ifBlank { if (online.examples.isNotEmpty()) "영어 예문: FreeDictionaryAPI / Wiktionary" else "" },
                    if (machineExamples) MachineTranslation.CREDIT_EXAMPLES else "")
                    .filter { it.isNotBlank() }.joinToString("\n"))
            // An entry with a missing example translation is not cached, so searching again retries.
            if (korean != null && missingTranslations == 0) synchronized(resultCache) { resultCache[q.lowercase(Locale.ROOT)] = entry }
            present(entry, when {
                korean == null -> "한글 뜻풀이를 찾지 못했습니다"
                missingTranslations > 0 -> "검색 결과 · 예문 해석을 불러오지 못했습니다. 다시 검색하면 해석이 추가됩니다."
                usedAutoMeaning || machineExamples || supplementCredit.contains("자동 번역") -> "검색 결과 · 일부 자동 번역 포함"
                else -> "검색 결과"
            }, canSave = korean != null, suggestions = if (spelling.isNotEmpty()) spelling else similarPhrases,
                suggestionTitle = if (spelling.isNotEmpty()) "혹시 이 단어인가요?" else "비슷한 표현")
        }
    }

    /** Automatic dictionary/translation lookup; null when offline, rate limited or not Korean. */
    private fun autoMeaning(word: String, requestId: Int): MachineMeaning? = try {
        requestContext.set(requestId)
        MachineTranslation.meaning(http(MachineTranslation.url(word, true), 2500, 3500), word)
    } catch (_: Exception) { null }

    /** Translates sentences in parallel; a failed sentence is null. Results are cached per session. */
    private fun translateSentences(sentences: List<String>, requestId: Int): List<String?> {
        val futures = sentences.map { sentence ->
            if (translationCache.containsKey(sentence)) null
            else translateIo.submit<String?> {
                try {
                    requestContext.set(requestId)
                    MachineTranslation.sentence(http(MachineTranslation.url(sentence, false), 2500, 3500), sentence)
                        ?.also { translationCache[sentence] = it }
                } catch (_: Exception) { null }
            }
        }
        return sentences.mapIndexed { i, sentence ->
            translationCache[sentence] ?: try { futures[i]?.get(5, java.util.concurrent.TimeUnit.SECONDS) }
            catch (_: Exception) { futures[i]?.cancel(true); null }
        }
    }

    private fun localMeaningCredit(local: LocalMeaning?, word: String): String = when (local?.source) {
        "KOWIKTIONARY" -> "한국어 위키낱말사전 / Kaikki.org 영한 표제어 (CC BY-SA 4.0)\nhttps://ko.wiktionary.org/wiki/" + Uri.encode(word)
        "MERGED" -> "한국어 위키낱말사전 / Kaikki.org 영한 표제어 (CC BY-SA 4.0)\n국립국어원 한국어기초사전 영어 대역의 역색인 (CC BY-SA 2.0 KR)\nhttps://ko.wiktionary.org/wiki/" + Uri.encode(word)
        "NIKL" -> "국립국어원 한국어기초사전 영어 대역의 역색인 (CC BY-SA 2.0 KR)"
        else -> "Wiktionary 사전 원형·뜻풀이 / 자체 검토 자료"
    }

    private fun lookupErrorMessage(error: Exception): String {
        val details = generateSequence<Throwable>(error) { it.cause }
            .mapNotNull { it.message }
            .joinToString(" ")
            .lowercase(Locale.ROOT)
        return when {
            "unknownhost" in details || "unable to resolve host" in details -> "인터넷 주소에 연결하지 못했습니다. 모바일 데이터나 Wi‑Fi 연결을 확인해 주세요."
            "timeout" in details || "timed out" in details -> "사전 서버 응답이 늦습니다. 잠시 후 다시 검색해 주세요."
            "http 404" in details || "not found" in details -> "단어를 찾지 못했습니다. 철자를 확인하거나 다른 표현으로 검색해 주세요."
            "ssl" in details || "certificate" in details -> "보안 연결에 실패했습니다. 기기의 날짜·시간과 네트워크 설정을 확인해 주세요."
            else -> "검색에 실패했습니다. 인터넷 연결을 확인하고 다시 시도해 주세요. (${error.message?.take(100) ?: "연결 오류"})"
        }
    }

    private fun fetchSuggestions(q: String): List<String> {
        val encoded = URLEncoder.encode(q, "UTF-8")
        val response = JSONArray(http("https://api.datamuse.com/sug?s=$encoded&max=6", 2000, 2500))
        val out = linkedSetOf<String>()
        for (i in 0 until response.length()) {
            val word = response.optJSONObject(i)?.optString("word", "")?.trim().orEmpty()
            if (word.isNotBlank() && !word.equals(q, ignoreCase = true)) out += word
        }
        return out.take(5)
    }

    private fun fetchDictionary(q: String): OnlineResult {
        return try {
            fetchOpenDictionary(q)
        } catch (primaryError: Exception) {
            try { fetchLegacyDictionary(q) } catch (fallbackError: Exception) {
                throw IllegalStateException(
                    "FreeDictionaryAPI: ${primaryError.message ?: primaryError.javaClass.simpleName}; " +
                        "Dictionary API: ${fallbackError.message ?: fallbackError.javaClass.simpleName}", primaryError
                )
            }
        }
    }

    private fun fetchOpenDictionary(q: String): OnlineResult {
        val encoded = URLEncoder.encode(q, "UTF-8").replace("+", "%20")
        val root = JSONObject(http("https://freedictionaryapi.com/api/v1/entries/en/$encoded?translations=true", 3500, 5000))
        val entries = root.optJSONArray("entries") ?: JSONArray()
        val allSenses = mutableListOf<JSONObject>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            val senses = entry.optJSONArray("senses") ?: JSONArray()
            for (j in 0 until senses.length()) senses.optJSONObject(j)?.let { sense ->
                sense.put("_partOfSpeech", entry.optString("partOfSpeech"))
                allSenses += sense
            }
        }
        val allDefinitions = allSenses.mapNotNull { it.optString("definition").trim().takeIf(String::isNotBlank) }.distinct()
        if (allDefinitions.isEmpty()) throw IllegalStateException("사전 결과가 없습니다")
        val samples = allSenses.flatMap { sense ->
            val examples = sense.optJSONArray("examples") ?: JSONArray()
            (0 until examples.length()).mapNotNull { examples.optString(it).trim().takeIf(String::isNotBlank) }
        }.filter { SentenceExamples.isSimple(it) }.distinct().take(2)
        val korean = KoreanLexicalMeanings.format(allSenses.map { sense ->
            val translations = sense.optJSONArray("translations") ?: JSONArray()
            val words = (0 until translations.length()).mapNotNull { index ->
                val translation = translations.optJSONObject(index) ?: return@mapNotNull null
                val code = translation.optJSONObject("language")?.optString("code").orEmpty()
                if (code == "ko" || code == "kor") translation.optString("word").takeIf { it.isNotBlank() } else null
            }
            KoreanDictionarySense(sense.optString("_partOfSpeech"), words)
        })
        val parts = (0 until entries.length()).mapNotNull { entries.optJSONObject(it)?.optString("partOfSpeech")?.takeIf(String::isNotBlank) }.distinct()
        val pronunciations = (0 until entries.length()).flatMap { i ->
            val list = entries.optJSONObject(i)?.optJSONArray("pronunciations") ?: JSONArray()
            (0 until list.length()).mapNotNull { j -> list.optJSONObject(j)?.takeIf { it.optString("type").equals("ipa", true) }?.optString("text") }
        }
        return OnlineResult(numbered(allDefinitions.take(StudyMeanings.MAX_SENSES)), korean, samples, allDefinitions.joinToString("\n"), parts, Pronunciation.first(pronunciations))
    }

    private fun fetchLegacyDictionary(q: String): OnlineResult {
        val encoded = URLEncoder.encode(q, "UTF-8").replace("+", "%20")
        val root = JSONArray(http("https://api.dictionaryapi.dev/api/v2/entries/en/$encoded", 2500, 4000))
        val json = root.optJSONObject(0) ?: throw IllegalStateException("사전 결과가 없습니다")
        val meanings = json.optJSONArray("meanings") ?: JSONArray()
        val definitions = linkedSetOf<String>(); val samples = linkedSetOf<String>()
        for (i in 0 until meanings.length()) {
            val defs = meanings.optJSONObject(i)?.optJSONArray("definitions") ?: continue
            for (j in 0 until defs.length()) {
                val item = defs.optJSONObject(j) ?: continue
                item.optString("definition").takeIf(String::isNotBlank)?.let(definitions::add)
                item.optString("example").takeIf { SentenceExamples.isSimple(it) }?.let(samples::add)
            }
        }
        if (definitions.isEmpty()) throw IllegalStateException("정의를 찾지 못했습니다")
        val parts = (0 until meanings.length()).mapNotNull { meanings.optJSONObject(it)?.optString("partOfSpeech")?.takeIf(String::isNotBlank) }.distinct()
        val phonetics = json.optJSONArray("phonetics") ?: JSONArray()
        val ipa = Pronunciation.first(listOf(json.optString("phonetic")) + (0 until phonetics.length()).map { phonetics.optJSONObject(it)?.optString("text") })
        return OnlineResult(numbered(definitions.take(StudyMeanings.MAX_SENSES)), "", samples.take(2), definitions.joinToString("\n"), parts, ipa)
    }

    private fun numbered(lines: List<String>) = lines.mapIndexed { i, d -> "${i + 1}. $d" }.joinToString("\n")

    private fun http(address: String, connectTimeout: Int = 3500, readTimeout: Int = 5000): String {
        val requestId = requestContext.get()
        if (Thread.currentThread().isInterrupted || (requestId != null && requestId != lookupSequence.get())) throw java.io.InterruptedIOException("Search cancelled")
        val c = URL(address).openConnection() as HttpURLConnection
        if (requestId != null) connections[requestId] = c
        c.requestMethod = "GET"; c.connectTimeout = connectTimeout; c.readTimeout = readTimeout
        c.setRequestProperty("User-Agent", "SajeonApp/1.0 (Android)")
        return try { val code = c.responseCode; val stream = if (code in 200..299) c.inputStream else c.errorStream; val body = stream.bufferedReader().use { it.readText() }; if (code !in 200..299) throw IllegalStateException("HTTP $code"); body } finally { if (requestId != null) connections.remove(requestId, c); c.disconnect() }
    }

    fun close() {
        closed = true
        lookupSequence.incrementAndGet()
        searchTask?.cancel(true)
        searchIo.shutdownNow()
        translateIo.shutdownNow()
        connections.values.forEach { connection -> io.execute { connection.disconnect() } }
        io.shutdown()
    }
}
