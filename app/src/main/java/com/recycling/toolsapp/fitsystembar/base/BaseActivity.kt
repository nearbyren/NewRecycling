package com.recycling.toolsapp.fitsystembar.base

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import com.recycling.toolsapp.FaceApplication
import com.recycling.toolsapp.R
import com.recycling.toolsapp.dialog.LoadingDialog
import com.recycling.toolsapp.utils.FragmentCoordinator
import com.serial.port.utils.Loge
import nearby.lib.signal.livebus.BusType
import nearby.lib.signal.livebus.LiveBus
import java.lang.ref.WeakReference


abstract class BaseActivity : AppCompatActivity() {
    private val weakFragment by lazy { WeakReference(LoadingDialog()) }

    private var activityCount = 0 // 用于判断是否有Activity处于前台

    override fun onStart() {
        super.onStart()
        activityCount++
        if (activityCount == 1) {
            // 从后台回到前台
            FaceApplication.getInstance().isAppForeground.value = true
        }
    }

    override fun onStop() {
        super.onStop()
        activityCount--
        if (activityCount == 0) {
            // 应用切换到后台
            FaceApplication.getInstance().isAppForeground.value = false
        }
    }

    //    private val weakFragment by lazy { WeakReference(LoadingNativeDialog()) }
// 淡入淡出动画
    private var mAnimationIn: Animation? = null
    private var mAnimationOut: Animation? = null
    lateinit var fragmentCoordinator: FragmentCoordinator
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mAnimationIn = AnimationUtils.loadAnimation(this, R.anim.anim_fragment_in)
        mAnimationOut = AnimationUtils.loadAnimation(this, R.anim.anim_fragment_out)
        initContentView()
        findViewById<TextView>(R.id.toolbar_left_title)?.setOnClickListener {
            val curClazz = fragmentCoordinator?.getCurrentFragment()
            Loge.d("FragmentCoordinator toolbar_ic_left 返回键 $curClazz")
            fragmentCoordinator.navigateBack()
        }
        fragmentCoordinator =
                FragmentCoordinator.getInstance(supportFragmentManager, R.id.fragment_container)
        initialize(savedInstanceState)
        hideSystemBars()
    }

    fun navigateTo(
        fragmentClass: Class<out Fragment>,
        args: Bundle? = null,
        animConfig: FragmentCoordinator.AnimConfig? = null,
        deepLink: String? = null,
        addToBackStack: Boolean = true,
        lifecycleCallback: FragmentCoordinator.FragmentLifecycleCallback? = null,
        resultCallback: FragmentCoordinator.FragmentResultCallback? = null,
    ) {
        fragmentCoordinator.let {
            it.navigateTo(
                fragmentClass = fragmentClass,
                args = args,
                animConfig = animConfig,
                deepLink = deepLink,
                addToBackStack = addToBackStack,
                lifecycleCallback = lifecycleCallback,
                resultCallback = resultCallback,
            )
        }
    }

    fun navigateBackTo(fragmentClass: Class<out Fragment>): Boolean {
        return fragmentCoordinator.let {
            it.navigateBackTo(fragmentClass)
        }
    }

    fun showLoadingView(isShow: Boolean) {
        try {
            if (isShow) {
                if (!isFinishing && !(weakFragment.get()?.isAdded)!!) {
                    weakFragment.get()?.show(this)
                }
            } else {
                if (!isFinishing) {
                    weakFragment.get()?.dismissAllowingStateLoss()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 处理隐藏状态栏和底部虚拟键
     */
    private fun hideSystemBars() {
        // 检测 Android 版本
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用 WindowInsetsController
            window.insetsController?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior =
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE // 允许滑动调出导航栏
            }
        } else {
            // Android 10 或更低版本
            @Suppress("DEPRECATION") window.decorView.systemUiVisibility =
                    (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    private fun showSystemBar(show: Boolean) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (show) {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }


    override fun onResume() {
        super.onResume()
    }


    override fun onDestroy() {
        super.onDestroy()
    }

    protected open fun initContentView() {
        setContentView(layoutRes())
    }

    /***
     * 获取替换的根布局
     * return
     */
    open fun getLoading(): Int {
        return -1
    }

    /***
     * 确保应用的字体缩放比例始终为1.0
     *
     */
    override fun getResources(): Resources {
        var resources = super.getResources()
        val newConfig = resources.configuration
        val displayMetrics = resources.displayMetrics
        if (resources != null && newConfig.fontScale != 1f) {
            newConfig.fontScale = 1f
            val configurationContext = createConfigurationContext(newConfig)
            resources = configurationContext.resources
            displayMetrics.scaledDensity = displayMetrics.density * newConfig.fontScale
        }
        return resources
    }

    /**
     * 加载布局文件
     *
     * @return
     */
    @LayoutRes protected abstract fun layoutRes(): Int

    /***
     * 生命周期create
     */
    protected abstract fun initialize(savedInstanceState: Bundle?)

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            LiveBus.get(BusType.BUS_RESET_COUNTDOWN).post(BusType.BUS_RESET_COUNTDOWN)
        }
        return super.dispatchTouchEvent(ev)
    }

    fun cancelCountdown() {
//        findViewById<AppCompatTextView>(R.id.toolbar_lock_countdown)?.visibility = View.INVISIBLE
    }

    @SuppressLint("SetTextI18n") fun updateCountdown(countdown: Int) {
//        Loge.d("测试 倒计时来了 $countdown")
//        findViewById<AppCompatTextView>(R.id.toolbar_lock_countdown)?.visibility = View.VISIBLE
        findViewById<AppCompatTextView>(R.id.toolbar_lock_countdown)?.text =
                "${if (countdown == 0) "  " else countdown}"
    }

    fun showActionBarBack() {
//        findViewById<AppCompatImageView>(R.id.toolbar_ic_left)?.visibility = View.VISIBLE
//        findViewById<AppCompatImageView>(R.id.fanhui)?.visibility = View.VISIBLE
    }

    fun hideActionBarBack() {
//        findViewById<AppCompatImageView>(R.id.toolbar_ic_left)?.visibility = View.GONE
//        findViewById<AppCompatImageView>(R.id.fanhui)?.visibility = View.GONE
    }

    fun showActionBar() {
//        if ((findViewById<AppCompatTextView>(R.id.toolbar_cl).visibility and 0x0C) == View.VISIBLE) {
//            return
//        }
//        val view = findViewById<ConstraintLayout>(R.id.toolbar_cl)
//        val view = findViewById<AppCompatImageView>(R.id.fanhui)
//        Loge.d("测试 showActionBar ${if (view == null) " null" else "not null"}")
//        view?.visibility = View.VISIBLE

//        findViewById<ConstraintLayout>(R.id.toolbar_cl)?.setAnimation(mAnimationIn)
//        findViewById<ConstraintLayout>(R.id.toolbar_cl)?.startAnimation(mAnimationIn)
    }

    fun hideActionBar() {
//        if ((findViewById<AppCompatTextView>(R.id.toolbar_cl).visibility and 0x0C) == View.INVISIBLE) {
//            return
//        }
        val view = findViewById<ConstraintLayout>(R.id.toolbar_cl)
        Loge.d("测试 hideActionBar ${if (view == null) " null" else "not null"}")
        view?.visibility = View.GONE
//        findViewById<ConstraintLayout>(R.id.toolbar_cl)?.setAnimation(mAnimationOut)
//        findViewById<ConstraintLayout>(R.id.toolbar_cl)?.startAnimation(mAnimationOut)
    }

    fun setActionBarVisibility(visibility: Int) {
        Loge.d("测试 setActionBarVisibility $visibility")
//        val view = findViewById<ConstraintLayout>(R.id.toolbar_cl)
//        val view = findViewById<AppCompatImageView>(R.id.fanhui)
//        view?.visibility = visibility
    }

    override fun onBackPressed() {
        if (!fragmentCoordinator.navigateBack()) {
            Loge.d("FragmentCoordinator onBackPressed")
        }
    }
}
