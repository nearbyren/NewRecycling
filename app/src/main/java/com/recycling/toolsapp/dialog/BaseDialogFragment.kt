package com.recycling.toolsapp.dialog

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.FloatRange
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.recycling.toolsapp.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel


/**
 * @description: DialogFragment的基类
 * @since: 1.0.0
 */
abstract class BaseDialogFragment : AppCompatDialogFragment(), CoroutineScope by MainScope() {
    protected lateinit var rootView: View

    private var width: Int = ViewGroup.LayoutParams.MATCH_PARENT
    private var height: Int = ViewGroup.LayoutParams.WRAP_CONTENT
    private var gravity: Int = Gravity.CENTER
    private var animRes: Int = -1
    private var dimAmount: Float = 0.5f
    private var alpha: Float = 1f

    @SuppressLint("ResourceType") override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(if (getDialogStyle() != null) getDialogStyle()!! else STYLE_NO_TITLE, if (getDialogTheme() != null) getDialogTheme()!! else R.style.lib_uikit_CommonDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        rootView = inflater.inflate(getLayoutId(), container, false)
        return rootView
    }

    /**
     * 处理隐藏状态栏和底部虚拟键
     */
    private fun setupFullScreenDialog() {
        dialog?.window?.apply {
            // 隐藏导航栏和状态栏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                insetsController?.hide(WindowInsets.Type.navigationBars() or WindowInsets.Type.statusBars())
                insetsController?.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION") decorView.systemUiVisibility =
                        (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            }

            // 设置对话框为全屏布局
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initialize(view, savedInstanceState)
    }

    override fun onStart() {
        try {
            // 在super之前执行，因为super.onStart()中dialog会执行自己的show方法
            updateAttributes()
            super.onStart()
            dialog?.window?.apply {
                setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                //解决键盘无法弹出问题
                clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
                setupFullScreenDialog()
            }
        } catch (e: Exception) {
            dismissAllowingStateLoss()
        }
    }

    /**
     * 设置宽度
     */
    fun setWidth(width: Int) {
        this.width = width
    }

    /**
     * 设置高度
     */
    fun setHeight(height: Int) {
        this.height = height
    }

    /**
     * 设置位置
     */
    fun setGravity(gravity: Int) {
        this.gravity = gravity
    }

    /**
     * 设置窗口透明度
     */
    fun setDimAmount(@FloatRange(from = 0.0, to = 1.0) dimAmount: Float) {
        this.dimAmount = dimAmount
    }

    /**
     * 设置背景透明度
     */
    fun setAlpha(@FloatRange(from = 0.0, to = 1.0) alpha: Float) {
        this.alpha = alpha
    }

    /**
     * 设置显示和隐藏动画
     */
    fun setAnimationRes(animation: Int) {
        this.animRes = animation
    }

    /**
     * 更新属性
     */
    private fun updateAttributes() {
        dialog?.let { dialog ->
            dialog.window?.let { window ->
                val params: WindowManager.LayoutParams = window.attributes
                params.width = width
                params.height = height
                params.gravity = gravity
                params.windowAnimations = animRes
                params.dimAmount = dimAmount
                params.alpha = alpha
                window.attributes = params
            }
        }

    }

    protected abstract fun getDialogStyle(): Int?

    @StyleRes protected abstract fun getDialogTheme(): Int?

    @LayoutRes protected abstract fun getLayoutId(): Int

    protected abstract fun initialize(view: View, savedInstanceState: Bundle?)

    open fun isShowing(): Boolean {
        return isAdded && dialog != null && dialog!!.isShowing
    }

    open fun show(activity: FragmentActivity, tag: String? = null) {
        showAllowStateLoss(activity.supportFragmentManager, tag ?: this::javaClass.name)
    }

    open fun show(fragment: Fragment, tag: String? = null) {
        showAllowStateLoss(fragment.childFragmentManager, tag ?: this::javaClass.name)
    }

    override fun onDestroy() {
        cancel()
        super.onDestroy()
    }


}