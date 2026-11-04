package com.malayalamlive.tv

import android.graphics.Rect
import android.view.View
import androidx.leanback.widget.BaseGridView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/*
 * The TV layouts use leanback grids, which track a "selected" item for the D-pad. Touch layouts use
 * plain RecyclerViews instead: a leanback grid scrolled by finger snaps back to its selected item
 * whenever anything re-lays out (every channel logo that finishes loading).
 */

/** Selected item on a leanback grid; first visible item on a plain list. Setting it scrolls there. */
var RecyclerView.selection: Int
    get() = (this as? BaseGridView)?.selectedPosition
        ?: (layoutManager as? LinearLayoutManager)?.findFirstVisibleItemPosition()?.coerceAtLeast(0)
        ?: 0
    set(value) {
        if (this is BaseGridView) selectedPosition = value else scrollToPosition(value)
    }

/** Even gaps between items of a plain list or grid; pair it with half as much padding on the list. */
class SpacingDecoration(private val spacePx: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        val half = spacePx / 2
        outRect.set(half, half, half, half)
    }
}

fun android.content.Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
