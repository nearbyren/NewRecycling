package com.recycling.toolsapp.utils

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle

class FragmentNavigator(
    private val fragmentManager: FragmentManager,
    private val containerId: Int
) {
    companion object {
        private const val STATE_KEY = "FragmentNavigator_State"
    }

    // 使用ViewModel保存共享状态（此处简化为Bundle）
    private var stateBundle: Bundle? = null

    // 核心导航方法
    fun navigateTo(
        fragment: Fragment,
        tag: String,
        addToBackStack: Boolean = true,
        animation: Pair<Int, Int>? = null
    ) {
        val currentFragment = getCurrentFragment()

        fragmentManager.commit(allowStateLoss = false) {
            animation?.let { setCustomAnimations(it.first, it.second) }

            currentFragment?.let { hide(it) }

            if (fragment.isAdded) {
                show(fragment)
            } else {
                add(containerId, fragment, tag)
            }

            if (addToBackStack) {
                addToBackStack(tag)
            }

            setReorderingAllowed(true)
            setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
        }

        logLifecycle("navigateTo: $tag")
    }

    // 获取当前显示的Fragment
    fun getCurrentFragment(): Fragment? {
        return fragmentManager.findFragmentById(containerId)?.takeIf { it.isVisible }
    }

    // 处理返回栈
    fun handleBackPressed(): Boolean {
        val fragment = getCurrentFragment()
        if (fragment is BackPressHandler && fragment.onBackPressed()) {
            return true
        }

        return if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStackImmediate()
            true
        } else {
            false
        }
    }

    // 状态保存
    fun saveState(outState: Bundle) {
        fragmentManager.putFragment(outState, STATE_KEY, getCurrentFragment() ?: return)
    }

    // 状态恢复
    fun restoreState(savedState: Bundle?) {
        savedState?.let {
            val fragment = fragmentManager.getFragment(it, STATE_KEY)
            fragment?.let {
                navigateTo(it, it.tag ?: "", addToBackStack = false)
            }
        }
    }

    // 内存优化：清理不可见Fragment
    fun optimizeMemory() {
        fragmentManager.fragments.forEach { fragment ->
            if (fragment != getCurrentFragment() && fragment.isAdded) {
                fragmentManager.commit {
                    setMaxLifecycle(fragment, Lifecycle.State.STARTED)
                }
            }
        }
    }

    // 生命周期监控
    private fun logLifecycle(action: String) {
        val current = getCurrentFragment()
        Log.d("FragmentNavigator", """
            Action: $action
            Current: ${current?.javaClass?.simpleName}
            BackStackCount: ${fragmentManager.backStackEntryCount}
            FragmentStates: ${fragmentManager.fragments.joinToString { it.lifecycle.currentState.name }}
        """.trimIndent())
    }

    interface BackPressHandler {
        fun onBackPressed(): Boolean
    }
}

// 使用示例：
//class MainActivity : AppCompatActivity() {
//    private lateinit var navigator: FragmentNavigator
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        navigator = FragmentNavigator(supportFragmentManager, R.id.fragment_container)
//        navigator.restoreState(savedInstanceState)
//
//        if (savedInstanceState == null) {
//            navigator.navigateTo(HomeFragment(), "Home", animation = Pair(R.anim.slide_in, R.anim.fade_out))
//        }
//    }
//
//    override fun onSaveInstanceState(outState: Bundle) {
//        super.onSaveInstanceState(outState)
//        navigator.saveState(outState)
//    }
//
//    override fun onBackPressed() {
//        if (!navigator.handleBackPressed()) {
//            super.onBackPressed()
//        }
//    }
//}