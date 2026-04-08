package nearby.lib.netwrok.base

import android.annotation.SuppressLint
import android.content.Context
import android.net.ParseException
import android.webkit.MimeTypeMap
import com.alibaba.fastjson.JSONArray
import com.alibaba.fastjson.JSONObject
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import nearby.lib.netwrok.request.RequestService
import nearby.lib.netwrok.response.HttpError
import nearby.lib.netwrok.response.InfoResponse
import nearby.lib.netwrok.response.ResponseHolder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import org.json.JSONException
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.lang.reflect.Type
import java.net.ConnectException
import java.util.concurrent.CancellationException
import kotlin.reflect.KClass

/**
 * @description: Http客户端
 * @since: 1.0.0
 */
open class HttpClient : HttpClientBase() {

    fun init(context: Context, httpClientConfig: HttpClientConfig) {
        initialize(context, httpClientConfig)
    }

    /**
     * Http Get
     */
    @JvmOverloads
    suspend fun <T> get(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> {
        var ct = mapOf<String, String>()
        if (!params.isNullOrEmpty()) {
            val json = getGson().toJson(params)
            val typeToken = object : TypeToken<Map<String, String>>() {}.type
            ct = getGson().fromJson(json, typeToken)
        }
        return request(type, kClazz, isInfoResponse) {
            it.get(url, headers ?: mapOf(), ct)
        }
    }

    /**
     * Http Get
     */
    @JvmOverloads
    suspend fun <T> getFlow(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> {
        var ct = mapOf<String, String>()
        if (!params.isNullOrEmpty()) {
            val json = getGson().toJson(params)
            val typeToken = object : TypeToken<Map<String, String>>() {}.type
            ct = getGson().fromJson(json, typeToken)
        }
        return requestFlow(type, kClazz, isInfoResponse) {
            it.get(url, headers ?: mapOf(), ct)
        }
    }

    /**
     * Http Post
     * Data post in form
     */
    @JvmOverloads
    suspend fun <T> postForm(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> {
        var ct = mapOf<String, String>()
        if (!params.isNullOrEmpty()) {
            val json = getGson().toJson(params)
            val typeToken = object : TypeToken<Map<String, String>>() {}.type
            ct = getGson().fromJson(json, typeToken)
        }
        return request(type, kClazz, isInfoResponse) {
            it.postForm(url, headers ?: mapOf(), ct)
        }
    }

    /**
     * Http Post
     * Data post in form
     */
    @JvmOverloads
    suspend fun <T> postFormFlow(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> {
        var ct = mapOf<String, String>()
        if (!params.isNullOrEmpty()) {
            val json = getGson().toJson(params)
            val typeToken = object : TypeToken<Map<String, String>>() {}.type
            ct = getGson().fromJson(json, typeToken)
        }
        return requestFlow(type, kClazz, isInfoResponse) {
            it.postForm(url, headers ?: mapOf(), ct)
        }
    }

    /**
     * Http Post
     * Data post in json
     */
    @JvmOverloads
    suspend fun <T> postJson(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> {
        var ct = ""
        if (!params.isNullOrEmpty()) {
            ct = getGson().toJson(params)
        }
        return postJsonString(url, headers, ct, type, kClazz, isInfoResponse)
    }

    /**
     * Http Post
     * Data post in json
     */
    @JvmOverloads
    suspend fun <T> postJsonFlow(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> {
        var ct = ""
        if (!params.isNullOrEmpty()) {
            ct = getGson().toJson(params)
        }
        return postJsonStringFlow(url, headers, ct, type, kClazz, isInfoResponse)
    }

    /**
     * Http Post array
     * Data post in json
     */
    @JvmOverloads
    suspend fun <T> postJsonArray(url: String, headers: Map<String, String>? = null, params: MutableList<Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> {
        var ct = ""
        if (!params.isNullOrEmpty()) {
            ct = getGson().toJson(params)
        }
        return postJsonString(url, headers, ct, type, kClazz, isInfoResponse)
    }

    /**
     * Http Post array
     * Data post in json
     */
    @JvmOverloads
    suspend fun <T> postJsonArrayFlow(url: String, headers: Map<String, String>? = null, params: MutableList<Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> {
        var ct = ""
        if (!params.isNullOrEmpty()) {
            ct = getGson().toJson(params)
        }
        return postJsonStringFlow(url, headers, ct, type, kClazz, isInfoResponse)
    }

    @JvmOverloads
    suspend fun <T> postJsonString(url: String, headers: Map<String, String>? = null, content: String? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = request(type, kClazz, isInfoResponse) {
        it.postJson(url, headers ?: mapOf(), content ?: "")
    }

    @JvmOverloads
    suspend fun <T> postJsonStringFlow(url: String, headers: Map<String, String>? = null, content: String? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = requestFlow(type, kClazz, isInfoResponse) {
        it.postJson(url, headers ?: mapOf(), content ?: "")
    }

    /**
     * Support Multipart body for POST
     */
    @JvmOverloads
    suspend fun <T> postMultipart(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = request(type, kClazz, isInfoResponse) {
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        params?.forEach { params ->
            if (params.value is File) {
                val file = params.value as File
                var mimeType =
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(file.absolutePath))
                if (mimeType.isNullOrBlank()) {
                    mimeType = "file/*"
                }
                mb.addFormDataPart(params.key, file.name, file.asRequestBody(mimeType.toMediaTypeOrNull()))
            } else {
                mb.addFormDataPart(params.key, params.value.toString())
            }
        }
        it.postOrigin(url, headers ?: mapOf(), mb.build())
    }

    /**
     * Support Multipart body for POST
     */
    @JvmOverloads
    suspend fun <T> postMultipartFlow(url: String, headers: Map<String, String>? = null, params: Map<String, Any>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = requestFlow(type, kClazz, isInfoResponse) {
        val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
        params?.forEach { params ->
            if (params.value is File) {
                val file = params.value as File
                var mimeType =
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(file.absolutePath))
                if (mimeType.isNullOrBlank()) {
                    mimeType = "file/*"
                }
                mb.addFormDataPart(params.key, file.name, file.asRequestBody(mimeType.toMediaTypeOrNull()))
            } else {
                mb.addFormDataPart(params.key, params.value.toString())
            }
        }
        it.postOrigin(url, headers ?: mapOf(), mb.build())
    }

    @JvmOverloads
    suspend fun <T> put(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = request(type, kClazz, isInfoResponse) { it.put(url, headers ?: mapOf()) }

    @JvmOverloads
    suspend fun <T> putFlow(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = requestFlow(type, kClazz, isInfoResponse) { it.put(url, headers ?: mapOf()) }

    @JvmOverloads
    suspend fun <T> delete(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = request(type, kClazz, isInfoResponse) { it.delete(url, headers ?: mapOf()) }

    @JvmOverloads
    suspend fun <T> deleteFlow(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = requestFlow(type, kClazz, isInfoResponse) { it.delete(url, headers ?: mapOf()) }

    @JvmOverloads
    suspend fun <T> head(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = request(type, kClazz, isInfoResponse) { it.head(url, headers ?: mapOf()) }

    @JvmOverloads
    suspend fun <T> headFlow(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = requestFlow(type, kClazz, isInfoResponse) { it.head(url, headers ?: mapOf()) }

    @JvmOverloads
    suspend fun <T> options(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> = request(type, kClazz, isInfoResponse) { it.options(url, headers ?: mapOf()) }

    @JvmOverloads
    suspend fun <T> optionsFlow(url: String, headers: Map<String, String>? = null, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): Flow<ResponseHolder<T>> = requestFlow(type, kClazz, isInfoResponse) { it.options(url, headers ?: mapOf()) }

    suspend fun downloadFile(url: String): Response<ResponseBody>? {
        return try {
            getRequestService().downloadFile(url)
        } catch (cause: Throwable) {
            null
        }
    }

    /**
     * 建议使用此方法发起网络请求
     * 因为协程中出现异常时，会直接抛出异常，所以使用try...catch方法捕获异常
     */
    open suspend fun <T> request(type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true, call: suspend (service: RequestService) -> Response<String>): ResponseHolder<T> {
        return try {
            val response = call.invoke(getRequestService())
            parseResponse(response, type, kClazz, isInfoResponse)
        } catch (cause: Throwable) {
            val httpError = catchException(cause)
            ResponseHolder.Error(httpError)
        }
    }

    /**
     * 发起网络请求，返回Flow
     * 使用普通的协程访问已经足够满足大部分请求，此处使用Flow请求仅仅作为一个扩展
     * 如需大规模使用Flow，可以按照上述request方式进行扩充
     */
    open fun <T> requestFlow(type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true, call: suspend (service: RequestService) -> Response<String>): Flow<ResponseHolder<T>> {
        return try {
            flow {
                val response = call.invoke(getRequestService())
                emit(parseResponse(response, type, kClazz, isInfoResponse))
            }
        } catch (cause: Throwable) {
            flow {
                val httpError = catchException(cause)
                emit(ResponseHolder.Error(httpError))
            }
        }
    }

    /**
     * 解析请求返回的Response
     */
    open fun <T> parseResponse(response: Response<String>, type: Type, kClazz: KClass<*> = String::class, isInfoResponse: Boolean = true): ResponseHolder<T> {
        try {
            return if (response.isSuccessful && response.body() != null) {
                // 请求成功
                if (isInfoResponse) {
                    resolveInfoResponse(response, type, kClazz)
                } else {
                    resolveUnInfoResponse(response, type, kClazz)
                }
            } else {
                // 请求失败
                resolveFailedResponse(response)
            }
        } catch (cause: Throwable) {
            cause.printStackTrace()
            val httpError = catchException(cause)
            httpError.httpCode = response.code()
            return ResponseHolder.Error(httpError)
        }
    }

    /**
     * 解析成功的网络请求返回的响应，InfoResponse形式
     *//*    open fun <T> resolveInfoResponse(response: Response<String>, type: Type): ResponseHolder<T> {
            //测试响应 这里可以处理mock数据
    //        val b = "{\"msg\":\"手机号格式有误\",\"code\":300,\"data\":{}}"
            val resp = getGson().fromJson<InfoResponse<T>>(response.body(), type)
            return if (resp.isSuccessful()) {
                // 请求成功，返回成功响应
                ResponseHolder.Success(resp.data)
            } else if (resp.isSpecialCode(getConfig().codes)) {
                //特殊回调到初始化httpClient
                getConfig().callback?.let {
                    it.toCallback(resp.code, resp.msg)
                }
                ResponseHolder.Failure(resp.code, null)
            } else {
                // 请求成功，返回失败响应
                ResponseHolder.Failure(resp.code, resp.msg)
            }
        }*/

    open fun <T> resolveInfoResponse(response: Response<String>, type: Type, kClazz: KClass<*> = String::class): ResponseHolder<T> {
        //测试响应 这里可以处理mock数据
//        val b = "{\"msg\":\"手机号格式有误\",\"code\":300,\"data\":{}}"
//        {
//            "msg2": "操作成功",
//            "code": 200,
//            "data": [{}]
//        }
//        {
//            "msg2": "操作成功",
//            "code": 200,
//            "data": {}
//        }
//        {
//            "msg2": "操作成功",
//            "code": 200,
//            "data": ""
//        }
//        {
//            "msg2": "操作成功",
//            "code": 200,
//            "data": null
//        }
//        {
//            "msg2": "操作成功",
//            "code": 200,
//        }
        val jsonParser = JSONObject.parse(response.body()) as JSONObject
        var code = 500
        var msg: String? = null
        var bodyElement: Any? = null
        if (getConfig().tData.isNotEmpty()) {
            getConfig().tData.run {
                code = (jsonParser[this["code"]] ?: 500) as Int
                msg = (jsonParser[this["message"]] ?: "无法获取message") as String
                bodyElement = jsonParser[this["data"]]
            }
        }
//        Loge.e("动态处理请求结果 code = $code  - msg = $msg bodyElement = $bodyElement")
        //检测数据返回类型
        val networkDataType = when (bodyElement) {
            is String -> String::class
            is JSONObject -> Any::class
            is JSONArray -> Array::class
            else -> null
        }

        val resp = getGson().fromJson(response.body(), InfoResponse::class.java)
        //映射解析网络数据状态
        if (!resp.isSuccessful() && bodyElement == null) {
//            println("动态处理请求结果 null.")
            return ResponseHolder.Failure(code, "$msg,data is null")
        }

        return if ((bodyElement is String || bodyElement == null) && resp.isSuccessful() && kClazz == String::class) {
//            println("动态处理请求结果 String.")
            val v = getGson().fromJson<InfoResponse<T>>(response.body(), type)
            // 请求成功，返回成功响应
            ResponseHolder.Success(v.data)
        } else if (bodyElement is JSONObject && resp.isSuccessful() && kClazz == Any::class) {
//            println("动态处理请求结果 JSONObject.")
            val v = getGson().fromJson<InfoResponse<T>>(response.body(), type)
            // 请求成功，返回成功响应
            ResponseHolder.Success(v.data)
        } else if (bodyElement is JSONArray && resp.isSuccessful() && kClazz == Array::class) {
//            println("动态处理请求结果 JSONArray.")
            val v = getGson().fromJson<InfoResponse<T>>(response.body(), type)
            // 请求成功，返回成功响应
            ResponseHolder.Success(v.data)
        } else if (resp.isSpecialCode(getConfig().codes)) {
//            println("动态处理请求结果 特殊code.")
            //特殊回调到初始化httpClient
            getConfig().callback?.toCallback(code, msg)
            ResponseHolder.Failure(code, msg)
        } else if (networkDataType != kClazz) {
//            println("动态处理请求结果 接收数据类型与网络数据不匹配.")
            ResponseHolder.Failure(code, "接收数据类型与网络数据不匹配")
        } else {
//            println("动态处理请求结果 默认异常.")
            ResponseHolder.Failure(code, msg)
        }
    }

    /**
     * 解析成功的网络请求返回的响应，非InfoResponse形式
     */
    open fun <T> resolveUnInfoResponse(response: Response<String>, type: Type, kClazz: KClass<*> = String::class): ResponseHolder<T> {
        val resp = getGson().fromJson<T>(response.body(), type)
        return ResponseHolder.Success(resp)
    }

    /**
     * 解析失败的网络请求返回的响应
     */
    open fun resolveFailedResponse(response: Response<String>): ResponseHolder<Nothing> {
        val errorCode = response.raw().code
        val errorMsg = response.raw().message
        val httpError =
                HttpError(httpCode = response.code(), errorCode = errorCode, errorMsg = errorMsg)
        return ResponseHolder.Error(httpError)
    }

    /**
     * 捕获异常
     */
    @SuppressLint("MissingPermission") open fun catchException(cause: Throwable): HttpError {
        return when (cause) {
            is ConnectException -> HttpError(errorCode = HttpError.CONNECT_ERROR, errorMsg = "网络连接异常", cause = cause)

            is InterruptedIOException -> HttpError(errorCode = HttpError.CONNECT_TIMEOUT, errorMsg = "网络请求超时", cause = cause)

            is HttpException -> HttpError(errorCode = HttpError.BAD_NETWORK, errorMsg = "网络请求出错", cause = cause)

            is JsonParseException, is JSONException, is ParseException, is ClassCastException -> HttpError(errorCode = HttpError.PARSE_ERROR, errorMsg = "数据解析异常", cause = cause)

            is IOException -> HttpError(errorCode = HttpError.IO_ERROR, errorMsg = "io异常", cause = cause)

            is CancellationException -> HttpError(errorCode = HttpError.CANCEL_REQUEST, errorMsg = "取消异常", cause = cause)

            is NullPointerException -> HttpError(errorCode = HttpError.NULL_ERROR, errorMsg = "空指针异常", cause = cause)

            else -> HttpError(errorCode = HttpError.UN_KNOW_ERROR, errorMsg = "未知错误", cause = cause)
        }
    }
}