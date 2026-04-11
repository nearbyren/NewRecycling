package com.recycling.toolsapp.utils


import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import java.util.Random

/**
 * 权限请求结果的回调
 */
interface PermissionResultInterface {
    fun requiresPermission()
    fun onPermissionDenied(key: Array<String>, grantResults: IntArray)
    fun onNeverAskAgain(key: Array<String>, grantResults: IntArray)
}

/**
 * 使用代理 fragment，统一处理来自 act/frg 的请求
 */
internal sealed class PermissionRequestFragment : Fragment() {
    protected val requestCode = Random().nextInt(1000)

    companion object {
        var permissionRequestWrapper: PermissionResultInterface? = null
    }

    protected fun dismiss() = fragmentManager?.beginTransaction()?.remove(this)?.commitAllowingStateLoss()

    internal class NormalRequestPermissionFragment : PermissionRequestFragment() {
        private val TAG = PermissionRequestFragment::class.java.simpleName

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            val permissions = arguments?.getStringArray(BUNDLE_PERMISSIONS_KEY) ?: return
            requestPermissions(permissions, requestCode)
        }

        override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            if (requestCode == this.requestCode) {
                if (PermissionUtils.verifyPermissions(*grantResults)) {
                    permissionRequestWrapper?.requiresPermission()
                } else {
                    if (!PermissionUtils.shouldShowRequestPermissionRationale(this, *permissions)) {
                        permissionRequestWrapper?.onNeverAskAgain(permissions, grantResults)
                    } else {
                        permissionRequestWrapper?.onPermissionDenied(permissions, grantResults)
                    }
                }
            }
            dismiss()
        }

        override fun onDestroy() {
            super.onDestroy()
            Log.i(TAG, "======> NormalRequestPermissionFragment onDestroy");
        }

        companion object {
            const val BUNDLE_PERMISSIONS_KEY = "key:permissions"

            fun newInstance(permissions: Array<out String>) = NormalRequestPermissionFragment().apply {
                arguments = Bundle().apply {
                    putStringArray(BUNDLE_PERMISSIONS_KEY, permissions)
                }
            }
        }
    }

}