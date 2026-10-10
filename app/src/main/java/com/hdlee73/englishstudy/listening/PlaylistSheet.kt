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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Bottom sheet with the playlist groups shown as folders. Tapping a folder opens it and lists its tracks;
 * the back arrow returns to the folders. Tracks can be reordered by dragging the handle; a track or a whole folder can be set to repeat.
 */
class PlaylistSheet(
    private val context: Context,
    private val svc: PlaybackService,
    private val addFilesTo: (Int) -> Unit,
    private val importDeviceFolder: () -> Unit,
    /** Opens the system folder picker for the folder subtitles are saved in; calls back once one is chosen. */
    private val pickSaveFolder: (onPicked: () -> Unit) -> Unit
) {
    private val dialog = BottomSheetDialog(context)
    private val root: View = LayoutInflater.from(context).inflate(R.layout.ls_sheet_playlist, null)
    private val title: TextView = root.findViewById(R.id.sheetTitle)
    private val backButton: ImageButton = root.findViewById(R.id.sheetBackButton)
    private val addButton: ImageButton = root.findViewById(R.id.sheetAddButton)
    private val menuButton: ImageButton = root.findViewById(R.id.sheetMenuButton)
    private val viewButton: ImageButton = root.findViewById(R.id.sheetViewButton)
    private val fetchButton: android.widget.Button = root.findViewById(R.id.fetchEpisodeButton)
    private val srtAllButton: android.widget.Button = root.findViewById(R.id.srtAllButton)
    private val prefs = context.getSharedPreferences(TrackStore.PREFS, Context.MODE_PRIVATE)
    /** Folders as a list of rows instead of a grid of cards; remembered. */
    private var listView = prefs.getBoolean(KEY_LIST_VIEW, false)
    private val folders: RecyclerView = root.findViewById(R.id.folderGrid)
    private val summary: TextView = root.findViewById(R.id.groupSummary)
    private val list: RecyclerView = root.findViewById(R.id.trackList)
    private val emptyText: TextView = root.findViewById(R.id.emptyText)
    private val adapter = TrackAdapter()
    private val folderAdapter = FolderAdapter()
    private val touchHelper = ItemTouchHelper(DragCallback())

    private var viewGroup = svc.activeGroup
    /** False while the folders are shown, true while the tracks of [viewGroup] are shown. */
    private var inFolder = false
    /** The folder whose subfolders are listed (null = top level); only meaningful while [inFolder] is false. */
    private var levelParent: String? = null
    private var folderRows: List<Int> = emptyList()
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
        applyFolderLayout()
        folders.adapter = folderAdapter
        viewButton.setOnClickListener {
            listView = !listView
            prefs.edit().putBoolean(KEY_LIST_VIEW, listView).apply()
            applyFolderLayout()
            folders.adapter = folderAdapter
        }
        srtAllButton.setOnClickListener { SrtDialog.show(context, svc.groupEntries(viewGroup), pickSaveFolder) }
        fetchButton.setOnClickListener {
            // The 6min folder is created by the download itself when it does not exist yet.
            val have = svc.groups.filter { it.name.equals(SixMinuteEnglish.GROUP_NAME, ignoreCase = true) }
                .flatMap { it.entries }.mapNotNull { SixMinuteEnglish.episodeKey(it.name) }
            SixMinuteEnglish.fetchLatest(context, have)
            toast("최신 에피소드를 확인하고 있어요…")
        }

        backButton.setOnClickListener { goBack() }
        addButton.setOnClickListener {
            if (inFolder) addFilesTo(viewGroup) else {
                IconMenu.show(context, it, listOf(
                    IconMenu.Item(1, if (levelParent != null) "하위 폴더 만들기" else "새 폴더 만들기", R.drawable.ls_ic_folder_add),
                    IconMenu.Item(2, "기기 폴더 가져오기 (하위 폴더 포함)", R.drawable.ls_ic_folder_open)
                ), alignEnd = true) { id ->
                    if (id == 1) promptNewFolder(levelParent) else importDeviceFolder()
                }
            }
        }
        menuButton.setOnClickListener { showGroupMenu(it) }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && (inFolder || levelParent != null)) {
                if (event.action == android.view.KeyEvent.ACTION_UP) goBack()
                true
            } else false
        }

        render()
        dialog.show()
    }

    /** Called regularly by the activity; redraws only when something relevant changed. */
    fun refreshIfChanged() {
        if (!dialog.isShowing || dragging) return
        if (signature() != lastSignature) render()
    }

    private fun signature(): String = buildString {
        append(SubtitleLinks.version).append('|').append(inFolder).append('|').append(levelParent).append('|').append(viewGroup).append('|').append(svc.activeGroup).append('|').append(svc.player.currentMediaItemIndex)
        svc.groups.forEach { append('|').append(it.name).append(':').append(it.entries.size).append(':').append(it.parentId) }
    }

    private fun applyFolderLayout() {
        val columns = if (context.resources.configuration.screenWidthDp >= 600) 4 else 2
        folders.layoutManager = if (listView) LinearLayoutManager(context) else GridLayoutManager(context, columns)
        viewButton.setImageResource(if (listView) R.drawable.ls_ic_grid else R.drawable.ls_ic_list)
        viewButton.contentDescription = if (listView) "카드로 보기" else "목록으로 보기"
    }

    /** The folder's parent, or null for the top level (also when the parent no longer exists). */
    private fun parentOf(g: TrackStore.Group): String? = g.parentId?.takeIf { id -> svc.groups.any { it.id == id } }

    private fun childrenOf(parent: String?): List<Int> = svc.groups.indices.filter { parentOf(svc.groups[it]) == parent }

    private fun depthOf(g: TrackStore.Group): Int {
        var depth = 0
        var p = parentOf(g)
        while (p != null && depth < 20) {
            depth++
            p = svc.groups.firstOrNull { it.id == p }?.let { parentOf(it) }
        }
        return depth
    }

    /** A folder with subfolders opens their list (with a row for its own tracks); one without opens its tracks. */
    private fun openFolder(index: Int) {
        val group = svc.groups.getOrNull(index) ?: return
        viewGroup = index
        if (childrenOf(group.id).isNotEmpty()) {
            levelParent = group.id
            inFolder = false
        } else {
            levelParent = parentOf(group)
            inFolder = true
        }
        render()
    }

    private fun goBack() {
        if (inFolder) {
            inFolder = false
        } else {
            val current = svc.groups.firstOrNull { it.id == levelParent }
            levelParent = current?.let { parentOf(it) }
        }
        render()
    }

    private fun promptNewFolder(parentId: String? = null) {
        context.promptText(if (parentId != null) "하위 폴더" else "새 폴더", "폴더 ${svc.groups.size + 1}", "만들기") { name ->
            val index = svc.createGroup(name, parentId)
            levelParent = parentId
            inFolder = true
            viewGroup = index
            render()
        }
    }

    private fun render() {
        viewGroup = viewGroup.coerceIn(0, svc.groups.lastIndex)
        if (levelParent != null && svc.groups.none { it.id == levelParent }) levelParent = null
        lastSignature = signature()
        val level = svc.groups.firstOrNull { it.id == levelParent }
        backButton.visibility = if (inFolder || level != null) View.VISIBLE else View.GONE
        menuButton.visibility = if (inFolder) View.VISIBLE else View.GONE
        viewButton.visibility = if (inFolder) View.GONE else View.VISIBLE
        fetchButton.visibility = View.VISIBLE
        srtAllButton.visibility = if (inFolder && svc.groupEntries(viewGroup).isNotEmpty()) View.VISIBLE else View.GONE
        folders.visibility = if (inFolder) View.GONE else View.VISIBLE
        list.visibility = if (inFolder) View.VISIBLE else View.GONE
        if (!inFolder) {
            val subs = childrenOf(levelParent)
            folderRows = (if (level != null) listOf(ROW_OWN) else emptyList()) + subs + ROW_NEW
            title.text = level?.name ?: "재생목록"
            addButton.contentDescription = if (level != null) "하위 폴더 만들기" else "새 폴더"
            summary.text = if (level != null) "하위 폴더 ${subs.size}개  ·  이 폴더의 곡은 맨 위 줄에서 볼 수 있어요"
            else "폴더 ${subs.size}개  ·  폴더를 눌러 곡을 보세요 (길게 누르면 메뉴)"
            emptyText.visibility = View.GONE
            folderAdapter.notifyDataSetChanged()
            return
        }
        val entries = svc.groupEntries(viewGroup)
        val isActive = viewGroup == svc.activeGroup
        title.text = svc.groups[viewGroup].name
        addButton.contentDescription = "이 폴더에 파일 추가"
        summary.text = "${entries.size}곡" + if (isActive) "  ·  재생 중인 폴더" else "  ·  곡을 누르면 이 폴더로 전환돼요"
        adapter.submit(entries, isActive)
        emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
    }

    private inner class FolderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.folderIcon)
        val badge: TextView = view.findViewById(R.id.folderBadge)
        val name: TextView = view.findViewById(R.id.folderName)
        val count: TextView = view.findViewById(R.id.folderCount)
    }

    /** The folders, followed by a "new folder" card. */
    private inner class FolderAdapter : RecyclerView.Adapter<FolderHolder>() {
        override fun getItemViewType(position: Int): Int = if (listView) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderHolder =
            FolderHolder(LayoutInflater.from(parent.context).inflate(if (viewType == 1) R.layout.ls_item_folder_row else R.layout.ls_item_folder, parent, false))

        override fun onBindViewHolder(holder: FolderHolder, position: Int) {
            val row = folderRows[position]
            if (row == ROW_NEW) {
                holder.icon.setImageResource(R.drawable.ls_ic_add)
                holder.badge.text = ""
                holder.name.text = if (levelParent != null) "하위 폴더" else "새 폴더"
                holder.count.text = if (listView) "" else "폴더 만들기"
                holder.itemView.setBackgroundResource(R.drawable.ls_bg_folder)
                holder.itemView.setOnClickListener { promptNewFolder(levelParent) }
                holder.itemView.setOnLongClickListener(null)
                return
            }
            if (row == ROW_OWN) {
                val own = svc.groups.indexOfFirst { it.id == levelParent }
                val group = svc.groups.getOrNull(own) ?: return
                val active = own == svc.activeGroup
                holder.icon.setImageResource(R.drawable.ls_ic_folder_open)
                holder.badge.text = if (active) "▶ 재생 중" else ""
                holder.name.text = "이 폴더의 곡 보기"
                holder.count.text = "${group.entries.size}곡"
                holder.itemView.setBackgroundResource(if (active) R.drawable.ls_bg_folder_active else R.drawable.ls_bg_folder)
                holder.itemView.setOnClickListener {
                    viewGroup = own
                    inFolder = true
                    render()
                }
                holder.itemView.setOnLongClickListener(null)
                return
            }
            val group = svc.groups[row]
            val active = row == svc.activeGroup
            val subs = childrenOf(group.id).size
            holder.icon.setImageResource(R.drawable.ls_ic_folder)
            holder.badge.text = if (active) "▶ 재생 중" else ""
            holder.name.text = group.name
            holder.count.text = "${group.entries.size}곡" + if (subs > 0) " · 하위 ${subs}" else ""
            holder.itemView.setBackgroundResource(if (active) R.drawable.ls_bg_folder_active else R.drawable.ls_bg_folder)
            holder.itemView.setOnClickListener { openFolder(row) }
            holder.itemView.setOnLongClickListener {
                viewGroup = row
                showGroupMenu(it)
                true
            }
        }

        override fun getItemCount(): Int = folderRows.size
    }

    private fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ menus

    private fun showGroupMenu(anchor: View) {
        val group = svc.groups.getOrNull(viewGroup) ?: return
        val index = viewGroup
        val items = listOf(
            IconMenu.Item(1, "이 폴더 재생", R.drawable.ls_ic_play),
            IconMenu.Item(2, "이 폴더 반복 재생", R.drawable.ls_ic_repeat),
            IconMenu.Item(6, "하위 폴더 만들기", R.drawable.ls_ic_folder_add, dividerBefore = true),
            IconMenu.Item(7, "폴더 이동", R.drawable.ls_ic_folder_move),
            IconMenu.Item(8, "앞으로 (위로) 옮기기", R.drawable.ls_ic_arrow_up),
            IconMenu.Item(9, "뒤로 (아래로) 옮기기", R.drawable.ls_ic_arrow_down),
            IconMenu.Item(10, "곡을 이름순으로 정렬", R.drawable.ls_ic_sort),
            IconMenu.Item(3, "이름 변경", R.drawable.ls_ic_edit),
            IconMenu.Item(11, "폴더 곡 모두 자막(SRT) 만들기", R.drawable.ls_ic_subtitles),
            IconMenu.Item(4, "폴더 비우기", R.drawable.ls_ic_clear_list, dividerBefore = true),
            IconMenu.Item(5, if (svc.groups.size > 1) "폴더 삭제" else "폴더 삭제 (마지막 폴더는 비우기만)", R.drawable.ls_ic_delete, destructive = true)
        )
        IconMenu.show(context, anchor, items, alignEnd = anchor.width < anchor.resources.displayMetrics.widthPixels / 2) { id ->
            when (id) {
                1 -> {
                    if (group.entries.isEmpty()) toast("비어 있는 폴더입니다.") else svc.switchGroup(index, play = true)
                    render()
                }
                2 -> {
                    if (group.entries.isEmpty()) {
                        toast("비어 있는 폴더입니다.")
                    } else {
                        svc.switchGroup(index, play = true)
                        svc.updateRepeatMode(2)
                        toast("‘${group.name}’ 폴더를 반복 재생합니다.")
                    }
                    render()
                }
                3 -> context.promptText("폴더 이름", group.name) { name ->
                    svc.renameGroup(index, name)
                    render()
                }
                4 -> confirm("‘${group.name}’ 폴더의 곡을 모두 뺄까요?\n(곡별 기록은 유지됩니다)", "비우기") {
                    svc.clearGroup(index)
                    render()
                }
                5 -> {
                    val subs = svc.descendants(index)
                    val extra = if (subs.isEmpty()) "" else "\n하위 폴더 ${subs.size}개도 함께 삭제됩니다."
                    confirm("‘${group.name}’ 폴더를 삭제할까요?$extra\n(곡별 기록은 유지됩니다)", "삭제") {
                        (subs + index).sortedDescending().forEach { svc.deleteGroup(it) }
                        viewGroup = 0
                        inFolder = false
                        render()
                    }
                }
                6 -> promptNewFolder(group.id)
                7 -> chooseParentFolder(index)
                8, 9 -> {
                    val moved = svc.reorderGroup(index, if (id == 8) -1 else 1)
                    if (!moved) toast(if (id == 8) "이미 맨 앞입니다." else "이미 맨 뒤입니다.")
                    else viewGroup = svc.groups.indexOf(group)
                    render()
                }
                11 -> {
                    if (group.entries.isEmpty()) toast("비어 있는 폴더입니다.")
                    else SrtDialog.show(context, group.entries.toList(), pickSaveFolder)
                }
                10 -> {
                    svc.sortGroupByName(index)
                    toast("곡을 이름순으로 정렬했습니다.")
                    render()
                }
            }
        }
    }

    /** Lets the user pick where [index] goes: the top level or any folder that is not itself or inside itself. */
    private fun chooseParentFolder(index: Int) {
        val group = svc.groups.getOrNull(index) ?: return
        val blocked = svc.descendants(index).toSet() + index
        val targets = svc.groups.indices.filter { it !in blocked }
        val labels = listOf("📁  최상위") + targets.map { "    ".repeat(depthOf(svc.groups[it])) + "📁  " + svc.groups[it].name }
        MaterialAlertDialogBuilder(context)
            .setTitle("‘${group.name}’ 폴더를 어디로 옮길까요?")
            .setItems(labels.toTypedArray()) { _, which ->
                val parent = if (which == 0) null else svc.groups[targets[which - 1]].id
                if (parent == parentOf(group)) {
                    toast("이미 그 위치에 있습니다.")
                } else if (svc.moveGroup(index, parent)) {
                    toast("폴더를 옮겼습니다.")
                    levelParent = parent
                    inFolder = false
                }
                render()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showTrackMenu(anchor: View, position: Int) {
        val entry = svc.groupEntries(viewGroup).getOrNull(position) ?: return
        val items = mutableListOf(IconMenu.Item(1, "이 파일 반복 재생", R.drawable.ls_ic_repeat_one))
        if (svc.groups.size > 1) items += IconMenu.Item(2, "다른 폴더로 이동", R.drawable.ls_ic_folder_move)
        items += IconMenu.Item(5, "자막(SRT) 만들기", R.drawable.ls_ic_subtitles)
        items += IconMenu.Item(3, "이어듣기 기록 초기화", R.drawable.ls_ic_refresh)
        items += IconMenu.Item(4, "목록에서 삭제", R.drawable.ls_ic_delete, destructive = true, dividerBefore = true)
        IconMenu.show(context, anchor, items, alignEnd = true) { id ->
            when (id) {
                1 -> {
                    svc.playIn(viewGroup, position)
                    svc.updateRepeatMode(1)
                    toast("‘${entry.name}’ 파일을 반복 재생합니다.")
                    render()
                }
                2 -> chooseTargetGroup(position, entry)
                5 -> SrtDialog.show(context, listOf(entry), pickSaveFolder)
                3 -> {
                    svc.resetProgress(viewGroup, position)
                    render()
                }
                4 -> {
                    svc.removeFrom(viewGroup, position)
                    render()
                }
            }
        }
    }

    private fun chooseTargetGroup(position: Int, entry: TrackStore.Entry) {
        val targets = svc.groups.indices.filter { it != viewGroup }
        MaterialAlertDialogBuilder(context)
            .setTitle("어느 폴더로 옮길까요?")
            .setItems(targets.map { "    ".repeat(depthOf(svc.groups[it])) + svc.groups[it].name }.toTypedArray()) { _, which ->
                val moved = svc.moveToGroup(viewGroup, position, targets[which])
                if (!moved) toast("‘${entry.name}’ 파일이 이미 그 폴더에 있습니다.")
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
        val subtitle: ImageButton = view.findViewById(R.id.trackSubtitle)
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
                    dismiss()
                }
            }
            // The subtitle button sits on every row: teal when the track already has subtitles.
            val hasSubtitle = SubtitleLinks.find(context.applicationContext, entry.uri, entry.name) != null
            holder.subtitle.imageTintList = android.content.res.ColorStateList.valueOf(context.getColor(if (hasSubtitle) R.color.ls_teal_600 else R.color.ls_text_secondary))
            holder.subtitle.contentDescription = if (hasSubtitle) "자막 있음" else "자막(SRT) 만들기"
            holder.subtitle.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos >= 0) SrtDialog.show(context, listOf(rows[pos]), pickSaveFolder)
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

    private companion object {
        const val KEY_LIST_VIEW = "folder_list_view"
        const val ROW_OWN = -1
        const val ROW_NEW = -2
    }
}
