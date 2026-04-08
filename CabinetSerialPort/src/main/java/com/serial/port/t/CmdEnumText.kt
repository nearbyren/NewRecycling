package com.serial.port.t

enum class CmdEnumText(val byte: Byte, val text: String) {

    CMDFF(0xFF.toByte(), "初始化"),
    CMD0(0x00.toByte(), "发送256字节文件"),
    CMD1(0x01.toByte(), "打开/关闭投口"),
    CMD2(0x02.toByte(), "查询投口状态"),
    CMD3(0x03.toByte(), "打开清运门"),
    CMD4(0x04.toByte(), "查询当前重量"),
    CMD5(0x05.toByte(), "查询当前设备状态"),
    CMD6(0x06.toByte(), "灯光控制"),
    CMD7(0x07.toByte(), "进入升级状态"),
    CMD8(0x08.toByte(), "查询升级状态"),
    CMD9(0x09.toByte(), "查询升级结果"),
    CMD10(0x0A.toByte(), "查询重启指令"),
    CMD11(0x0B.toByte(), "查询软件版本"),
    CMD16(0x0B.toByte(), "去皮清零"),
    CMD17(0x10.toByte(), "校准"),
    CMD18(0x12.toByte(), "文件发送效验"),
    CMD19(0x13.toByte(), "设置阻力值");

    companion object {
        fun fromCmdText(byte: Byte): String {
            return CmdEnumText.values().find { it.byte == byte }?.text
                ?: throw IllegalArgumentException("Invalid status: $byte")
        }
    }
}