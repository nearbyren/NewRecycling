package com.recycling.toolsapps.utils;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.serial.port.utils.AppUtils;

import java.lang.reflect.Method;
import java.util.List;

public class IccidOper {
    //在自己内部定义自己的一个实例，只供内部调用
    private static final IccidOper instance = new IccidOper();
    private static TelephonyManager telephonyManager;

    //这里提供了一个供外部访问本class的静态方法，可以直接访问
    public static IccidOper getInstance(Context context) {
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return instance;
    }

    private static String TAG = "IccidOper";

    public String getIccid() {
        SubscriptionManager sm = null;
        String iccid = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            sm = SubscriptionManager.from(AppUtils.getContext());
            if (ActivityCompat.checkSelfPermission(AppUtils.getContext(), android.Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            int count = sm.getActiveSubscriptionInfoCount();
            int max = sm.getActiveSubscriptionInfoCountMax();
            Log.d(TAG, "card number=" + String.valueOf(count));
            Log.d(TAG, "card slot num" + String.valueOf(max));
            List<SubscriptionInfo> sis = null;
            sis = sm.getActiveSubscriptionInfoList();
            for (SubscriptionInfo subInfo : sis) {
                iccid = subInfo.getIccId();
                Log.d(TAG, "iccid=" + iccid);
            }
        }
        return iccid;
    }

    public String getIMEI() {
        /*TelephonyManager telephonyManager =
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);*/
        String imei = "";
        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0及以上使用getImei()方法
                if (telephonyManager.getPhoneCount() > 1) {
                    // 双卡设备
                    imei = "IMEI1: " + telephonyManager.getImei(0) + "\nIMEI2: " + telephonyManager.getImei(1);
                } else {
                    // 单卡设备
                    imei = telephonyManager.getImei();
                }
            } else {
                // Android 8.0以下使用getDeviceId()方法
                imei = telephonyManager.getDeviceId();
            }

            Log.d(TAG, "IMEI: " + imei);
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return imei;
    }

    public String getIMSI() {
        String imsi = "";
        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0及以上版本，支持双卡
                if (telephonyManager.getPhoneCount() > 1) {
                    // 双卡设备
                    StringBuilder imsiBuilder = new StringBuilder();
                    for (int i = 0; i < telephonyManager.getPhoneCount(); i++) {
                        try {
                            String cardImsi = getImsiBySlot(i, telephonyManager);
                            if (cardImsi != null && !cardImsi.isEmpty()) {
                                imsiBuilder.append("卡槽").append(i + 1).append(": ").append(cardImsi).append("\n");
                            }
                        } catch (Exception e) {
                            imsiBuilder.append("卡槽").append(i + 1).append(": 获取失败\n");
                        }
                    }
                    imsi = imsiBuilder.toString();
                } else {
                    // 单卡设备
                    imsi = telephonyManager.getSubscriberId();
                }
            } else {
                // Android 8.0以下版本
                imsi = telephonyManager.getSubscriberId();
            }

            if (imsi != null && !imsi.isEmpty()) {
                Log.d(TAG, "imsi is " + imsi);
            } else {
                Log.d(TAG, "can not get imsi");
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }
        return imsi;
    }

    private String getImsiBySlot(int slotIndex, TelephonyManager telephonyManager) {
        try {
            @SuppressLint("SoonBlockedPrivateApi") Method getSubscriberIdMethod = telephonyManager.getClass().getDeclaredMethod("getSubscriberId", int.class);
            return (String) getSubscriberIdMethod.invoke(telephonyManager, slotIndex);
        } catch (Exception e) {
            return null;
        }
    }
}

