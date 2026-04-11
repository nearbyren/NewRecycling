package com.recycling.toolsapp.utils

/***
 * 记录上报故障信息
 */
enum class EnumFaultState(val code: Int, val desc: String) {

    DOOR_111(111,"投送门一开门异常"),
    DOOR_121(121,"投送门二开门异常"),

    DOOR_110(110,"投递门二关门异常"),
    DOOR_120(120,"投递门二关门异常"),

    DOOR_311(311,"清运门一开门异常"),
    DOOR_321(321,"清运门二开门异常"),
    DOOR_331(331,"清运门三开门异常"),

    DOOR_410(410,"清运门一关门异常"),
    DOOR_420(420,"清运门二关门异常"),
    DOOR_430(430,"清运门三关门异常"),

    DOOR_51(51,"摄像头内异常"),
    DOOR_52(52,"摄像头外异常"),

    DOOR_6(6,"电磁锁异常"),

    DOOR_5(5,"摄像头异常"),

    DOOR_7(7,"内灯异常"),
    DOOR_8(8,"外灯异常"),

    DOOR_91(91,"内灯异常"),
    DOOR_92(92,"外灯异常"),

    DOOR_11(11,"格口一满溢"),
    DOOR_12(12,"格口二满溢"),

    DOOR_2110(2110,"格口一下发满溢"),
    DOOR_2120(2120,"格口二下发满溢"),

    DOOR_5101(5101,"格口一校准状态"),
    DOOR_5102(5102,"格口一故障状态"),

    DOOR_5201(5201,"格口二校准状态"),
    DOOR_5202(5202,"格口二故障状态"),


    PROTECTED(4,"受保护");

    companion object {

        fun findByCode(code: Int): EnumFaultState? {
            return EnumFaultState.values().firstOrNull { it.code == code }
        }

        fun getDescByCode(code: Int): String {
            return findByCode(code)?.desc ?: "异常"
        }
    }
}

