package nearby.lib.netwrok.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.net.URLEncoder

/**
 * @description: 请求头部拦截器
 * @since: 1.0.0
 */
class HeadersInterceptor(private val headers: Map<String, String>) : Interceptor {



    private fun getValueEncoded(value: String?): String {
        if (value == null) return "null"
        val newValue = value.replace("\n", "")
        val encodedBuilder = StringBuilder()
        for (c in newValue) {
            if (c <= '\u001f' || c >= '\u007f') {
                encodedBuilder.append(URLEncoder.encode(c.toString(), "UTF-8"))
            } else {
                encodedBuilder.append(c)
            }
        }
        return encodedBuilder.toString()
    }


    override fun intercept(chain: Interceptor.Chain): Response {
        val requestBuilder = chain.request().newBuilder()
        for (header in headers.iterator()) {
            requestBuilder.addHeader(header.key, getValueEncoded(header.value))
        }
        return chain.proceed(requestBuilder.build())
    }
}