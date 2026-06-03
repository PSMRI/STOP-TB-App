package org.piramalswasthya.stoptb.utils

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Scrolls the viewport so the form row at [position] is visible after validation.
 *
 * [RecyclerView.scrollToPosition] alone is insufficient when the list is inside a
 * [NestedScrollView] (common form layouts in this app) or when [notifyItemChanged] has
 * not finished laying out the error row yet.
 */
/**
 * Scrolls a [NestedScrollView] / [ScrollView] parent so [this] field is visible (static forms).
 */
fun View.scrollToFormValidationField() {
    val topOffsetPx = (16 * resources.displayMetrics.density).toInt()
    post {
        val rect = Rect(0, -topOffsetPx, width, height + topOffsetPx)
        requestRectangleOnScreen(rect, false)
        scrollAncestorsToReveal(this, topOffsetPx)
        requestFocusOnFirstFocusable(this)
    }
}

fun RecyclerView.scrollToFormValidationError(position: Int) {
    if (position < 0) return
    val layoutManager = layoutManager as? LinearLayoutManager ?: return
    val topOffsetPx = (16 * resources.displayMetrics.density).toInt()
    stopScroll()
    post { revealFormValidationRow(layoutManager, position, topOffsetPx, attempt = 0) }
}

private fun RecyclerView.revealFormValidationRow(
    layoutManager: LinearLayoutManager,
    position: Int,
    topOffsetPx: Int,
    attempt: Int,
) {
    layoutManager.scrollToPositionWithOffset(position, topOffsetPx)
    val rowView = layoutManager.findViewByPosition(position)
        ?: findViewHolderForAdapterPosition(position)?.itemView

    if (rowView != null) {
        revealFormField(rowView, topOffsetPx)
        return
    }
    if (attempt < 6) {
        postDelayed({ revealFormValidationRow(layoutManager, position, topOffsetPx, attempt + 1) }, 50L)
        return
    }
    smoothScrollToPosition(position)
    postDelayed({
        val resolvedView = layoutManager.findViewByPosition(position)
            ?: findViewHolderForAdapterPosition(position)?.itemView
        if (resolvedView != null) revealFormField(resolvedView, topOffsetPx)
    }, 200)
}

private fun revealFormField(itemView: View, topOffsetPx: Int) {
    val rect = Rect(0, -topOffsetPx, itemView.width, itemView.height + topOffsetPx)
    itemView.requestRectangleOnScreen(rect, false)
    scrollAncestorsToReveal(itemView, topOffsetPx)
    requestFocusOnFirstFocusable(itemView)
}

private fun scrollAncestorsToReveal(target: View, topOffsetPx: Int) {
    var child: View = target
    var parent = child.parent
    while (parent is View) {
        when (parent) {
            is NestedScrollView -> {
                val rect = Rect()
                child.getDrawingRect(rect)
                parent.offsetDescendantRectToMyCoords(child, rect)
                val targetScrollY = (rect.top - topOffsetPx).coerceAtLeast(0)
                val deltaY = targetScrollY - parent.scrollY
                if (deltaY != 0) parent.smoothScrollBy(0, deltaY)
            }
            is ScrollView -> {
                val rect = Rect()
                child.getDrawingRect(rect)
                parent.offsetDescendantRectToMyCoords(child, rect)
                val scrollY = (rect.top - topOffsetPx).coerceAtLeast(0)
                parent.smoothScrollTo(0, scrollY)
            }
            is RecyclerView -> {
                val recyclerView = parent
                val rect = Rect()
                child.getDrawingRect(rect)
                recyclerView.offsetDescendantRectToMyCoords(child, rect)
                if (rect.top < topOffsetPx || rect.bottom > recyclerView.height) {
                    recyclerView.smoothScrollBy(0, rect.top - topOffsetPx)
                }
            }
        }
        child = parent
        parent = child.parent
    }
}

private fun requestFocusOnFirstFocusable(view: View) {
    if (view.isFocusable && view.isShown) {
        view.requestFocus()
        return
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            requestFocusOnFirstFocusable(view.getChildAt(i))
            if (view.findFocus() != null) return
        }
    }
}
