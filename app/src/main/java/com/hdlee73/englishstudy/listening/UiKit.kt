package com.hdlee73.englishstudy.listening

import com.hdlee73.englishstudy.R

import android.content.Context
import android.util.TypedValue
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.dialog.MaterialAlertDialogBuilder

fun Context.dp(value: Int): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

/** Small text-input dialog used for naming groups and saved ranges. */
fun Context.promptText(title: String, initial: String, positive: String = "확인", onOk: (String) -> Unit) {
    val input = EditText(this).apply {
        setText(initial)
        setSelectAllOnFocus(true)
        setSingleLine()
    }
    val holder = FrameLayout(this).apply {
        setPadding(dp(24), dp(8), dp(24), 0)
        addView(input)
    }
    MaterialAlertDialogBuilder(this)
        .setTitle(title)
        .setView(holder)
        .setPositiveButton(positive) { _, _ -> onOk(input.text.toString()) }
        .setNegativeButton("취소", null)
        .show()
}

/** A Material filter chip created in code (single-selection groups use these for the settings). */
fun Context.filterChip(label: String, checked: Boolean): Chip = Chip(this).apply {
    setChipDrawable(
        ChipDrawable.createFromAttributes(
            this@filterChip, null, 0, com.google.android.material.R.style.Widget_Material3_Chip_Filter
        )
    )
    isCheckable = true
    text = label
    isChecked = checked
}
