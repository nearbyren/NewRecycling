package com.recycling.toolsapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build

import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NetworkStateMonitor(context: Context) {

    private val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var lastNetworkState: Boolean? = null
    private var debounceHandler = Handler(Looper.getMainLooper())

    @RequiresApi(Build.VERSION_CODES.M)
    private val debounceRunnable = Runnable { checkNetworkState() }

    // 使用 StateFlow 提供响应式状态更新
    private val _networkState = MutableStateFlow(false)
    val networkState: StateFlow<Boolean> get() = _networkState

    // 监听器回调（兼容旧用法）
    private var networkStateListener: ((isConnected: Boolean) -> Unit)? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        // 网络连接时触发（可能还未完全就绪）
        @RequiresApi(Build.VERSION_CODES.M) override fun onAvailable(network: Network) {
            debounceStateCheck()
        }

        // 网络断开时触发
        @RequiresApi(Build.VERSION_CODES.M) override fun onLost(network: Network) {
            debounceStateCheck()
        }

        // 网络能力变化时触发（最准确的状态）
        @RequiresApi(Build.VERSION_CODES.M) override fun onCapabilitiesChanged(
            network: Network,
            capabilities: NetworkCapabilities,
        ) {
            debounceStateCheck()
        }

        // 网络断开连接时触发（更早的事件）
        @RequiresApi(Build.VERSION_CODES.M) override fun onUnavailable() {
            debounceStateCheck()
        }
    }

    @RequiresApi(Build.VERSION_CODES.M) @SuppressLint("MissingPermission") fun register() {

        val networkRequest =
                NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        }

        // 初始状态检查
        debounceStateCheck()
    }

    fun addNetworkStateListener(listener: ((Boolean) -> Unit)? = null) {
        networkStateListener = listener
    }

    @RequiresApi(Build.VERSION_CODES.M) fun unregister() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        debounceHandler.removeCallbacks(debounceRunnable)
        networkStateListener = null
    }

    // 防抖处理：避免短时间内多次状态变化
    @RequiresApi(Build.VERSION_CODES.M) private fun debounceStateCheck(delay: Long = 500) {
        debounceHandler.removeCallbacks(debounceRunnable)
        debounceHandler.postDelayed(debounceRunnable, delay)
    }

    // 实际检查网络状态
    @RequiresApi(Build.VERSION_CODES.M) @SuppressLint("MissingPermission")
    private fun checkNetworkState() {
        val activeNetwork = connectivityManager.activeNetwork ?: run {
            updateState(false)
            return
        }

        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: run {
            updateState(false)
            return
        }

        val isInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val isConnected = isInternet && isValidated

        updateState(isConnected)
    }

    // 更新状态（避免重复通知）
    private fun updateState(isConnected: Boolean) {
        if (lastNetworkState == isConnected) return

        lastNetworkState = isConnected
        _networkState.value = isConnected
        networkStateListener?.invoke(isConnected)
    }
}