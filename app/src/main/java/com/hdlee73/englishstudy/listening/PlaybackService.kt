package com.hdlee73.englishstudy.listening

import com.hdlee73.englishstudy.R

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns the player so audio keeps going with the screen off, while folded, or with the Activity
 * gone. Besides playback it runs everything that has to work in the background: playlist,
 * A-B / file repeat with an optional shadowing gap, sleep timer and per-track resume positions.
 * The media session gives notification and Bluetooth/headset controls.
 */
@OptIn(markerClass = [UnstableApi::class])
class PlaybackService : MediaSessionService() {
    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    private val binder = LocalBinder()
    private val handler = Handler(Looper.getMainLooper())
    private var mediaSession: MediaSession? = null
    private val prefs by lazy { getSharedPreferences(TrackStore.PREFS, MODE_PRIVATE) }

    lateinit var player: ExoPlayer
        private set
    lateinit var store: TrackStore
        private set

    // ---- settings (persisted) ----
    /** 0 = off, 1 = repeat file, 2 = repeat the active group. */
    var repeatMode = 0
        private set
    /** File repeat has no count any more: it repeats until switched off. */
    val fileRepeatLimit = -1
    /** How many times the A-B range plays in total; -1 = until cleared. */
    var abRepeatLimit = -1
        private set
    /** Silence between repeats, for speaking along. */
    var gapMs = 0L
        private set

    // ---- state of the current track ----
    private var currentUri: String? = null
    private var current = TrackStore.TrackState()
    var abStartMs = -1L
        private set
    var abEndMs = -1L
        private set
    val bookmarks = LongArray(TrackStore.BOOKMARK_SLOTS) { -1L }
    val savedRanges: List<TrackStore.SavedRange> get() = current.ranges
    private var abLoopsDone = 0
    private var abExhausted = false
    private var fileRepeatsDone = 0
    private var lastPositionMs = 0L
    private var lastSaveAt = 0L

    // ---- gap / sleep timer / retry ----
    private var gapPending = false
    private val gapResume = Runnable {
        gapPending = false
        player.play()
    }
    private var sleepEndRealtime = 0L
    var sleepAtTrackEnd = false
        private set
    private val sleepRunnable = Runnable {
        sleepEndRealtime = 0L
        cancelGap()
        player.pause()
    }
    private var retryCount = 0
    private var retryPosition = 0L
    private var retryShouldPlay = false

