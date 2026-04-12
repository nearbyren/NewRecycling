package com.recycling.toolsapp.utils

/***
 * 故障状态
 */
object FaultType {
    /***
     * 1.投送门开门异常
     * 2.投递门关门异常
     * 3.清运门开门异常
     * 4.清运门关门异常
     * 5.摄像头异常
     * 6.电磁锁异常
     * 7:内灯异常
     * 8:外灯异常
     * 9:推杆异常
     * @see 1.投送门开门异常 111 121
     * @see 2.投递门关门异常 110 120
     * @see 3.清运门开门异常 311 321 331
     * @see 4.清运门关门异常 410 420 430
     * @see 5.摄像头异常 51 52
     * @see 6.电磁锁异常
     * @see 7:内灯异常
     * @see 8:外灯异常
     * @see 9:推杆异常 91 92
     * @see 11:1红外满溢
     * @see 12:2红外满溢
     * @see 211:超重满溢
     * @see 212:超重满溢
     * @see 5101:格口一校准状态
     * @see 5102:格口一故障状态
     * @see 5201:格口二校准状态
     * @see 5202:格口二故障状态
     */

    const val FAULT_CODE_0 = 0

    /***
     * 投送门开门异常 111
     */
    const val FAULT_CODE_111 = 111
    /***
     * 投送门开门异常 121
     */
    const val FAULT_CODE_121 = 121
    /***
     * 投递门关门异常 110
     */
    const val FAULT_CODE_110 = 110
    /***
     * 投递门关门异常 120
     */
    const val FAULT_CODE_120 = 120
    /***
     * 清运门一开门异常 311
     */
    const val FAULT_CODE_311 = 311
    /***
     * 清运门二开门异常 321
     */
    const val FAULT_CODE_321 = 321
    /***
     * 清运一门关门异常 410
     */
    const val FAULT_CODE_410 = 410
    /***
     * 清运二门关门异常 420
     */
    const val FAULT_CODE_420 = 420
    /***
     * 5.摄像头异常 51 52
     */
    const val FAULT_CODE_51 = 51
    /***
     *5.摄像头异常 51 52
     */
    const val FAULT_CODE_52 = 52
    /***
     * 5.摄像头异常 51 52
     */
    const val FAULT_CODE_5 = 5
    /***
     * 6.电磁锁异常
     */
    const val FAULT_CODE_6 = 6
    /***
     * 7:内灯异常
     */
    const val FAULT_CODE_7 = 7
    /***
     * 8:外灯异常
     */
    const val FAULT_CODE_8 = 8
    /***
     * 9:推杆异常 91 92
     */
    const val FAULT_CODE_91 = 91
    /***
     * 9:推杆异常 91 92
     */
    const val FAULT_CODE_92 = 92
    /***
     * 满溢
     */
    const val FAULT_CODE_1111 = 1111
    /***
     * 满溢
     */
    const val FAULT_CODE_1112 = 1112
    /***
     * 11:1红外满溢
     */
    const val FAULT_CODE_11 = 11
    /***
     * 12:2红外满溢
     */
    const val FAULT_CODE_12 = 12
    /***
     * 211:超重满溢 格口一
     */
    const val FAULT_CODE_211 = 211
    /***
     * 212:超重满溢 格口二
     */
    const val FAULT_CODE_212 = 212
    /***
     * 2110:服务器下发满溢 格口一
     */
    const val FAULT_CODE_2110 = 2110
    /***
     * 2120:服务器下发满溢 格口二
     */
    const val FAULT_CODE_2120 = 2120
    /***
     * 5101:格口一校准状态
     */
    const val FAULT_CODE_5101 = 5101
    /***
     * 5102:格口一故障状态
     */
    const val FAULT_CODE_5102 = 5102
    /***
     * 5201:格口二校准状态
     */
    const val FAULT_CODE_5201 = 5201
    /***
     * 5202:格口二故障状态
     */
    const val FAULT_CODE_5202 = 5202


}