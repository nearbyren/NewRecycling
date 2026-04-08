package com.serial.port.t

enum class OpenStatusText(val status: Int, val text: String) {

    IDLE(-1,"空闲"),FAILED(0, "关"), SUCCESS(1, "开"), ING(2, "开关中"), FAULT(3, "故障");

    companion object {
        fun fromStatus(status: Int): String {
            return OpenStatusText.values().find { it.status == status }?.text
                ?: throw IllegalArgumentException("Invalid status: $status")
        }
    }
}