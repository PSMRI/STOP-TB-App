package org.piramalswasthya.stoptb.helpers

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import androidx.core.widget.NestedScrollView

/** Stops the scroll view from auto-jumping to the focused field after list updates. */
class FocusStableNestedScrollView(context: Context, attrs: AttributeSet?) :
    NestedScrollView(context, attrs) {

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0
}
