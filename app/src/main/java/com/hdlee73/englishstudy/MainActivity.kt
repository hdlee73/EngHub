package com.hdlee73.englishstudy

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.hdlee73.englishstudy.dictionary.DictionaryViewModel
import com.hdlee73.englishstudy.dictionary.ExportWorker
import com.hdlee73.englishstudy.dictionary.WordSpeaker
import com.hdlee73.englishstudy.docvoice.DocVoiceLink
import com.hdlee73.englishstudy.docvoice.DocVoiceViewModel
import com.hdlee73.englishstudy.docvoice.ui.DocVoiceScreen
import com.hdlee73.englishstudy.listening.ListeningLink
import com.hdlee73.englishstudy.listening.ListeningScreen
import com.hdlee73.englishstudy.reading.ReadingViewModel
import com.hdlee73.englishstudy.translate.TranslateViewModel
import com.hdlee73.englishstudy.ui.TranslateScreen
import com.hdlee73.englishstudy.speaking.LearningViewModel
import com.hdlee73.englishstudy.speaking.model.LearningMode
import com.hdlee73.englishstudy.speaking.model.LessonPhase
import com.hdlee73.englishstudy.speaking.model.RecognitionStrictness
import com.hdlee73.englishstudy.speaking.model.VoiceAccent
import com.hdlee73.englishstudy.speaking.speech.RetryEvaluator
import com.hdlee73.englishstudy.speaking.speech.SpeechEngine
import com.hdlee73.englishstudy.speaking.ui.SpeakFlowApp
import com.hdlee73.englishstudy.study.SavedWordSentences
import com.hdlee73.englishstudy.study.StudyViewModel
import com.hdlee73.englishstudy.ui.AppRoot
import com.hdlee73.englishstudy.ui.AppTab
import com.hdlee73.englishstudy.ui.DictionaryScreen
import com.hdlee73.englishstudy.ui.FlashcardScreen
import com.hdlee73.englishstudy.ui.QuizScreen
import com.hdlee73.englishstudy.ui.ReadingScreen

class MainActivity : AppCompatActivity() {
    private val learningVm: LearningViewModel by viewModels()
    private val dictionaryVm: DictionaryViewModel by viewModels()
    private val studyVm: StudyViewModel by viewModels()
    private val readingVm: ReadingViewModel by viewModels()
    private val translateVm: TranslateViewModel by viewModels()
    private val docVoiceVm: DocVoiceViewModel by viewModels()
    private lateinit var speech: SpeechEngine
    private lateinit var wordSpeaker: WordSpeaker

