package com.cabinet.toolsapp.tools.bus

/**
 * @date: 2024/9/11 0:02
 * @desc: 描述
 */
data class MessageEvent(
    var message:String = "",
    var state:Boolean = false,
    var phone:String = "",
    var event:String = "",
)