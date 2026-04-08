package com.recycling.toolsapps.utils

import android.content.Context
import android.telephony.TelephonyManager

object DeviceInfoHelper2 {

    /**
     * 获取 IMEI 通过反射
     */
    fun getImei(context: Context): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // 方法1: 使用 getImei(int)
            try {
                val method = telephonyManager.javaClass.getMethod("getImei", Int::class.javaPrimitiveType)
                for (slot in 0 until 2) {
                    val imei = method.invoke(telephonyManager, slot) as? String
                    if (!imei.isNullOrEmpty()) return imei
                }
            } catch (e: Exception) {
                // 忽略，尝试下一个方法
            }

            // 方法2: 使用 getDeviceId()
            try {
                val method = telephonyManager.javaClass.getMethod("getDeviceId")
                method.invoke(telephonyManager) as? String
            } catch (e: Exception) {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取 IMSI 通过反射
     */
    fun getImsi(context: Context): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // 方法1: 使用 getSubscriberId(int)
            try {
                val method = telephonyManager.javaClass.getMethod("getSubscriberId", Int::class.javaPrimitiveType)
                for (slot in 0 until 2) {
                    val imsi = method.invoke(telephonyManager, slot) as? String
                    if (!imsi.isNullOrEmpty()) return imsi
                }
            } catch (e: Exception) {
                // 忽略，尝试下一个方法
            }

            // 方法2: 使用 getSubscriberId()
            try {
                val method = telephonyManager.javaClass.getMethod("getSubscriberId")
                method.invoke(telephonyManager) as? String
            } catch (e: Exception) {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取 ICCID 通过反射
     */
    fun getIccid(context: Context): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // 方法1: 使用 getSimSerialNumber(int)
            try {
                val method = telephonyManager.javaClass.getMethod("getSimSerialNumber", Int::class.javaPrimitiveType)
                for (slot in 0 until 2) {
                    val iccid = method.invoke(telephonyManager, slot) as? String
                    if (!iccid.isNullOrEmpty()) return iccid
                }
            } catch (e: Exception) {
                // 忽略，尝试下一个方法
            }

            // 方法2: 使用 getSimSerialNumber()
            try {
                val method = telephonyManager.javaClass.getMethod("getSimSerialNumber")
                method.invoke(telephonyManager) as? String
            } catch (e: Exception) {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取所有设备信息
     */
    fun getAllDeviceInfo(context: Context): Map<String, String?> {
        return mapOf(
            "IMEI" to getImei(context),
            "IMSI" to getImsi(context),
            "ICCID" to getIccid(context)
        )
    }
}