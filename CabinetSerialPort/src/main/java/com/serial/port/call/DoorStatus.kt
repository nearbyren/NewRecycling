package com.serial.port.call

interface DoorStatus {

    companion object {
        /***
         * 门开
         */
        const val SUCCEED = 1

        /***
         * 门关
         */
        const val FAIL = 0

        /***
         * 开关门中
         */
        const val ING = 2

        /***
         * 故障
         */
        const val FAULT = 3

    }
}