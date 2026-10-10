package com.hdlee73.englishstudy.listening

import com.hdlee73.englishstudy.R

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
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
 * playback position and highlights the current sentence. Tap = jump there, long press = loop it,
 * long press then drag = loop every sentence the finger passes over.
 */
class SubtitleSheet(
    private val context: Context,
    private val svc: PlaybackService,
    /** Sends the words picked in the subtitles to the dictionary / word list ... */
    private val onLookup: (String) -> Unit,
    /** ... or to the translator. */
    private val onTranslate: (String) -> Unit,
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
    /** When on, the words of a subtitle can be selected like any text (a word, a phrase), without regard to the time stamps. */
    private var selectMode = false
    private var dragAnchor = -1
    private var dragEnd = -1
    private var dragY = 0f
    private var dragX = 0f
    private var lastDownRawX = 0f
    private var lastDownRawY = 0f
    /** True while a finger drags a text selection (select mode), false for the sentence-range drag. */
    private var textDrag = false
    private var dragAnchorOffset = 0
    private var dragEndOffset = 0
    /** The text picked in select mode, which may run over several sentences: (cue index, character offset) of both ends. */
    private var sel: TextSel? = null

    private class TextSel(val loPos: Int, val loOff: Int, val hiPos: Int, val hiOff: Int)
    private val autoScroll = object : Runnable {
        override fun run() {
            if (dragAnchor < 0) return
            val edge = list.height * 0.15f
            val dy = when {
                dragY < edge -> -((edge - dragY) / edge * 28f + 4f)
                dragY > list.height - edge -> ((dragY - (list.height - edge)) / edge * 28f + 4f)
                else -> 0f
            }
            if (dy != 0f) {
                list.scrollBy(0, dy.toInt())
                updateDrag()
            }
            handler.postDelayed(this, 16L)
        }
    }

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

        list.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                    lastDownRawX = e.rawX
                    lastDownRawY = e.rawY
                }
                if (dragAnchor < 0) return false
                if (e.actionMasked == MotionEvent.ACTION_MOVE) {
                    rv.parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) finishDrag(e.actionMasked == MotionEvent.ACTION_UP)
                return false
            }

            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {
                if (dragAnchor < 0) return
                when (e.actionMasked) {
                    MotionEvent.ACTION_MOVE -> {
                        dragY = e.y
                        dragX = e.x
                        updateDrag()
                    }
                    MotionEvent.ACTION_UP -> finishDrag(true)
                    MotionEvent.ACTION_CANCEL -> finishDrag(false)
                }
            }

            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
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

    private fun startDrag(position: Int, y: Float) {
        dragAnchor = position
        dragEnd = position
        dragY = y
        list.parent?.requestDisallowInterceptTouchEvent(true)
        adapter.notifyItemChanged(position)
        handler.post(autoScroll)
    }

    private fun updateDrag() {
        if (textDrag) {
            updateTextDrag()
            return
        }
        val child = list.findChildViewUnder(list.width / 2f, dragY.coerceIn(0f, list.height - 1f)) ?: return
        val pos = list.getChildAdapterPosition(child)
        if (pos < 0 || pos == dragEnd) return
        val lo = minOf(dragAnchor, dragEnd, pos)
        val hi = maxOf(dragAnchor, dragEnd, pos)
        dragEnd = pos
        adapter.notifyItemRangeChanged(lo, hi - lo + 1)
    }

    /** Character offset in cue row [pos] under the screen point ([rawX], [rawY]); rows scrolled away give the nearest end. */
    private fun offsetAt(pos: Int, rawX: Float, rawY: Float): Int {
        val holder = list.findViewHolderForAdapterPosition(pos) as? CueHolder ?: return -1
        val loc = IntArray(2)
        holder.text.getLocationOnScreen(loc)
        return holder.text.getOffsetForPosition(rawX - loc[0], rawY - loc[1])
    }

    private fun startTextDrag(position: Int) {
        val offset = offsetAt(position, lastDownRawX, lastDownRawY).coerceAtLeast(0)
        textDrag = true
        sel = null
        dragAnchor = position
        dragEnd = position
        dragAnchorOffset = offset
        dragEndOffset = offset
        dragX = list.width / 2f
        dragY = lastDownRawY - IntArray(2).also { list.getLocationOnScreen(it) }[1]
        list.parent?.requestDisallowInterceptTouchEvent(true)
        publishTextSel()
        handler.post(autoScroll)
    }

    private fun updateTextDrag() {
        val child = list.findChildViewUnder(list.width / 2f, dragY.coerceIn(0f, list.height - 1f)) ?: return
        val pos = list.getChildAdapterPosition(child)
        if (pos < 0) return
        val loc = IntArray(2)
        list.getLocationOnScreen(loc)
        val offset = offsetAt(pos, loc[0] + dragX, loc[1] + dragY)
        if (offset < 0 || (pos == dragEnd && offset == dragEndOffset)) return
        dragEnd = pos
        dragEndOffset = offset
        publishTextSel()
    }

    /** Orders the two ends, widens them to whole words and redraws the rows that changed. */
    private fun publishTextSel() {
        val forward = dragAnchor < dragEnd || (dragAnchor == dragEnd && dragAnchorOffset <= dragEndOffset)
        val loPos = if (forward) dragAnchor else dragEnd
        val loOffRaw = if (forward) dragAnchorOffset else dragEndOffset
        val hiPos = if (forward) dragEnd else dragAnchor
        val hiOffRaw = if (forward) dragEndOffset else dragAnchorOffset
        val loText = cues.getOrNull(loPos)?.text ?: return
        val hiText = cues.getOrNull(hiPos)?.text ?: return
        var lo = loOffRaw.coerceIn(0, loText.length)
        while (lo > 0 && !loText[lo - 1].isWhitespace()) lo--
        var hi = hiOffRaw.coerceIn(0, hiText.length)
        while (hi < hiText.length && !hiText[hi].isWhitespace()) hi++
        val old = sel
        val fresh = TextSel(loPos, lo, hiPos, hi)
        sel = fresh
        val from = minOf(old?.loPos ?: loPos, loPos)
        val to = maxOf(old?.hiPos ?: hiPos, hiPos)
        adapter.notifyItemRangeChanged(from, to - from + 1)
    }

    private fun clearTextSel() {
        val old = sel ?: return
        sel = null
        adapter.notifyItemRangeChanged(old.loPos, old.hiPos - old.loPos + 1)
    }

    /** The picked text, sentence parts joined by a space. */
    private fun selectedText(s: TextSel): String = (s.loPos..s.hiPos).mapNotNull { pos ->
        val text = cues.getOrNull(pos)?.text ?: return@mapNotNull null
        text.substring(if (pos == s.loPos) s.loOff else 0, if (pos == s.hiPos) s.hiOff.coerceAtMost(text.length) else text.length)
    }.joinToString(" ").replace(Regex("\\s+"), " ").trim()

    private fun finishTextDrag(apply: Boolean) {
        textDrag = false
        dragAnchor = -1
        dragEnd = -1
        handler.removeCallbacks(autoScroll)
        val picked = sel
        if (!apply || picked == null || selectedText(picked).isEmpty()) {
            clearTextSel()
            return
        }
        showSelectionMenu(picked)
    }

    private fun showSelectionMenu(picked: TextSel) {
        val text = selectedText(picked)
        val anchor = list.findViewHolderForAdapterPosition(picked.hiPos)?.itemView ?: list
        IconMenu.show(
            context, anchor, listOf(
                IconMenu.Item(MENU_PLAY, "이 부분 재생", R.drawable.ls_ic_play),
                IconMenu.Item(MENU_LOOKUP, "단어장·사전", R.drawable.ls_ic_edit),
                IconMenu.Item(MENU_TRANSLATE, "번역", R.drawable.ls_ic_subtitles),
                IconMenu.Item(MENU_COPY, "복사", R.drawable.ls_ic_list)
            ), alignEnd = false, onDismiss = { clearTextSel() }
        ) { id ->
            when (id) {
                MENU_PLAY -> playSelection(picked)
                MENU_LOOKUP -> onLookup(text)
                MENU_TRANSLATE -> onTranslate(text)
                MENU_COPY -> {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("subtitle", text))
                    Toast.makeText(context, "복사했어요.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /** Loops from the start of the first picked word to the end of the last one, over any number of sentences. */
    private fun playSelection(s: TextSel) {
        val first = cues.getOrNull(s.loPos) ?: return
        val last = cues.getOrNull(s.hiPos) ?: return
        fun timeAt(cue: Cue, offset: Int): Long =
            cue.startMs + (cue.endMs - cue.startMs).coerceAtLeast(1L) * offset / cue.text.length.coerceAtLeast(1)
        val from = timeAt(first, s.loOff)
        val to = if (s.hiOff >= last.text.length) last.endMs else timeAt(last, s.hiOff)
        val a = (from - 350L).coerceAtLeast(maxOf(0L, first.startMs - 300L))
        val b = maxOf(to + 450L, a + 800L)
        svc.setA(a)
        if (svc.setB(b)) {
            svc.resume()
            Toast.makeText(context, "고른 부분을 반복 재생합니다. (시간은 문장 안의 위치로 추정)", Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishDrag(apply: Boolean) {
        if (textDrag) {
            finishTextDrag(apply)
            return
        }
        val a = dragAnchor
        val b = dragEnd
        dragAnchor = -1
        dragEnd = -1
        handler.removeCallbacks(autoScroll)
        if (a < 0) return
        val lo = minOf(a, b)
        val hi = maxOf(a, b)
        adapter.notifyItemRangeChanged(lo, hi - lo + 1)
        if (!apply) return
        val first = cues.getOrNull(lo) ?: return
        val last = cues.getOrNull(hi) ?: return
        svc.setA(first.startMs)
        if (svc.setB(last.endMs.coerceAtLeast(first.startMs + 500L))) {
            svc.resume()
            Toast.makeText(context, if (lo == hi) "이 문장을 구간 반복으로 지정했습니다." else "문장 ${hi - lo + 1}개를 구간 반복으로 지정했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMenu(anchor: View) {
        val items = mutableListOf(IconMenu.Item(3, if (selectMode) "글자 선택 모드 끄기" else "글자 선택 모드 (단어·구절 고르기)", R.drawable.ls_ic_edit))
        items += IconMenu.Item(1, if (svc.subtitleUri == null) "자막 파일 선택" else "다른 자막 파일 선택", R.drawable.ls_ic_subtitles)
        if (svc.subtitleUri != null) items += IconMenu.Item(2, "이 곡의 자막 제거", R.drawable.ls_ic_delete, destructive = true)
        IconMenu.show(context, anchor, items, alignEnd = true) { id ->
            when (id) {
                1 -> pickFile()
                3 -> {
                    selectMode = !selectMode
                    hint.let { (it as? TextView)?.text = if (selectMode) SELECT_HINT else NORMAL_HINT }
                    adapter.notifyDataSetChanged()
                    Toast.makeText(context, if (selectMode) "자막 글자를 길게 눌러 단어나 구절을 고르세요." else "글자 선택 모드를 껐습니다.", Toast.LENGTH_SHORT).show()
                }
                2 -> {
                    svc.setSubtitle(null)
                    reload()
                }
            }
        }
    }

    private companion object {
        const val MENU_PLAY = 1
        const val MENU_LOOKUP = 2
        const val MENU_TRANSLATE = 3
        const val MENU_COPY = 4
        const val NORMAL_HINT = "문장을 누르면 그 위치로 이동 · 길게 누르면 그 문장을 구간 반복 · 길게 누른 채 끌면 여러 문장을 구간 반복"
        const val SELECT_HINT = "글자 선택 모드: 자막 글자를 길게 누른 채 끌면 다른 문장(시간대)까지 이어서 고를 수 있어요. 손을 떼면 ‘이 부분 재생 · 단어장·사전 · 번역 · 복사’ 메뉴가 나와요. (메뉴 ⋮ 에서 끌 수 있어요)"
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
            val selected = dragAnchor >= 0 && position in minOf(dragAnchor, dragEnd)..maxOf(dragAnchor, dragEnd)
            val current = position == currentIndex || selected
            holder.time.text = formatTime(cue.startMs)
            holder.text.text = cue.text
            holder.text.setTextColor(context.getColor(if (current) R.color.ls_teal_700 else R.color.ls_text_secondary))
            holder.text.setTypeface(null, if (current) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            holder.itemView.setBackgroundResource(if (current) R.drawable.ls_bg_row_current else 0)
            holder.text.setTextIsSelectable(false)
            if (selectMode) {
                val picked = sel
                if (picked != null && position in picked.loPos..picked.hiPos) {
                    val span = android.text.SpannableString(cue.text)
                    val from = if (position == picked.loPos) picked.loOff.coerceIn(0, cue.text.length) else 0
                    val to = if (position == picked.hiPos) picked.hiOff.coerceIn(0, cue.text.length) else cue.text.length
                    if (to > from) span.setSpan(android.text.style.BackgroundColorSpan(0x66FFD54F), from, to, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    holder.text.text = span
                }
                holder.itemView.setOnClickListener { clearTextSel() }
                holder.itemView.setOnLongClickListener {
                    val pos = holder.bindingAdapterPosition
                    if (pos >= 0) startTextDrag(pos)
                    true
                }
                return
            }
            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                cues.getOrNull(pos)?.let { svc.player.seekTo(it.startMs) }
            }
            holder.itemView.setOnLongClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos >= 0) startDrag(pos, holder.itemView.top + holder.itemView.height / 2f)
                true
            }
        }

        override fun getItemCount(): Int = cues.size
    }
}
