package com.recycling.toolsapp.utils



import androidx.fragment.app.FragmentActivity
import com.serial.port.utils.Loge


internal typealias Fun = () -> Unit
internal typealias ShowRationaleFun = (PermissionRequest) -> Unit

/**
 * 弹窗弹出时，PermissionRequest 作为参数传递给弹窗，以便链接到后续的流程
 */
interface PermissionRequest {
    fun proceed()
    fun cancel()
}

internal class KtxPermissionRequest(
    private val requestPermission: Fun,
    private val permissionDenied: Fun?
) : PermissionRequest {
    override fun proceed() {
        requestPermission.invoke()
    }

    override fun cancel() {
        permissionDenied?.invoke()
    }

    companion object {
        fun create(onPermissionDenied: Fun?, requestPermission: Fun) = KtxPermissionRequest(
            requestPermission = requestPermission,
            permissionDenied = onPermissionDenied
        )
    }
}

/**
 * 包装回调，打开代理 fragment 进行权限请求
 */
class PermissionsRequester(
    vararg val permissions: String,
    private val activity: FragmentActivity,
    private val onShowRationale: ShowRationaleFun?,
    private val onPermissionDenied: Fun?,
    private val requiresPermission: Fun,
    onNeverAskAgain: Fun?,
) {
    private val requestFun: Fun = {
        activity.supportFragmentManager
            .beginTransaction()
            .replace(
                android.R.id.content,
                PermissionRequestFragment.NormalRequestPermissionFragment.newInstance(permissions)
            )
            .commitAllowingStateLoss()
    }

    init {
        PermissionRequestFragment.permissionRequestWrapper = object : PermissionResultInterface {
            override fun requiresPermission() {
                Loge.d("权限申请 init requiresPermission")
                requiresPermission.invoke()
            }

            override fun onPermissionDenied(key: Array<String>, grantResults: IntArray) {
                Loge.d("权限申请 init onPermissionDenied")
                onPermissionDenied?.invoke()
            }

            override fun onNeverAskAgain(key: Array<String>, grantResults: IntArray) {
                Loge.d("权限申请 init onNeverAskAgain")
                onNeverAskAgain?.invoke()
            }
        }
    }

    fun launch() {
        try {
            Loge.d("权限申请 launch $onShowRationale ")

            if (PermissionUtils.hasSelfPermissions(activity, *permissions)) {
                Loge.d("权限申请 launch if onNeverAskAgain")
                requiresPermission()
            } else {
                if (PermissionUtils.shouldShowRequestPermissionRationale(activity, *permissions) && onShowRationale != null) {
                    Loge.d("权限申请 launch else if")
                    onShowRationale.invoke(KtxPermissionRequest.create(onPermissionDenied, requestFun))
                } else {
                    Loge.d("权限申请 launch else else")
                    requestFun.invoke()
                }
            }
        }catch ( e :Exception){
            e.printStackTrace()
        }

    }
}