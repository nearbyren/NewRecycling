//package com.recycling.toolsapp.utils
//
//import android.Manifest.permission
//import android.annotation.SuppressLint
//import android.app.Activity
//import android.app.Dialog
//import android.content.Context
//import android.content.res.Resources
//import android.graphics.Color
//import android.graphics.Point
//import android.os.Build
//import android.util.Size
//import android.view.Gravity
//import android.view.KeyCharacterMap
//import android.view.KeyEvent
//import android.view.View
//import android.view.ViewConfiguration
//import android.view.ViewGroup
//import android.view.ViewGroup.MarginLayoutParams
//import android.view.Window
//import android.view.WindowManager
//import android.widget.FrameLayout
//import androidx.annotation.ColorInt
//import androidx.annotation.IntDef
//import androidx.annotation.RequiresApi
//import androidx.annotation.RequiresPermission
//import androidx.fragment.app.FragmentActivity
//import androidx.lifecycle.LiveData
//import androidx.lifecycle.MutableLiveData
//import com.recycling.toolsapp.R
//import com.recycling.toolsapp.utils.OSUtils.isFlyme
//import com.recycling.toolsapp.utils.OSUtils.isMiui
//import java.lang.reflect.Field
//import java.lang.reflect.Method
//
//
///**
// * @description: -
// * @since: 1.0.0
// */
//object SystemUIUtils {
//
//    /**=============================================================================================
//     * status bar
//     **===========================================================================================*/
//
//    private const val TAG_STATUS_BAR = "TAG_STATUS_BAR"
//    private const val TAG_OFFSET = "TAG_OFFSET"
//    private const val KEY_OFFSET = -123
//
//    /**
//     * 获取StatusBar的高度
//     */
//    @JvmStatic
//    fun getStatusBarHeight(): Int {
//        val resources = Resources.getSystem()
//        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
//        return resources.getDimensionPixelSize(resourceId)
//    }
//
//    /**
//     * 设置StatusBar是否可见
//     */
//    @JvmStatic
//    fun setStatusBarVisibility(
//        activity: Activity,
//        isVisible: Boolean
//    ) {
//        setStatusBarVisibility(activity.window, isVisible)
//    }
//
//    /**
//     * 设置StatusBar是否可见
//     */
//    @JvmStatic
//    fun setStatusBarVisibility(
//        window: Window,
//        isVisible: Boolean
//    ) {
//        if (isVisible) {
//            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
//            showStatusBarView(window)
//            addMarginTopEqualStatusBarHeight(window)
//        } else {
//            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
//            hideStatusBarView(window)
//            subtractMarginTopEqualStatusBarHeight(window)
//        }
//    }
//
//    /**
//     * StatusBar是否可见
//     */
//    @JvmStatic
//    fun isStatusBarVisible(activity: Activity): Boolean {
//        val flags = activity.window.attributes.flags
//        return flags and WindowManager.LayoutParams.FLAG_FULLSCREEN == 0
//    }
//
//    /**
//     * 将StatusBar变透明
//     */
//    @JvmStatic
//    fun transparentStatusBar(activity: Activity) {
//        transparentStatusBar(activity.window)
//    }
//
//    /**
//     * 将StatusBar变透明
//     */
//    @JvmStatic
//    fun transparentStatusBar(window: Window) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
//            window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
//            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
//            val option = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//            val vis = window.decorView.systemUiVisibility
//            window.decorView.systemUiVisibility = option or vis
//            window.statusBarColor = Color.TRANSPARENT
//        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            val attributes: WindowManager.LayoutParams = window.getAttributes()
//            val flagTranslucentStatus = WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
//            attributes.flags != flagTranslucentStatus
//            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
//            window.attributes = attributes
//        }
//    }
//
//    /**
//     * 设置状态栏深色浅色切换
//     */
//    fun setStatusBarDarkTheme(activity: Activity, dark: Boolean): Boolean {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                setStatusBarFontIconDark(activity, TYPE_M, dark)
//            } else if (isMiui()) {
//                setStatusBarFontIconDark(activity, TYPE_MIUI, dark)
//            } else if (isFlyme()) {
//                setStatusBarFontIconDark(activity, TYPE_FLYME, dark)
//            } else { //其他情况
//                return false
//            }
//            return true
//        }
//        return false
//    }
//
//    const val TYPE_MIUI = 0
//    const val TYPE_FLYME = 1
//    const val TYPE_M = 3 //6.0
//
//
//    @IntDef(TYPE_MIUI, TYPE_FLYME, TYPE_M)
//    internal annotation class ViewType
//
//    /**
//     * 设置 状态栏深色浅色切换
//     */
//    fun setStatusBarFontIconDark(activity: Activity, @ViewType type: Int, dark: Boolean): Boolean {
//        return when (type) {
//            TYPE_MIUI -> setMiuiUI(activity, dark)
//            TYPE_FLYME -> setFlymeUI(activity, dark)
//            TYPE_M -> setCommonUI(activity, dark)
//            else -> setCommonUI(activity, dark)
//        }
//    }
//
//    //设置6.0 状态栏深色浅色切换
//    fun setCommonUI(activity: Activity, dark: Boolean): Boolean {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            val decorView = activity.window.decorView
//            if (decorView != null) {
//                var vis = decorView.systemUiVisibility
//                vis = if (dark) {
//                    vis or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
//                } else {
//                    vis and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
//                }
//                if (decorView.systemUiVisibility != vis) {
//                    decorView.systemUiVisibility = vis
//                }
//                return true
//            }
//        }
//        return false
//    }
//
//    //设置Flyme 状态栏深色浅色切换
//    fun setFlymeUI(activity: Activity, dark: Boolean): Boolean {
//        return try {
//            val window = activity.window
//            val lp = window.attributes
//            val darkFlag: Field =
//                WindowManager.LayoutParams::class.java.getDeclaredField("MEIZU_FLAG_DARK_STATUS_BAR_ICON")
//            val meizuFlags: Field =
//                WindowManager.LayoutParams::class.java.getDeclaredField("meizuFlags")
//            darkFlag.setAccessible(true)
//            meizuFlags.setAccessible(true)
//            val bit: Int = darkFlag.getInt(null)
//            var value: Int = meizuFlags.getInt(lp)
//            value = if (dark) {
//                value or bit
//            } else {
//                value and bit.inv()
//            }
//            meizuFlags.setInt(lp, value)
//            window.attributes = lp
//            true
//        } catch (e: java.lang.Exception) {
//            e.printStackTrace()
//            false
//        }
//    }
//
//    //设置MIUI 状态栏深色浅色切换
//    fun setMiuiUI(activity: Activity, dark: Boolean): Boolean {
//        return try {
//            val window = activity.window
//            val clazz: Class<*> = activity.window.javaClass
//            @SuppressLint("PrivateApi") val layoutParams =
//                Class.forName("android.view.MiuiWindowManager\$LayoutParams")
//            val field: Field = layoutParams.getField("EXTRA_FLAG_STATUS_BAR_DARK_MODE")
//            val darkModeFlag: Int = field.getInt(layoutParams)
//            val extraFlagField: Method =
//                clazz.getDeclaredMethod("setExtraFlags", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
//            extraFlagField.setAccessible(true)
//            if (dark) {    //状态栏亮色且黑色字体
//                extraFlagField.invoke(window, darkModeFlag, darkModeFlag)
//            } else {
//                extraFlagField.invoke(window, 0, darkModeFlag)
//            }
//            true
//        } catch (e: java.lang.Exception) {
//            e.printStackTrace()
//            false
//        }
//    }
//
//    /**
//     * 为View添加MarginTop，MarginTop距离等于StatusBar的高度
//     */
//    @JvmStatic
//    fun addMarginTopEqualStatusBarHeight(view: View) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return
//        view.tag = TAG_OFFSET
//        val haveSetOffset = view.getTag(KEY_OFFSET)
//        if (haveSetOffset != null && haveSetOffset as Boolean) return
//        val layoutParams = view.layoutParams as MarginLayoutParams
//        layoutParams.setMargins(
//            layoutParams.leftMargin,
//            layoutParams.topMargin + getStatusBarHeight(),
//            layoutParams.rightMargin,
//            layoutParams.bottomMargin
//        )
//        view.setTag(KEY_OFFSET, true)
//    }
//
//    /**
//     * 为View减去MarginTop，MarginTop距离等于StatusBar的高度
//     */
//    @JvmStatic
//    fun subtractMarginTopEqualStatusBarHeight(view: View) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return
//        val haveSetOffset = view.getTag(KEY_OFFSET)
//        if (haveSetOffset == null || !(haveSetOffset as Boolean)) return
//        val layoutParams = view.layoutParams as MarginLayoutParams
//        layoutParams.setMargins(
//            layoutParams.leftMargin,
//            layoutParams.topMargin - getStatusBarHeight(),
//            layoutParams.rightMargin,
//            layoutParams.bottomMargin
//        )
//        view.setTag(KEY_OFFSET, false)
//    }
//
//    private fun addMarginTopEqualStatusBarHeight(window: Window) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return
//        val withTag = window.decorView.findViewWithTag<View>(TAG_OFFSET) ?: return
//        addMarginTopEqualStatusBarHeight(withTag)
//    }
//
//    private fun subtractMarginTopEqualStatusBarHeight(window: Window) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return
//        val withTag = window.decorView.findViewWithTag<View>(TAG_OFFSET) ?: return
//        subtractMarginTopEqualStatusBarHeight(withTag)
//    }
//
//    /**
//     * 设置StatusBar的颜色
//     */
//    @JvmStatic
//    fun setStatusBarColor(
//        activity: Activity,
//        @ColorInt color: Int
//    ): View? {
//        return setStatusBarColor(activity, color, false)
//    }
//
//    /**
//     * 设置StatusBar的颜色
//     *
//     * @param activity The activity.
//     * @param color    The status bar's color.
//     * @param isDecor  True to add fake status bar in DecorView
//     *                 False to add fake status bar in ContentView.
//     */
//    @JvmStatic
//    fun setStatusBarColor(
//        activity: Activity,
//        @ColorInt color: Int,
//        isDecor: Boolean
//    ): View? {
//        return setStatusBarColor(activity.window, color, isDecor)
//    }
//
//    /**
//     * 设置StatusBar的颜色
//     */
//    @JvmStatic
//    fun setStatusBarColor(
//        window: Window,
//        @ColorInt color: Int
//    ): View? {
//        return setStatusBarColor(window, color, false)
//    }
//
//    /**
//     * 设置StatusBar的颜色
//     *
//     * @param window  The window.
//     * @param color   The status bar's color.
//     * @param isDecor True to add fake status bar in DecorView,
//     *                False to add fake status bar in ContentView.
//     */
//    @JvmStatic
//    fun setStatusBarColor(
//        window: Window,
//        @ColorInt color: Int,
//        isDecor: Boolean
//    ): View? {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return null
//        transparentStatusBar(window)
//        return applyStatusBarColor(window, color, isDecor)
//    }
//
//    private fun applyStatusBarColor(
//        activity: Activity,
//        color: Int,
//        isDecor: Boolean
//    ): View? {
//        return applyStatusBarColor(activity.window, color, isDecor)
//    }
//
//    private fun applyStatusBarColor(
//        window: Window,
//        color: Int,
//        isDecor: Boolean
//    ): View? {
//        val parent =
//            if (isDecor) window.decorView as ViewGroup else (window.findViewById<View>(androidx.appcompat.R.id.content) as ViewGroup)
//        var fakeStatusBarView = parent.findViewWithTag<View>(TAG_STATUS_BAR)
//        if (fakeStatusBarView != null) {
//            if (fakeStatusBarView.visibility == View.GONE) {
//                fakeStatusBarView.visibility = View.VISIBLE
//            }
//            fakeStatusBarView.setBackgroundColor(color)
//        } else {
//            fakeStatusBarView = createStatusBarView(window.context, color)
//            parent.addView(fakeStatusBarView)
//        }
//        return fakeStatusBarView
//    }
//
//    private fun hideStatusBarView(activity: Activity) {
//        hideStatusBarView(activity.window)
//    }
//
//    private fun hideStatusBarView(window: Window) {
//        val decorView = window.decorView as ViewGroup
//        val fakeStatusBarView = decorView.findViewWithTag<View>(TAG_STATUS_BAR) ?: return
//        fakeStatusBarView.visibility = View.GONE
//    }
//
//    private fun showStatusBarView(window: Window) {
//        val decorView = window.decorView as ViewGroup
//        val fakeStatusBarView = decorView.findViewWithTag<View>(TAG_STATUS_BAR) ?: return
//        fakeStatusBarView.visibility = View.VISIBLE
//    }
//
//    private fun createStatusBarView(
//        context: Context,
//        color: Int
//    ): View? {
//        val statusBarView = View(context)
//        statusBarView.layoutParams = ViewGroup.LayoutParams(
//            ViewGroup.LayoutParams.MATCH_PARENT, getStatusBarHeight()
//        )
//        statusBarView.setBackgroundColor(color)
//        statusBarView.tag = TAG_STATUS_BAR
//        return statusBarView
//    }
//
//
//    /**=============================================================================================
//     * notification bar
//     **===========================================================================================*/
//
//    /**
//     * 设置通知栏是否可见
//     */
//    @RequiresPermission(permission.EXPAND_STATUS_BAR)
//    @JvmStatic
//    fun setNotificationBarVisibility(isVisible: Boolean) {
//        val methodName: String
//        methodName = if (isVisible) {
//            if (Build.VERSION.SDK_INT <= 16) "expand" else "expandNotificationsPanel"
//        } else {
//            if (Build.VERSION.SDK_INT <= 16) "collapse" else "collapsePanels"
//        }
//        invokePanels(methodName)
//    }
//
//    @SuppressLint("WrongConstant")
//    private fun invokePanels(methodName: String) {
//        try {
//            val service =
//                AppUtils.getApplication().getSystemService("statusbar")
//            @SuppressLint("PrivateApi") val statusBarManager =
//                Class.forName("android.app.StatusBarManager")
//            val expand = statusBarManager.getMethod(methodName)
//            expand.invoke(service)
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
//    }
//
//    /**=============================================================================================
//     * nav bar
//     **===========================================================================================*/
//    /**
//     * 获取NavBar的高度
//     *
//     * @return the navigation bar's height
//     */
//    @JvmStatic
//    fun getNavBarHeight(): Int {
//        val res = Resources.getSystem()
//        val resourceId = res.getIdentifier("navigation_bar_height", "dimen", "android")
//        return if (resourceId != 0) {
//            res.getDimensionPixelSize(resourceId)
//        } else {
//            0
//        }
//    }
//
//    /**
//     * 设置NavBar是否可见
//     */
//    @JvmStatic
//    fun setNavBarVisibility(activity: Activity, isVisible: Boolean) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return
//        setNavBarVisibility(activity.window, isVisible)
//    }
//
//    /**
//     * 设置NavBar是否可见
//     */
//    @JvmStatic
//    fun setNavBarVisibility(window: Window, isVisible: Boolean) {
//        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return
//        val decorView = window.decorView as ViewGroup
//        var i = 0
//        val count = decorView.childCount
//        while (i < count) {
//            val child = decorView.getChildAt(i)
//            val id = child.id
//            if (id != View.NO_ID) {
//                val resourceEntryName = getResNameById(id)
//                if ("navigationBarBackground" == resourceEntryName) {
//                    child.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
//                }
//            }
//            i++
//        }
//        val uiOptions = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
//        if (isVisible) {
//            decorView.systemUiVisibility = decorView.systemUiVisibility and uiOptions.inv()
//        } else {
//            decorView.systemUiVisibility = decorView.systemUiVisibility or uiOptions
//        }
//    }
//
//    /**
//     * 判断NavBar是否可见
//     */
//    @JvmStatic
//    fun isNavBarVisible(activity: Activity): Boolean {
//        return isNavBarVisible(activity.window)
//    }
//
//    /**
//     * 判断NavBar是否可见
//     */
//    @JvmStatic
//    fun isNavBarVisible(window: Window): Boolean {
//        var isVisible = false
//        val decorView = window.decorView as ViewGroup
//        var i = 0
//        val count = decorView.childCount
//        while (i < count) {
//            val child = decorView.getChildAt(i)
//            val id = child.id
//            if (id != View.NO_ID) {
//                val resourceEntryName = getResNameById(id)
//                if ("navigationBarBackground" == resourceEntryName && child.visibility == View.VISIBLE) {
//                    isVisible = true
//                    break
//                }
//            }
//            i++
//        }
//        if (isVisible) {
//            val visibility = decorView.systemUiVisibility
//            isVisible = visibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION == 0
//        }
//        return isVisible
//    }
//
//    private fun getResNameById(id: Int): String {
//        return try {
//            AppUtils.getContext().resources.getResourceEntryName(id)
//        } catch (ignore: Exception) {
//            ""
//        }
//    }
//
//    /**
//     * 设置NavBar的颜色
//     */
//    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
//    @JvmStatic
//    fun setNavBarColor(activity: Activity, @ColorInt color: Int) {
//        setNavBarColor(activity.window, color)
//    }
//
//    /**
//     * 设置NavBar的颜色
//     */
//    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
//    @JvmStatic
//    fun setNavBarColor(window: Window, @ColorInt color: Int) {
//        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
//        window.navigationBarColor = color
//    }
//
//    /**
//     * 获取NavBar的颜色
//     */
//    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
//    @JvmStatic
//    fun getNavBarColor(activity: Activity): Int {
//        return getNavBarColor(activity.window)
//    }
//
//    /**
//     * 获取NavBar的颜色
//     */
//    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
//    @JvmStatic
//    fun getNavBarColor(window: Window): Int {
//        return window.navigationBarColor
//    }
//
//    /**
//     * 是否有NavBar
//     */
//    @JvmStatic
//    fun isSupportNavBar(): Boolean {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
//            val wm = AppUtils.getApplication()
//                    .getSystemService(Context.WINDOW_SERVICE) as WindowManager
//            val display = wm.defaultDisplay
//            val size = Point()
//            val realSize = Point()
//            display.getSize(size)
//            display.getRealSize(realSize)
//            return realSize.y != size.y || realSize.x != size.x
//        }
//        val menu = ViewConfiguration.get(AppUtils.getContext()).hasPermanentMenuKey()
//        val back = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK)
//        return !menu && !back
//    }
//
//    /**=============================================================================================
//     * 沉浸式
//     * immersiveStatusBar() // 沉浸式状态栏
//     * immersiveNavigationBar() // 沉浸式导航栏
//     * setLightStatusBar(true) // 设置浅色状态栏背景（文字为深色）
//     * setLightNavigationBar(true) // 设置浅色导航栏背景（文字为深色）
//     * setStatusBarColor(color) // 设置状态栏背景色
//     * setNavigationBarColor(color) // 设置导航栏背景色
//     * navigationBarHeightLiveData.observe(this) {}// 监听导航栏高度变化
//     * val dialog = Dialog(this, R.style.Heycan_SampleDialog)
//     * dialog.setContentView(R.layout.dialog_loading)
//     * dialog.immersiveStatusBar()
//     * dialog.immersiveNavigationBar()
//     * dialog.setLightStatusBar(true)
//     * dialog.setLightNavigationBar(true)
//     * dialog.show()
//     *
//     **===========================================================================================*/
//
//    fun Activity.setLightStatusBar(isLightingColor: Boolean) {
//        val window = this.window
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (isLightingColor) {
//                window.decorView.systemUiVisibility =
//                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
//            } else {
//                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//            }
//        }
//    }
//
//    fun Activity.setLightNavigationBar(isLightingColor: Boolean) {
//        val window = this.window
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isLightingColor) {
//            window.decorView.systemUiVisibility =
//                window.decorView.systemUiVisibility or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
//        }
//    }
//
//    /**
//     * 必须在Activity的onCreate时调用
//     */
//    fun Activity.immersiveStatusBar() {
//        val view = (window.decorView as ViewGroup).getChildAt(0)
//        val listener = object : View.OnLayoutChangeListener {
//            override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
//                val lp = view.layoutParams as FrameLayout.LayoutParams
//                if (lp.topMargin > 0) {
//                    lp.topMargin = 0
//                    v?.layoutParams = lp
//                }
//                if (view.paddingTop > 0) {
//                    view.setPadding(0, 0, 0, view.paddingBottom)
//                    val content = findViewById<View>(android.R.id.content)
//                    content.requestLayout()
//
//                }
//                view?.removeOnLayoutChangeListener(this)
//            }
//
//        }
//        view.addOnLayoutChangeListener(listener)
//
//        val content = findViewById<View>(android.R.id.content)
//        content.setPadding(0, 0, 0, content.paddingBottom)
//
//        window.decorView.findViewById(R.id.status_bar_view) ?: View(window.context).apply {
//            id = R.id.status_bar_view
//            val params =
//                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, statusHeight)
//            params.gravity = Gravity.TOP
//            layoutParams = params
//            (window.decorView as ViewGroup).addView(this)
//
//            (window.decorView as ViewGroup).setOnHierarchyChangeListener(object :
//                ViewGroup.OnHierarchyChangeListener {
//                override fun onChildViewAdded(parent: View?, child: View?) {
//                    if (child?.id == android.R.id.statusBarBackground) {
//                        child.scaleX = 0f
//                    }
//                }
//
//                override fun onChildViewRemoved(parent: View?, child: View?) {
//                }
//            })
//        }
//        setStatusBarColor(Color.TRANSPARENT)
//    }
//
//    /**
//     * 必须在Activity的onCreate时调用
//     */
//    fun Activity.immersiveNavigationBar(callback: (() -> Unit)? = null) {
//        val view = (window.decorView as ViewGroup).getChildAt(0)
//        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
//            val lp = view.layoutParams as FrameLayout.LayoutParams
//            if (lp.bottomMargin > 0) {
//                lp.bottomMargin = 0
//                v.layoutParams = lp
//            }
//            if (view.paddingBottom > 0) {
//                view.setPadding(0, view.paddingTop, 0, 0)
//                val content = findViewById<View>(android.R.id.content)
//                content.requestLayout()
//            }
//        }
//
//        val content = findViewById<View>(android.R.id.content)
//        content.setPadding(0, content.paddingTop, 0, -1)
//
//        val heightLiveData = MutableLiveData<Int>()
//        heightLiveData.value = 0
//        window.decorView.setTag(R.id.navigation_height_live_data, heightLiveData)
//        callback?.invoke()
//
//        window.decorView.findViewById(R.id.navigation_bar_view) ?: View(window.context).apply {
//            id = R.id.navigation_bar_view
//            val params = FrameLayout.LayoutParams(
//                FrameLayout.LayoutParams.MATCH_PARENT,
//                heightLiveData.value ?: 0
//            )
//            params.gravity = Gravity.BOTTOM
//            layoutParams = params
//            (window.decorView as ViewGroup).addView(this)
//
//            if (this@immersiveNavigationBar is FragmentActivity) {
//                heightLiveData.observe(this@immersiveNavigationBar) {
//                    val lp = layoutParams
//                    lp.height = heightLiveData.value ?: 0
//                    layoutParams = lp
//                }
//            }
//
//            (window.decorView as ViewGroup).setOnHierarchyChangeListener(object :
//                ViewGroup.OnHierarchyChangeListener {
//                override fun onChildViewAdded(parent: View?, child: View?) {
//                    if (child?.id == android.R.id.navigationBarBackground) {
//                        child.scaleX = 0f
//                        bringToFront()
//
//                        child.addOnLayoutChangeListener { _, _, top, _, bottom, _, _, _, _ ->
//                            heightLiveData.value = bottom - top
//                        }
//                    } else if (child?.id == android.R.id.statusBarBackground) {
//                        child.scaleX = 0f
//                    }
//                }
//
//                override fun onChildViewRemoved(parent: View?, child: View?) {
//                }
//            })
//        }
//        setNavigationBarColor(Color.TRANSPARENT)
//    }
//
//    /**
//     * 当设置了immersiveStatusBar时，如需使用状态栏，可调佣该函数
//     */
//    fun Activity.fitStatusBar(fit: Boolean) {
//        val content = findViewById<View>(android.R.id.content)
//        if (fit) {
//            content.setPadding(0, statusHeight, 0, content.paddingBottom)
//        } else {
//            content.setPadding(0, 0, 0, content.paddingBottom)
//        }
//    }
//
//    fun Activity.fitNavigationBar(fit: Boolean) {
//        val content = findViewById<View>(android.R.id.content)
//        if (fit) {
//            content.setPadding(0, content.paddingTop, 0, navigationBarHeightLiveData.value ?: 0)
//        } else {
//            content.setPadding(0, content.paddingTop, 0, -1)
//        }
//        if (this is FragmentActivity) {
//            navigationBarHeightLiveData.observe(this) {
//                if (content.paddingBottom != -1) {
//                    content.setPadding(0, content.paddingTop, 0, it)
//                }
//            }
//        }
//    }
//
//    val Activity.isImmersiveNavigationBar: Boolean
//        get() =
//            window.attributes.flags and WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION != 0
//
//    val Activity.statusHeight: Int
//        get() {
//            val resourceId =
//                resources.getIdentifier("status_bar_height", "dimen", "android")
//            if (resourceId > 0) {
//                return resources.getDimensionPixelSize(resourceId)
//            }
//            return 0
//        }
//
//    val Activity.navigationHeight: Int
//        get() {
//            return navigationBarHeightLiveData.value ?: 0
//        }
//
//    val Activity.screenSize: Size
//        get() {
//            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                Size(
//                    windowManager.currentWindowMetrics.bounds.width(),
//                    windowManager.currentWindowMetrics.bounds.height()
//                )
//            } else {
//                Size(windowManager.defaultDisplay.width, windowManager.defaultDisplay.height)
//            }
//        }
//
//    fun Activity.setStatusBarColor(color: Int) {
//        val statusBarView = window.decorView.findViewById<View?>(R.id.status_bar_view)
//        if (color == 0 && Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
//            statusBarView?.setBackgroundColor(STATUS_BAR_MASK_COLOR)
//        } else {
//            statusBarView?.setBackgroundColor(color)
//        }
//    }
//
//    fun Activity.setNavigationBarColor(color: Int) {
//        val navigationBarView = window.decorView.findViewById<View?>(R.id.navigation_bar_view)
//        if (color == 0 && Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
//            navigationBarView?.setBackgroundColor(STATUS_BAR_MASK_COLOR)
//        } else {
//            navigationBarView?.setBackgroundColor(color)
//        }
//    }
//
//    @Suppress("UNCHECKED_CAST")
//    val Activity.navigationBarHeightLiveData: LiveData<Int>
//        get() {
//            var liveData =
//                window.decorView.getTag(R.id.navigation_height_live_data) as? LiveData<Int>
//            if (liveData == null) {
//                liveData = MutableLiveData()
//                window.decorView.setTag(R.id.navigation_height_live_data, liveData)
//            }
//            return liveData
//        }
//
//    val Activity.screenWidth: Int get() = screenSize.width
//
//    val Activity.screenHeight: Int get() = screenSize.height
//
//    private const val STATUS_BAR_MASK_COLOR = 0x7F000000
//
//
//    /**
//     * Created by dengchunguo on 2021/4/25
//     */
//    fun Dialog.setLightStatusBar(isLightingColor: Boolean) {
//        val window = this.window ?: return
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            if (isLightingColor) {
//                window.decorView.systemUiVisibility =
//                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
//            } else {
//                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//            }
//        }
//    }
//
//    fun Dialog.setLightNavigationBar(isLightingColor: Boolean) {
//        val window = this.window ?: return
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && isLightingColor) {
//            window.decorView.systemUiVisibility =
//                window.decorView.systemUiVisibility or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
//        }
//    }
//
//    fun Dialog.immersiveStatusBar() {
//        val window = this.window ?: return
//        (window.decorView as ViewGroup).setOnHierarchyChangeListener(object :
//            ViewGroup.OnHierarchyChangeListener {
//            override fun onChildViewAdded(parent: View?, child: View?) {
//                if (child?.id == android.R.id.statusBarBackground) {
//                    child.scaleX = 0f
//                }
//            }
//
//            override fun onChildViewRemoved(parent: View?, child: View?) {
//            }
//        })
//    }
//
//    fun Dialog.immersiveNavigationBar() {
//        val window = this.window ?: return
//        (window.decorView as ViewGroup).setOnHierarchyChangeListener(object :
//            ViewGroup.OnHierarchyChangeListener {
//            override fun onChildViewAdded(parent: View?, child: View?) {
//                if (child?.id == android.R.id.navigationBarBackground) {
//                    child.scaleX = 0f
//                } else if (child?.id == android.R.id.statusBarBackground) {
//                    child.scaleX = 0f
//                }
//            }
//
//            override fun onChildViewRemoved(parent: View?, child: View?) {
//            }
//        })
//    }
//}