package com.recycling.toolsapp

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.Gson
import com.recycling.toolsapp.http.RepoImpl
import com.recycling.toolsapp.model.LogEntity
import com.recycling.toolsapp.nav.NavTouDoubleActivity
import com.recycling.toolsapp.nav.NavTouSingleActivity
import com.recycling.toolsapp.vm.CabinetVM
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_ui)
        // 1. 开始观察跳转信号
        observeNavigation()
        Loge.d("UI层：收到跳转信号 ->，Hash: ${this.hashCode()}")
        // 2. 初始业务检查
        val init = SPreUtil[AppUtils.getContext(), SPreUtil.init, false] as Boolean
        if (init) {
            cabinetVM.getSocketUrl(false)
        } else {
            startActivity(Intent(this, InitFactoryActivity::class.java))
            finish()
        }
    }

    private fun observeNavigation() {
        lifecycleScope.launch {
            // 使用 repeatOnLifecycle 确保只有在前台时处理跳转
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                cabinetVM.navigationEvent.collect { (host, port) ->
                    Loge.e("UI层：收到跳转信号 -> $host $port")
                    // 这里的代码只会由 VM 的 emit 触发一次
                    toGoMain(host, port)
                }
            }
        }
    }

    private fun toGoMain(mHost: String, mPort: Int) {
        Loge.e("UI层：收到跳转信号 -> $mHost $mPort")

        // 保存配置
        SPreUtil.put(AppUtils.getContext(), SPreUtil.host, mHost)
        SPreUtil.put(AppUtils.getContext(), SPreUtil.port, mPort)

        val typeGrid = SPreUtil[AppUtils.getContext(), SPreUtil.type_grid, -1] as Int
        val intent = Intent().apply {
            putExtras(Bundle().apply {
                putString("host", mHost)
                putInt("port", mPort)
            })
        }

        when (typeGrid) {
            1, 3 -> intent.setClass(this, NavTouSingleActivity::class.java)
            2 -> intent.setClass(this, NavTouDoubleActivity::class.java)
            else -> { /* 处理未知类型 */ return
            }
        }

        startActivity(intent)
        finish()
    }

}