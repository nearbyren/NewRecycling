package com.recycling.toolsapp.fitsystembar.base.bind

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.cabinet.toolsapp.tools.bus.FlowBus
import com.cabinet.toolsapp.tools.bus.TitleBarEvent
import com.recycling.toolsapp.fitsystembar.showSystemBar
import com.serial.port.utils.Loge
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

abstract class BaseLazyTimedFragment : Fragment() {
    private var timerJob: Job? = null
    private var elapsedTime = 0L // 已经过去的时间
    private var isTimerPaused = false // 计时器是否被暂停
    private var timerStartTime = 0L // 计时器开始的时间戳
    private var lastPauseTime = 0L // 上次暂停的时间戳

    // 定时更新任务（用于实时更新UI）
    private var updateJob: Job? = null

    // 懒加载相关状态
    private var isViewCreated = false // View是否已创建
    private var isFirstVisible = true // 是否是第一次可见
    private var isFragmentVisible = false // Fragment是否可见（综合判断）
    private var isUserVisible = true // ViewPager中的可见性（默认true，后面会根据实际情况更新）
    private var rootView: View? = null
    abstract fun layoutRes(): Int
    protected abstract fun initialize(savedInstanceState: Bundle?)
    protected fun setRootView(view: View) {
        this.rootView = view
    }

    protected fun getRootView(): View? {
        return rootView
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        rootView?.let {
            return it
        }
        rootView = inflater.inflate(layoutRes(), container, false)
        initialize(savedInstanceState)
        return rootView
    }

    // 是否启用自动关闭功能
    protected open fun isAutoCloseEnabled(): Boolean = false

    // 子类可重写此方法设置显示时长(毫秒)
    protected open fun getDisplayDuration(): Long = 35000

    // 更新间隔(毫秒)，子类可重写
    protected open fun getUpdateInterval(): Long = 1000 // 默认1秒更新一次

    // 子类可重写此方法处理关闭前的逻辑
    protected open fun onBeforeClose() {}

    // 子类可重写此方法处理关闭后的逻辑
    protected open fun onAfterClose() {}

    // 子类可重写此方法自定义关闭行为
    protected open fun performCloseAction() {
//        findNavController().popBackStack()
        findNavController().navigateUp()
    }

    // 子类可重写此方法获取剩余时间更新（每秒调用）
    // remainingSeconds: 剩余秒数（整数）
    // totalSeconds: 总秒数（整数）
    // progress: 进度百分比(0.0 - 1.0)
    protected open fun onTimerUpdate(
        remainingSeconds: Int,
        totalSeconds: Int,
        progress: Float,
    ) {
        // 默认实现为空，子类可重写以更新UI
    }

    // 子类可重写此方法处理计时结束（在onBeforeClose之前调用）
    protected open fun onTimerFinished() {
        // 默认实现为空，子类可重写
    }

    // ==================== 懒加载相关方法 ====================

    // 子类可重写此方法：当Fragment首次可见时调用（用于懒加载数据）
    protected open fun onFirstVisible() {
        // 默认实现为空，子类可重写
    }

    // 子类可重写此方法：当Fragment可见时调用（每次可见都会调用）
    protected open fun onFragmentVisible() {
        // 默认实现为空，子类可重写
        val destination = findNavController().currentDestination
        val label = destination?.label
        Loge.e("测试我来了 label = $label destination = $destination")
        lifecycleScope.launch {
            FlowBus.with<TitleBarEvent>("TitleBarEvent").post(this, TitleBarEvent().apply {
                toShowTitleBar = if (label != "fragmentMain") true else false
            })
        }
    }

    // 子类可重写此方法：当Fragment不可见时调用
    protected open fun onFragmentInvisible() {
        // 默认实现为空，子类可重写
    }

    // 子类可重写此方法：判断是否需要懒加载
    protected open fun shouldLazyLoad(): Boolean = true

    // ==================== 可见性判断 ====================

    // 判断Fragment是否真正可见（综合考虑各种情况）
    private fun isFragmentReallyVisible(): Boolean {
        // 基础检查：View必须已创建，Fragment必须已添加，不在移除状态
        if (!isViewCreated || !isAdded || isRemoving || view == null) {
            return false
        }

        // 检查View是否已附加到窗口
        if (view?.windowToken == null || view?.isAttachedToWindow != true) {
            return false
        }

        // 检查是否被隐藏（show/hide）
        if (isHidden) {
            return false
        }

        // 检查是否在ViewPager中并且当前可见
        if (!isUserVisible) {
            return false
        }

        // 检查Fragment所属的Activity是否正在运行
        return activity != null && !activity?.isFinishing!!
    }

