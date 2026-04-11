package com.recycling.toolsapp.utils

import android.annotation.SuppressLint
import android.content.Context
import android.telephony.SubscriptionManager
import java.lang.reflect.Method

@Suppress("DEPRECATION")
object SubscriptionManagerReflector {

    /**
     * 通过 SubscriptionManager 获取多卡信息
     */
    @SuppressLint("NewApi") fun getSubscriptionInfo(context: Context): List<Map<String, String?>> {
        val result = mutableListOf<Map<String, String?>>()

        try {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            if (subscriptionManager == null) {
                println("无法获取 SubscriptionManager")
                return emptyList()
            }

            // 获取活跃的订阅列表
            val getActiveSubscriptionInfoListMethod: Method? = try {
                subscriptionManager.javaClass.getMethod("getActiveSubscriptionInfoList")
            } catch (e: Exception) {
                null
            }

            val subscriptionList = getActiveSubscriptionInfoListMethod?.invoke(subscriptionManager) as? List<*>

            subscriptionList?.forEach { subscriptionInfo ->
                val infoMap = mutableMapOf<String, String?>()

                try {
                    val infoClass = subscriptionInfo!!.javaClass

                    // 获取 IMSI
                    val getImsiMethod = infoClass.getMethod("getImsi")
                    infoMap["imsi"] = getImsiMethod.invoke(subscriptionInfo) as? String

                    // 获取 ICCID
                    val getIccidMethod = infoClass.getMethod("getIccid")
                    infoMap["iccid"] = getIccidMethod.invoke(subscriptionInfo) as? String

                    // 获取卡槽索引
                    val getSimSlotIndexMethod = infoClass.getMethod("getSimSlotIndex")
                    infoMap["slotIndex"] = (getSimSlotIndexMethod.invoke(subscriptionInfo) as? Int)?.toString()

                    result.add(infoMap)
                } catch (e: Exception) {
                    println("处理订阅信息时出错: ${e.message}")
                }
            }
        } catch (e: Exception) {
            println("获取订阅信息失败: ${e.message}")
        }

        return result
    }
}