package com.recycling.toolsapps.fitsystembar.base.bind

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

abstract class BaseBindLazyTimeFragment<B : ViewDataBinding> : BaseLazyTimedFragment() {

    private var _binding: B? = null
    private var savedState: Bundle? = null
    private var isBindingInitialized = false

    protected lateinit var binding: B
        private set


    // 绑定创建回调
    protected open fun onBindingCreated(binding: B, savedInstanceState: Bundle?) {
        // 默认空实现
    }

    // 绑定销毁回调
    protected open fun onBindingDestroyed() {
        // 默认空实现
    }

    // View创建后的初始化
//    protected open fun initialize(savedInstanceState: Bundle?) {
//         默认空实现
//    }
    // 是否使用DataBinding的自动生命周期管理
    protected open fun useDataBindingLifecycle(): Boolean = true

    // 是否在onDestroyView时自动解绑
    protected open fun autoUnbindInDestroyView(): Boolean = true

    // 是否保存和恢复绑定状态
    protected open fun shouldSaveBindingState(): Boolean = true

    override fun onAttach(context: Context) {
        super.onAttach(context)
        // 监听Fragment的生命周期变化
        lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                when (event) {
                    Lifecycle.Event.ON_START -> onFragmentStart()
                    Lifecycle.Event.ON_STOP -> onFragmentStop()
                    Lifecycle.Event.ON_DESTROY -> {
                        // 确保在Fragment销毁时清理
                        ensureBindingCleanup()
                        source.lifecycle.removeObserver(this)
                    }

                    else -> {}
                }
            }
        })
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        // 如果已经有绑定的视图且父容器相同，直接复用
        val existingView = getRootView()
        if (existingView != null && existingView.parent == container) {
            return existingView
        }

        // 保存状态以便后续恢复
        if (savedInstanceState != null) {
            savedState = savedInstanceState
        }

        // 创建绑定
        try {
            _binding = DataBindingUtil.inflate(inflater, layoutRes(), container, false)
        } catch (e: Exception) {
            // 处理布局资源找不到的情况
            throw RuntimeException("Failed to inflate layout resource: ${layoutRes()}", e)
        }

        binding = _binding ?: return null

        // 设置生命周期所有者（可选）
        if (useDataBindingLifecycle()) {
            binding.lifecycleOwner = viewLifecycleOwner
        }

        // 应用保存的状态
//        if (shouldSaveBindingState() && savedState != null) {
//            binding.restoreFromSavedState(savedState)
//        }

        // 标记绑定已初始化
        isBindingInitialized = true

        // 调用绑定创建回调
        onBindingCreated(binding, savedState ?: savedInstanceState)

        // 初始化
        initialize(savedState ?: savedInstanceState)

        // 设置根视图
        setRootView(binding.root)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 确保绑定完成后执行额外初始化
        if (isBindingInitialized) {
            onViewCreatedWithBinding()
        }
    }

    // 绑定完成后的额外初始化（子类可重写）
    protected open fun onViewCreatedWithBinding() {
        // 默认空实现
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // 保存绑定状态
        if (shouldSaveBindingState()) {
            _binding?.let { binding ->
                savedState = Bundle()
//                binding.saveToSavedState(savedState!!)
                outState.putBundle("binding_state", savedState)
            }
        }
        // 保存其他自定义状态
        outState.putBoolean("is_binding_initialized", isBindingInitialized)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        // 恢复绑定状态
        if (shouldSaveBindingState()) {
            savedInstanceState?.getBundle("binding_state")?.let { state ->
                savedState = state
//                _binding?.restoreFromSavedState(state)
            }
        }

        // 恢复绑定初始化状态
        isBindingInitialized =
            savedInstanceState?.getBoolean("is_binding_initialized", false) ?: false
    }

    override fun onDestroyView() {
        // 调用绑定销毁回调
        if (isBindingInitialized) {
            onBindingDestroyed()
        }

        // 清理绑定
        if (autoUnbindInDestroyView()) {
            cleanupBinding()
        }

        super.onDestroyView()
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保绑定被清理
        ensureBindingCleanup()
    }

    // 安全的绑定访问方法
    protected fun withBinding(block: (B) -> Unit) {
        _binding?.let { block(it) }
    }

    // 安全的绑定访问方法（带返回值）
    protected fun <T> withBinding(block: (B) -> T): T? {
        return _binding?.let { block(it) }
    }

    // 检查绑定是否可用
    protected fun isBindingAvailable(): Boolean {
        return _binding != null && isBindingInitialized && view != null && view?.windowToken != null
    }

    // 重新绑定视图（用于配置变化等情况）
    protected fun rebindView(inflater: LayoutInflater, container: ViewGroup?) {
        cleanupBinding()
        onCreateView(inflater, container, null)
    }

    // 清理绑定
    private fun cleanupBinding() {
        _binding?.let {
            try {
                it.unbind()
            } catch (e: Exception) {
                // 忽略解绑时的异常
            }
        }
        _binding = null
        isBindingInitialized = false
    }

    // 确保绑定清理
    private fun ensureBindingCleanup() {
        if (_binding != null) {
            cleanupBinding()
        }
    }

    // Fragment生命周期回调（子类可重写）
    protected open fun onFragmentStart() {
        // 默认空实现
    }

    protected open fun onFragmentStop() {
        // 默认空实现
    }

    // 提供扩展方法，方便子类使用
    protected fun bindVariable(variableId: Int, value: Any?) {
        withBinding { it.setVariable(variableId, value) }
    }

    protected fun executePendingBindings() {
        withBinding { it.executePendingBindings() }
    }

    // 兼容旧版本的方法（保持向后兼容）
    protected open fun injectDataBinding(inflater: LayoutInflater, container: ViewGroup?) {
        // 默认实现，调用新的绑定创建逻辑
        onCreateView(inflater, container, null)
    }
}