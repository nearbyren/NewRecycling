package com.serial.port.call

interface CommandStatus {

    companion object {
        /***
         * 成功
         */
        const val SUCCEED = 1

        /***
         * 失败
         */
        const val FAIL = 0

        /***
         * 故障
         */
        const val FAULT = 2

    }
}