    // 检查并更新Fragment的可见性状态
    private fun checkAndUpdateVisibility() {
        val wasVisible = isFragmentVisible
        val isNowVisible = isFragmentReallyVisible()

        if (isNowVisible && !wasVisible) {
            // 从不可见变为可见
            isFragmentVisible = true
            onFragmentBecomeVisible()
        } else if (!isNowVisible && wasVisible) {
            // 从可见变为不可见
            isFragmentVisible = false
            onFragmentBecomeInvisible()
        }
    }

    // Fragment变为可见时的处理
    private fun onFragmentBecomeVisible() {
        // 首次可见，执行懒加载
        if (isFirstVisible && shouldLazyLoad()) {
            isFirstVisible = false
            onFirstVisible()
        }

        // 每次可见都执行
        onFragmentVisible()

        // 恢复或启动计时器
        if (isAutoCloseEnabled()) {
            if (isTimerPaused) {
                startOrResumeAutoCloseTimer()
            } else if (timerJob == null) {
                startOrResumeAutoCloseTimer()
            }
        }
    }

    // Fragment变为不可见时的处理
    private fun onFragmentBecomeInvisible() {
        onFragmentInvisible()

        // 暂停计时器
        if (isAutoCloseEnabled() && !isTimerPaused) {
            pauseAutoCloseTimer()
        }
    }

    // ==================== 计时器控制 ====================

    // 启动或恢复定时器
    protected fun startOrResumeAutoCloseTimer() {
        if (!isAutoCloseEnabled() || !isFragmentVisible) return

        cancelAutoCloseTimer() // 先取消已存在的定时器

        // 计算剩余时间
        val remainingTime = if (isTimerPaused && elapsedTime > 0) {
            // 如果是暂停后恢复，使用剩余时间
            getDisplayDuration() - elapsedTime
        } else {
            // 首次启动或重新启动
            getDisplayDuration()
        }

        // 如果剩余时间小于等于0，立即执行关闭
        if (remainingTime <= 0) {
            executeCloseAction()
            return
        }

        timerStartTime = SystemClock.elapsedRealtime()
        isTimerPaused = false

        // 启动定时关闭协程
        timerJob = lifecycleScope.launch {
            delay(remainingTime)
            executeCloseAction()
        }

        // 启动定时更新协程
        startUpdateJob()
    }

