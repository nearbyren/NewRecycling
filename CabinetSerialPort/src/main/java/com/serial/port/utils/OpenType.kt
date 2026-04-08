package com.serial.port.utils

enum class OpenType(val code: Int, val status: Boolean) {

    OPEN(1, true), CLOSE(0, false);

    companion object {
        /***
         * @param code
         * 0.false 【归还】
         * 1.true 【领取】
         */
        fun fromStatus(code: Int): Boolean {
            return OpenType.values().find { it.code == code }?.status ?: throw IllegalArgumentException("Invalid status: $code")
        }
    }
}