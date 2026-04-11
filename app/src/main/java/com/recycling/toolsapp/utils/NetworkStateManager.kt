package com.recycling.toolsapp.utils

/**
 * @author: lr
 * @created on: 2025/11/12 上午10:48
 * @description:
 */
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow



/**
 * 网络状态管理器
 */
class NetworkStateManager private constructor(private val context: Context) {
  /**
   * 网络质量枚举
   */
  enum class NetworkQuality {
    UNKNOWN,    // 未知
    POOR,       // 信号差 - 基本无法使用
    FAIR,       // 一般 - 可以基本使用但可能较慢
    GOOD,       // 良好 - 正常使用
    EXCELLENT   // 优秀 - 网络状况很好
  }

  /**
   * 网络状态密封类
   */
  sealed class NetworkState {
    object Unknown : NetworkState() {
      override fun toString(): String = "Unknown"
    }

    object Disconnected : NetworkState() {
      override fun toString(): String = "Disconnected"
    }

    data class Connected(
      val type: ConnectionType,
      val isMetered2: Boolean = false,
      val hasInternet: Boolean = true
    ) : NetworkState() {
      override fun toString(): String {
        return "Connected(type=$type, isMetered2=$isMetered2, hasInternet=$hasInternet)"
      }
    }

    /**
     * 扩展属性：检查是否已连接
     */
    val isConnected: Boolean
      get() = this is Connected

    /**
     * 扩展属性：获取连接类型
     */
    val connectionType: ConnectionType?
      get() = if (this is Connected) type else null

    /**
     * 扩展属性：检查是否有互联网访问
     */
    val hasInternetAccess: Boolean
      get() = when (this) {
        is Connected -> hasInternet
        else -> false
      }

    /**
     * 扩展属性：检查是否为计量网络
     */
    val isMetered: Boolean
      get() = when (this) {
        is Connected -> isMetered
        else -> false
      }
  }

  /**
   * 连接类型枚举
   */
  enum class ConnectionType {
    WIFI {
      override fun toString(): String = "WiFi"
      override fun getDisplayName(): String = "WiFi网络"
    },
    CELLULAR {
      override fun toString(): String = "Cellular"
      override fun getDisplayName(): String = "移动网络"
    },
    ETHERNET {
      override fun toString(): String = "Ethernet"
      override fun getDisplayName(): String = "有线网络"
    },
    VPN {
      override fun toString(): String = "VPN"
      override fun getDisplayName(): String = "VPN网络"
    },
    BLUETOOTH {
      override fun toString(): String = "Bluetooth"
      override fun getDisplayName(): String = "蓝牙网络"
    },
    OTHER {
      override fun toString(): String = "Other"
      override fun getDisplayName(): String = "其他网络"
    };

    abstract fun getDisplayName(): String
  }

  /**
   * 网络状态变化事件
   */
  data class NetworkStateChangeEvent(
    val previousState: NetworkState,
    val currentState: NetworkState,
    val timestamp: Long = System.currentTimeMillis()
  ) {
    val isConnectionLost: Boolean
      get() = previousState.isConnected && !currentState.isConnected

    val isConnectionRestored: Boolean
      get() = !previousState.isConnected && currentState.isConnected

    val connectionTypeChanged: Boolean
      get() = previousState.connectionType != currentState.connectionType
  }
  companion object {
    @Volatile
    private var instance: NetworkStateManager? = null

    fun getInstance(context: Context): NetworkStateManager {
      return instance ?: synchronized(this) {
        instance ?: NetworkStateManager(context.applicationContext).also { instance = it }
      }
    }
  }

  private val connectivityManager by lazy {
    context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
  }

  // 网络状态流
  private val _networkState = MutableStateFlow<NetworkState>(NetworkState.Unknown)
  val networkState: StateFlow<NetworkState> = _networkState.asStateFlow()

