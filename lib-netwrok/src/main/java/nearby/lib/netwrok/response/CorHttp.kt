package nearby.lib.netwrok.response

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import nearby.lib.netwrok.base.HttpClient
import nearby.lib.netwrok.base.HttpClientConfig
import nearby.lib.netwrok.interceptor.HttpLoggingInterceptor
import nearby.lib.netwrok.request.SpecialCallback
import okhttp3.Cache
import okhttp3.Interceptor
import java.io.File
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * @description: -
 * @since: 1.0.0
 */
class CorHttp {
    private val httClient by lazy { HttpClient() }
    private val LOG_TAG = "CorHttp>>>"
    private val LOG_DIVIDER = "||================================================================="
    private var baseIp = "https://app.nearby.ren"
    private var context: Context? = null
    private var currentPath: String? = null
    private var interceptor: Interceptor? = null
    private var callback: SpecialCallback? = null
    private var currentHeaders: Map<String, String> = mapOf(Pair("ren", "nearby"))
    private var tData: Map<String, String> =
            mapOf("code" to "code", "message" to "message", "data" to "data")
    private var codes: ArrayList<Int> = arrayListOf()
    private var cachePath: String? = null
    private var cacheMax: Long? = null
    private var openLog: Boolean = false
    companion object {

        @Volatile
        private var instance: CorHttp? = null

        @JvmStatic fun getInstance() = instance ?: synchronized(this) {
            instance ?: CorHttp().also { instance = it }
        }
    }

    /****
     * @param context 上下文
     * @param openLog 是否打开日志
     * @param baseIp 请求路径 例如 https://app.nearby.ren
     * @param path 二级路径  例如 https://app.nearby.ren/app 二级为 app
     * @param cachePath 缓存路径
     * @param cacheMax 最大缓存
     * @param codes 特殊响应code 处理
     * @param callback 特殊相应code 处理回调
     */
    fun init(
        context: Context,
        openLog: Boolean = false,
        baseIp: String = "https://app.nearby.ren",
        path: String? = null,
        headers: Map<String, String> = mapOf(Pair("ren", "nearby")),
        needToInterceptor: Interceptor? = null,
        cachePath: String = context.cacheDir.toString() + "HttpCache",
        cacheMax: Long? = null,
        codes: ArrayList<Int> = arrayListOf(),
        tData: Map<String, String> = mapOf("code" to "code", "message" to "message", "data" to "data"),
        callback: SpecialCallback? = null,
    ) {
        this.baseIp = baseIp
        this.context = context
        this.currentPath = path
        this.currentHeaders = headers
        this.interceptor = needToInterceptor
        this.tData = tData
        this.codes = codes
        this.callback = callback
        this.cachePath = cachePath
        this.cacheMax = cacheMax
        this.openLog = openLog
        val url = path?.let {
            "${baseIp}/$path/"
        } ?: "$baseIp/"
        val configParams =
                HttpClientConfig.builder().setBaseUrl(url).setTData(tData).setSpecialCallback(callback).setCodes(codes).setCache(Cache(File(cachePath), cacheMax ?: (1024L * 1024 * 100))).openLog(openLog).setGson(Gson()).setLogger(object : HttpLoggingInterceptor.Logger {
                    override fun log(message: String) {
                        if (message.contains("--> END") || message.contains("<-- END")) {
//                            LogUtil.d(message)
//                            LogUtil.e(LOG_DIVIDER)
                            LogUtils.e(LOG_TAG, "||  $message")
                            LogUtils.e(LOG_TAG, LOG_DIVIDER)
                        } else if (message.contains("-->") || message.contains("<--")) {
                            LogUtils.e(LOG_TAG, LOG_DIVIDER)
                            LogUtils.e(LOG_TAG, "||  $message")
//                            LogUtil.e(LOG_DIVIDER)
//                            LogUtil.d(message)
                        } else {
                            LogUtils.e(LOG_TAG, "||  $message")
//                        LogUtil.dJson(message)
                        }
                    }
                }).setHeaders(headers)
        needToInterceptor?.let {
            configParams.addInterceptor(it)
        }
        val config = configParams.build()
        httClient.init(context, config)
    }

