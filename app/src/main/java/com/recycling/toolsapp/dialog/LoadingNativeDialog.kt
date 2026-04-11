package com.recycling.toolsapp.dialog

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.recycling.toolsapp.R
import com.recycling.toolsapp.databinding.LoadingNaviteDialogBinding


/**
 * @description: 加载弹框
 * @since: 1.0.0原生
 */
class LoadingNativeDialog : BaseBindDialogFragment<LoadingNaviteDialogBinding>() {
    private var cancelAction: (() -> Unit)? = null

    override fun getDialogStyle(): Int {
        return STYLE_NO_FRAME
    }

    override fun getDialogTheme(): Int {
        return R.style.lib_uikit_TransparentDialog
    }

    override fun getLayoutId(): Int {
        return R.layout.loading_navite_dialog
    }

    override fun initialize(view: View, savedInstanceState: Bundle?) {
//        dialog?.setCancelable(false)
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.setOnCancelListener { cancelAction?.invoke() }
    }

    override fun show(activity: FragmentActivity, tag: String?) {
        super.show(activity, tag)
        binding.loading.show()
    }

    override fun show(fragment: Fragment, tag: String?) {
        super.show(fragment, tag)
        binding.loading.show()
    }

    override fun dismiss() {
        binding.loading.hide()
        dialog?.setOnCancelListener(null)
        super.dismiss()
    }

    override fun dismissAllowingStateLoss() {
        binding.loading.hide()
        dialog?.setOnCancelListener(null)
        super.dismissAllowingStateLoss()
    }

    fun setCancelAction(action: (() -> Unit)? = null) {
        this.cancelAction = action
    }
}