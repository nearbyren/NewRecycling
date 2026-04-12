package com.serial.port.utils

object CmdCode {
    /***
     * 固件版本
     */
    const val GJ_VERSION = 20260320
    /***
     *投口正常
     */
    const val GE_WEIGHT_FAULT_1 = -1

    /***
     *查询到重量结果 1
     */
    const val GE_WEIGHT_RESULT = 1

    /***
     * 查询重量前 0
     */
    const val GE_WEIGHT_FRONT = 0

    /***
     * 查询重量持续中 10
     */
    const val GE_WEIGHT_ING = 10

    /***
     * 开门中 10
     */
    const val GE_OPEN_ING = 10

    /***
     * 查询重量后 1
     */
    const val GE_WEIGHT_BACK = 1

    /***
     * 查询重量前 30
     */
    const val GE_WEIGHT_CLEAR_FRONT = 30

    /***
     * 查询重量后 31
     */
    const val GE_WEIGHT_CLEAR_BACK = 31

    /***
     * 开 1
     */
    const val GE_OPEN = 1

    /***
     * 关 0
     */
    const val GE_CLOSE = 0

    /***
     * 开关中 2
     */
    const val GE_OPEN_CLOSE_ING = 2
    /***
     * 强制关闭 2
     */
    const val GE_COERCIVE_CLOSE = 2

    /***
     * 开关门故障 3
     */
    const val GE_OPEN_CLOSE_FAULT = 3

    /***
     * 设备状态 1
     */
    const val GE_DEVICE_STATUS = 1

    /***
     * 清运门一打开 11
     */
    const val CLEAR_OPEN_1_1 = 11

    /***
     * 清运门二打开 21
     */
    const val CLEAR_OPEN_2_1 = 21

    /***
     * 清运门一查询 10
     */
    const val CLEAR_QUERY_1_0 = 10

    /***
     * 清运门二查询 20
     */
    const val CLEAR_QUERY_2_0 = 20

    /***
     * 格口一
     */
    const val GE1 = 1

    /***
     * 格口二
     */
    const val GE2 = 2

    /***
     * 默认 -1
     */
    const val GE = -1

    /***
     * 启动关格口一 10
     */
    const val GE10 = 10
    /***
     * 启动强制关格口一 12
     */
    const val GE12 = 12
    /***
     * 启动开格口一 11
     */
    const val GE11 = 11

    /***
     * 启动关格口二 20
     */
    const val GE20 = 20
    /***
     * 启动强制关格口二 22
     */
    const val GE22 = 22
    /***
     * 启动开格口二 21
     */
    const val GE21 = 21

    /***
     *内灯光 开 11
     */
    const val IN_LIGHTS_OPEN = 11

    /***
     *内灯光 关 12
     */
    const val IN_LIGHTS_CLOSE = 12

    /***
     *外灯光 开 21
     */
    const val OUT_LIGHTS_OPEN = 21

    /***
     *外灯光 关 22
     */
    const val OUT_LIGHTS_CLOSE = 22

    /***
     * 校准0
     */
    const val CALIBRATION_0 = 0

    /***
     * 校准1
     */
    const val CALIBRATION_1 = 1

    /***
     * 校准2
     */
    const val CALIBRATION_2 = 2

    /***
     * 校准3
     */
    const val CALIBRATION_3 = 3

    /***
     * 校准4
     */
    const val CALIBRATION_4 = 4

    /***
     * 校准5
     */
    const val CALIBRATION_5 = 5
}