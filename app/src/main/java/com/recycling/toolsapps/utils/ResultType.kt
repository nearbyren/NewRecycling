package com.recycling.toolsapps.utils

/***
 * 指令结果
 */
object ResultType {


    /***
     * debug
     * 调试
     */
    const val DEBUGGING_SECONDS = 300
    const val DEBUGGING_SECONDS2 = 300000L
    /***
     * 手机登录
     */
    const val LOGIN_MOBILE_SECONDS = 80
    const val LOGIN_MOBILE_SECONDS2 = 80000L
    /***
     * 清运页秒
     */
    const val DELIVERY_CLEAR_SECONDS = 300
    const val DELIVERY_CLEAR_SECONDS2 = 300000L
    /***
     * 计重页秒
     */
    const val DELIVERY_SECONDS = 20
    /**
     * 用户点击 1
     */
    const val RESULT_CLICK = 1

    /**
     * 倒计时结束 2
     */
    const val RESULT_END = 2
    /**
     * 读取下位机 门开
     */
    const val RESULT_OPEN = 310

    /***
     * 读取下位机 门关
     */
    const val RESULT_CLOSE = 301


}