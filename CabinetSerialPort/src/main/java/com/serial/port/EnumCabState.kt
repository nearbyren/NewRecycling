package com.serial.port

/***
 * 柜体状态信息
 */
enum class EnumCabState(val code: String, val desc: String) {
    //烟雾传感器
    SmokeYes("10", "不报警"),
    SmokeNo("11", "<font color='red'>报警</font>"),

    //红外传感
    LrYes("20", "无溢出"),
    LrNo("21", "<font color='red'>有溢出</font>"),
    //投口传感器门状态
    DoorSensorYes("30", "门关"),
    DoorSensorNo("31", "<font color='red'>门开"),

    //防夹手传感器门状态
    HandsSensorYes("40", "无夹手"),
    HandsSensorNo("41", "<font color='red'>有夹手</font>"),

    //投口门状态
    DoorYes("50", "门关"),
    DoorNo("51", "<font color='red'>门开</font>"),
    //清运门锁
    LockYes("60", "门关"),
    LockNo("61", "<font color='red'>门开</font>"),
    //程序状态
    RunYes("70", "<font color='red'>未校准</font>"),
    RunNo("71", "已校准"),
    //夹手状态
    HandsYes("80", "无夹手"),
    HandsNo("81", "<font color='red'>夹到手</font>"),


    PROTECTED("4","受保护");

    companion object {

        fun findByCode(code: String): EnumCabState? {
            return EnumCabState.values().firstOrNull { it.code == code }
        }

        fun getDescByCode(code: String): String {
            return findByCode(code)?.desc ?: "异常"
        }
    }
}

