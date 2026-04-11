package com.recycling.toolsapp.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.LayoutRes
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar
import com.recycling.toolsapp.R


/**
 * Snackbar工具类
 * 支持Activity、Fragment
 * 适配Android 11
 * 支持水平居中显示
 * 支持屏幕中间显示
 *        binding.logo.setOnClickListener {
 *             SnackbarUtils.show(
 *                 fragment = this@NewHomeFragment,
 *                 message = "更新完成！", // 默认布局使用
 *                 customLayoutResId = R.layout.custom_snackbar,
 *                 customViewCallback = { customView ->
 *                     // 操作自定义视图
 *                     customView.findViewById<TextView>(android.R.id.text1).text = "哈哈哈哈哈"
 *                 },
 *                 actionText = "撤销",
 *                 position = SnackbarUtils.Position.CENTER
 *             )
 *         }
 */
object SnackbarUtils {
    /**
     * 显示位置枚举
     */
    enum class Position {
        BOTTOM,     // 底部
        CENTER      // 中间
    }

    /**
     * 显示Snackbar
     * @param view 锚点视图
     * @param message 消息内容
     * @param duration 显示时长
     * @param actionText 操作按钮文本
     * @param actionListener 操作按钮点击事件
     * @param textColor 文本颜色
     * @param backgroundColor 背景颜色
     * @param textAlignment 文本对齐方式
     * @param horizontalCenter 是否水平居中显示
     * @param position 显示位置
     */
    @SuppressLint("RestrictedApi") fun show(
        view: View,
        message: CharSequence,
        @BaseTransientBottomBar.Duration duration: Int = Snackbar.LENGTH_SHORT,
        actionText: CharSequence? = null,
        actionListener: View.OnClickListener? = null,
        @ColorInt textColor: Int = Color.WHITE,
        @ColorInt backgroundColor: Int = Color.parseColor("#FF333333"),
        textAlignment: Int = View.TEXT_ALIGNMENT_TEXT_START,
        horizontalCenter: Boolean = false,
        position: Position = Position.BOTTOM,
        @LayoutRes customLayoutResId: Int? = null, // 新增：自定义布局ID
        customViewCallback: ((View) -> Unit)? = null, // 新增：自定义视图回调
    ): Snackbar? {
        if (!isViewAttached(view)) return null

        val snackbar = Snackbar.make(view, message, duration).apply {
            // 2. 处理自定义布局
            customLayoutResId?.let { layoutId ->
                try {
                    // 1. 获取Snackbar的根布局（可能是SnackbarLayout或ConstraintLayout）
                    val rootView = this.view

                    // 2. 创建自定义视图
                    val customView =
                            LayoutInflater.from(rootView.context).inflate(layoutId, rootView as? ViewGroup, false)

                    // 3. 清除原始内容并添加自定义视图
                    if (rootView is ViewGroup) {
                        rootView.removeAllViews()
                        rootView.addView(customView)
                        rootView.setPadding(0, 0, 0, 0)
                    }

                    // 4. 回调自定义视图
                    customViewCallback?.invoke(customView)
                } catch (e: Exception) {
                    Log.e("SnackbarUtils", "Custom layout error: ${e.message}")
                    // 回退：使用默认消息
                    setText(message)
                }
            }
            // 3. 配置文本和操作（仅在默认布局时生效）
            if (customLayoutResId == null) {
                setText(message)
                setTextColor(textColor)
                setActionTextColor(textColor)
                setBackgroundTint(backgroundColor)
                view.background = null // 清除默认背景

                // 文本对齐设置
                view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.apply {
                    this.textAlignment = textAlignment
                    if (textAlignment == View.TEXT_ALIGNMENT_CENTER) {
                        gravity = Gravity.CENTER
                    }
                }
            }
        }

        // 5. 位置布局参数配置
        snackbar.view.apply {
            layoutParams = when (val params = layoutParams) {
                is CoordinatorLayout.LayoutParams -> configureCoordinatorParams(params, position, horizontalCenter)
                is FrameLayout.LayoutParams -> configureFrameParams(position, horizontalCenter)
                else -> params // 保留原始参数
            }
            if (customLayoutResId == null) {
                setBackgroundResource(R.drawable.snackbar_bottom_bg)
                // 设置样式
                snackbar.setActionTextColor(textColor)
                snackbar.setTextColor(textColor)
                // 设置文本对齐
                val snackbarText =
                        snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                snackbarText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                snackbarText?.let {
                    it.textAlignment = textAlignment
                    if (textAlignment == View.TEXT_ALIGNMENT_CENTER) {
                        it.gravity = Gravity.CENTER
                    }
                }

                // 设置操作按钮
                if (actionText != null && actionListener != null) {
                    // 默认布局 - 使用标准setAction
                    snackbar.setAction(actionText) { v ->
                        actionListener.onClick(v)
                        snackbar.dismiss()
                    }
                }
            } else {
                setBackgroundColor(ContextCompat.getColor(context, android.R.color.transparent))
                // 自定义布局 - 查找按钮并手动设置点击事件
                try {
//                    if (actionListener != null) {
//                        val vi = snackbar.view.findViewById<AppCompatImageView>(android.R.id.button1)
//                           vi ?.setOnClickListener { v ->
//                            actionListener.onClick(v)
//                            snackbar.dismiss()
//                        }
//                    }
                } catch (e: Exception) {
                    Log.e("SnackbarUtils", "Action button not found in custom layout")
                }

            }
        }
        // 设置位置和布局参数
        val params = snackbar.view.layoutParams as? CoordinatorLayout.LayoutParams
        if (params != null) {
            // CoordinatorLayout布局
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
            if (position == Position.CENTER) {
                params.gravity = Gravity.CENTER
            } else {
                params.gravity = if (horizontalCenter) {
                    Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                } else {
                    Gravity.BOTTOM
                }
            }
            params.setMargins(0, 0, 0, if (position == Position.CENTER) 0 else params.bottomMargin)
            snackbar.view.layoutParams = params
        } else {
            // 普通布局
            val frameParams =
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            frameParams.gravity = if (position == Position.CENTER) {
                Gravity.CENTER
            } else {
                if (horizontalCenter) {
                    Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                } else {
                    Gravity.BOTTOM
                }
            }
            snackbar.view.layoutParams = frameParams
        }

        snackbar.show()
        return snackbar
    }