    private var currentTab = AppTab.DICTIONARY
    /** An audio file opened from elsewhere, or a widget / notification tap: the Listening tab takes it over. */
    private var listeningIntent by mutableStateOf<Intent?>(null)
    /** Bumped when a DocVoice notification is tapped, so the DocVoice tab comes to the front. */
    private var docVoiceRequest by mutableStateOf(0)
    private var hasOpenDialog = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null && ListeningLink.isForListening(intent)) listeningIntent = intent
        if (savedInstanceState == null && DocVoiceLink.isForDocVoice(intent)) docVoiceRequest++
        // Fetches each new BBC 6 Minute English episode into the Listening tab's "6min" folder.
        com.hdlee73.englishstudy.listening.SixMinuteEnglish.schedule(applicationContext)
        wordSpeaker = WordSpeaker(this) { notice(it) }
        speech = SpeechEngine(
            context = this,
            onPromptFinished = learningVm::onPromptFinished,
            onRecognizerReady = learningVm::onRecognizerReady,
            onPartialResult = learningVm::onPartialRecognition,
            onResult = learningVm::onRecognition,
            onUnavailable = learningVm::onRecognitionUnavailable,
            onBluetoothDevicesChanged = learningVm::onBluetoothDevicesChanged,
            onNotice = learningVm::showMessage,
            recordingTee = learningVm::onAudioChunk,
            isRecording = learningVm::isRecording,
            onInputDeviceChanged = learningVm::onMicrophoneChanged
        )
        setContent {
            val learning by learningVm.state.collectAsStateWithLifecycle()
            val dictionary by dictionaryVm.state.collectAsStateWithLifecycle()
            val saved by dictionaryVm.savedWords.collectAsStateWithLifecycle()
            val flash by studyVm.flashcards.collectAsStateWithLifecycle()
            val quiz by studyVm.quiz.collectAsStateWithLifecycle()
            val reading by readingVm.state.collectAsStateWithLifecycle()
            val translation by translateVm.state.collectAsStateWithLifecycle()

            var tabIndex by rememberSaveable { mutableStateOf(0) }
            // The tab a dictionary lookup was started from, so the dictionary can offer a way back to it.
            var returnTab by rememberSaveable { mutableStateOf<Int?>(null) }
            LaunchedEffect(docVoiceRequest) {
                if (docVoiceRequest > 0) {
                    if (tabIndex == AppTab.SPEAKING.ordinal) {
                        learningVm.pauseForBackground()
                        speech.stop()
                    }
                    tabIndex = AppTab.DOCVOICE.ordinal
                }
            }
            LaunchedEffect(listeningIntent) {
                if (listeningIntent != null) {
                    if (tabIndex == AppTab.SPEAKING.ordinal) {
                        learningVm.pauseForBackground()
                        speech.stop()
                    }
                    tabIndex = AppTab.LISTENING.ordinal
                }
            }
            val tab = AppTab.values()[tabIndex.coerceIn(0, AppTab.values().size - 1)]
            var settingsOpen by rememberSaveable { mutableStateOf(false) }
            var datasetsOpen by rememberSaveable { mutableStateOf(false) }
            var bluetoothPermissionRequested by rememberSaveable { mutableStateOf(false) }
            var exportFormat by rememberSaveable { mutableStateOf(1) }
            SideEffect {
                currentTab = tab
                hasOpenDialog = settingsOpen || datasetsOpen || learning.editingDataset != null
            }

            val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                learningVm.importDatasets(uris)
            }
            val audioPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                val microphoneGranted = grants[Manifest.permission.RECORD_AUDIO]
                    ?: (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                if (microphoneGranted) learningVm.retryListening()
                else learningVm.onRecognitionUnavailable("마이크 권한이 거부되었습니다.")
            }
            val flashListPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                studyVm.importFlashDatasets(uris)
            }
            val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            val exportLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            ) { uri ->
                if (uri != null) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    } catch (_: SecurityException) {
                    } catch (_: UnsupportedOperationException) {
                    }
                    val request = OneTimeWorkRequestBuilder<ExportWorker>()
                        .setInputData(workDataOf(ExportWorker.KEY_URI to uri.toString(), ExportWorker.KEY_FORMAT to exportFormat))
                        .build()
                    WorkManager.getInstance(this@MainActivity).enqueue(request)
                    dictionaryVm.showMessage("파일 저장을 백그라운드에서 시작했습니다.")
                }
            }

            LaunchedEffect(learning.phase, learning.position, learning.listenRequestId, learning.promptRequestId) {
                speech.mirrorAudio = learning.settings.mirrorAudio
                speech.phoneMic = learning.settings.phoneMic
                speech.bluetoothInputAddress = learning.settings.bluetoothInputAddress
                speech.outdoorAudio = learning.settings.outdoorAudio
                speech.biasTowardExpected = learning.settings.strictness != RecognitionStrictness.STRICT
                speech.recognitionLanguage = if (learning.settings.voiceAccent == VoiceAccent.UK) "en-GB" else "en-US"
                when (learning.phase) {
                    LessonPhase.SPEAKING -> learning.current?.let {
                        val korean = learning.settings.mode == LearningMode.TRANSLATION && it.korean.isNotBlank()
                        speech.speak(
                            text = if (korean) it.korean else it.english,
                            korean = korean,
                            accent = learning.settings.voiceAccent
                        )
                    }
                    LessonPhase.LISTENING -> {
                        val requiredPermissions = buildList {
                            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                                add(Manifest.permission.RECORD_AUDIO)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED &&
                                !bluetoothPermissionRequested
                            ) {
                                add(Manifest.permission.BLUETOOTH_CONNECT)
                            }
                        }
                        if (requiredPermissions.isEmpty()) {
                            val expected = learning.current?.english.orEmpty()
                            speech.listen(
                                if (learning.settings.strictness == RecognitionStrictness.STRICT) expected
                                else RetryEvaluator.remaining(expected, learning.matchedWords)
                            )
                        } else {
                            if (Manifest.permission.BLUETOOTH_CONNECT in requiredPermissions) bluetoothPermissionRequested = true
                            audioPermissions.launch(requiredPermissions.toTypedArray())
                        }
                    }
                    LessonPhase.RETRYING, LessonPhase.PAUSED, LessonPhase.COMPLETE, LessonPhase.IDLE, LessonPhase.TIMED_OUT -> speech.stop()
                    else -> Unit
                }
            }

            LaunchedEffect(learning.feedbackSequence) {
                val success = learning.feedbackSuccess ?: return@LaunchedEffect
                val sequence = learning.feedbackSequence
                if (sequence > 0L) {
                    if (success) {
                        speech.playSuccessSound { learningVm.onFeedbackFinished(sequence, true) }
                    } else {
                        // Incorrect/unfinished attempts remain completely silent.
                        learningVm.onFeedbackFinished(sequence, false)
                    }
                }
            }

            val savedSentences = remember(saved) { SavedWordSentences.fromEntries(saved) }
            // The saved words' example sentences become a dataset of their own, always up to date.
            LaunchedEffect(savedSentences) { learningVm.syncSavedWordsDataset(savedSentences) }

            AppRoot(
                tab = tab,
                onTab = { next ->
                    if (tab == AppTab.SPEAKING && next != AppTab.SPEAKING) {
                        learningVm.pauseForBackground()
                        speech.stop()
                    }
                    if (next != AppTab.DICTIONARY) wordSpeaker.stop()
                    // Words saved or datasets loaded since the last visit show up in the study setup screens.
                    if (next == AppTab.FLASHCARDS || next == AppTab.QUIZ) studyVm.refreshSetup()
                    if (next == AppTab.READING) readingVm.refresh()
                    returnTab = null
                    tabIndex = next.ordinal
                }
            ) { selected ->
                when (selected) {
                    AppTab.DICTIONARY -> androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize()) {
                      val back = returnTab?.let { AppTab.values().getOrNull(it) }
                      if (back != null) {
                          com.hdlee73.englishstudy.ui.ReturnBar("${back.label} 탭으로 돌아가기") {
                              wordSpeaker.stop()
                              returnTab = null
                              tabIndex = back.ordinal
                          }
                      }
                      androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.weight(1f)) { DictionaryScreen(
                        state = dictionary,
                        saved = saved,
                        onQueryChange = dictionaryVm::onQueryChange,
                        onSearch = dictionaryVm::searchNow,
                        onPickSuggestion = dictionaryVm::pickSuggestion,
                        onSave = dictionaryVm::saveCurrent,
                        onDelete = dictionaryVm::deleteSaved,
                        onShowSaved = dictionaryVm::setShowSaved,
                        onSort = dictionaryVm::setSort,
                        onExport = { format ->
                            if (saved.isEmpty()) {
                                dictionaryVm.showMessage("내보낼 저장 단어가 없습니다.")
                            } else {
                                exportFormat = format
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                exportLauncher.launch(
                                    when (format) {
                                        2 -> "영어예문_한영.xlsx"
                                        3 -> "영어예문.xlsx"
                                        else -> "영어단어장.xlsx"
                                    }
                                )
                            }
                        },
                        onSpeak = wordSpeaker::speak,
                        onOpenUrl = ::openUrl,
                        onMessageDismiss = dictionaryVm::clearMessage
                    )
                      }
                    }
                    AppTab.FLASHCARDS -> FlashcardScreen(
                        state = flash,
                        onSource = studyVm::setFlashSource,
                        onAddList = {
                            flashListPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel"))
                        },
                        onDeleteList = studyVm::deleteFlashDataset,
                        onMessageDismiss = studyVm::clearFlashMessage,
                        onFilter = studyVm::setFilter,
                        onShuffle = studyVm::setShuffle,
                        onFrontIsWord = studyVm::setFrontIsWord,
                        onStart = studyVm::startFlashcards,
                        onFlip = studyVm::flip,
                        onAnswer = studyVm::answerCard,
                        onRetryMissed = studyVm::retryMissedCards,
                        onEnd = studyVm::endFlashcards,
                        onSpeak = wordSpeaker::speak
                    )
                    AppTab.QUIZ -> QuizScreen(
                        state = quiz,
                        onSource = studyVm::setQuizSource,
                        onCount = studyVm::setQuizCount,
                        onStart = studyVm::startQuiz,
                        onChoose = studyVm::chooseAnswer,
                        onNext = studyVm::nextQuestion,
                        onRetryWrong = studyVm::retryWrong,
                        onEnd = studyVm::endQuiz,
                        onSpeak = wordSpeaker::speak
                    )
                    AppTab.READING -> ReadingScreen(
                        state = reading,
                        onOpen = readingVm::open,
                        onClose = readingVm::close,
                        onMode = readingVm::setMode,
                        onSaveExpression = readingVm::saveExpression,
                        onLookup = { word ->
                            // Search the tapped word in the dictionary tab.
                            dictionaryVm.pickSuggestion(word)
                            wordSpeaker.stop()
                            returnTab = AppTab.READING.ordinal
                            tabIndex = AppTab.DICTIONARY.ordinal
                        },
                        onSpeak = wordSpeaker::speak,
                        onTranslateSnippet = readingVm::translateSnippet,
                        onClearSnippet = readingVm::clearSnippet,
                        onMessageDismiss = readingVm::clearMessage,
                        savedScroll = readingVm.scrollPosition,
                        onScroll = readingVm::saveScroll,
                        savedSelection = readingVm.savedSelection,
                        onSelection = readingVm::saveSelection
                    )
                    AppTab.TRANSLATE -> TranslateScreen(
                        state = translation,
                        onInput = translateVm::setInput,
                        onTranslate = translateVm::translate,
                        onSwap = translateVm::swap,
                        onClear = translateVm::clear,
                        onLookup = { text ->
                            dictionaryVm.pickSuggestion(text)
                            wordSpeaker.stop()
                            returnTab = AppTab.TRANSLATE.ordinal
                            tabIndex = AppTab.DICTIONARY.ordinal
                        },
                        onSpeak = wordSpeaker::speak,
                        onMessageDismiss = translateVm::clearMessage,
                        onSaveToSpeaking = { english, korean ->
                            learningVm.addTranslatedSentence(english, korean) { added ->
                                translateVm.showMessage(
                                    when (added) {
                                        true -> "문장말하기 ‘번역 저장 문장’ 데이터셋에 추가했어요."
                                        false -> "이미 ‘번역 저장 문장’ 데이터셋에 있는 문장이에요."
                                        null -> "데이터셋에 저장하지 못했어요."
                                    }
                                )
                            }
                        }
                    )
                    AppTab.SPEAKING -> SpeakFlowApp(
                        state = learning,
                        settingsOpen = settingsOpen,
                        datasetsOpen = datasetsOpen,
                        onSettingsOpen = { learningVm.pauseForBackground(); speech.refreshBluetoothDevices(); settingsOpen = true },
                        onSettingsClose = { settingsOpen = false },
                        onSettingsSave = { learningVm.updateSettings(it); settingsOpen = false },
                        onDatasetsOpen = { learningVm.pauseForBackground(); datasetsOpen = true },
                        onDatasetsClose = { datasetsOpen = false },
                        onDatasetSelect = { learningVm.selectDataset(it); datasetsOpen = false },
                        onDatasetDelete = learningVm::deleteDataset,
                        onDatasetEdit = { datasetsOpen = false; learningVm.editDataset(it) },
                        onEditorClose = learningVm::closeEditor,
                        onSentenceSave = learningVm::saveSentence,
                        onOpenUpdate = { openUrl("https://github.com/hdlee73/LexiFlow/releases/latest") },
                        onToggleFavorite = { learningVm.toggleFavorite() },
                        onDatasetSequence = { learningVm.selectDatasets(it); datasetsOpen = false },
                        onImport = {
                            datasetsOpen = false
                            filePicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "application/vnd.ms-excel"))
                        },
                        onPlayPause = learningVm::togglePause,
                        onRestart = learningVm::restart,
                        onPrevious = learningVm::previous,
                        onNext = learningVm::next,
                        onReplay = learningVm::startSpeaking,
                        onRetry = learningVm::retryListening,
                        onToggleRecording = learningVm::toggleRecording,
                        onMessageDismiss = learningVm::clearMessage
                    )
                    AppTab.DOCVOICE -> DocVoiceScreen(docVoiceVm)
                    AppTab.LISTENING -> ListeningScreen(
                        pendingIntent = listeningIntent,
                        onIntentConsumed = { listeningIntent = null }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (ListeningLink.isForListening(intent)) listeningIntent = intent
        if (DocVoiceLink.isForDocVoice(intent)) docVoiceRequest++
    }

    private fun notice(text: String) = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            notice("링크를 열 수 있는 앱이 없습니다.")
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // The Enter key of a headset or keyboard moves the speaking lesson on; elsewhere it keeps its normal meaning.
        if (currentTab == AppTab.SPEAKING && !hasOpenDialog &&
            event.keyCode in setOf(KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
        ) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) learningVm.next()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        learningVm.saveProgress()
        super.onPause()
    }

    override fun onStop() {
        learningVm.pauseForBackground()
        speech.stop()
        wordSpeaker.stop()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // A word copied in another app is searched when the dictionary tab comes to the front.
        if (hasFocus && currentTab == AppTab.DICTIONARY) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
            if (text.isNotBlank()) dictionaryVm.onClipboardText(text)
        }
    }

    override fun onDestroy() {
        speech.destroy()
        wordSpeaker.close()
        super.onDestroy()
    }
}
