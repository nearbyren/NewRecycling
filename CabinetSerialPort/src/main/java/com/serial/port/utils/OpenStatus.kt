package com.serial.port.utils

enum class OpenStatus(val code: Int, val status: Boolean) {

    FAILED(0, false), SUCCESS(1, true), FAULT(2, false);

    companion object {
        /***
         * 0.false 【开仓失败】
         * 1.true 【开仓成功】
         * 2.false 【开仓故障】
         * @param code 0 false 1 true
         */
        fun fromStatus(code: Int): Boolean {
            return OpenStatus.values().find { it.code == code }?.status ?: throw IllegalArgumentException("Invalid status: $code")
        }
    }
}