package com.hdlee73.englishstudy.listening

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.hdlee73.englishstudy.R
import java.text.DateFormat
import java.util.Date

/**
 * The list of subtitle files made or linked in the app, like the playlist: tap a row to link it to the track
 * that is playing, the ⋮ button on a row opens its menu (link, delete).
 */
class SubtitleListSheet(
    private val context: Context,
    private val svc: PlaybackService,
    private val onChanged: () -> Unit
) {
    private val dialog = BottomSheetDialog(context)
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()

    fun show() {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        root.addView(TextView(context).apply {
            text = "자막 목록"
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(context.getColor(R.color.ls_text_primary))
        })
        root.addView(TextView(context).apply {
            text = "‘자막 만들기’로 만든 자막 파일이에요. 줄을 누르면 지금 재생 중인 곡의 자막으로 연결돼요."
            textSize = 12f
            setTextColor(context.getColor(R.color.ls_text_secondary))
            setPadding(0, dp(4), 0, dp(8))
        })
        root.addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (context.resources.displayMetrics.heightPixels * 0.6f).toInt()))
        dialog.setContentView(root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        render()
        dialog.show()
    }

    private fun render() {
        list.removeAllViews()
        val items = SubtitleLinks.list(context)
        if (items.isEmpty()) {
            list.addView(TextView(context).apply {
                text = "아직 만든 자막이 없어요.\n재생목록의 곡 메뉴나 플레이어의 ‘자막 만들기’로 만들 수 있어요."
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(32), 0, dp(32))
                setTextColor(context.getColor(R.color.ls_text_secondary))
            })
            return
        }
        val current = svc.subtitleUri
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        items.forEach { item ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(60)
                setPadding(dp(4), dp(6), 0, dp(6))
                val ripple = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                setBackgroundResource(ripple.resourceId)
                setOnClickListener { link(item) }
            }
            val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(context).apply {
                text = (if (item.link == current) "✔ " else "") + item.audioName
                textSize = 15f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(context.getColor(if (item.link == current) R.color.ls_teal_700 else R.color.ls_text_primary))
            })
            texts.addView(TextView(context).apply {
                text = "${dateFormat.format(Date(item.savedAt))} · ${item.sizeBytes / 1024 + 1}KB" + if (item.link == current) " · 지금 곡에 연결됨" else ""
                textSize = 12f
                setTextColor(context.getColor(R.color.ls_text_secondary))
            })
            row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(ImageButton(context).apply {
                setImageResource(R.drawable.ls_ic_more)
                setBackgroundColor(0)
                contentDescription = "메뉴"
                setOnClickListener { menu(it, item) }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            list.addView(row)
        }
    }

    private fun link(item: SubtitleLinks.Item) {
        if (svc.currentTrackUri == null) {
            Toast.makeText(context, "먼저 오디오 파일을 추가해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        svc.setSubtitle(item.link)
        onChanged()
        Toast.makeText(context, "지금 곡의 자막으로 연결했어요.", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun menu(anchor: View, item: SubtitleLinks.Item) {
        IconMenu.show(
            context, anchor, listOf(
                IconMenu.Item(1, "지금 곡의 자막으로 연결", R.drawable.ls_ic_subtitles),
                IconMenu.Item(2, "자막 파일 삭제", R.drawable.ls_ic_delete, destructive = true, dividerBefore = true)
            ), alignEnd = true
        ) { id ->
            when (id) {
                1 -> link(item)
                2 -> {
                    if (svc.subtitleUri == item.link) svc.setSubtitle(null)
                    SubtitleLinks.remove(context, item.link)
                    onChanged()
                    render()
                }
            }
        }
    }
}
