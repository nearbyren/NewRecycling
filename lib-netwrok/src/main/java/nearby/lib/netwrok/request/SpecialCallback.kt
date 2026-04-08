package nearby.lib.netwrok.request


/****
 * 特殊情况处理回调
 */
interface SpecialCallback {
    fun toCallback(code: Int, msg: String? = null)
}