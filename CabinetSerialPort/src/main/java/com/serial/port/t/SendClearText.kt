package com.serial.port.t

enum class SendClearText(val status: Int, val text: String) {

    CLEAROPEN(11,"打开清运门"),
    CLEARQUERY(10,"查询清运门"),
    CLEAR(200,"清运"),
    ERROE(-1,"异常"),
    CMDQUERY(0, "查询指令"),
    CMDOPEN(1, "打开指令"),
    FAULT(3, "故障");

    companion object {
        fun fromStatus(status: Int): String {
            return SendClearText.values().find { it.status == status }?.text
                ?: throw IllegalArgumentException("Invalid status: $status")
        }
    }
}