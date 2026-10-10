package com.hdlee73.englishstudy.listening

import android.content.Context
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.hdlee73.englishstudy.docvoice.core.SaveFolder
import com.hdlee73.englishstudy.docvoice.core.Storage

/**
 * Asks where and under what name the subtitle file(s) are saved, then starts the SRT job.
 * [pickFolder] opens the system folder picker and calls back when a folder was chosen.
 */
object SrtDialog {
    fun show(context: Context, entries: List<TrackStore.Entry>, pickFolder: (onPicked: () -> Unit) -> Unit) {
        val todo = entries.distinctBy { it.uri }.filter { SubtitleLinks.find(context.applicationContext, it.uri, it.name) == null }
        if (todo.isEmpty()) {
            android.widget.Toast.makeText(context, "이미 모두 자막이 있어요. (플레이어의 자막 버튼에서 볼 수 있어요)", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val single = todo.size == 1
        val pad = (24 * context.resources.displayMetrics.density).toInt()
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 3, pad, 0)
        }
        column.addView(TextView(context).apply {
            text = if (single) "‘${todo[0].name}’의 자막을 만들어요. 말을 받아쓰는 작업이라 곡이 길면 오래 걸리고, 처음에는 음성 모델을 내려받아요."
            else "곡 ${todo.size}개의 자막을 차례로 만들어요. 이미 자막이 있는 곡은 건너뛰어요. 처음에는 음성 모델을 내려받아요."
            textSize = 14f
        })
        var input: EditText? = null
        if (single) {
            input = EditText(context).apply {
                setSingleLine()
                hint = "파일 이름 (비우면 곡 이름)"
                setText(Storage.cleanName(todo[0].name.substringBeforeLast('.', todo[0].name), "srt"))
                setSelectAllOnFocus(true)
            }
            column.addView(input)
            column.addView(TextView(context).apply { text = "확장자 .srt 는 자동으로 붙어요."; textSize = 12f })
        } else {
            column.addView(TextView(context).apply { text = "파일 이름은 곡 이름을 그대로 써요."; textSize = 12f; setPadding(0, pad / 3, 0, 0) })
        }
        val folderLabel = TextView(context).apply { textSize = 14f; setPadding(0, pad / 2, 0, 0) }
        val reset = Button(context).apply { text = "기본 폴더로" }
        fun refresh() {
            folderLabel.text = "저장 폴더: ${SaveFolder.label(context)}"
            reset.visibility = if (SaveFolder.get(context) == null) View.GONE else View.VISIBLE
        }
        reset.setOnClickListener { SaveFolder.set(context, null); refresh() }
        val change = Button(context).apply { text = "저장 폴더 선택"; setOnClickListener { pickFolder { refresh() } } }
        column.addView(folderLabel)
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(change)
        buttons.addView(reset)
        column.addView(buttons)
        column.addView(TextView(context).apply { text = "자막은 곡에 바로 연결돼 플레이어에서 보이고, 같은 파일이 위 폴더에도 저장돼요."; textSize = 12f })
        refresh()
        MaterialAlertDialogBuilder(context)
            .setTitle("자막(SRT) 만들기")
            .setView(column)
            .setPositiveButton("만들기") { _, _ ->
                val typed = input?.text?.toString().orEmpty()
                val names = if (single) mapOf(todo[0].uri to Storage.cleanName(typed, "srt")) else emptyMap()
                SrtMaker.start(context, todo, names = names)
            }
            .setNegativeButton("취소", null)
            .show()
    }
}
