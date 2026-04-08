package com.serial.port.utils;

class Config {

    companion object {

        const val COUNT_NUM = 2 //丢包未接收到，和反馈失败后的重试次数
        const val FAIL_RETRY_NUM = 5

        const val ZHENG = 0
        const val FU = 1

        const val CODE_REQUEST = 0x100
        const val CODE_OPEN_SOME = 0x101
    }


    interface LOCKER_NUM {

        companion object {

            val LOCKER_1 = 1
            val LOCKER_2 = 2
            val LOCKER_3 = 3
            val LOCKER_4 = 4
            val LOCKER_5 = 5
            val LOCKER_6 = 6
            val LOCKER_7 = 7
            val LOCKER_8 = 8
            val LOCKER_9 = 9
            val LOCKER_10 = 10
            val LOCKER_11 = 11
            val LOCKER_12 = 12
            val LOCKER_ALL_ID = 20
        }
    }


    interface LOCKER_TAG {

        companion object {

            val LOCKER_TAG1 = "LOCKER_TAG1"
            val LOCKER_TAG2 = "LOCKER_TAG2"
            val LOCKER_TAG3 = "LOCKER_TAG3"
            val LOCKER_TAG4 = "LOCKER_TAG4"
            val LOCKER_TAG5 = "LOCKER_TAG5"
            val LOCKER_TAG6 = "LOCKER_TAG6"
            val LOCKER_TAG7 = "LOCKER_TAG7"
            val LOCKER_TAG8 = "LOCKER_TAG8"
            val LOCKER_TAG9 = "LOCKER_TAG9"
            val LOCKER_TAG10 ="LOCKER_TAG10"
            val LOCKER_TAG11 ="LOCKER_TAG11"
            val LOCKER_TAG12 ="LOCKER_TAG12"
        }
    }

    interface CMD {

        companion object {
            const val POSITION_SLAVE = 0
            const val POSITION_FUN = 1  //命令码
            const val DATA18 = 18  //数据中第18个
            const val DATA19 = 19  //数据中第19个
            const val DATA20 = 20  //数据中第19个
            const val DATA21 = 21  //数据中第19个

            const val STATUS_TEMP_DATA_SIZE = 39  //状态数据(含温度)应有的数据长度  39
            const val POSITION_LEN = 3  //第四个字节，代表数据长度


            const val HEAD_AND_FUN_AND_DATALONG = 4 //地址码1个+命令码1个+长度2
            const val CRC16_SIZE = 2

            const val OTHER_BYTESIZE = 10  //等待充电、关门、判定充满、异常等，共10个字节
        }
    }

    interface STATUS {

        companion object {
            const val SUCCEED = 1
            const val FAIL = 2

            const val STATUS_IDLE = 0
            const val STATUS_IN_CHARGE = 1
            const val STATUS_FULL = 2
            const val STATUS_FAILURE = 3

        }
    }


}