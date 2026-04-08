package com.recycling.toolsapps

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.recycling.toolsapps.http.RepoImpl
import com.recycling.toolsapps.model.LogEntity
import com.recycling.toolsapps.nav.NavTouDoubleNewActivity
import com.recycling.toolsapps.nav.NavTouSingleNewActivity
import com.recycling.toolsapps.vm.CabinetVM
import com.serial.port.utils.AppUtils
import com.serial.port.utils.Loge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nearby.lib.netwrok.response.SPreUtil

/***
 * 这里考虑优化启动逻辑处理问题
 */
class StartUiActivity : AppCompatActivity() {
    private val cabinetVM: CabinetVM by viewModels()
    var getCount = 5
    private val httpRepo by lazy { RepoImpl() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_ui)
        //这里http获取业务ID
        CoroutineScope(Dispatchers.Main).launch {
            val init = SPreUtil[AppUtils.getContext(), SPreUtil.init, false] as Boolean
            if (init) {
                getSocketUrl(false)
//                val host = SPreUtil[AppUtils.getContext(), SPreUtil.host, BuildConfig.socketIP] as String
//                val port = SPreUtil[AppUtils.getContext(), SPreUtil.port, BuildConfig.socketPort] as Int
//                Loge.e("出厂配置 initSocket startUI 进入主界面 $host $port")
//                initSocket(host, port)
//                startActivity(Intent(this@StartUiActivity, HomeActivity::class.java))
            } else {
                Loge.e("出厂配置 initSocket startUI 进入初始化")
                startActivity(Intent(this@StartUiActivity, InitFactoryActivity::class.java))
                finish()
            }
        }
//        Loge.e("屏幕尺寸大小 start：${getScreenParams()}")
    }
    /***
     *获取屏幕尺寸和旋转角度
     */
    private fun getScreenParams(): String {
        val displayManager =
           getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val rotation = display.rotation
        val surfaceRotationDegrees = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        val dm = DisplayMetrics()
      windowManager.defaultDisplay.getMetrics(dm)
        val heightPixels = dm.heightPixels
        val widthPixels = dm.widthPixels
        val xdpi = dm.xdpi
        val ydpi = dm.ydpi
        val densityDpi = dm.densityDpi
        val density = dm.density
        val scaledDensity = dm.scaledDensity
        val stringBuffer = StringBuffer()
        val heightDP = heightPixels / density
        val widthDP = widthPixels / density
        stringBuffer.append("屏幕信息")
        stringBuffer.append("\n")
        stringBuffer.append("heightPixels: ${heightPixels}px | widthPixels: ${widthPixels}px | surfaceRotationDegrees: $surfaceRotationDegrees")
        stringBuffer.append("\n")
        stringBuffer.append("xdpi: ${xdpi}dpi | ydpi: ${ydpi}dpi")
        stringBuffer.append("\n")
        stringBuffer.append("densityDpi: ${densityDpi}dpi | density: $density | scaledDensity: $scaledDensity")
        stringBuffer.append("\n")
        stringBuffer.append(
            "heightDP: ${heightDP}dp | widthDP: ${widthDP}dp | 取的dp: ${
                resources.getDimension(
                    R.dimen.dp_25
                )
            } | 取的sp: ${
                resources.getDimension(
                    R.dimen.sp_7
                )
            }"
        )
        return stringBuffer.toString()
    }
    private fun hideLoading(isShow: Boolean, text: String) {
        cabinetVM.mainScope.launch {
            Toast.makeText(AppUtils.getContext(), text, Toast.LENGTH_LONG).show()
//            cpbLoading?.isVisible = isShow
        }
    }


    @SuppressLint("SetTextI18n")
    private fun getSocketUrl(isDelay: Boolean) {
        val postSn = SPreUtil.get(AppUtils.getContext(), SPreUtil.init_sn, "") as String
        cabinetVM.ioScope.launch {
            if (isDelay) {
                delay(20000)
            }
            val from = mutableMapOf<String, Any>()
            from["sn"] = postSn
            val headers = mutableMapOf<String, String>()
            headers["token"] = BuildConfig.initToken
            Loge.e("获取socket连接 from ${Gson().toJson(from)} | headers $headers")
            httpRepo.connectAddress(headers, from).onCompletion {
                Loge.d("出厂配置 onCompletion $headers | $from")
            }.onSuccess { initString ->
                hideLoading(false, "初始化智能回收柜成功")
                initString?.let { url ->
                    val socketUrl = url.split(":")
                    Loge.d("获取socket连接 onSuccess ${Thread.currentThread().name}| $socketUrl |  ${socketUrl.size} ")
//                    initSocket(socketUrl[0], socketUrl[1].toInt())
                    initSocket(BuildConfig.socketIP, BuildConfig.socketPort)
                    cabinetVM.insertInfoLog(LogEntity().apply {
                        cmd = "connectAddress"
                        msg = "获取socket地址成功"
                        time = AppUtils.getDateYMDHMS()
                    })
                    getCount = -1
                }

            }.onFailure { code, message ->
                Loge.d("获取socket连接 onFailure $code $message")
                showText("onCath ${code} ${message}")
                cabinetVM.insertInfoLog(LogEntity().apply {
                    cmd = "connectAddress"
                    msg = "$code,$message"
                    time = AppUtils.getDateYMDHMS()
                })
                repeatedly()
            }.onCatch { e ->
                showText("onCath ${e.errorCode} ${e.errorMsg}")
                hideLoading(false, "获取socket连接 onCatch ${e.errorMsg}")
                cabinetVM.insertInfoLog(LogEntity().apply {
                    cmd = "connectAddress"
                    msg = "${e.errorCode},${e.errorMsg}"
                    time = AppUtils.getDateYMDHMS()
                })
                repeatedly()
            }
        }
    }

    private fun repeatedly() {
        if (getCount > 0) {
            getCount--
            getSocketUrl(true)
        }
    }

    private fun initSocket(
        mHost: String? = BuildConfig.socketIP,
        mPort: Int? = BuildConfig.socketPort,
    ) {
        toGoMain(mHost!!,mPort!!)
    }

    fun toGoMain(
        mHost: String = BuildConfig.socketIP,
        mPort: Int = BuildConfig.socketPort
    ) {
        if (mHost != null) {
            SPreUtil.put(AppUtils.getContext(), SPreUtil.host, mHost)
        }
        if (mPort != null) {
            SPreUtil.put(AppUtils.getContext(), SPreUtil.port, mPort)
        }
        val typeGrid = SPreUtil[AppUtils.getContext(), SPreUtil.type_grid, -1]
        val b = Bundle()
        b.putString("host",mHost)
        b.putInt("port",mPort)
        val intent = Intent()
        intent.putExtras(b)
        when (typeGrid) {
            1, 3 -> {
                intent.setClass(this@StartUiActivity, NavTouSingleNewActivity::class.java)
                startActivity(intent)
            }

            2 -> {
                intent.setClass(this@StartUiActivity, NavTouDoubleNewActivity::class.java)
                startActivity(intent)
            }
        }
        finish()
    }

    private fun showText(text: String) {
        cabinetVM.mainScope.launch {
            Toast.makeText(AppUtils.getContext(), text, Toast.LENGTH_LONG)
                .show()
        }
    }
}