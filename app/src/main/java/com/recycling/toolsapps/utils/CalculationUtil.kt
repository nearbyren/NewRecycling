package com.recycling.toolsapps.utils

import com.serial.port.utils.Loge
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * @author: lr
 * @created on: 2025/11/2 下午5:12
 * @description:
 */
object CalculationUtil {
    /***
     * 相减
     * @param after
     * @param before
     */
    fun subtractFloats(after: String, before: String): String {
        val bd1 = BigDecimal(after)
        val bd2 = BigDecimal(before)
        return bd1.subtract(bd2).setScale(2, RoundingMode.HALF_UP).toString()
    }
    /***
     * 相减 当重量变化超过0.5kg则返回true
     * @param after 最新重量
     * @param before 检测前重量
     * @return 如果 (after - before) < -0.5 返回 true
     */
    fun subtractFloatsBoolean(after: String, before: String): Boolean {
        val bd1 = BigDecimal(after)
        val bd2 = BigDecimal(before)
        val b = bd1.subtract(bd2).setScale(2, RoundingMode.HALF_UP).toDouble()
        return b < -0.5
    }

    /***
     * 换算后的值 小于等于0.1则取原来值
     */
    fun lessEqual(after: String): Boolean {
        val bd1 = BigDecimal(after).setScale(2, RoundingMode.HALF_UP).toDouble()
        val bd2 = BigDecimal("0.10").setScale(2, RoundingMode.HALF_UP).toDouble()
        return bd1 <= bd2
    }

    /**
     * 比较两个数值字符串的大小
     * @param num1 第一个数值字符串
     * @param num2 第二个数值字符串
     * @return 比较结果：1表示num1>num2，-1表示num1<num2，0表示相等
     */
    fun compareNumbers(num1: String, num2: String): Int {
        val bd1 = BigDecimal(num1)
        val bd2 = BigDecimal(num2)
        return bd1.compareTo(bd2)
    }

    /**
     * 检查num1是否大于num2
     * @param num1 第一个数值字符串
     * @param num2 第二个数值字符串
     * @return 如果num1大于num2返回true，否则返回false
     */
    fun isGreater(num1: String, num2: String): Boolean {
        return compareNumbers(num1, num2) > 0
    }

    /**
     * 检查num1是否小于num2
     * @param num1 第一个数值字符串
     * @param num2 第二个数值字符串
     * @return 如果num1小于num2返回true，否则返回false
     */
    fun isLess(num1: String, num2: String): Boolean {
        return compareNumbers(num1, num2) < 0
    }

    /**
     * 检查num1是否等于num2
     * @param num1 第一个数值字符串
     * @param num2 第二个数值字符串
     * @return 如果num1等于num2返回true，否则返回false
     */
    fun isEqual(num1: String, num2: String): Boolean {
        return compareNumbers(num1, num2) == 0
    }

    /**
     * 判断两个重量的差值是否小于 0.5kg
     * @param weight1 第一个重量（kg）
     * @param weight2 第二个重量（kg）
     * @return 差值小于 0.5kg 返回 true，否则 false
     */
    fun isWeightDiffLessThan500g(weight1: Double, weight2: Double): Boolean {
        val diff = kotlin.math.abs(weight2 - weight1)
        return diff > -0.5
    }

    /***
     * 相加
     * @param after
     * @param before
     */
    fun addFloats(num1: String, num2: String): String {
        val bd1 = BigDecimal(num1)
        val bd2 = BigDecimal(num2)
        return bd1.add(bd2).setScale(2, RoundingMode.HALF_UP).toString()
    }
    /***
     * 相乘
     * @param after
     * @param before
     */
    fun multiplyFloats(after: String, before: String): String {
        val bd1 = BigDecimal(after)
        val bd2 = BigDecimal(before)
        return bd1.multiply(bd2).setScale(2, RoundingMode.HALF_UP).toString()
    }
    /***
     * 相除
     * @param after
     * @param before
     */
    fun divideFloats(after: String, before: String): String {
        val bd1 = BigDecimal(after)
        val bd2 = BigDecimal(before)
        return bd1.divide(bd2, 2, RoundingMode.HALF_UP).toString()
    }

    /***
     * 计算获取百分比
     * @param weight
     * @param totalWeight
     */
    fun getWeightPercent(weight: Float, totalWeight: Float): String {
        return if (totalWeight == 0.00f) {
            "0%"// 避免除零错误
        } else {
            val result = (weight / totalWeight) * 100
           val getResult =  "%.2f".format(result)
            "$getResult%"
        }
    }
    /**
     * 获取重量
     * @param weight
     */
    fun getWeight(weight: Int): String {
        Loge.e("换算重量前 $weight")
        if (weight <= 0) return "0.00"
        return "%.2f".format(weight.toDouble() / 1000)
//        return new BigDecimal(weight).divide(BigDecimal.valueOf(1000)).setScale(2, RoundingMode.HALF_UP).toString();

    }
}