    // === 位置布局工具方法 ===
    private fun configureCoordinatorParams(params: CoordinatorLayout.LayoutParams, position: Position, horizontalCenter: Boolean): CoordinatorLayout.LayoutParams {
        params.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            gravity = when (position) {
                Position.CENTER -> Gravity.CENTER
                Position.BOTTOM -> if (horizontalCenter) {
                    Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                } else {
                    Gravity.BOTTOM
                }
            }
            if (position == Position.CENTER) bottomMargin = 0
        }
        return params
    }

    private fun configureFrameParams(position: Position, horizontalCenter: Boolean): FrameLayout.LayoutParams {
        return FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = when (position) {
                Position.CENTER -> Gravity.CENTER
                Position.BOTTOM -> if (horizontalCenter) {
                    Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
                } else {
                    Gravity.BOTTOM
                }
            }
        }
    }

    /**
     * 在Activity中显示Snackbar
     */
    fun show(
        activity: Activity,
        message: CharSequence,
        @BaseTransientBottomBar.Duration duration: Int = Snackbar.LENGTH_SHORT,
        actionText: CharSequence? = null,
        actionListener: View.OnClickListener? = null,
        @ColorInt textColor: Int = Color.WHITE,
        @ColorInt backgroundColor: Int = Color.parseColor("#FF333333"),
        textAlignment: Int = View.TEXT_ALIGNMENT_TEXT_START,
        horizontalCenter: Boolean = false,
        position: Position = Position.BOTTOM,
        @LayoutRes customLayoutResId: Int? = null, // 新增：自定义布局ID
        customViewCallback: ((View) -> Unit)? = null, // 新增：自定义视图回调
    ): Snackbar? {
        if (activity.isFinishing) return null
        val rootView = findSuitableAnchorView(activity)
        return show(rootView, message, duration, actionText, actionListener, textColor, backgroundColor, textAlignment, horizontalCenter, position, customLayoutResId, customViewCallback)
    }

    /**
     * 在Fragment中显示Snackbar
     */
    fun show(
        fragment: Fragment,
        message: CharSequence,
        @BaseTransientBottomBar.Duration duration: Int = Snackbar.LENGTH_SHORT,
        actionText: CharSequence? = null,
        actionListener: View.OnClickListener? = null,
        @ColorInt textColor: Int = Color.WHITE,
        @ColorInt backgroundColor: Int = Color.parseColor("#FF333333"),
        textAlignment: Int = View.TEXT_ALIGNMENT_TEXT_START,
        horizontalCenter: Boolean = false,
        position: Position = Position.BOTTOM,
        @LayoutRes customLayoutResId: Int? = null, // 新增：自定义布局ID
        customViewCallback: ((View) -> Unit)? = null, // 新增：自定义视图回调
    ): Snackbar? {
        val view = fragment.view ?: return null
        return show(view, message, duration, actionText, actionListener, textColor, backgroundColor, textAlignment, horizontalCenter, position, customLayoutResId, customViewCallback)
    }

    /**
     * 检查视图是否已附加到窗口
     */
    private fun isViewAttached(view: View?): Boolean {
        if (view == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            view.isAttachedToWindow
        } else {
            view.windowToken != null
        }
    }

    /**
     * 查找合适的锚点视图
     */
    private fun findSuitableAnchorView(activity: Activity): View {
        // 优先查找CoordinatorLayout
        val coordinator = activity.findViewById<View>(android.R.id.content)
        if (coordinator is CoordinatorLayout) {
            return coordinator
        }

        // 次选根布局
        val rootView = activity.findViewById<ViewGroup>(android.R.id.content)
        if (rootView.childCount > 0) {
            return rootView.getChildAt(0)
        }

        return rootView
    }
}