    // 启动更新任务（实时更新剩余时间）
    private fun startUpdateJob() {
        updateJob?.cancel()

        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(getDisplayDuration()).toInt()

        updateJob = lifecycleScope.launch {
            while (isActive && isFragmentVisible) {
                delay(getUpdateInterval())

                if (isTimerPaused) {
                    // 如果暂停了，等待下次恢复
                    continue
                }

                val remainingTime = getRemainingTime()
                val remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime).toInt()

                // 计算进度（0.0 - 1.0）
                val progress = if (getDisplayDuration() > 0) {
                    (getDisplayDuration() - remainingTime).toFloat() / getDisplayDuration()
                } else {
                    0f
                }

                // 调用更新回调
                onTimerUpdate(remainingSeconds, totalSeconds, progress.coerceIn(0f, 1f))

                // 如果剩余时间小于更新间隔，等待关闭
                if (remainingTime <= getUpdateInterval()) {
                    break
                }
            }
        }
    }

    // 暂停定时器
    protected fun pauseAutoCloseTimer() {
        timerJob?.cancel()
        timerJob = null
        updateJob?.cancel()
        updateJob = null

        if (!isTimerPaused && timerStartTime > 0) {
            // 计算已经过去的时间
            val currentElapsed = SystemClock.elapsedRealtime() - timerStartTime
            elapsedTime += currentElapsed
            isTimerPaused = true
            lastPauseTime = SystemClock.elapsedRealtime()

            // 发送最后一次更新（暂停时的状态）
            val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(getDisplayDuration()).toInt()
            val remainingSeconds = TimeUnit.MILLISECONDS.toSeconds(getRemainingTime()).toInt()
            val progress = if (getDisplayDuration() > 0) {
                elapsedTime.toFloat() / getDisplayDuration()
            } else {
                0f
            }
            onTimerUpdate(remainingSeconds, totalSeconds, progress.coerceIn(0f, 1f))
        }
    }

    // 重启定时器（重置所有计时）
    protected fun restartAutoCloseTimer() {
        elapsedTime = 0L
        isTimerPaused = false
        timerStartTime = 0L
        lastPauseTime = 0L

        if (isFragmentVisible) {
            startOrResumeAutoCloseTimer()
        }
    }

    // 取消定时器（完全重置）
    protected fun cancelAutoCloseTimer() {
        timerJob?.cancel()
        timerJob = null
        updateJob?.cancel()
        updateJob = null
        elapsedTime = 0L
        isTimerPaused = false
        timerStartTime = 0L
        lastPauseTime = 0L
    }

    // 获取剩余时间（毫秒）
    protected fun getRemainingTime(): Long {
        if (!isAutoCloseEnabled()) return 0L

        return if (isTimerPaused) {
            // 暂停状态：剩余时间 = 总时间 - 已过去时间
            maxOf(0L, getDisplayDuration() - elapsedTime)
        } else if (timerStartTime > 0) {
            // 运行状态：剩余时间 = 总时间 - (当前时间 - 开始时间 + 之前已过去时间)
            val currentElapsed = SystemClock.elapsedRealtime() - timerStartTime
            val totalElapsed = elapsedTime + currentElapsed
            maxOf(0L, getDisplayDuration() - totalElapsed)
        } else {
            // 未启动状态
            getDisplayDuration()
        }
    }

    // 获取剩余秒数（整数）
    protected fun getRemainingSeconds(): Int {
        val remainingTime = getRemainingTime()
        return TimeUnit.MILLISECONDS.toSeconds(remainingTime).toInt()
    }

    // 获取总秒数（整数）
    protected fun getTotalSeconds(): Int {
        return TimeUnit.MILLISECONDS.toSeconds(getDisplayDuration()).toInt()
    }

    // 获取当前进度（0.0 - 1.0）
    protected fun getProgress(): Float {
        val remainingTime = getRemainingTime()
        return if (getDisplayDuration() > 0) {
            (getDisplayDuration() - remainingTime).toFloat() / getDisplayDuration()
        } else {
            0f
        }
    }

    // 是否正在计时
    protected fun isTimerRunning(): Boolean {
        return timerJob != null && !isTimerPaused
    }

    // 是否已暂停
    protected fun isTimerPaused(): Boolean {
        return isTimerPaused
    }

    // 获取已过去时间（毫秒）
    protected fun getElapsedTime(): Long {
        return elapsedTime
    }

    // 执行关闭操作
    private fun executeCloseAction() {
        // 发送计时结束回调
        onTimerFinished()

        // 发送最后一次更新（0秒）
        val totalSeconds = getTotalSeconds()
        onTimerUpdate(0, totalSeconds, 1.0f)

        // 执行关闭
        onBeforeClose()
        performCloseAction()
        onAfterClose()
        cancelAutoCloseTimer() // 执行完成后清理
    }

    // ==================== 生命周期方法 ====================

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isViewCreated = true
        activity?.window?.showSystemBar(false)
        // 恢复计时器状态
        if (savedInstanceState != null) {
            elapsedTime = savedInstanceState.getLong("elapsedTime", 0L)
            isTimerPaused = savedInstanceState.getBoolean("isTimerPaused", false)
            timerStartTime = savedInstanceState.getLong("timerStartTime", 0L)

            // 如果是暂停状态，恢复上次暂停的时间戳
            if (isTimerPaused) {
                lastPauseTime = SystemClock.elapsedRealtime()
            }
        }

        // 延迟检查可见性，确保UI已经准备好
        view.post {
            checkAndUpdateVisibility()
        }
    }

    override fun onResume() {
        super.onResume()
        // 确保UI已经准备好后检查可见性
        view?.post {
            checkAndUpdateVisibility()
        }
    }

    override fun onPause() {
        super.onPause()
        // 立即检查可见性（即将不可见）
        checkAndUpdateVisibility()
    }

    override fun onStop() {
        super.onStop()
        // 确保在onStop时检查可见性
        checkAndUpdateVisibility()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        // show/hide状态变化
        isUserVisible = !hidden
        view?.post {
            checkAndUpdateVisibility()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        // ViewPager中的可见性变化
        isUserVisible = isVisibleToUser
        view?.post {
            checkAndUpdateVisibility()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rootView = null
        isViewCreated = false
        isFragmentVisible = false
        cancelAutoCloseTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAutoCloseTimer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存计时器状态，以便在配置变化时恢复
        outState.putLong("elapsedTime", elapsedTime)
        outState.putBoolean("isTimerPaused", isTimerPaused)
        outState.putLong("timerStartTime", timerStartTime)
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取当前是否可见
     */
    protected fun isFragmentVisible(): Boolean {
        return isFragmentVisible
    }
}
