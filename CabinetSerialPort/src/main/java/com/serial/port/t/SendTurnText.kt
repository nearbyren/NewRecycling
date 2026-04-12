package com.serial.port.t

enum class SendTurnText(val status: Int, val text: String) {

    PUSH11(11,"推开投口一"),
    PUSH10(10,"普关投口一"),
    PUSH12(12,"强关格口一"),
    PUSH21(21,"推开投口二"),
    PUSH20(20,"普关投口二"),
    PUSH22(22,"强关格口二"),
    PITCH(100,"投口"),
    ERROE(-1,"异常"),
    FAILED(0, "关"),
    SUCCESS(1, "开"),
    ING(2, "强制关门"),
    FAULT(3, "故障");

    companion object {
        fun fromStatus(status: Int): String {
            return SendTurnText.values().find { it.status == status }?.text
                ?: throw IllegalArgumentException("Invalid status: $status")
        }
    }
}