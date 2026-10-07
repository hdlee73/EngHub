package com.hdlee73.englishstudy.listening

import com.hdlee73.englishstudy.R

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * On-demand subtitle window. It shows the subtitle file linked to the playing track, follows the
 * playback position and highlights the current sentence. Tap = jump there, long press = loop it.
 */
class SubtitleSheet(
    private val context: Context,
    private val svc: PlaybackService,
    private val pickFile: () -> Unit
) {
    private val dialog = BottomSheetDialog(context)
    private val root: View = LayoutInflater.from(context).inflate(R.layout.ls_sheet_subtitles, null)
    private val list: RecyclerView = root.findViewById(R.id.cueList)
    private val empty: View = root.findViewById(R.id.cueEmpty)
    private val emptyText: TextView = root.findViewById(R.id.cueEmptyText)
    private val hint: View = root.findViewById(R.id.subHint)
    private val playButton: ImageButton = root.findViewById(R.id.subPlayButton)
    private val layoutManager = LinearLayoutManager(context)
    private val adapter = CueAdapter()
    private val handler = Handler(Looper.getMainLooper())

    private var cues: List<Cue> = emptyList()
    private var loadedKey: String? = null
    private var generation = 0
    private var currentIndex = -1
    private var lastUserScroll = 0L
    private var lastActive: Boolean? = null

    fun show() {
        val height = (context.resources.displayMetrics.heightPixels * 0.88f).toInt()
        root.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        dialog.setContentView(root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        // Keep the bottom sheet from stealing vertical drags meant for the list.
        dialog.behavior.isDraggable = false

        list.layoutManager = layoutManager
        list.adapter = adapter
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) lastUserScroll = System.currentTimeMillis()
            }
        })

        playButton.setOnClickListener { svc.togglePlay() }
        root.findViewById<ImageButton>(R.id.subMenuButton).setOnClickListener { showMenu(it) }
        root.findViewById<Button>(R.id.cuePick).setOnClickListener { pickFile() }
        refresh()
        dialog.show()
    }

    /** Forces the subtitle file to be read again, e.g. right after a new one was chosen. */
    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    fun reload() {
        loadedKey = null
        refresh()
    }

    /** Called by the activity a few times a second while the window is open. */
    fun refresh() {
        if (!dialog.isShowing) return
        val subtitle = svc.subtitleUri
        val key = (svc.currentTrackUri ?: "") + "|" + (subtitle ?: "")
        if (key != loadedKey) {
            loadedKey = key
            load(subtitle)
            return
        }
        val active = svc.isActive
        if (lastActive != active) {
            lastActive = active
            playButton.setImageResource(if (active) R.drawable.ls_ic_pause else R.drawable.ls_ic_play)
        }
        followPosition()
    }

    private fun load(subtitle: String?) {
        val mine = ++generation
        cues = emptyList()
        currentIndex = -1
        adapter.notifyDataSetChanged()
        if (subtitle == null) {
            showEmpty("이 곡에 연결된 자막이 없어요.\n.srt 자막 파일을 고르면 재생 위치에 맞춰 문장이 표시돼요.", true)
            return
        }
        showEmpty("자막을 불러오는 중…", false)
        Thread {
            val parsed = try {
                SubtitleParser.load(context, subtitle)
            } catch (_: Throwable) {
                null
            }
            handler.post {
                if (mine != generation) return@post
                when {
                    parsed == null -> showEmpty("자막 파일을 읽을 수 없어요.\n다른 파일을 골라 주세요.", true)
                    parsed.isEmpty() -> showEmpty("자막 형식을 인식하지 못했어요. (.srt, .vtt, .lrc 지원)", true)
                    else -> {
                        cues = parsed
                        empty.visibility = View.GONE
                        list.visibility = View.VISIBLE
                        hint.visibility = View.VISIBLE
                        adapter.notifyDataSetChanged()
                        followPosition(force = true)
                    }
                }
            }
        }.start()
    }

    private fun showEmpty(message: String, withButton: Boolean) {
        emptyText.text = message
        root.findViewById<Button>(R.id.cuePick).visibility = if (withButton) View.VISIBLE else View.GONE
        empty.visibility = View.VISIBLE
        list.visibility = View.GONE
        hint.visibility = View.GONE
    }

    private fun indexAt(positionMs: Long): Int {
        var low = 0
        var high = cues.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (cues[mid].startMs <= positionMs) {
                result = mid
                low = mid + 1
            } else high = mid - 1
        }
        return result
    }

    private fun followPosition(force: Boolean = false) {
        if (cues.isEmpty()) return
        val index = indexAt(svc.player.currentPosition)
        if (index == currentIndex && !force) return
        val old = currentIndex
        currentIndex = index
        if (old >= 0) adapter.notifyItemChanged(old)
        if (index >= 0) {
            adapter.notifyItemChanged(index)
            val idle = System.currentTimeMillis() - lastUserScroll > 4_000L
            if (idle || force) layoutManager.scrollToPositionWithOffset(index, list.height / 3)
        }
    }

    private fun showMenu(anchor: View) {
        val items = mutableListOf(IconMenu.Item(1, if (svc.subtitleUri == null) "자막 파일 선택" else "다른 자막 파일 선택", R.drawable.ls_ic_subtitles))
        if (svc.subtitleUri != null) items += IconMenu.Item(2, "이 곡의 자막 제거", R.drawable.ls_ic_delete, destructive = true)
        IconMenu.show(context, anchor, items, alignEnd = true) { id ->
            when (id) {
                1 -> pickFile()
                2 -> {
                    svc.setSubtitle(null)
                    reload()
                }
            }
        }
    }

    private inner class CueHolder(view: View) : RecyclerView.ViewHolder(view) {
        val time: TextView = view.findViewById(R.id.cueTime)
        val text: TextView = view.findViewById(R.id.cueText)
    }

    private inner class CueAdapter : RecyclerView.Adapter<CueHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CueHolder =
            CueHolder(LayoutInflater.from(parent.context).inflate(R.layout.ls_item_cue, parent, false))

        override fun onBindViewHolder(holder: CueHolder, position: Int) {
            val cue = cues[position]
            val current = position == currentIndex
            holder.time.text = formatTime(cue.startMs)
            holder.text.text = cue.text
            holder.text.setTextColor(context.getColor(if (current) R.color.ls_teal_700 else R.color.ls_text_secondary))
            holder.text.setTypeface(null, if (current) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            holder.itemView.setBackgroundResource(if (current) R.drawable.ls_bg_row_current else 0)
            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                cues.getOrNull(pos)?.let { svc.player.seekTo(it.startMs) }
            }
            holder.itemView.setOnLongClickListener {
                val c = cues.getOrNull(holder.bindingAdapterPosition) ?: return@setOnLongClickListener true
                svc.setA(c.startMs)
                if (svc.setB(c.endMs.coerceAtLeast(c.startMs + 500L))) {
                    svc.resume()
                    Toast.makeText(context, "이 문장을 구간 반복으로 지정했습니다.", Toast.LENGTH_SHORT).show()
                }
                true
            }
        }

        override fun getItemCount(): Int = cues.size
    }
}
