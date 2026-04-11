package com.recycling.toolsapp.utils

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class GridSpacingItemDecoration(
    private val spanCount: Int,     // 列数
    private val spacing: Int,      // 间距值（单位：px）
    private val includeEdge: Boolean // 是否包含边缘间距
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val column = position % spanCount

        if (includeEdge) {
            // 包含边缘的布局
            outRect.left = spacing - column * spacing / spanCount
            outRect.right = (column + 1) * spacing / spanCount

            if (position < spanCount) { // 顶部间距
                outRect.top = spacing
            }
            outRect.bottom = spacing // 底部间距
        } else {
            // 不包含边缘的布局
            outRect.left = column * spacing / spanCount
            outRect.right = spacing - (column + 1) * spacing / spanCount
            if (position >= spanCount) { // 非首行顶部间距
                outRect.top = spacing
            }
        }
    }
}