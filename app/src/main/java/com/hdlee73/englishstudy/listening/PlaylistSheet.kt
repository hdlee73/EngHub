package com.hdlee73.englishstudy.listening

import com.hdlee73.englishstudy.R

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Bottom sheet with the playlist groups as chips and the tracks of the chosen group below.
 * Tracks can be reordered by dragging the handle; a track or a whole group can be set to repeat.
 */
class PlaylistSheet(
    private val context: Context,
    private val svc: PlaybackService,
    private val addFilesTo: (Int) -> Unit
) {
    private val dialog = BottomSheetDialog(context)
    private val root: View = LayoutInflater.from(context).inflate(R.layout.ls_sheet_playlist, null)
    private val chips: ChipGroup = root.findViewById(R.id.groupChips)
    private val summary: TextView = root.findViewById(R.id.groupSummary)
    private val list: RecyclerView = root.findViewById(R.id.trackList)
    private val emptyText: TextView = root.findViewById(R.id.emptyText)
    private val adapter = TrackAdapter()
    private val touchHelper = ItemTouchHelper(DragCallback())

    private var viewGroup = svc.activeGroup
    private var lastSignature = ""
    private var dragging = false

    val isShowing: Boolean get() = dialog.isShowing

    fun dismiss() {
        if (dialog.isShowing) dialog.dismiss()
    }

    fun show() {
        val height = (context.resources.displayMetrics.heightPixels * 0.82f).toInt()
        root.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        dialog.setContentView(root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        list.layoutManager = LinearLayoutManager(context)
        list.adapter = adapter
        touchHelper.attachToRecyclerView(list)

        root.findViewById<ImageButton>(R.id.sheetAddButton).setOnClickListener { addFilesTo(viewGroup) }
        root.findViewById<ImageButton>(R.id.sheetMenuButton).setOnClickListener { showGroupMenu(it) }

        render()
        dialog.show()
    }

    /** Called regularly by the activity; redraws only when something relevant changed. */
    fun refreshIfChanged() {
        if (!dialog.isShowing || dragging) return
        if (signature() != lastSignature) render()
    }

    private fun signature(): String = buildString {
        append(viewGroup).append('|').append(svc.activeGroup).append('|').append(svc.player.currentMediaItemIndex)
        svc.groups.forEach { append('|').append(it.name).append(':').append(it.entries.size) }
    }

    private fun render() {
        viewGroup = viewGroup.coerceIn(0, svc.groups.lastIndex)
        lastSignature = signature()
        renderChips()
        val entries = svc.groupEntries(viewGroup)
        val isActive = viewGroup == svc.activeGroup
        summary.text = "${entries.size}곡" + if (isActive) "  ·  재생 중인 그룹" else "  ·  곡을 누르면 이 그룹으로 전환돼요"
        adapter.submit(entries, isActive)
        emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun renderChips() {
        chips.removeAllViews()
        svc.groups.forEachIndexed { index, group ->
            val chip = newChip(com.google.android.material.R.style.Widget_Material3_Chip_Filter)
            chip.isCheckable = true
            chip.text = (if (index == svc.activeGroup) "▶ " else "") + group.name + " · " + group.entries.size
            chip.isChecked = index == viewGroup
            chip.setOnClickListener {
                viewGroup = index
                render()
            }
            chips.addView(chip)
        }
        val add = newChip(com.google.android.material.R.style.Widget_Material3_Chip_Assist)
        add.text = "＋ 그룹"
        add.setOnClickListener {
            context.promptText("새 그룹", "그룹 ${svc.groups.size + 1}", "만들기") { name ->
                viewGroup = svc.createGroup(name)
                render()
            }
        }
        chips.addView(add)
    }

    private fun newChip(style: Int): Chip = Chip(context).apply {
        setChipDrawable(ChipDrawable.createFromAttributes(context, null, 0, style))
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ menus

    private fun showGroupMenu(anchor: View) {
        val group = svc.groups.getOrNull(viewGroup) ?: return
        val menu = PopupMenu(context, anchor)
        menu.menu.add(0, 1, 0, "이 그룹 재생")
        menu.menu.add(0, 2, 1, "이 그룹 반복 재생")
        menu.menu.add(0, 3, 2, "이름 변경")
        menu.menu.add(0, 4, 3, "그룹 비우기")
        menu.menu.add(0, 5, 4, if (svc.groups.size > 1) "그룹 삭제" else "그룹 삭제 (마지막 그룹은 비우기만)")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    if (group.entries.isEmpty()) toast("비어 있는 그룹입니다.") else svc.switchGroup(viewGroup, play = true)
                    render()
                }
                2 -> {
                    if (group.entries.isEmpty()) {
                        toast("비어 있는 그룹입니다.")
                    } else {
                        svc.switchGroup(viewGroup, play = true)
                        svc.updateRepeatMode(2)
                        toast("‘${group.name}’ 그룹을 반복 재생합니다.")
                    }
                    render()
                }
                3 -> context.promptText("그룹 이름", group.name) { name ->
                    svc.renameGroup(viewGroup, name)
                    render()
                }
                4 -> confirm("‘${group.name}’ 그룹의 곡을 모두 뺄까요?\n(곡별 기록은 유지됩니다)", "비우기") {
                    svc.clearGroup(viewGroup)
                    render()
                }
                5 -> confirm("‘${group.name}’ 그룹을 삭제할까요?\n(곡별 기록은 유지됩니다)", "삭제") {
                    val wasLast = svc.groups.size == 1
                    svc.deleteGroup(viewGroup)
                    if (!wasLast) viewGroup = (viewGroup - 1).coerceAtLeast(0)
                    render()
                }
            }
            true
        }
        menu.show()
    }

    private fun showTrackMenu(anchor: View, position: Int) {
        val entry = svc.groupEntries(viewGroup).getOrNull(position) ?: return
        val menu = PopupMenu(context, anchor)
        menu.menu.add(0, 1, 0, "이 파일 반복 재생")
        if (svc.groups.size > 1) menu.menu.add(0, 2, 1, "다른 그룹으로 이동")
        menu.menu.add(0, 3, 2, "이어듣기 기록 초기화")
        menu.menu.add(0, 4, 3, "목록에서 삭제")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    svc.playIn(viewGroup, position)
                    svc.updateRepeatMode(1)
                    toast("‘${entry.name}’ 파일을 반복 재생합니다.")
                    render()
                }
                2 -> chooseTargetGroup(position, entry)
                3 -> {
                    svc.resetProgress(viewGroup, position)
                    render()
                }
                4 -> {
                    svc.removeFrom(viewGroup, position)
                    render()
                }
            }
            true
        }
        menu.show()
    }

    private fun chooseTargetGroup(position: Int, entry: TrackStore.Entry) {
        val targets = svc.groups.indices.filter { it != viewGroup }
        MaterialAlertDialogBuilder(context)
            .setTitle("어느 그룹으로 옮길까요?")
            .setItems(targets.map { svc.groups[it].name }.toTypedArray()) { _, which ->
                val moved = svc.moveToGroup(viewGroup, position, targets[which])
                if (!moved) toast("‘${entry.name}’ 파일이 이미 그 그룹에 있습니다.")
                render()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun confirm(message: String, positive: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(context)
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> action() }
            .setNegativeButton("취소", null)
            .show()
    }

    // ------------------------------------------------------------------ list

    private inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val state: TextView = view.findViewById(R.id.trackState)
        val name: TextView = view.findViewById(R.id.trackName)
        val more: ImageButton = view.findViewById(R.id.trackMore)
        val drag: ImageView = view.findViewById(R.id.trackDrag)
    }

    private inner class TrackAdapter : RecyclerView.Adapter<Holder>() {
        val rows = mutableListOf<TrackStore.Entry>()
        private var currentPosition = -1

        fun submit(entries: List<TrackStore.Entry>, active: Boolean) {
            rows.clear()
            rows.addAll(entries)
            currentPosition = if (active) svc.player.currentMediaItemIndex else -1
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.ls_item_track, parent, false)
            return Holder(view)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: Holder, position: Int) {
            val entry = rows[position]
            val isCurrent = position == currentPosition
            holder.name.text = entry.name
            holder.name.setTextColor(context.getColor(if (isCurrent) R.color.ls_teal_700 else R.color.ls_text_primary))
            holder.name.setTypeface(null, if (isCurrent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            holder.state.text = when {
                isCurrent -> "▶"
                svc.isDone(entry.uri) -> "✓"
                else -> (position + 1).toString()
            }
            holder.state.setTextColor(context.getColor(if (isCurrent || holder.state.text == "✓") R.color.ls_teal_600 else R.color.ls_text_secondary))
            holder.itemView.setBackgroundResource(if (isCurrent) R.drawable.ls_bg_row_current else 0)
            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos >= 0) {
                    svc.playIn(viewGroup, pos)
                    render()
                }
            }
            holder.more.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos >= 0) showTrackMenu(it, pos)
            }
            holder.drag.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(holder)
                false
            }
        }

        override fun getItemCount(): Int = rows.size
    }

    private inner class DragCallback : ItemTouchHelper.Callback() {
        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
            makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)

        override fun isLongPressDragEnabled(): Boolean = false

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from < 0 || to < 0) return false
            adapter.rows.add(to, adapter.rows.removeAt(from))
            adapter.notifyItemMoved(from, to)
            svc.moveInGroup(viewGroup, from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) dragging = true
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            dragging = false
            recyclerView.post { render() }
        }
    }
}
