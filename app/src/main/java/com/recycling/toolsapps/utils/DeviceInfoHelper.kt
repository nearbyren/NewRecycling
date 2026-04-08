package com.recycling.toolsapps.utils

import android.content.Context
import android.telephony.TelephonyManager
import java.lang.reflect.Method

object DeviceInfoHelper {

        /**
         * 通过反射获取IMSI
         */
        fun getImsi(context: Context): String? {
            return try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val getSubscriberIdMethod: Method = telephonyManager.javaClass.getMethod("getSubscriberId")
                getSubscriberIdMethod.invoke(telephonyManager) as? String
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        /**
         * 通过反射获取IMEI
         */
        fun getImei(context: Context, slotIndex: Int = 0): String? {
            return try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val getImeiMethod: Method = telephonyManager.javaClass.getMethod("getImei", Int::class.javaPrimitiveType)
                getImeiMethod.invoke(telephonyManager, slotIndex) as? String
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }

        /**
         * 通过反射获取ICCID
         */
        fun getIccid(context: Context): String? {
            return try {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                val getSimSerialNumberMethod: Method = telephonyManager.javaClass.getMethod("getSimSerialNumber")
                getSimSerialNumberMethod.invoke(telephonyManager) as? String
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
}