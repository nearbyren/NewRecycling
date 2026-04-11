package com.recycling.toolsapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.TelephonyManager
import androidx.core.app.ActivityCompat

class SignalMonitor(private val context: Context) {
    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    // 检查权限
    fun checkPermissions(): Boolean {
        val permissions = mutableListOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.READ_PHONE_NUMBERS)
        }
        return permissions.all {
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // 获取信号强度
    @SuppressLint("MissingPermission", "NewApi") fun getSignalStrength(): List<Pair<String, Int>> {
        if (!checkPermissions()) return listOf("No permissions" to -1)

        return telephonyManager.allCellInfo?.mapNotNull { cellInfo ->
            when (cellInfo) {
                is CellInfoGsm -> "GSM" to cellInfo.cellSignalStrength.dbm
                is CellInfoCdma -> "CDMA" to cellInfo.cellSignalStrength.dbm
                is CellInfoLte -> "LTE" to cellInfo.cellSignalStrength.dbm
                is CellInfoWcdma -> "WCDMA" to cellInfo.cellSignalStrength.dbm
                is CellInfoNr -> "5G" to cellInfo.cellSignalStrength.dbm
                else -> null
            }
        } ?: listOf("No signal data" to -1)
    }
}