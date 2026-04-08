package nearby.lib.netwrok.response

/**
 * @description: 成功的网络请求返回的数据模型
 * @since: 1.0.0
 */
data class InfoResponse<T>(val code: Int, val status: Int, val msg: String, val data: T?) {
    /**
     * 判断本次请求返回的响应是否为成功响应，此方法一定要实现
     */
    fun isSuccessful(): Boolean {
        return code == 200 || code == 1
    }

    fun isSpecialCode(codes: ArrayList<Int>): Boolean {
        return code in codes
    }
}