    // 新增方法：动态更新 baseUrl
    fun updateBaseUrl(newBaseUrl: String) {
        require(context != null) { "必须先调用 init() 初始化" }

        // 保存到 SharedPreferences
        SPreUtil.put(context, SPreUtil.setting_ip, newBaseUrl)

        // 更新内存中的 baseIp
        this.baseIp = newBaseUrl

        // 重建 HttpClient
        rebuildHttpClient()
    }

    // 私有方法：构建完整 URL
    private fun buildFullUrl(baseIp: String, path: String?): String {
        return path?.let { "$baseIp/$it/" } ?: "$baseIp/"
    }

    // 私有方法：重新初始化 HttpClient
    private fun rebuildHttpClient() {
        require(context != null) { "上下文不可为空" }
        val url = buildFullUrl(baseIp, currentPath)
        val configParams =
                HttpClientConfig.builder().setBaseUrl(url).setTData(tData).setSpecialCallback(callback).setCodes(codes).setCache(Cache(File(cachePath),
                    cacheMax ?: (1024L * 1024 * 100))).openLog(openLog).setGson(Gson()).setLogger(object : HttpLoggingInterceptor.Logger {
                        override fun log(message: String) {
                            if (message.contains("--> END") || message.contains("<-- END")) {
//                            LogUtil.d(message)
//                            LogUtil.e(LOG_DIVIDER)
                                LogUtils.e(LOG_TAG, "||  $message")
                                LogUtils.e(LOG_TAG, LOG_DIVIDER)
                            } else if (message.contains("-->") || message.contains("<--")) {
                                LogUtils.e(LOG_TAG, LOG_DIVIDER)
                                LogUtils.e(LOG_TAG, "||  $message")
//                            LogUtil.e(LOG_DIVIDER)
//                            LogUtil.d(message)
                            } else {
                                LogUtils.e(LOG_TAG, "||  $message")
//                        LogUtil.dJson(message)
                            }
                        }
                    }).setHeaders(currentHeaders)
        interceptor?.let {
            configParams.addInterceptor(it)
        }
        val config = configParams.build()

        httClient.init(context!!, config)
    }

    fun getClient(): HttpClient {
        return httClient
    }

    @JvmOverloads
    suspend fun <T> get(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = httClient.get(url, headers, params, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> getFlow(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = httClient.getFlow(url, headers, params, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> post(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = httClient.postJson(url, headers, params, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> postFlow(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = httClient.postJsonFlow(url, headers, params, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> postForm(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = httClient.postForm(url, headers, params, type, kClazz, isInfoResponse)


    suspend fun <T> postFormFlow(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = httClient.postFormFlow(url, headers, params, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> postJsonArray(url: String, headers: Map<String, String>? = null, params: MutableList<Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = httClient.postJsonArray(url, headers, params, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> postJsonArrayFlow(url: String, headers: Map<String, String>? = null, params: MutableList<Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = httClient.postJsonArrayFlow(url, headers, params, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> postString(url: String, headers: Map<String, String>? = null, content: String? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = httClient.postJsonString(url, headers, content, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> postStringFlow(url: String, headers: Map<String, String>? = null, content: String? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = httClient.postJsonStringFlow(url, headers, content, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> postMultipart(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = httClient.postMultipart(url, headers, params, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> postMultipartFlow(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = httClient.postMultipartFlow(url, headers, params, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> put(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = httClient.put(url, headers, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> putFlow(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = httClient.putFlow(url, headers, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> delete(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = httClient.delete(url, headers, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> deleteFlow(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = httClient.deleteFlow(url, headers, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> head(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = httClient.head(url, headers, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> headFlow(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = httClient.headFlow(url, headers, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> options(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = httClient.options(url, headers, type, kClazz, isInfoResponse)

    @JvmOverloads
    suspend fun <T> optionsFlow(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = httClient.optionsFlow(url, headers, type, kClazz, isInfoResponse)
}