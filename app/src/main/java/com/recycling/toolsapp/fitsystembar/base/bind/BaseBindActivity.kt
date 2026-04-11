package com.recycling.toolsapp.fitsystembar.base.bind

import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.recycling.toolsapp.fitsystembar.base.BaseActivity

/**
 * @author: lr
 * @created on: 2022/7/10 11:14 上午
 * @description:集成DataBinding的Activity的基类
 */
abstract class BaseBindActivity<B : ViewDataBinding> : BaseActivity() {

    protected val simpleBindName: String get() = javaClass.simpleName

    protected  lateinit var binding: B
        private set

    //将layout绑定
    override fun initContentView() {
        injectDataBinding()
    }

    private fun injectDataBinding() {
        binding = DataBindingUtil.setContentView(this, layoutRes())
        binding.lifecycleOwner = this

    }

    override fun onDestroy() {
        binding.unbind()//解绑
        super.onDestroy()
    }
}