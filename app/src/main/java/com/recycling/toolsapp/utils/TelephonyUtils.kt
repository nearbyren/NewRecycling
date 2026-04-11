package com.recycling.toolsapp.utils


import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat


object TelephonyUtils {
    // 检查权限状态
    private fun checkPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    // 获取IMEI（需要READ_PHONE_STATE权限）
    fun getImei(context: Context): String? {
        if (!checkPermission(context, Manifest.permission.READ_PHONE_STATE)) {
            return "PERMISSION_DENIED"
        }

        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                tm.imei ?: "NOT_AVAILABLE"
            } else {
                @Suppress("DEPRECATION") tm.deviceId ?: "NOT_AVAILABLE"
            }
        } catch (e: Exception) {
            "ERROR: ${e.localizedMessage}"
        }
    }

    // 获取IMSI（需要READ_PHONE_STATE权限）
    fun getImsi(context: Context): String? {
        if (!checkPermission(context, Manifest.permission.READ_PHONE_STATE)) {
            return "PERMISSION_DENIED"
        }

        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            @Suppress("DEPRECATION") tm.subscriberId ?: "NOT_AVAILABLE"
        } catch (e: Exception) {
            "ERROR: ${e.localizedMessage}"
        }
    }

    // 获取ICCID（需要READ_PHONE_STATE权限）
    fun getIccid(context: Context): String? {
        if (!checkPermission(context, Manifest.permission.READ_PHONE_STATE)) {
            return "PERMISSION_DENIED"
        }

        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.simSerialNumber ?: "NO_SIM_CARD"
        } catch (e: Exception) {
            "ERROR: ${e.localizedMessage}"
        }
    }

    @SuppressLint("HardwareIds", "MissingPermission")
    fun getPrivilegedIds(context: Context): Triple<String?, String?, String?> {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return Triple(
            // IMEI (需要READ_PRIVILEGED_PHONE_STATE权限)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) tm.imei else tm.deviceId,
            // IMSI (需要READ_PRIVILEGED_PHONE_STATE权限)
            tm.subscriberId,
            // ICCID (需要READ_PHONE_STATE权限)
            tm.simSerialNumber)
    }


    fun getIccid2(context: Context): String {
        var iccid = ""
        try {
            val telephonyManager =
                    context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            val telephonyClass = Class.forName("android.telephony.TelephonyManager")
            val getIccIdMethod = telephonyClass.getDeclaredMethod("getIccId")
            getIccIdMethod.isAccessible = true
            iccid = getIccIdMethod.invoke(telephonyManager) as String
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        return iccid
    }
    fun getIMSI(context: Context): String {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        @SuppressLint("MissingPermission")
        val imsi = tm.subscriberId
        return imsi
    }
}
