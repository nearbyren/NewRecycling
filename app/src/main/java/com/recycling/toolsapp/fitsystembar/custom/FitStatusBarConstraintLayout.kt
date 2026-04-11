package com.recycling.toolsapp.fitsystembar.custom
import android.content.Context
import android.util.AttributeSet
import com.recycling.toolsapp.fitsystembar.base.FitSystemBarConstraintLayout

/**
 * 设置paddingTop为顶部系统栏的高度，其它方向的padding不变
 */
class FitStatusBarConstraintLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FitSystemBarConstraintLayout(context, attrs, defStyleAttr) {

    init {
        paddingBottomSystemWindowInsets = false
    }
}