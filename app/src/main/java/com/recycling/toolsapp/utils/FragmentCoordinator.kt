package com.recycling.toolsapp.utils

import android.os.Bundle
import androidx.annotation.AnimRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.recycling.toolsapp.FaceApplication
import com.recycling.toolsapp.R
import com.serial.port.utils.Loge
import java.util.Stack


class FragmentCoordinator private constructor(
    private val fragmentManager: FragmentManager,
    private val containerId: Int,
) {
    val fragmentStack = Stack<FragmentInfo>()
    private val lifecycleCallbacks = mutableMapOf<String, FragmentLifecycleCallback>()
    private val resultCallbacks = mutableMapOf<Class<out Fragment>, ResultCallbackWrapper>()
    val animation =
            AnimConfigBuilder().setEnterAnim(R.anim.fade_in).setExitAnim(R.anim.fade_out).build()

    // 添加根Fragment标识
    private var rootFragmentTag: String? = null

    // 修改为使用 LinkedHashMap 保持顺序，并存储更多信息
    private data class ResultCallbackWrapper(
        val callback: FragmentResultCallback,
        var pendingResult: Bundle? = null,
        var isDestroyed: Boolean = false,
    )

    companion object {
        @Volatile
        private var instance: FragmentCoordinator? = null

        fun getInstance(fragmentManager: FragmentManager, containerId: Int): FragmentCoordinator {
            return instance ?: synchronized(this) {
                Loge.d("FragmentCoordinator getInstance")
                instance ?: FragmentCoordinator(fragmentManager, containerId).also { instance = it }
            }
        }
    }

    data class AnimConfig(
        @AnimRes val enter: Int = R.anim.slide_in_right,
        @AnimRes val exit: Int = R.anim.slide_out_left,
        @AnimRes val popEnter: Int = R.anim.slide_in_left,
        @AnimRes val popExit: Int = R.anim.slide_out_right,
    )

    interface FragmentLifecycleCallback {
        fun onFragmentCreated(fragment: Fragment) {}
        fun onFragmentViewCreated(fragment: Fragment) {}
        fun onFragmentResumed(fragment: Fragment) {}
        fun onFragmentPaused(fragment: Fragment) {}
        fun onFragmentDestroyed(fragment: Fragment) {}
    }

    interface FragmentResultCallback {
        fun onResult(result: Bundle)
    }

    data class FragmentInfo(
        val fragmentClass: Class<out Fragment>,
        val tag: String,
        val args: Bundle? = null,
        val animConfig: AnimConfig? = null,
        val deepLink: String? = null,
        val isRootFragment: Boolean = false // 添加根Fragment标识
    )

    init {
        fragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentCreated(fm: FragmentManager, f: Fragment, s: Bundle?) {
                Loge.d("FragmentCoordinator onFragmentCreated")
                lifecycleCallbacks[f.tag]?.onFragmentCreated(f)
            }

            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: android.view.View, s: Bundle?) {
                lifecycleCallbacks[f.tag]?.onFragmentViewCreated(f)
                Loge.d("FragmentCoordinator onFragmentViewCreated $f")

            }

            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                lifecycleCallbacks[f.tag]?.onFragmentResumed(f)
                Loge.d("FragmentCoordinator onFragmentResumed $f")

            }

            override fun onFragmentPaused(fm: FragmentManager, f: Fragment) {
                lifecycleCallbacks[f.tag]?.onFragmentPaused(f)
                Loge.d("FragmentCoordinator onFragmentPaused $f")

            }

            override fun onFragmentDestroyed(fm: FragmentManager, f: Fragment) {
                Loge.d("FragmentCoordinator onFragmentDestroyed $f")
                lifecycleCallbacks[f.tag]?.onFragmentDestroyed(f)
                lifecycleCallbacks.remove(f.tag)
                // 只标记为已销毁，但不立即移除回调
                resultCallbacks[f.javaClass]?.let { wrapper ->
                    wrapper.isDestroyed = true
                    // 如果没有待处理的结果，才移除回调
                    if (wrapper.pendingResult == null) {
                        resultCallbacks.remove(f.javaClass)
                    }
                }
            }
        }, true)
    }

    /**
     * 设置根Fragment，这个方法应该在应用启动时调用一次
     */
    fun setRootFragment(
        fragmentClass: Class<out Fragment>,
        args: Bundle? = null,
        lifecycleCallback: FragmentLifecycleCallback? = null
    ) {
        Loge.d("FragmentCoordinator setRootFragment ${fragmentClass.simpleName}")

        // 清除所有现有的Fragment
        clearStack()

        val tag = fragmentClass.simpleName
        rootFragmentTag = tag

        // 保存回调
        lifecycleCallback?.let { lifecycleCallbacks[tag] = it }

        val fragment = fragmentClass.newInstance().apply {
            arguments = args
        }

        val transaction = fragmentManager.beginTransaction()
        transaction.replace(containerId, fragment, tag)
        transaction.commitNowAllowingStateLoss()

        // 将根Fragment添加到栈中，但不添加到回退栈
        fragmentStack.push(FragmentInfo(fragmentClass, tag, args, null, null, true))

        Loge.d("FragmentCoordinator 根Fragment设置完成: $tag")
    }

    // 清理方法也需要相应修改
    fun clearResultCallback(fragmentClass: Class<out Fragment>) {
        resultCallbacks[fragmentClass]?.let { wrapper ->
            if (wrapper.pendingResult == null) {
                resultCallbacks.remove(fragmentClass)
            }
        }
    }

    fun navigateTo(
        fragmentClass: Class<out Fragment>,
        args: Bundle? = null,
        animConfig: AnimConfig? = null,
        deepLink: String? = null,
        addToBackStack: Boolean = true,
        lifecycleCallback: FragmentLifecycleCallback? = null,
        resultCallback: FragmentResultCallback? = null,
    ) {
        Loge.d("FragmentCoordinator navigateTo ${fragmentClass.simpleName}")

        // 检查是否已经设置了根Fragment
        if (rootFragmentTag == null) {
            Loge.e("FragmentCoordinator 错误：请先调用setRootFragment设置根Fragment")
            return
        }

        val tag = fragmentClass.simpleName

        // 保存回调，使用包装类
        lifecycleCallback?.let { lifecycleCallbacks[tag] = it }
        resultCallback?.let {
            resultCallbacks[fragmentClass] =
                    ResultCallbackWrapper(callback = it, pendingResult = null, isDestroyed = false)
        }

        val fragment = fragmentClass.newInstance().apply {
            arguments = args
        }

        val transaction = fragmentManager.beginTransaction()

        animConfig?.let {
            transaction.setCustomAnimations(it.enter, it.exit, it.popEnter, it.popExit)
        } ?: kotlin.run {
            transaction.setCustomAnimations(R.anim.fade_in, R.anim.fade_out, R.anim.fade_in, R.anim.fade_out)
        }

        // 使用hide/show而不是replace来保留之前的Fragment
        val currentFragment = getCurrentFragment()
        currentFragment?.let {
            transaction.hide(it)
        }

        // 检查是否已经添加过该Fragment
        val existingFragment = fragmentManager.findFragmentByTag(tag)
        if (existingFragment != null) {
            transaction.show(existingFragment)
        } else {
            transaction.add(containerId, fragment, tag)
        }

        transaction.setReorderingAllowed(true)

        if (addToBackStack) {
            transaction.addToBackStack(tag)
            fragmentStack.push(FragmentInfo(fragmentClass, tag, args, animConfig, deepLink, false))
        }

        transaction.commitAllowingStateLoss()
        fragmentManager.executePendingTransactions()

        Loge.d("FragmentCoordinator 导航完成，当前栈大小: ${fragmentStack.size}")
    }


    fun navigateBack(): Boolean {
        Loge.d("FragmentCoordinator navigateBack 当前栈大小: ${fragmentStack.size}")

        if (fragmentStack.size <= 1) {
            Loge.d("FragmentCoordinator 已经是根Fragment，不能回退")
            return false
        }

        if (fragmentManager.isStateSaved) {
            Loge.d("FragmentCoordinator 状态已保存，不能回退")
            return false
        }

        // 使用同步方法弹出回退栈
        val popped = fragmentManager.popBackStackImmediate()
        if (popped) {
            // 成功弹出后移除栈顶记录
            val removedFragment = fragmentStack.pop()
            resultCallbacks.remove(removedFragment.fragmentClass)

            // 显示前一个Fragment
            val previousFragment = getCurrentFragment()
            previousFragment?.let {
                fragmentManager.beginTransaction()
                    .show(it)
                    .commitAllowingStateLoss()
            }

            Loge.d("FragmentCoordinator 回退成功，当前栈大小: ${fragmentStack.size}")
            return true
        }

        Loge.d("FragmentCoordinator 回退失败")
        return false
    }

    /***
     * @param fragmentClass
     * 导航回到指定fragment
     */
    fun navigateBackTo(fragmentClass: Class<out Fragment>): Boolean {
        val isAppForeground = FaceApplication.getInstance().isAppForeground.value ?: true
        Loge.d("FragmentCoordinator navigateBackTo 是否已保存状态：${fragmentManager.isStateSaved} 前台：$isAppForeground")
        if (!isAppForeground) {
            return false
        }

        val index = fragmentStack.indexOfFirst { it.fragmentClass == fragmentClass }
        Loge.d("FragmentCoordinator navigateBackTo $index = simpleName = ${fragmentClass.simpleName}")

        if (index != -1 && index < fragmentStack.size - 1) {
            // 弹出直到目标Fragment
            while (fragmentStack.size > index + 1) {
                if (fragmentManager.popBackStackImmediate()) {
                    val removed = fragmentStack.pop()
                    resultCallbacks.remove(removed.fragmentClass)
                } else {
                    break
                }
            }

            // 确保目标Fragment可见
            val targetFragment = fragmentManager.findFragmentByTag(fragmentClass.simpleName)
            targetFragment?.let {
                fragmentManager.beginTransaction()
                    .show(it)
                    .commitAllowingStateLoss()
            }

            Loge.d("FragmentCoordinator 导航回到 ${fragmentClass.simpleName} 成功")
            return true
        }

        Loge.d("FragmentCoordinator 导航回到 ${fragmentClass.simpleName} 失败")
        return false
    }

    /**
     * 关闭指定的Fragment
     * @param fragmentClass 要关闭的Fragment类
     * @param animate 是否使用动画
     * @return 是否成功关闭
     */
    fun closeFragment(fragmentClass: Class<out Fragment>, animate: Boolean = true): Boolean {
        val isAppForeground = FaceApplication.getInstance().isAppForeground.value ?: true
        Loge.d("FragmentCoordinator closeFragment 是否已保存状态：${fragmentManager.isStateSaved} 前台：$isAppForeground")

        if (!isAppForeground || fragmentManager.isStateSaved) {
            return false
        }

        // 查找要关闭的Fragment在栈中的位置
        val targetIndex = fragmentStack.indexOfFirst { it.fragmentClass == fragmentClass }
        if (targetIndex == -1) {
            Loge.d("FragmentCoordinator 未找到要关闭的Fragment: ${fragmentClass.simpleName}")
            return false
        }

        // 不能关闭根Fragment
        if (targetIndex == 0) {
            Loge.d("FragmentCoordinator 不能关闭根Fragment: ${fragmentClass.simpleName}")
            return false
        }

        val targetInfo = fragmentStack[targetIndex]
        Loge.d("FragmentCoordinator 准备关闭Fragment: ${targetInfo.tag}, 位置: $targetIndex")

        // 如果要关闭的是当前Fragment，直接回退
        if (targetIndex == fragmentStack.size - 1) {
            return navigateBack()
        }

        // 如果要关闭的是中间某个Fragment，需要特殊处理
        return closeMiddleFragment(targetIndex, targetInfo, animate)
    }

    /**
     * 关闭中间位置的Fragment
     */
    private fun closeMiddleFragment(targetIndex: Int, targetInfo: FragmentInfo, animate: Boolean): Boolean {
        try {
            // 1. 先找到目标Fragment
            val targetFragment = fragmentManager.findFragmentByTag(targetInfo.tag)
            if (targetFragment == null) {
                Loge.d("FragmentCoordinator 未找到Fragment实例: ${targetInfo.tag}")
                // 即使找不到实例，也从栈中移除记录
                fragmentStack.removeAt(targetIndex)
                return true
            }

            // 2. 创建事务
            val transaction = fragmentManager.beginTransaction()

            if (animate) {
                // 使用淡出动画
                transaction.setCustomAnimations(0, R.anim.fade_out, 0, 0)
            }

            // 3. 移除Fragment
            transaction.remove(targetFragment)

            // 4. 从栈中移除记录
            fragmentStack.removeAt(targetIndex)

            // 5. 移除相关的回调和监听器
            lifecycleCallbacks.remove(targetInfo.tag)
            resultCallbacks.remove(targetInfo.fragmentClass)

            // 6. 提交事务
            transaction.commitAllowingStateLoss()
            fragmentManager.executePendingTransactions()

            Loge.d("FragmentCoordinator 成功关闭中间Fragment: ${targetInfo.tag}")
            return true

        } catch (e: Exception) {
            Loge.e("FragmentCoordinator 关闭Fragment时发生异常: ${e.message}")
            return false
        }
    }

    /**
     * 关闭多个Fragment
     * @param fragmentClasses 要关闭的Fragment类列表
     * @return 成功关闭的数量
     */
    fun closeFragments(fragmentClasses: List<Class<out Fragment>>): Int {
        var successCount = 0
        for (fragmentClass in fragmentClasses) {
            if (closeFragment(fragmentClass, false)) { // 批量关闭时不使用动画
                successCount++
            }
        }
        Loge.d("FragmentCoordinator 批量关闭完成: 成功$successCount/${fragmentClasses.size}")
        return successCount
    }

    /**
     * 关闭除指定Fragment之外的所有Fragment
     * @param keepFragmentClass 要保留的Fragment类
     * @return 是否成功
     */
    fun closeAllExcept(keepFragmentClass: Class<out Fragment>): Boolean {
        Loge.d("FragmentCoordinator closeAllExcept ${keepFragmentClass.simpleName}")

        val fragmentsToClose = fragmentStack.filter {
            it.fragmentClass != keepFragmentClass && !it.isRootFragment
        }.map { it.fragmentClass }

        return closeFragments(fragmentsToClose) == fragmentsToClose.size
    }

    /**
     * 关闭当前Fragment之上的所有Fragment
     * @return 是否成功
     */
    fun closeAllAboveCurrent(): Boolean {
        val currentIndex = fragmentStack.size - 1
        if (currentIndex <= 0) {
            return false
        }

        // 从栈顶开始关闭，直到当前Fragment
        while (fragmentStack.size > currentIndex + 1) {
            if (!navigateBack()) {
                return false
            }
        }
        return true
    }

    fun handleDeepLink(deepLink: String): Boolean {
        val fragmentInfo = fragmentStack.find { it.deepLink == deepLink }
        Loge.d("FragmentCoordinator handleDeepLink $deepLink fragmentInfo = $fragmentInfo")
        return if (fragmentInfo != null) {
            navigateBackTo(fragmentInfo.fragmentClass)
            true
        } else {
            false
        }
    }

    fun sendResult(result: Bundle) {
        Loge.d("FragmentCoordinator sendResult 当前栈大小: ${fragmentStack.size}")
        if (fragmentStack.size >= 2) {
            val previousFragment = fragmentStack[fragmentStack.size - 1]
            val tag = previousFragment.fragmentClass
            val wrapper = resultCallbacks[previousFragment.fragmentClass]
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                if (wrapper != null) {
                    if (!wrapper.isDestroyed) {
                        // Fragment 还未销毁，直接调用回调
                        wrapper.callback.onResult(result)
                        resultCallbacks.remove(tag)
                    } else {
                        // Fragment 已销毁，但还有待处理的结果
                        wrapper.pendingResult = result
                        wrapper.callback.onResult(result)
                        // 结果已处理，可以安全移除
                        resultCallbacks.remove(tag)
                    }
                    Loge.d("移除tag = $tag")
                }
            }
        }
    }

    fun clearStack() {
        // 保留根Fragment
        if (rootFragmentTag != null && fragmentStack.isNotEmpty()) {
            // 只清除根Fragment之上的所有Fragment
            while (fragmentStack.size > 1) {
                if (fragmentManager.popBackStackImmediate()) {
                    fragmentStack.pop()
                } else {
                    break
                }
            }

            // 确保根Fragment可见
            val rootFragment = fragmentManager.findFragmentByTag(rootFragmentTag)
            rootFragment?.let {
                fragmentManager.beginTransaction()
                    .show(it)
                    .commitAllowingStateLoss()
            }
        } else {
            fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            fragmentStack.clear()
        }

        Loge.d("FragmentCoordinator 栈已清除，剩余栈大小: ${fragmentStack.size}")
    }

    fun getCurrentFragment(): Fragment? {
        Loge.d("FragmentCoordinator getCurrentFragment 当前栈大小: ${fragmentStack.size}")
        return if (fragmentStack.isNotEmpty()) {
            val current = fragmentStack.peek()
            fragmentManager.findFragmentByTag(current.tag)
        } else {
            null
        }
    }

    /**
     * 获取根Fragment
     */
    fun getRootFragment(): Fragment? {
        return rootFragmentTag?.let { fragmentManager.findFragmentByTag(it) }
    }

    /**
     * 检查是否是根Fragment
     */
    fun isRootFragment(fragment: Fragment): Boolean {
        return fragment.tag == rootFragmentTag
    }

    /**
     * 检查指定Fragment是否存在
     */
    fun containsFragment(fragmentClass: Class<out Fragment>): Boolean {
        return fragmentStack.any { it.fragmentClass == fragmentClass }
    }

    /**
     * 获取Fragment在栈中的位置
     */
    fun getFragmentPosition(fragmentClass: Class<out Fragment>): Int {
        return fragmentStack.indexOfFirst { it.fragmentClass == fragmentClass }
    }

    class AnimConfigBuilder {
        private var enter: Int = R.anim.slide_in_right
        private var exit: Int = R.anim.slide_out_left
        private var popEnter: Int = R.anim.slide_in_left
        private var popExit: Int = R.anim.slide_out_right

        fun setEnterAnim(@AnimRes anim: Int) = apply { enter = anim }
        fun setExitAnim(@AnimRes anim: Int) = apply { exit = anim }
        fun setPopEnterAnim(@AnimRes anim: Int) = apply { popEnter = anim }
        fun setPopExitAnim(@AnimRes anim: Int) = apply { popExit = anim }

        fun build() = AnimConfig(enter, exit, popEnter, popExit)
    }
}