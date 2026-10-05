package com.hdlee73.englishstudy.listening

import com.hdlee73.englishstudy.R

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the playlist and everything the app remembers per track: resume position,
 * "listened to the end" flag, the active A-B range, saved ranges and bookmarks.
 */
class TrackStore(context: Context) {
    data class Entry(val uri: String, val name: String)

    /** A named playlist. The player's queue is always the active group. */
    class Group(var name: String, val entries: MutableList<Entry> = mutableListOf(), var index: Int = 0)

    data class SavedRange(val name: String, val startMs: Long, val endMs: Long)

    class TrackState {
        var positionMs = 0L
        var done = false
        var subtitleUri: String? = null
        var abStartMs = -1L
        var abEndMs = -1L
        val bookmarks = LongArray(BOOKMARK_SLOTS) { -1L }
        val ranges = mutableListOf<SavedRange>()
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val tracks = context.getSharedPreferences(TRACK_PREFS, Context.MODE_PRIVATE)

    var currentIndex: Int
        get() = prefs.getInt(KEY_INDEX, 0)
        set(value) { prefs.edit().putInt(KEY_INDEX, value).apply() }

    var activeGroup: Int
        get() = prefs.getInt(KEY_ACTIVE_GROUP, 0)
        set(value) { prefs.edit().putInt(KEY_ACTIVE_GROUP, value).apply() }

    /** Always returns at least one group; the pre-1.4 single playlist becomes the group "기본". */
    fun loadGroups(): MutableList<Group> {
        val groups = mutableListOf<Group>()
        val raw = prefs.getString(KEY_GROUPS, null)
        if (raw != null) runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val group = Group(o.optString("n", "그룹"), mutableListOf(), o.optInt("i", 0))
                val items = o.optJSONArray("e")
                if (items != null) {
                    for (j in 0 until items.length()) {
                        val item = items.getJSONObject(j)
                        group.entries += Entry(item.getString("u"), item.optString("n", "오디오"))
                    }
                }
                groups += group
            }
        }
        if (groups.isEmpty()) groups += Group(FIRST_GROUP_NAME, loadPlaylist(), currentIndex)
        removeDefaultGroup(groups)
        return groups
    }

    /**
     * The old default folder "기본" is removed once (v1.15.0); its audio files stay on the phone. When it is the only folder it is renamed instead.
     */
    private fun removeDefaultGroup(groups: MutableList<Group>) {
        if (prefs.getBoolean(KEY_DEFAULT_REMOVED, false)) return
        val index = groups.indexOfFirst { it.name == "기본" }
        if (index >= 0) {
            var active = activeGroup.coerceIn(0, groups.lastIndex)
            if (groups.size == 1) {
                groups[0].name = FIRST_GROUP_NAME
            } else {
                groups.removeAt(index)
                active = when {
                    active > index -> active - 1
                    active == index -> 0
                    else -> active
                }
            }
            saveGroups(groups, active)
        }
        prefs.edit().putBoolean(KEY_DEFAULT_REMOVED, true).apply()
    }

    fun saveGroups(groups: List<Group>, active: Int) {
        val array = JSONArray()
        groups.forEach { group ->
            val items = JSONArray()
            group.entries.forEach { items.put(JSONObject().put("u", it.uri).put("n", it.name)) }
            array.put(JSONObject().put("n", group.name).put("i", group.index).put("e", items))
        }
        prefs.edit().putString(KEY_GROUPS, array.toString()).putInt(KEY_ACTIVE_GROUP, active).apply()
    }

    fun loadPlaylist(): MutableList<Entry> {
        val raw = prefs.getString(KEY_PLAYLIST, null)
        if (raw == null) return migrateLegacy()
        val list = mutableListOf<Entry>()
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list += Entry(item.getString("u"), item.optString("n", "오디오"))
            }
        }
        return list
    }

    fun savePlaylist(entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { array.put(JSONObject().put("u", it.uri).put("n", it.name)) }
        prefs.edit().putString(KEY_PLAYLIST, array.toString()).apply()
    }

    fun getState(uri: String): TrackState {
        val state = TrackState()
        val raw = tracks.getString(uri, null) ?: return state
        runCatching {
            val o = JSONObject(raw)
            state.positionMs = o.optLong("pos", 0L)
            state.done = o.optBoolean("done", false)
            state.subtitleUri = o.optString("sub", "").takeIf { it.isNotEmpty() }
            state.abStartMs = o.optLong("a", -1L)
            state.abEndMs = o.optLong("b", -1L)
            o.optJSONArray("bm")?.let { bm ->
                for (i in 0 until minOf(bm.length(), BOOKMARK_SLOTS)) state.bookmarks[i] = bm.optLong(i, -1L)
            }
            o.optJSONArray("ranges")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    state.ranges += SavedRange(r.optString("n", "구간"), r.optLong("s"), r.optLong("e"))
                }
            }
        }
        return state
    }

    fun saveState(uri: String, state: TrackState) {
        val bm = JSONArray()
        state.bookmarks.forEach { bm.put(it) }
        val ranges = JSONArray()
        state.ranges.forEach { ranges.put(JSONObject().put("n", it.name).put("s", it.startMs).put("e", it.endMs)) }
        val json = JSONObject()
            .put("pos", state.positionMs)
            .put("done", state.done)
            .put("sub", state.subtitleUri ?: "")
            .put("a", state.abStartMs)
            .put("b", state.abEndMs)
            .put("bm", bm)
            .put("ranges", ranges)
        tracks.edit().putString(uri, json.toString()).apply()
    }

    /** Carries over what versions before 1.3.0 stored: a single last file with its settings. */
    private fun migrateLegacy(): MutableList<Entry> {
        val list = mutableListOf<Entry>()
        val lastUri = prefs.getString("last_uri", null)
        // Files picked from the old device-folder list are MediaStore addresses that need the
        // music permission, which this version no longer asks for; they have to be added again.
        if (lastUri != null && !lastUri.startsWith("content://media/")) {
            list += Entry(lastUri, prefs.getString("last_name", null) ?: "선택한 오디오")
            val state = TrackState()
            state.positionMs = prefs.getLong("last_position", 0L).coerceAtLeast(0L)
            state.abStartMs = prefs.getLong("repeat_start", -1L)
            state.abEndMs = prefs.getLong("repeat_end", -1L)
            repeat(BOOKMARK_SLOTS) { state.bookmarks[it] = prefs.getLong("bookmark_$it", -1L) }
            saveState(lastUri, state)
        }
        val edit = prefs.edit().remove("last_uri").remove("last_position")
            .remove("repeat_start").remove("repeat_end").remove("folder_path")
        repeat(BOOKMARK_SLOTS) { edit.remove("bookmark_$it") }
        edit.apply()
        savePlaylist(list)
        return list
    }

    companion object {
        const val PREFS = "bbc_player"
        const val BOOKMARK_SLOTS = 3
        private const val TRACK_PREFS = "bbc_tracks"
        private const val KEY_PLAYLIST = "playlist"
        private const val KEY_GROUPS = "groups"
        private const val KEY_ACTIVE_GROUP = "active_group"
        private const val KEY_INDEX = "playlist_index"
        private const val KEY_DEFAULT_REMOVED = "default_group_removed"
        const val FIRST_GROUP_NAME = "내 폴더"
    }
}
