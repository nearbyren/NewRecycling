package nearby.lib.netwrok.response

/**
 * @description: 解析后的网络请求异常信息类
 * @since: 1.0.0
 */
open class HttpError(
    var httpCode: Int? = null,
    var errorCode: Int,
    var errorMsg: String,
    var cause: Throwable? = null
) {

    companion object {
        /**
         * 异常错误码
         */
        const val UN_KNOW_ERROR = -1
        const val CANCEL_REQUEST = -2
        const val CONNECT_ERROR = -3
        const val CONNECT_TIMEOUT = -4
        const val BAD_NETWORK = -5
        const val PARSE_ERROR = -6
        const val IO_ERROR = -7
        const val NULL_ERROR = -8
    }
}
