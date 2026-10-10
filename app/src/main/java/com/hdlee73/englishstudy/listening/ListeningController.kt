package com.hdlee73.englishstudy.listening

import com.hdlee73.englishstudy.ui.FilePick
import com.hdlee73.englishstudy.R

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.app.Activity
import android.view.LayoutInflater
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * Thin view over [PlaybackService]: it draws the service's state a few times a second and hands
 * button presses over. Everything that must keep working with the screen off lives in the service.
 */
class ListeningController(
    private val activity: ComponentActivity,
    private val ctx: Context,
    private val root: View,
    /** Words picked in the subtitles are sent on to the dictionary / word list and to the translator. */
    private val onLookup: (String) -> Unit = {},
    private val onTranslate: (String) -> Unit = {}
) {
    /** An intent (open-with, widget button, notification) waiting for the service to be ready. */
    var launchIntent: Intent? = null
    private var disposed = false

    private var playbackService: PlaybackService? = null
    private var serviceBound = false
    private var uiReady = false

    private lateinit var playButton: ImageButton
    private lateinit var seekBar: RepeatSeekBar
    private lateinit var timeText: TextView
    private lateinit var remainingText: TextView
    private lateinit var fileName: TextView
    private lateinit var repeatStatus: TextView
    private lateinit var aLabel: TextView
    private lateinit var bLabel: TextView
    private lateinit var timerBadge: TextView
    private lateinit var shuffleButton: ImageButton
    private lateinit var repeatButton: ImageButton
    private var lastShuffle: Boolean? = null
    private var lastRepeatMode = -1
    private lateinit var rangeListButton: Button
    private lateinit var abCountPill: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { ctx.getSharedPreferences(TrackStore.PREFS, Context.MODE_PRIVATE) }
    private var fineStepMs = 100L
    private var dragging = false
    private var lastActive: Boolean? = null
    private var pendingAddGroup = -1
    private var playlistSheet: PlaylistSheet? = null
    private var subtitleSheet: SubtitleSheet? = null

    private val requestNotifications =
        activity.activityResultRegistry.register("ls_notifications", ActivityResultContracts.RequestPermission()) { }

    private val pickFiles = activity.activityResultRegistry.register("ls_pick_files", ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@register
        val uris = mutableListOf<Uri>()
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) clip.getItemAt(i).uri?.let { uris.add(it) }
        }
        data.data?.let { if (it !in uris) uris.add(it) }
        addFiles(uris)
    }

    private val pickFolder = activity.activityResultRegistry.register("ls_pick_folder", ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) importFolder(tree)
    }

    private val pickSubtitle = activity.activityResultRegistry.register("ls_pick_subtitle", ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@register
        playbackService?.setSubtitle(keepable(uri).toString())
        subtitleSheet?.reload()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            playbackService = (binder as PlaybackService.LocalBinder).getService()
            initializeUi()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            handler.removeCallbacks(uiTicker)
            playbackService = null
            uiReady = false
        }
    }

    /** Called once, right after the controller is created: binds the playback service. */
    fun start() {
        bindViews()
        askNotificationPermissionOnce()
        serviceBound = ctx.bindService(
            Intent(ctx, PlaybackService::class.java).setAction(PlaybackService.ACTION_LOCAL_BIND),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
        PlayerWidgetProvider.updateAll(ctx)
    }

    fun onStart() {
        if (uiReady) startUiTicker()
    }

    fun onStop() {
        handler.removeCallbacks(uiTicker)
    }

    /** Called when the tab leaves the screen or the app closes: playback itself carries on in the service. */
    fun dispose() {
        if (disposed) return
        disposed = true
        handler.removeCallbacksAndMessages(null)
        playlistSheet?.dismiss()
        subtitleSheet?.dismiss()
        requestNotifications.unregister()
        pickFiles.unregister()
        pickSubtitle.unregister()
        if (serviceBound) {
            ctx.unbindService(serviceConnection)
            serviceBound = false
        }
    }

    /** An open-with / widget / notification intent for the Listening tab. */
    fun deliver(intent: Intent) {
        if (uiReady) handleLaunchAction(intent) else launchIntent = intent
    }

    // ------------------------------------------------------------------ setup

    private fun bindViews() {
        playButton = root.findViewById(R.id.playButton)
        seekBar = root.findViewById(R.id.seekBar)
        timeText = root.findViewById(R.id.timeText)
        remainingText = root.findViewById(R.id.remainingText)
        fileName = root.findViewById(R.id.fileName)
        repeatStatus = root.findViewById(R.id.repeatStatus)
        aLabel = root.findViewById(R.id.aLabel)
        bLabel = root.findViewById(R.id.bLabel)
        timerBadge = root.findViewById(R.id.timerBadge)
        shuffleButton = root.findViewById(R.id.shuffleButton)
        repeatButton = root.findViewById(R.id.repeatButton)
        rangeListButton = root.findViewById(R.id.rangeListButton)
        abCountPill = root.findViewById(R.id.abCountPill)
    }

    private fun initializeUi() {
        if (uiReady) return
        uiReady = true
        fineStepMs = prefs.getLong("fine_step", 100L)
        handler.post { syncDeviceFolders() }
        configureTransport()
        configureRangeControls()
        root.findViewById<ImageButton>(R.id.addButton).setOnClickListener { openFilePicker() }
        root.findViewById<ImageButton>(R.id.playlistButton).setOnClickListener { showPlaylist() }
        root.findViewById<ImageButton>(R.id.timerButton).setOnClickListener { showTimerSheet() }
        root.findViewById<ImageButton>(R.id.subtitleButton).setOnClickListener { showSubtitles() }
        root.findViewById<ImageButton>(R.id.settingsButton).setOnClickListener { showSettings() }
        shuffleButton.setOnClickListener {
            val svc = playbackService ?: return@setOnClickListener
            svc.updateShuffle(!svc.shuffle)
            toast(if (svc.shuffle) "그룹 안에서 랜덤 재생" else "순서대로 재생")
        }
        repeatButton.setOnClickListener {
            val svc = playbackService ?: return@setOnClickListener
            svc.updateRepeatMode((svc.repeatMode + 1) % 3)
            toast(repeatLabel(svc))
        }
        configurePills()
        fileName.isSelected = true
        launchIntent?.let { handleLaunchAction(it) }
        launchIntent = null
        startUiTicker()
    }

    private fun askNotificationPermissionOnce() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted || prefs.getBoolean("asked_notifications", false)) return
        prefs.edit().putBoolean("asked_notifications", true).apply()
        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun handleLaunchAction(source: Intent) {
        val svc = playbackService ?: return
        val action = source.action ?: return
        when (action) {
            Intent.ACTION_VIEW -> {
                val uri = source.data
                if (uri != null) {
                    takePersistable(uri)
                    svc.openAndPlay(TrackStore.Entry(uri.toString(), displayName(uri)))
                } else toast("오디오 파일을 열 수 없습니다.")
            }
            ListeningLink.ACTION_PICK_DRIVE -> handler.post { openFilePicker() }
            ListeningLink.ACTION_RESUME_LAST -> {
                if (svc.player.mediaItemCount == 0) handler.post { openFilePicker() } else svc.resume()
            }
        }
        if (action == Intent.ACTION_VIEW || action == ListeningLink.ACTION_PICK_DRIVE || action == ListeningLink.ACTION_RESUME_LAST) {
            // Do not repeat the action after a configuration change or when brought back.
            source.action = Intent.ACTION_MAIN
        }
    }

    // ------------------------------------------------------------------ adding files

    /** One system picker covers Drive and device storage, and allows several files at once. */
    private fun openFilePicker(group: Int = playbackService?.activeGroup ?: 0) {
        pendingAddGroup = group
        val intent = FilePick.intent(ctx, arrayOf("audio/*"), multiple = true)
        pickFiles.launch(intent)
    }

    /** Turns a device folder and its subfolders into playlist folders ("Root", "Root/Sub", ...). */
    private fun importFolder(tree: Uri) {
        val svc = playbackService ?: return
        try {
            ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        toast("폴더를 읽고 있어요…")
        Thread {
            val root = runCatching { FolderImport.scan(ctx, tree) }.getOrNull()
            handler.post {
                if (playbackService == null) return@post
                if (root == null || !root.hasAudio) {
                    toast("이 폴더에는 오디오 파일이 없습니다.")
                    return@post
                }
                val added = svc.applyScan(tree.toString(), root, force = true)
                toast(
                    if (added == 0) "이미 모두 들어 있는 폴더입니다."
                    else "${added}곡을 재생목록에 추가했습니다. 이 폴더에 새 파일이 생기면 자동으로 들어옵니다."
                )
            }
        }.start()
    }

    /** Adds audio files that appeared in the device folders behind imported playlist folders (also in their subfolders). */
    private fun syncDeviceFolders() {
        val svc = playbackService ?: return
        svc.linkLegacyImports()
        val trees = svc.linkedTrees()
        if (trees.isEmpty()) return
        Thread {
            val scans = trees.mapNotNull { t ->
                runCatching { t to FolderImport.scan(ctx, Uri.parse(t)) }.getOrNull()
            }
            handler.post {
                if (playbackService == null) return@post
                var added = 0
                scans.forEach { (t, root) -> added += svc.applyScan(t, root, force = false) }
                if (added > 0) toast("기기 폴더에서 새 파일 ${added}곡을 재생목록에 추가했습니다.")
            }
        }.start()
    }

    /** Picks from My Files come without a lasting grant, so those are copied into the app and played from the copy. */
    private fun addFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (uris.all { isPersistable(it) }) { addPicked(uris.map { it to displayName(it) }); return }
        toast("파일을 앱으로 가져오는 중…")
        Thread {
            val picked = uris.map { displayName(it).let { name -> keepable(it, name) to name } }
            handler.post { addPicked(picked) }
        }.start()
    }

    private fun addPicked(picked: List<Pair<Uri, String>>) {
        val svc = playbackService ?: return
        val entries = picked.map { (uri, name) -> TrackStore.Entry(uri.toString(), name) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val group = pendingAddGroup.takeIf { it in svc.groups.indices } ?: svc.activeGroup
        pendingAddGroup = -1
        val added = svc.addEntries(entries, group, startIfIdle = group == svc.activeGroup)
        toast(
            if (added == 0) "이미 그 그룹에 있는 파일입니다."
            else "${added}곡을 ‘${svc.groups[group].name}’ 그룹에 추가했습니다."
        )
    }

    private fun isPersistable(uri: Uri): Boolean {
        if (uri.scheme == "file") return true
        takePersistable(uri)
        return ctx.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    /** The uri itself when the app may keep reading it, otherwise a private copy under files/picked. */
    private fun keepable(uri: Uri, name: String = displayName(uri)): Uri {
        if (isPersistable(uri)) return uri
        return runCatching {
            val dir = java.io.File(ctx.filesDir, "picked").apply { mkdirs() }
            val safe = name.replace(Regex("[^\\w.\\- ]+"), "_").take(120).ifBlank { "audio" }
            val size = ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            val same = dir.listFiles()?.firstOrNull { it.name.endsWith("_$safe") && size > 0 && it.length() == size }
            val file = same ?: java.io.File(dir, "${System.currentTimeMillis()}_$safe").also { target ->
                ctx.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                    ?: throw java.io.IOException("cannot open")
            }
            Uri.fromFile(file)
        }.getOrElse { handler.post { toast("파일을 가져오지 못했습니다.") }; uri }
    }

    private fun takePersistable(uri: Uri) {
        try {
            ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }

    private fun displayName(uri: Uri): String {
        return runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "선택한 오디오"
    }

    // ------------------------------------------------------------------ transport

    private fun configureTransport() {
        playButton.setOnClickListener {
            val svc = playbackService ?: return@setOnClickListener
            if (svc.player.mediaItemCount == 0) toast("먼저 오디오 파일을 추가해 주세요.") else svc.togglePlay()
        }
        root.findViewById<ImageButton>(R.id.previousTrackButton).setOnClickListener {
            val player = playbackService?.player ?: return@setOnClickListener
            when {
                player.mediaItemCount == 0 -> toast("먼저 오디오 파일을 추가해 주세요.")
                player.hasPreviousMediaItem() -> player.seekToPreviousMediaItem()
                else -> { toast("재생목록의 첫 곡입니다."); player.seekTo(0) }
            }
        }
        root.findViewById<ImageButton>(R.id.nextTrackButton).setOnClickListener {
            val player = playbackService?.player ?: return@setOnClickListener
            when {
                player.mediaItemCount == 0 -> toast("먼저 오디오 파일을 추가해 주세요.")
                player.hasNextMediaItem() -> player.seekToNextMediaItem()
                else -> toast("재생목록의 마지막 곡입니다.")
            }
        }
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) showTimes(progress.toLong(), bar?.max?.toLong() ?: 0L)
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                dragging = true
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                dragging = false
                if (bar != null) playbackService?.player?.seekTo(bar.progress.toLong())
            }
        })
    }

    private fun showTimes(position: Long, duration: Long) {
        timeText.setIfChanged(formatTime(position))
        remainingText.setIfChanged("-" + formatTime((duration - position).coerceAtLeast(0L)))
    }

    // ------------------------------------------------------------------ A-B range

    private fun configureRangeControls() {
        root.findViewById<Button>(R.id.aButton).setOnClickListener {
            val svc = playbackService ?: return@setOnClickListener
            svc.setA(svc.player.currentPosition)
        }
        root.findViewById<Button>(R.id.bButton).setOnClickListener {
            val svc = playbackService ?: return@setOnClickListener
            if (!svc.setB(svc.player.currentPosition)) {
                toast(if (svc.abStartMs < 0) "먼저 A 시작점을 지정해 주세요." else "B 종료점은 A보다 뒤에 있어야 합니다.")
            }
        }
        root.findViewById<View>(R.id.clearRepeatButton).setOnClickListener { playbackService?.clearAb() }
        root.findViewById<Button>(R.id.aMinusButton).setOnClickListener { playbackService?.nudgeA(-fineStepMs) }
        root.findViewById<Button>(R.id.aPlusButton).setOnClickListener { playbackService?.nudgeA(fineStepMs) }
        root.findViewById<Button>(R.id.bMinusButton).setOnClickListener { playbackService?.nudgeB(-fineStepMs) }
        root.findViewById<Button>(R.id.bPlusButton).setOnClickListener { playbackService?.nudgeB(fineStepMs) }
        root.findViewById<Button>(R.id.saveRangeButton).setOnClickListener { promptSaveRange() }
        rangeListButton.setOnClickListener { showRanges() }
    }

    private fun promptSaveRange() {
        val svc = playbackService ?: return
        if (svc.abStartMs < 0 || svc.abEndMs <= svc.abStartMs) {
            toast("저장할 구간이 없습니다. A·B 지점을 먼저 지정해 주세요.")
            return
        }
        ctx.promptText(
            "구간 저장  " + formatTimeTenths(svc.abStartMs) + " – " + formatTimeTenths(svc.abEndMs),
            "구간 ${svc.savedRanges.size + 1}",
            "저장"
        ) { name ->
            svc.saveCurrentRange(name)
            toast("구간을 저장했습니다.")
        }
    }

    private fun showRanges() {
        val svc = playbackService ?: return
        val ranges = svc.savedRanges
        if (ranges.isEmpty()) {
            toast("저장된 구간이 없습니다. A·B를 지정한 뒤 '구간 저장'을 눌러 주세요.")
            return
        }
        val labels = ranges.map {
            it.name + "\n" + formatTimeTenths(it.startMs) + " – " + formatTimeTenths(it.endMs)
        }
        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("저장한 구간 · ${ranges.size}개")
            .setItems(labels.toTypedArray()) { _, which -> svc.applyRange(which) }
            .setNegativeButton("닫기", null)
            .create()
        dialog.setOnShowListener {
            dialog.listView.setOnItemLongClickListener { _, _, position, _ ->
                showRangeMenu(dialog, position)
                true
            }
        }
        dialog.show()
        toast("길게 누르면 이름 변경·삭제")
    }

    private fun showRangeMenu(parent: AlertDialog, index: Int) {
        val svc = playbackService ?: return
        val range = svc.savedRanges.getOrNull(index) ?: return
        MaterialAlertDialogBuilder(ctx)
            .setTitle(range.name)
            .setItems(arrayOf("이름 변경", "삭제")) { _, which ->
                parent.dismiss()
                if (which == 0) {
                    ctx.promptText("이름 변경", range.name) { name ->
                        svc.renameRange(index, name)
                        showRanges()
                    }
                } else {
                    svc.deleteRange(index)
                    showRanges()
                }
            }
            .show()
    }

    // ------------------------------------------------------------------ settings sheet

    private fun repeatLabel(svc: PlaybackService): String = when (svc.repeatMode) {
        1 -> "파일 반복"
        2 -> "그룹 반복 · ${svc.activeGroupName}"
        else -> "반복 안 함"
    }

    /** Drop-down list anchored to a pill; the chosen value is reported by index. */
    private fun showChoice(anchor: View, values: List<String>, selected: Int, picked: (Int) -> Unit) {
        IconMenu.show(ctx, anchor, values.mapIndexed { index, value ->
            IconMenu.Item(index, value, if (index == selected) R.drawable.ls_ic_check else 0)
        }, alignEnd = true, onPick = picked)
    }

    private val speedValues = listOf(0.5f, 0.75f, 0.85f, 1.0f, 1.1f, 1.25f, 1.5f, 1.75f, 2.0f)
    private val abCountValues = listOf(-1, 2, 3, 5, 10)

    private fun formatSpeed(speed: Float): String =
        String.format(Locale.US, "%.2f", speed).trimEnd('0').let { if (it.endsWith(".")) it + "0" else it } + "x"

    /** The A-B repeat count is a drop-down next to the range it applies to. */
    private fun configurePills() {
        abCountPill.setOnClickListener {
            val svc = playbackService ?: return@setOnClickListener
            val current = abCountValues.indexOf(svc.abRepeatLimit)
            showChoice(it, abCountValues.map { v -> if (v < 0) "무제한" else "${v}회" }, current) { index ->
                svc.updateAbRepeatLimit(abCountValues[index])
            }
        }
    }

    private fun newSheet(content: View, expanded: Boolean = true): BottomSheetDialog {
        val dialog = BottomSheetDialog(ctx)
        dialog.setContentView(content)
        if (expanded) {
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            dialog.behavior.skipCollapsed = true
        }
        return dialog
    }

    private fun showSettings() {
        val svc = playbackService ?: return
        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(24))
        }
        column.addView(View(ctx).apply {
            setBackgroundResource(R.drawable.ls_bg_handle)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(4)).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
        })

        fun heading(text: String, top: Int) = column.addView(TextView(ctx).apply {
            this.text = text
            setTextColor(ctx.getColor(R.color.ls_text_primary))
            textSize = if (top == 14) 18f else 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            if (top != 14) setTextColor(ctx.getColor(R.color.ls_teal_600))
            setPadding(0, dp(top), 0, dp(2))
        })

        fun row(title: String, values: List<String>, selected: String, changed: (String) -> Unit) {
            column.addView(TextView(ctx).apply {
                text = title
                setTextColor(ctx.getColor(R.color.ls_text_secondary))
                textSize = 12f
                setPadding(0, dp(8), 0, 0)
            })
            val group = ChipGroup(ctx).apply {
                isSingleLine = true
                isSingleSelection = true
                isSelectionRequired = true
            }
            values.forEach { value ->
                val chip = ctx.filterChip(value, value == selected)
                chip.setOnClickListener { changed(value) }
                group.addView(chip)
            }
            column.addView(HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                addView(group)
            })
        }

        heading("설정", 14)

        heading("재생", 18)
        val speeds = speedValues.map(::formatSpeed)
        row("재생 속도", speeds, formatSpeed(svc.player.playbackParameters.speed)) { value ->
            svc.setSpeed(value.removeSuffix("x").toFloat())
        }

        heading("학습", 18)
        val gapLabels = listOf("없음", "1초", "2초", "3초", "5초")
        val gapSelected = if (svc.gapMs <= 0L) "없음" else (svc.gapMs / 1000).toString() + "초"
        row("반복 사이 간격  (따라 말하기)", gapLabels, gapSelected) { value ->
            svc.updateGap(if (value == "없음") 0L else value.removeSuffix("초").toLong() * 1000)
        }
        val fineLabels = listOf("0.1초", "0.2초", "0.5초", "1초")
        val fineMs = { label: String -> (label.removeSuffix("초").toDouble() * 1000).toLong() }
        row("구간 미세 조정 단위", fineLabels, fineLabels.firstOrNull { fineMs(it) == fineStepMs } ?: "0.1초") { value ->
            fineStepMs = fineMs(value)
            prefs.edit().putLong("fine_step", fineStepMs).apply()
        }

        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"
        column.addView(TextView(ctx).apply {
            text = "앱 버전 $version"
            setTextColor(ctx.getColor(R.color.ls_text_secondary))
            textSize = 11f
            setPadding(0, dp(18), 0, 0)
        })
        newSheet(ScrollView(ctx).apply { addView(column) }).show()
    }

    // ------------------------------------------------------------------ playlist and timer

    private fun showPlaylist() {
        val svc = playbackService ?: return
        val sheet = PlaylistSheet(ctx, svc, { group -> openFilePicker(group) }, { pickFolder.launch(null) })
        playlistSheet = sheet
        sheet.show()
        syncDeviceFolders()
    }

    private fun openSubtitlePicker() {
        val intent = FilePick.intent(ctx, arrayOf("*/*"), multiple = false)
        pickSubtitle.launch(intent)
    }

    private fun showSubtitles() {
        val svc = playbackService ?: return
        if (svc.player.mediaItemCount == 0) {
            toast("먼저 오디오 파일을 추가해 주세요.")
            return
        }
        val sheet = SubtitleSheet(ctx, svc, onLookup = { subtitleSheet?.dismiss(); onLookup(it) }, onTranslate = { subtitleSheet?.dismiss(); onTranslate(it) }) { openSubtitlePicker() }
        subtitleSheet = sheet
        sheet.show()
    }

    private fun showTimerSheet() {
        val svc = playbackService ?: return
        val view = LayoutInflater.from(ctx).inflate(R.layout.ls_sheet_timer, null)
        val remaining = view.findViewById<TextView>(R.id.timerRemaining)
        val hint = view.findViewById<TextView>(R.id.timerHint)
        val dialog = newSheet(view, expanded = false)

        fun update() {
            val left = svc.sleepRemainingMs
            when {
                left >= 0 -> { remaining.text = formatTime(left); hint.text = "뒤에 재생이 멈춥니다" }
                svc.sleepAtTrackEnd -> { remaining.text = "곡 끝까지"; hint.text = "현재 곡이 끝나면 재생이 멈춥니다" }
                else -> { remaining.text = "꺼짐"; hint.text = "시간을 고르면 그 시간 뒤에 재생이 멈춥니다" }
            }
        }
        val tick = object : Runnable {
            override fun run() {
                update()
                handler.postDelayed(this, 500)
            }
        }
        listOf(R.id.timer15 to 15, R.id.timer30 to 30, R.id.timer45 to 45, R.id.timer60 to 60, R.id.timer90 to 90)
            .forEach { (id, minutes) ->
                view.findViewById<Button>(id).setOnClickListener {
                    svc.setSleepTimer(minutes)
                    toast("${minutes}분 뒤에 재생이 멈춥니다.")
                    dialog.dismiss()
                }
            }
        view.findViewById<Button>(R.id.timerEnd).setOnClickListener {
            svc.sleepAtEndOfTrack()
            toast("현재 곡이 끝나면 재생이 멈춥니다.")
            dialog.dismiss()
        }
        view.findViewById<Button>(R.id.timerOff).setOnClickListener {
            svc.cancelSleepTimer()
            toast("취침 타이머를 껐습니다.")
            dialog.dismiss()
        }
        dialog.setOnDismissListener { handler.removeCallbacks(tick) }
        handler.post(tick)
        dialog.show()
    }

    // ------------------------------------------------------------------ drawing the state

    private val uiTicker = object : Runnable {
        override fun run() {
            refreshUi()
            handler.postDelayed(this, 200)
        }
    }

    private fun startUiTicker() {
        handler.removeCallbacks(uiTicker)
        handler.post(uiTicker)
    }

    private fun TextView.setIfChanged(value: String) {
        if (text.toString() != value) text = value
    }

    private fun refreshUi() {
        val svc = playbackService ?: return
        val player = svc.player
        val count = player.mediaItemCount
        val hasMedia = count > 0

        if (SixMinuteEnglish.hasPending(ctx)) svc.importPending()
        val title = svc.currentTitle
        fileName.setIfChanged(title ?: "오디오 파일을 추가하세요")
        playlistSheet?.refreshIfChanged()
        subtitleSheet?.refresh()

        if (lastShuffle != svc.shuffle) {
            lastShuffle = svc.shuffle
            shuffleButton.isSelected = svc.shuffle
            shuffleButton.imageTintList = ColorStateList.valueOf(ctx.getColor(if (svc.shuffle) R.color.ls_teal_700 else R.color.ls_text_secondary))
        }
        if (lastRepeatMode != svc.repeatMode) {
            lastRepeatMode = svc.repeatMode
            repeatButton.setImageResource(when (svc.repeatMode) { 1 -> R.drawable.ls_ic_repeat_one; 2 -> R.drawable.ls_ic_repeat_all; else -> R.drawable.ls_ic_repeat })
            repeatButton.isSelected = svc.repeatMode != 0
            repeatButton.imageTintList = ColorStateList.valueOf(ctx.getColor(if (svc.repeatMode == 0) R.color.ls_text_secondary else R.color.ls_teal_700))
            repeatButton.contentDescription = repeatLabel(svc)
        }

        val duration = player.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
        val position = player.currentPosition.coerceAtLeast(0L)
        if (!dragging) {
            seekBar.max = duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            seekBar.progress = position.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            showTimes(position, duration)
        }
        seekBar.setRepeatRange(svc.abStartMs, svc.abEndMs, duration)
        abCountPill.setIfChanged("횟수 " + (if (svc.abRepeatLimit < 0) "무제한" else "${svc.abRepeatLimit}회") + " ▾")

        val active = svc.isActive
        if (lastActive != active) {
            lastActive = active
            playButton.setImageResource(if (active) R.drawable.ls_ic_pause else R.drawable.ls_ic_play)
            playButton.contentDescription = if (active) "일시정지" else "재생"
        }

        val a = svc.abStartMs
        val b = svc.abEndMs
        aLabel.setIfChanged(if (a >= 0) formatTimeTenths(a) else "--:--.-")
        bLabel.setIfChanged(if (b >= 0) formatTimeTenths(b) else "--:--.-")
        repeatStatus.setIfChanged(
            when {
                a >= 0 && b >= 0 -> "A " + formatTime(a) + "  ↔  B " + formatTime(b)
                a >= 0 -> "A " + formatTime(a) + "  ·  B 미지정"
                else -> "꺼짐"
            }
        )
        setEnabledIfChanged(R.id.aMinusButton, a >= 0)
        setEnabledIfChanged(R.id.aPlusButton, a >= 0)
        setEnabledIfChanged(R.id.bMinusButton, b >= 0)
        setEnabledIfChanged(R.id.bPlusButton, b >= 0)
        setEnabledIfChanged(R.id.saveRangeButton, a >= 0 && b > a)
        rangeListButton.setIfChanged("저장 목록 ${svc.savedRanges.size}")

        val remaining = svc.sleepRemainingMs
        val badge = when {
            remaining >= 0 -> "⏱ " + formatTime(remaining)
            svc.sleepAtTrackEnd -> "⏱ 곡 끝까지"
            else -> ""
        }
        if (badge.isEmpty()) {
            if (timerBadge.visibility != View.GONE) timerBadge.visibility = View.GONE
        } else {
            timerBadge.setIfChanged(badge)
            if (timerBadge.visibility != View.VISIBLE) timerBadge.visibility = View.VISIBLE
        }
    }

    private fun setEnabledIfChanged(id: Int, enabled: Boolean) {
        val view = root.findViewById<View>(id)
        if (view.isEnabled != enabled) view.isEnabled = enabled
    }

    private fun toast(message: String) = Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = ctx.dp(value)
}
