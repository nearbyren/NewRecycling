package com.serial.port.t

enum class SendOpenText(val status: Int, val text: String) {

    PITCH(100,"投口"), CLEAR(200,"清运"),ERROE(-1,"异常"),FAILED(0, "关"), SUCCESS(1, "开"), ING(2, "强制关门"), FAULT(3, "故障");

    companion object {
        fun fromStatus(status: Int): String {
            return SendOpenText.values().find { it.status == status }?.text
                ?: throw IllegalArgumentException("Invalid status: $status")
        }
    }
}