    private var tickerRunning = false
    private val ticker = object : Runnable {
        override fun run() {
            tick()
            if (player.isPlaying) handler.postDelayed(this, if (abActive()) 30L else 250L)
            else tickerRunning = false
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                retryCount = 0
                startTicker()
            } else if (player.playbackState == Player.STATE_READY) {
                // Paused while the UI may be gone (focus loss, headset unplugged, sleep timer).
                savePosition()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (gapPending && (playWhenReady ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
            ) cancelGap()
            if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM && sleepAtTrackEnd) {
                sleepAtTrackEnd = false
                player.pauseAtEndOfMediaItems = false
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                onFileRepeated()
                return
            }
            val oldUri = currentUri
            if (oldUri != null) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    current.done = true
                    current.positionMs = 0L
                } else {
                    current.positionMs = lastPositionMs
                }
                store.saveState(oldUri, current)
            }
            cancelGap()
            loadTrack(mediaItem)
            val seekToSaved = reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK ||
                reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            if (seekToSaved && !current.done && current.positionMs > 0L) player.seekTo(current.positionMs)
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                val uri = currentUri ?: return
                current.done = true
                current.positionMs = 0L
                store.saveState(uri, current)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (retryCount >= MAX_RETRIES) return
            retryPosition = player.currentPosition.coerceAtLeast(0)
            retryShouldPlay = player.playWhenReady
            retryCount++
            handler.postDelayed({
                if (!player.isPlaying) {
                    player.prepare()
                    player.seekTo(retryPosition)
                    player.playWhenReady = retryShouldPlay
                }
            }, retryCount * 1_500L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = TrackStore(this)
        repeatMode = prefs.getInt("repeat_mode", 0).coerceIn(0, 2)
        abRepeatLimit = prefs.getInt("ab_repeat_limit", -1)
        gapMs = prefs.getLong("gap_ms", 0L).coerceAtLeast(0L)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 2_500, 5_000)
            .build()
        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build().apply {
                setAudioAttributes(AudioAttributes.DEFAULT, true)
                // Pause when headphones are unplugged / Bluetooth disconnects instead of
                // suddenly playing through the speaker.
                setHandleAudioBecomingNoisy(true)
                setWakeMode(C.WAKE_MODE_LOCAL)
                playbackParameters = PlaybackParameters(prefs.getFloat("speed", 1.0f))
                shuffleModeEnabled = prefs.getBoolean("shuffle", false)
            }
        player.addListener(playerListener)

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.hdlee73.englishstudy.MainActivity::class.java).apply {
                putExtra(ListeningLink.EXTRA_OPEN, true)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, player).setSessionActivity(openApp).build()

        restoreGroups()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onBind(intent: Intent?): IBinder? =
        if (intent?.action == ACTION_LOCAL_BIND) binder else super.onBind(intent)

    /** Keeps the service in the foreground while a shadowing gap pauses playback for a moment. */
    override fun onUpdateNotification(session: MediaSession, startInForegroundRequired: Boolean) {
        super.onUpdateNotification(session, startInForegroundRequired || gapPending)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) {
            savePosition()
            player.removeListener(playerListener)
        }
        mediaSession?.release()
        mediaSession = null
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ groups and playlist

    /** All groups. The player's queue always mirrors the [activeGroup]. */
    lateinit var groups: MutableList<TrackStore.Group>
        private set
    var activeGroup = 0
        private set

    private fun restoreGroups() {
        groups = store.loadGroups()
        activeGroup = store.activeGroup.coerceIn(0, groups.lastIndex)
        loadActiveIntoPlayer(play = false, startIndex = null)
    }

    private fun toMediaItem(entry: TrackStore.Entry): MediaItem =
        MediaItem.Builder()
            .setMediaId(entry.uri)
            .setUri(Uri.parse(entry.uri))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.name).build())
            .build()

    /** Entries currently in the player's queue, i.e. those of the active group. */
    private fun queueEntries(): List<TrackStore.Entry> = (0 until player.mediaItemCount).map {
        val item = player.getMediaItemAt(it)
        TrackStore.Entry(item.mediaId, item.mediaMetadata.title?.toString() ?: "오디오")
    }

    fun groupEntries(group: Int): List<TrackStore.Entry> = groups.getOrNull(group)?.entries.orEmpty()

    fun isDone(uri: String): Boolean = if (uri == currentUri) current.done else store.getState(uri).done

    val currentTitle: String? get() = player.currentMediaItem?.mediaMetadata?.title?.toString()

    val activeGroupName: String get() = groups.getOrNull(activeGroup)?.name ?: ""

    private fun syncActiveFromPlayer() {
        val group = groups.getOrNull(activeGroup) ?: return
        group.entries.clear()
        group.entries.addAll(queueEntries())
        group.index = player.currentMediaItemIndex.coerceAtLeast(0)
    }

    private fun persistGroups() {
        syncActiveFromPlayer()
        store.saveGroups(groups, activeGroup)
    }

    /** Replaces the player's queue with the active group's tracks. */
    private fun loadActiveIntoPlayer(play: Boolean, startIndex: Int?) {
        cancelGap()
        val group = groups[activeGroup]
        if (group.entries.isEmpty()) {
            player.pause()
            player.clearMediaItems()
            return
        }
        val index = (startIndex ?: group.index).coerceIn(0, group.entries.lastIndex)
        val state = store.getState(group.entries[index].uri)
        player.setMediaItems(group.entries.map(::toMediaItem), index, if (state.done) 0L else state.positionMs)
        player.prepare()
        if (play) player.play() else player.pause()
    }

    fun createGroup(name: String): Int {
        groups.add(TrackStore.Group(name.trim().ifBlank { "새 그룹" }))
        persistGroups()
        return groups.lastIndex
    }

    fun renameGroup(group: Int, name: String) {
        if (name.isBlank()) return
        groups.getOrNull(group)?.name = name.trim()
        persistGroups()
    }

    fun deleteGroup(group: Int) {
        if (group !in groups.indices) return
        if (groups.size == 1) {
            clearGroup(0)
            return
        }
        if (group == activeGroup) {
            val next = if (group > 0) group - 1 else 1
            groups.removeAt(group)
            activeGroup = if (next > group) next - 1 else next
            loadActiveIntoPlayer(play = false, startIndex = null)
        } else {
            groups.removeAt(group)
            if (group < activeGroup) activeGroup--
        }
        persistGroups()
    }

    fun clearGroup(group: Int) {
        if (group !in groups.indices) return
        if (group == activeGroup) {
            cancelGap()
            player.pause()
            player.clearMediaItems()
        } else {
            groups[group].entries.clear()
            groups[group].index = 0
        }
        persistGroups()
    }

    /** Makes [group] the one that plays (and repeats) without starting it. */
    fun switchGroup(group: Int, play: Boolean) {
        if (group !in groups.indices) return
        if (group != activeGroup) {
            syncActiveFromPlayer()
            activeGroup = group
            loadActiveIntoPlayer(play, startIndex = null)
        } else if (play) resume()
        persistGroups()
    }

    /** Appends new files to [group]; starts them when that group is idle. Returns how many were added. */
    fun addEntries(newEntries: List<TrackStore.Entry>, group: Int = activeGroup, startIfIdle: Boolean = false): Int {
        if (group !in groups.indices) return 0
        if (group != activeGroup) {
            val target = groups[group]
            val known = target.entries.map { it.uri }.toSet()
            val fresh = newEntries.filter { it.uri !in known }
            target.entries.addAll(fresh)
            persistGroups()
            return fresh.size
        }
        val existing = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }.toSet()
        val fresh = newEntries.filter { it.uri !in existing }
        if (fresh.isEmpty()) return 0
        val wasEmpty = player.mediaItemCount == 0
        val firstNew = player.mediaItemCount
        val idle = !isActive
        if (wasEmpty) {
            val state = store.getState(fresh[0].uri)
            player.setMediaItems(fresh.map(::toMediaItem), 0, if (state.done) 0L else state.positionMs)
        } else {
            player.addMediaItems(fresh.map(::toMediaItem))
        }
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (startIfIdle && idle) {
            if (!wasEmpty) player.seekToDefaultPosition(firstNew)
            cancelGap()
            player.play()
        }
        persistGroups()
        return fresh.size
    }

    /** Plays [entry] now, adding it to the active group when it is not in there yet. */
    fun openAndPlay(entry: TrackStore.Entry) {
        addEntries(listOf(entry), activeGroup, startIfIdle = false)
        val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).mediaId == entry.uri } ?: return
        playIndex(index)
    }

    fun removeFrom(group: Int, index: Int) {
        val list = groups.getOrNull(group)?.entries ?: return
        if (index !in list.indices) return
        if (group == activeGroup) {
            player.removeMediaItem(index)
        } else {
            list.removeAt(index)
            val g = groups[group]
            if (g.index > index || g.index >= list.size) g.index = (g.index - 1).coerceAtLeast(0)
        }
        persistGroups()
    }

    fun moveInGroup(group: Int, from: Int, to: Int) {
        val g = groups.getOrNull(group) ?: return
        if (from !in g.entries.indices || to !in g.entries.indices || from == to) return
        if (group == activeGroup) {
            player.moveMediaItem(from, to)
        } else {
            g.entries.add(to, g.entries.removeAt(from))
            when (g.index) {
                from -> g.index = to
                in minOf(from, to)..maxOf(from, to) -> g.index += if (from < to) -1 else 1
            }
        }
        persistGroups()
    }

    /** Moves a track to another group. Returns false when that group already has the file. */
    fun moveToGroup(fromGroup: Int, index: Int, toGroup: Int): Boolean {
        val entry = groups.getOrNull(fromGroup)?.entries?.getOrNull(index) ?: return false
        if (fromGroup == toGroup) return false
        if (groups.getOrNull(toGroup)?.entries?.any { it.uri == entry.uri } != false) return false
        addEntries(listOf(entry), toGroup, startIfIdle = false)
        removeFrom(fromGroup, index)
        return true
    }

    fun resetProgress(group: Int, index: Int) {
        val uri = groups.getOrNull(group)?.entries?.getOrNull(index)?.uri ?: return
        val state = if (uri == currentUri) current else store.getState(uri)
        state.done = false
        state.positionMs = 0L
        store.saveState(uri, state)
    }

    /** Plays track [index] of [group], switching the player over to that group when needed. */
    fun playIn(group: Int, index: Int) {
        val g = groups.getOrNull(group) ?: return
        if (index !in g.entries.indices) return
        if (group == activeGroup) {
            playIndex(index)
            return
        }
        syncActiveFromPlayer()
        activeGroup = group
        loadActiveIntoPlayer(play = true, startIndex = index)
        persistGroups()
    }

    fun playIndex(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        cancelGap()
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (index != player.currentMediaItemIndex) player.seekToDefaultPosition(index)
        else if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    // ------------------------------------------------------------------ transport

    /** True while the player is (or is about to be) producing sound. */
    val isActive: Boolean
        get() = player.playWhenReady &&
            player.playbackState != Player.STATE_ENDED &&
            player.playbackState != Player.STATE_IDLE

    fun togglePlay() {
        if (player.mediaItemCount == 0) return
        cancelGap()
        if (isActive) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
            player.play()
        }
    }

    fun resume() {
        if (player.mediaItemCount > 0 && !isActive) togglePlay()
    }

    /** Stop: pause and go back to the start of the current track. */
    fun stopPlayback() {
        cancelGap()
        player.pause()
        player.seekTo(0)
        abLoopsDone = 0
        abExhausted = false
        lastPositionMs = 0L
        current.positionMs = 0L
        currentUri?.let { store.saveState(it, current) }
    }

    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
        prefs.edit().putFloat("speed", speed).apply()
    }

    // ------------------------------------------------------------------ repeat settings

    fun updateRepeatMode(mode: Int) {
        repeatMode = mode.coerceIn(0, 2)
        prefs.edit().putInt("repeat_mode", repeatMode).apply()
        fileRepeatsDone = 0
        applyRepeatMode()
    }

    /** Random order inside the active group (the player's queue). */
    val shuffle: Boolean get() = player.shuffleModeEnabled

    fun updateShuffle(on: Boolean) {
        player.shuffleModeEnabled = on
        prefs.edit().putBoolean("shuffle", on).apply()
    }

    fun updateAbRepeatLimit(limit: Int) {
        abRepeatLimit = limit
        prefs.edit().putInt("ab_repeat_limit", limit).apply()
        resetAbLoop()
    }

    fun updateGap(ms: Long) {
        gapMs = ms.coerceAtLeast(0L)
        prefs.edit().putLong("gap_ms", gapMs).apply()
    }

    private fun applyRepeatMode() {
        player.repeatMode = when (repeatMode) {
            1 -> if (fileRepeatLimit < 0 || fileRepeatsDone < fileRepeatLimit) Player.REPEAT_MODE_ONE
            else Player.REPEAT_MODE_OFF
            2 -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun onFileRepeated() {
        fileRepeatsDone++
        applyRepeatMode()
        if (gapMs > 0L) startGap()
    }

    // ------------------------------------------------------------------ A-B range

    private fun abActive() = abStartMs >= 0 && abEndMs > abStartMs && !abExhausted

    private fun resetAbLoop() {
        abLoopsDone = 0
        abExhausted = false
    }

    private fun persistAb() {
        current.abStartMs = abStartMs
        current.abEndMs = abEndMs
        currentUri?.let { store.saveState(it, current) }
    }

    private fun maxPosition(): Long = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE

    fun setA(positionMs: Long) {
        abStartMs = positionMs.coerceAtLeast(0L)
        if (abEndMs <= abStartMs) abEndMs = -1L
        resetAbLoop()
        persistAb()
    }

    /** Returns false when there is no A point yet or B is not after A. */
    fun setB(positionMs: Long): Boolean {
        if (abStartMs < 0 || positionMs <= abStartMs) return false
        abEndMs = positionMs
        resetAbLoop()
        persistAb()
        player.seekTo(abStartMs)
        return true
    }

    fun clearAb() {
        abStartMs = -1L
        abEndMs = -1L
        resetAbLoop()
        persistAb()
    }

    /** Moves point A by [deltaMs] and plays from the new A so it can be judged by ear. */
    fun nudgeA(deltaMs: Long) {
        if (abStartMs < 0) return
        val upper = if (abEndMs > 0) abEndMs - MIN_RANGE_MS else maxPosition()
        abStartMs = (abStartMs + deltaMs).coerceIn(0L, upper.coerceAtLeast(0L))
        resetAbLoop()
        persistAb()
        cancelGap()
        player.seekTo(abStartMs)
        player.play()
    }

    /** Moves point B by [deltaMs] and plays the last moments before it. */
    fun nudgeB(deltaMs: Long) {
        if (abEndMs < 0) return
        abEndMs = (abEndMs + deltaMs).coerceAtMost(maxPosition()).coerceAtLeast(abStartMs + MIN_RANGE_MS)
        resetAbLoop()
        persistAb()
        cancelGap()
        player.seekTo((abEndMs - PREVIEW_BEFORE_B_MS).coerceAtLeast(abStartMs))
        player.play()
    }

    fun saveCurrentRange(name: String?): Boolean {
        if (abStartMs < 0 || abEndMs <= abStartMs) return false
        val label = name?.takeIf { it.isNotBlank() } ?: "구간 ${current.ranges.size + 1}"
        current.ranges.add(TrackStore.SavedRange(label, abStartMs, abEndMs))
        persistAb()
        return true
    }

    fun applyRange(index: Int) {
        val range = current.ranges.getOrNull(index) ?: return
        abStartMs = range.startMs
        abEndMs = range.endMs
        resetAbLoop()
        persistAb()
        cancelGap()
        player.seekTo(abStartMs)
        player.play()
    }

    fun renameRange(index: Int, name: String) {
        val range = current.ranges.getOrNull(index) ?: return
        if (name.isBlank()) return
        current.ranges[index] = range.copy(name = name.trim())
        persistAb()
    }

    fun deleteRange(index: Int) {
        if (index !in current.ranges.indices) return
        current.ranges.removeAt(index)
        persistAb()
    }

    private fun onAbEnd() {
        abLoopsDone++
        if (abRepeatLimit >= 0 && abLoopsDone >= abRepeatLimit) {
            // Played as often as requested: carry on past B. Re-armed when seeking before A.
            abExhausted = true
            return
        }
        player.seekTo(abStartMs)
        if (gapMs > 0L) startGap()
    }

    // ------------------------------------------------------------------ subtitles

    val currentTrackUri: String? get() = currentUri
    val subtitleUri: String? get() = current.subtitleUri

    /** Links a subtitle file to the current track (null removes the link). */
    fun setSubtitle(uri: String?) {
        current.subtitleUri = uri
        persistAb()
    }

    // ------------------------------------------------------------------ bookmarks

    fun toggleBookmark(slot: Int): Boolean {
        if (slot !in bookmarks.indices) return false
        val removing = bookmarks[slot] >= 0
        bookmarks[slot] = if (removing) -1L else player.currentPosition.coerceAtLeast(0L)
        current.bookmarks[slot] = bookmarks[slot]
        persistAb()
        return !removing
    }

    // ------------------------------------------------------------------ shadowing gap

    private fun startGap() {
        handler.removeCallbacks(gapResume)
        gapPending = true
        player.pause()
        handler.postDelayed(gapResume, gapMs)
    }

    private fun cancelGap() {
        if (!gapPending) return
        gapPending = false
        handler.removeCallbacks(gapResume)
    }

    // ------------------------------------------------------------------ sleep timer

    /** Pauses after [minutes]; 0 turns the timer off. */
    fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes > 0) {
            val delay = minutes * 60_000L
            sleepEndRealtime = SystemClock.elapsedRealtime() + delay
            handler.postDelayed(sleepRunnable, delay)
        }
    }

    /** Pauses when the current track ends. */
    fun sleepAtEndOfTrack() {
        cancelSleepTimer()
        sleepAtTrackEnd = true
        player.pauseAtEndOfMediaItems = true
    }

    fun cancelSleepTimer() {
        handler.removeCallbacks(sleepRunnable)
        sleepEndRealtime = 0L
        if (sleepAtTrackEnd) {
            sleepAtTrackEnd = false
            player.pauseAtEndOfMediaItems = false
        }
    }

    /** Milliseconds until the timer fires, or -1 when no countdown is running. */
    val sleepRemainingMs: Long
        get() = if (sleepEndRealtime > 0L) (sleepEndRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0L) else -1L

    // ------------------------------------------------------------------ bookkeeping

    private fun loadTrack(item: MediaItem?) {
        val uri = item?.mediaId
        currentUri = uri
        current = if (uri != null) store.getState(uri) else TrackStore.TrackState()
        abStartMs = current.abStartMs
        abEndMs = current.abEndMs
        current.bookmarks.copyInto(bookmarks)
        resetAbLoop()
        fileRepeatsDone = 0
        lastPositionMs = 0L
        applyRepeatMode()
        persistGroups()
        prefs.edit().putString("last_name", item?.mediaMetadata?.title?.toString()).apply()
        PlayerWidgetProvider.updateAll(this)
    }

    private fun startTicker() {
        if (tickerRunning) return
        tickerRunning = true
        handler.post(ticker)
    }

    private fun tick() {
        val position = player.currentPosition
        lastPositionMs = position
        if (abStartMs >= 0 && abEndMs > abStartMs) {
            if (abExhausted) {
                if (position < abStartMs) resetAbLoop()
            } else if (position >= abEndMs) {
                onAbEnd()
            }
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastSaveAt >= SAVE_INTERVAL_MS) {
            lastSaveAt = now
            savePosition()
        }
    }

    private fun savePosition() {
        val uri = currentUri ?: return
        if (player.playbackState == Player.STATE_ENDED) return
        lastPositionMs = player.currentPosition.coerceAtLeast(0L)
        current.positionMs = lastPositionMs
        store.saveState(uri, current)
    }

    companion object {
        const val ACTION_LOCAL_BIND = "com.hdlee73.englishstudy.listening.action.LOCAL_BIND"
        private const val MAX_RETRIES = 3
        private const val MIN_RANGE_MS = 200L
        private const val PREVIEW_BEFORE_B_MS = 1_500L
        private const val SAVE_INTERVAL_MS = 5_000L
    }
}