  // 网络质量流
  private val _networkQuality = MutableStateFlow(NetworkQuality.UNKNOWN)
  val networkQuality: StateFlow<NetworkQuality> = _networkQuality.asStateFlow()

  // 网络状态变化事件流
  private val _networkStateChangeEvents = MutableStateFlow<NetworkStateChangeEvent?>(null)
  val networkStateChangeEvents: StateFlow<NetworkStateChangeEvent?> = _networkStateChangeEvents.asStateFlow()

  // 网络回调
  private var networkCallback: ConnectivityManager.NetworkCallback? = null

  // 是否正在监听
  private var isMonitoring = false

  /**
   * 开始监听网络状态
   */
  fun startMonitoring() {
    if (isMonitoring) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      registerNetworkCallback()
    } else {
      startLegacyMonitoring()
    }

    isMonitoring = true
    updateNetworkState() // 初始状态更新
  }

  /**
   * 停止监听网络状态
   */
  fun stopMonitoring() {
    if (!isMonitoring) return

    networkCallback?.let {
      try {
        connectivityManager.unregisterNetworkCallback(it)
      } catch (e: Exception) {
        // 忽略取消注册时的异常
      }
      networkCallback = null
    }

    isMonitoring = false
  }

  /**
   * 注册网络回调 (API 24+)
   */
  @RequiresApi(Build.VERSION_CODES.N)
  private fun registerNetworkCallback() {
    val networkRequest = NetworkRequest.Builder()
      .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
      .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
      .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
      .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
      .addTransportType(NetworkCapabilities.TRANSPORT_BLUETOOTH)
      .build()

    networkCallback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        val previousState = _networkState.value
        updateNetworkState()
        checkNetworkQuality()
        emitStateChangeEvent(previousState, _networkState.value)
      }

      override fun onLost(network: Network) {
        val previousState = _networkState.value
        updateNetworkState()
        _networkQuality.value = NetworkQuality.UNKNOWN
        emitStateChangeEvent(previousState, _networkState.value)
      }

      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
      ) {
        val previousState = _networkState.value
        updateNetworkState()
        checkNetworkQuality(networkCapabilities)
        emitStateChangeEvent(previousState, _networkState.value)
      }

      override fun onUnavailable() {
        val previousState = _networkState.value
        _networkState.value = NetworkState.Disconnected
        _networkQuality.value = NetworkQuality.UNKNOWN
        emitStateChangeEvent(previousState, _networkState.value)
      }
    }

    networkCallback?.let {
      try {
        connectivityManager.registerNetworkCallback(networkRequest, it)
      } catch (e: Exception) {
        // 处理注册失败的情况
        _networkState.value = NetworkState.Disconnected
      }
    }
  }

  /**
   * 传统监听方式 (API < 24)
   */
  private fun startLegacyMonitoring() {
    // 对于低版本Android，使用轮询方式
    // 在实际应用中可以使用 WorkManager 或 Handler 进行定期检查
    updateNetworkState()
  }

  /**
   * 更新网络状态
   */
  fun updateNetworkState() {
    val previousState = _networkState.value

    try {
      val activeNetwork = connectivityManager.activeNetwork
      val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

      val state = when {
        capabilities == null -> NetworkState.Disconnected
        else -> {
          val connectionType = getConnectionType(capabilities)
          val isMetered = isMeteredConnection(capabilities)
          val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

          NetworkState.Connected(
            type = connectionType,
            isMetered2 = isMetered,
            hasInternet = hasInternet
          )
        }
      }

      _networkState.value = state
      emitStateChangeEvent(previousState, state)

    } catch (e: Exception) {
      _networkState.value = NetworkState.Disconnected
      emitStateChangeEvent(previousState, NetworkState.Disconnected)
    }
  }

  /**
   * 发射状态变化事件
   */
  private fun emitStateChangeEvent(previousState: NetworkState, currentState: NetworkState) {
    if (previousState != currentState) {
      _networkStateChangeEvents.value = NetworkStateChangeEvent(
        previousState = previousState,
        currentState = currentState
      )
    }
  }

  /**
   * 获取连接类型
   */
  private fun getConnectionType(capabilities: NetworkCapabilities): ConnectionType {
    return when {
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> ConnectionType.BLUETOOTH
      else -> ConnectionType.OTHER
    }
  }

  /**
   * 检查是否为计量连接
   */
  private fun isMeteredConnection(capabilities: NetworkCapabilities): Boolean {
    return when {
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
        // 检查WiFi是否被标记为计量网络
        !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
      }
      else -> false
    }
  }

  /**
   * 检查网络质量
   */
  private fun checkNetworkQuality(capabilities: NetworkCapabilities? = null) {
    try {
      val currentCapabilities = capabilities ?:
      connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)

      val quality = when {
        currentCapabilities == null -> NetworkQuality.UNKNOWN
        currentCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
          // WiFi网络通常质量较好
          NetworkQuality.GOOD
        }
        currentCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
          // 移动网络质量需要进一步检测
          detectCellularNetworkQuality()
        }
        currentCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> {
          NetworkQuality.GOOD
        }
        else -> NetworkQuality.FAIR
      }

      _networkQuality.value = quality

    } catch (e: Exception) {
      _networkQuality.value = NetworkQuality.UNKNOWN
    }
  }

  /**
   * 检测移动网络质量
   */
  private fun detectCellularNetworkQuality(): NetworkQuality {
    return try {
      // 这里可以添加更复杂的网络质量检测逻辑
      // 例如：信号强度、网络延迟等
      // 暂时返回一般质量
      NetworkQuality.FAIR
    } catch (e: Exception) {
      NetworkQuality.UNKNOWN
    }
  }

  /**
   * 获取当前网络状态
   */
  fun getCurrentNetworkState(): NetworkState = _networkState.value

  /**
   * 检查网络是否可用
   */
  fun isNetworkAvailable(): Boolean {
    return when (val state = _networkState.value) {
      is NetworkState.Connected -> state.hasInternet
      else -> false
    }
  }

  /**
   * 检查是否为WiFi连接
   */
  fun isWifiConnected(): Boolean {
    return _networkState.value.connectionType == ConnectionType.WIFI
  }

  /**
   * 检查是否为移动数据连接
   */
  fun isCellularConnected(): Boolean {
    return _networkState.value.connectionType == ConnectionType.CELLULAR
  }

  /**
   * 检查是否为计量网络（需要节省流量）
   */
  fun isMeteredNetwork(): Boolean {
    return when (val state = _networkState.value) {
      is NetworkState.Connected -> state.isMetered
      else -> false
    }
  }

  /**
   * 获取网络状态描述
   */
  fun getNetworkStateDescription(): String {
    return when (val state = _networkState.value) {
      NetworkState.Unknown -> "网络状态未知"
      NetworkState.Disconnected -> "网络未连接"
      is NetworkState.Connected -> {
        val type = state.type.getDisplayName()
        val metered = if (state.isMetered) "（计量网络）" else "（非计量网络）"
        val internet = if (state.hasInternet) "可访问互联网" else "无法访问互联网"
        "$type$metered - $internet"
      }
    }
  }

  /**
   * 获取网络质量描述
   */
  fun getNetworkQualityDescription(): String {
    return when (_networkQuality.value) {
      NetworkQuality.UNKNOWN -> "网络质量未知"
      NetworkQuality.POOR -> "网络质量较差"
      NetworkQuality.FAIR -> "网络质量一般"
      NetworkQuality.GOOD -> "网络质量良好"
      NetworkQuality.EXCELLENT -> "网络质量优秀"
    }
  }

  /**
   * 清空状态变化事件（用于消费后清除）
   */
  fun clearStateChangeEvent() {
    _networkStateChangeEvents.value = null
  }
}