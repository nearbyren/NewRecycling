package com.recycling.toolsapps.utils

import android.telephony.CellSignalStrengthLte
import android.telephony.SignalStrength

class SignalStrengthAnalyzer {

    /**
     * 分析信号强度并返回可读结果
     */
    fun analyzeSignalStrength(signalStrength: SignalStrength): SignalAnalysisResult {
        return try {
            // 获取所有信号强度
            val lteSignals = signalStrength.getCellSignalStrengths(CellSignalStrengthLte::class.java)

            if (lteSignals.isNotEmpty()) {
                // 使用第一个 LTE 信号（通常是最主要的）
                analyzeLteSignal(lteSignals[0])
            } else {
                // 如果没有 LTE 信号，尝试其他网络类型或返回未知
                SignalAnalysisResult.unknown()
            }
        } catch (e: Exception) {
            SignalAnalysisResult.error(e.message ?: "分析信号时发生错误")
        }
    }

    /**
     * 分析 LTE 信号强度
     */
    private fun analyzeLteSignal(lteSignal: CellSignalStrengthLte): SignalAnalysisResult {
        val rsrp = lteSignal.rsrp // Reference Signal Received Power
        val rssi = lteSignal.rssi // Received Signal Strength Indicator
        val rsrq = lteSignal.rsrq // Reference Signal Received Quality
        val rssnr = lteSignal.rssnr // Signal to Noise Ratio
        val level = lteSignal.level // Android 计算的信号等级 (0-4)

        return SignalAnalysisResult(
            signalType = "LTE",
            primaryMetric = rsrp,
            secondaryMetric = rsrq,
            signalLevel = level,
            rawData = mapOf(
                "RSRP" to rsrp,
                "RSSI" to rssi,
                "RSRQ" to rsrq,
                "RSSNR" to rssnr,
                "Level" to level
            ),
            quality = calculateLteQuality(rsrp, rsrq, level),
            description = generateSignalDescription(rsrp, rsrq, level)
        )
    }

    /**
     * 计算 LTE 信号质量
     */
    private fun calculateLteQuality(rsrp: Int, rsrq: Int, level: Int): SignalQuality {
        return when {
            // 基于 RSRP 的判断（主要指标）
            rsrp >= -85 -> SignalQuality.EXCELLENT
            rsrp >= -95 -> SignalQuality.GOOD
            rsrp >= -105 -> SignalQuality.MODERATE
            rsrp >= -115 -> SignalQuality.POOR
            else -> SignalQuality.NO_SERVICE
        }
    }

    /**
     * 生成信号描述
     */
    private fun generateSignalDescription(rsrp: Int, rsrq: Int, level: Int): String {
        val quality = calculateLteQuality(rsrp, rsrq, level)
        val levelDesc = when (level) {
            0 -> "无服务"
            1 -> "极差"
            2 -> "差"
            3 -> "一般"
            4 -> "好"
            else -> "未知"
        }

        return "LTE信号: $levelDesc (RSRP: ${rsrp}dBm, RSRQ: ${rsrq}dB, 等级: $level)"
    }

    /**
     * 获取简化的信号等级描述
     */
    fun getSimpleSignalLevel(signalStrength: SignalStrength): String {
        val analysis = analyzeSignalStrength(signalStrength)
        return when (analysis.quality) {
            SignalQuality.EXCELLENT -> "优秀"
            SignalQuality.GOOD -> "良好"
            SignalQuality.MODERATE -> "一般"
            SignalQuality.POOR -> "差"
            SignalQuality.NO_SERVICE -> "无服务"
        }
    }

    /**
     * 获取详细的信号信息
     */
    fun getDetailedSignalInfo(signalStrength: SignalStrength): Map<String, Any> {
        val analysis = analyzeSignalStrength(signalStrength)
        return mapOf(
            "信号类型" to analysis.signalType,
            "信号质量" to analysis.quality.displayName,
            "信号等级" to analysis.signalLevel,
            "RSRP" to "${analysis.rawData["RSRP"]} dBm",
            "RSRQ" to "${analysis.rawData["RSRQ"]} dB",
            "RSSI" to "${analysis.rawData["RSSI"]} dBm",
            "描述" to analysis.description
        )
    }
}

/**
 * 信号分析结果数据类
 */
data class SignalAnalysisResult(
    val signalType: String,
    val primaryMetric: Int, // 主要指标值 (RSRP for LTE)
    val secondaryMetric: Int, // 次要指标值 (RSRQ for LTE)
    val signalLevel: Int, // Android 信号等级 0-4
    val rawData: Map<String, Int>,
    val quality: SignalQuality,
    val description: String
) {
    companion object {
        fun unknown() = SignalAnalysisResult(
            signalType = "未知",
            primaryMetric = 0,
            secondaryMetric = 0,
            signalLevel = 0,
            rawData = emptyMap(),
            quality = SignalQuality.NO_SERVICE,
            description = "无法获取信号信息"
        )

        fun error(message: String) = SignalAnalysisResult(
            signalType = "错误",
            primaryMetric = 0,
            secondaryMetric = 0,
            signalLevel = 0,
            rawData = emptyMap(),
            quality = SignalQuality.NO_SERVICE,
            description = "错误: $message"
        )
    }
}

/**
 * 信号质量枚举
 */
enum class SignalQuality(val displayName: String) {
    EXCELLENT("优秀"),
    GOOD("良好"),
    MODERATE("一般"),
    POOR("差"),
    NO_SERVICE("无服务")
}