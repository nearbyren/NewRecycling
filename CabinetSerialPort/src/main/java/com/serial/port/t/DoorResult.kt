package com.serial.port.t

/**
 * @author: lr
 * @created on: 2026/3/21 下午11:17
 * @description:
 */
/** 清运门/格口操作结果 */
data class DoorResult(
    /** 格口号*/
    val locker: Int = -1,
    /** 状态码 (0:已关闭, 1:已打开, 2:开关中, 3:故障)*/
    val status: Int = -1,
    /** 业务类型 (0:查询, 1:打开)*/
    val clearType: Int = -1,
    /**校准状态*/
    val caliStatus: Int = -1,
    /**阻力值*/
    val rodHinderValue: Int = 0,
    /**重量*/
    val weight: String = "0.00",
    /**指令*/
    val cmd: Int = -1,
    /**指令*/
    val cmdByte: Byte = 0xFF.toByte(),
    /**柜体信息*/
    val containers: MutableList<ContainersResult> = mutableListOf(),
    /**文件字节*/
    val byteArray: ByteArray = byteArrayOf(),
    /**升级状态*/
    val upStatus: Int = -1,
    /**芯片版本*/
    val chipVersion: Int = 20260320,
    /**true代表处理业务  指令返回状态*/
    val cmdStatus: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DoorResult

        if (locker != other.locker) return false
        if (status != other.status) return false
        if (clearType != other.clearType) return false
        if (caliStatus != other.caliStatus) return false
        if (rodHinderValue != other.rodHinderValue) return false
        if (weight != other.weight) return false
        if (cmd != other.cmd) return false
        if (cmdByte != other.cmdByte) return false
        if (containers != other.containers) return false
        if (!byteArray.contentEquals(other.byteArray)) return false
        if (upStatus != other.upStatus) return false
        if (upStatus != other.chipVersion) return false
        if (cmdStatus != other.cmdStatus) return false

        return true
    }

    override fun hashCode(): Int {
        var result = locker
        result = 31 * result + status
        result = 31 * result + clearType
        result = 31 * result + caliStatus
        result = 31 * result + rodHinderValue
        result = 31 * result + cmd
        result = 31 * result + cmdByte
        result = 31 * result + containers.hashCode()
        result = 31 * result + byteArray.contentHashCode()
        result = 31 * result + upStatus
        result = 31 * result + chipVersion
        result = 31 * result + cmdStatus.hashCode()
        return result
    }
}
