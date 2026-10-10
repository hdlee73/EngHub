package com.hdlee73.englishstudy.listening

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import android.content.res.ColorStateList
import com.hdlee73.englishstudy.R

/** Rounded pop-up menu with a leading icon on every row (the style used for every menu of the Listening tab). */
object IconMenu {
    /** [icon] 0 leaves the icon column empty (still aligned). */
    class Item(
        val id: Int,
        val label: String,
        val icon: Int = 0,
        val destructive: Boolean = false,
        val dividerBefore: Boolean = false
    )

    fun show(context: Context, anchor: View, items: List<Item>, alignEnd: Boolean = false, onDismiss: (() -> Unit)? = null, onPick: (Int) -> Unit) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val red = Color.parseColor("#C7452F")
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        val popup = PopupWindow(column, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
        popup.setBackgroundDrawable(GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), context.getColor(R.color.ls_surface_border))
        })
        popup.elevation = dp(10).toFloat()
        popup.isOutsideTouchable = true
        items.forEach { item ->
            if (item.dividerBefore) column.addView(View(context).apply {
                setBackgroundColor(context.getColor(R.color.ls_surface_border))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                    setMargins(dp(14), dp(4), dp(14), dp(4))
                }
            })
            val tint = if (item.destructive) red else context.getColor(R.color.ls_teal_600)
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                minimumWidth = dp(200)
                setPadding(dp(16), 0, dp(20), 0)
                val ripple = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
                setBackgroundResource(ripple.resourceId)
                setOnClickListener {
                    popup.dismiss()
                    onPick(item.id)
                }
            }
            row.addView(ImageView(context).apply {
                if (item.icon != 0) {
                    setImageResource(item.icon)
                    ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(tint))
                }
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(14) }
            })
            row.addView(TextView(context).apply {
                text = item.label
                textSize = 15f
                setTextColor(if (item.destructive) red else context.getColor(R.color.ls_text_primary))
            })
            column.addView(row)
        }
        if (onDismiss != null) popup.setOnDismissListener { onDismiss() }
        // Place the menu by hand: below the anchor when it fits, otherwise above it, and always inside the screen
        // (a menu opened from a row near the bottom used to be pushed off the screen and was not visible).
        column.measure(
            View.MeasureSpec.makeMeasureSpec(context.resources.displayMetrics.widthPixels, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val frame = android.graphics.Rect()
        anchor.getWindowVisibleDisplayFrame(frame)
        val loc = IntArray(2)
        anchor.getLocationOnScreen(loc)
        val maxHeight = (frame.height() - dp(16)).coerceAtLeast(dp(120))
        val popupHeight = column.measuredHeight.coerceAtMost(maxHeight)
        val popupWidth = column.measuredWidth
        popup.height = popupHeight
        val below = loc[1] + anchor.height - dp(4)
        val above = loc[1] - popupHeight + dp(4)
        val y = when {
            below + popupHeight <= frame.bottom - dp(8) -> below
            above >= frame.top + dp(8) -> above
            else -> (frame.bottom - dp(8) - popupHeight).coerceAtLeast(frame.top + dp(8))
        }
        val x = (if (alignEnd) loc[0] + anchor.width - popupWidth else loc[0] + dp(12))
            .coerceIn(frame.left + dp(8), (frame.right - popupWidth - dp(8)).coerceAtLeast(frame.left + dp(8)))
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
    }
}
