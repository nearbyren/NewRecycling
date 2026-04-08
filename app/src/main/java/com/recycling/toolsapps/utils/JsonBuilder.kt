package com.recycling.toolsapps.utils

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.Charset

/**
 * JSON构建工具类
 * 支持动态字段和可变键名
 */
object JsonBuilder {

    // 使用Gson实例，设置漂亮打印格式
    private val gson: Gson by lazy {
        GsonBuilder().setPrettyPrinting().create()
    }

    /***
     * obj 构建上传服务器json字符串
     */
    fun convertToJsonString(obj: Any): String {
        return gson.toJson(obj)
    }

    /**
     * 构建JSON对象
     * @param block 构建块的lambda表达式
     * @return 构建完成的JsonObject
     */
    fun build(block: JsonObjectBuilder.() -> Unit): JsonObject {
        return JsonObjectBuilder().apply(block).build()
    }

    /**
     * 将JsonObject转换为字节数组
     * @param jsonObject 要转换的JsonObject
     * @param charset 字符集，默认为UTF-8
     * @return 字节数组
     */
    fun toByteArray(jsonObject: JsonObject, charset: Charset = Charsets.UTF_8): ByteArray {
        return gson.toJson(jsonObject).toByteArray(charset)
    }

    /**
     * 将ByteArray转换为字符串
     * @param byteArray 要转换的byteArray
     * @param charset 字符集，默认为UTF-8
     * @return 字符串
     */
    fun toByteArrayToStringIsPretty(byteArray: ByteArray): String {
        if (byteArray.isEmpty()) return "null"
        return String(byteArray, Charsets.UTF_8).toString()
    }

    /**
     * 将ByteArray转换为字符串，如果是JSON则压缩格式
     * @param byteArray 要转换的byteArray 美化
     * @return 字符串
     */
    fun toByteArrayToStringNotPretty(byteArray: ByteArray): String {
        if (byteArray.isEmpty()) return "null"

        val originalString = String(byteArray, Charsets.UTF_8)

        return try {
            // 尝试解析为JSON并重新序列化为紧凑格式
            val json = Json { prettyPrint = false } // 禁用美化输出
            val jsonElement = json.parseToJsonElement(originalString)
            json.encodeToString(jsonElement)
        } catch (e: Exception) {
            // 如果不是有效的JSON，返回原始字符串
            originalString
        }
    }

    /**
     * JSON对象构建器类
     */
    class JsonObjectBuilder {
        private val jsonObject = JsonObject()

        /**
         * 添加字符串属性
         * @param key 键名
         * @param value 字符串值
         */
        fun addProperty(key: String, value: String) {
            jsonObject.addProperty(key, value)
        }

        /**
         * 添加数字属性
         * @param key 键名
         * @param value 数字值
         */
        fun addProperty(key: String, value: Number) {
            jsonObject.addProperty(key, value)
        }

        /**
         * 添加布尔属性
         * @param key 键名
         * @param value 布尔值
         */
        fun addProperty(key: String, value: Boolean) {
            jsonObject.addProperty(key, value)
        }

        /**
         * 添加字符属性
         * @param key 键名
         * @param value 字符值
         */
        fun addProperty(key: String, value: Char) {
            jsonObject.addProperty(key, value)
        }

        /**
         * 添加JSON数组
         * @param key 键名
         * @param block 数组构建块的lambda表达式
         */
        fun addArray(key: String, block: JsonArrayBuilder.() -> Unit) {
            val arrayBuilder = JsonArrayBuilder().apply(block)
            jsonObject.add(key, arrayBuilder.build())
        }

        /**
         * 添加嵌套JSON对象
         * @param key 键名
         * @param block 对象构建块的lambda表达式
         */
        fun addObject(key: String, block: JsonObjectBuilder.() -> Unit) {
            val objectBuilder = JsonObjectBuilder().apply(block)
            jsonObject.add(key, objectBuilder.build())
        }

        /**
         * 添加原始JSON元素
         * @param key 键名
         * @param element JSON元素
         */
        fun add(key: String, element: JsonElement) {
            jsonObject.add(key, element)
        }

        /**
         * 构建最终的JsonObject
         */
        fun build(): JsonObject {
            return jsonObject
        }
    }

    /**
     * JSON数组构建器类
     */
    class JsonArrayBuilder {
        private val jsonArray = JsonArray()

        /**
         * 添加字符串元素
         * @param value 字符串值
         */
        fun add(value: String) {
            jsonArray.add(value)
        }

        /**
         * 添加数字元素
         * @param value 数字值
         */
        fun add(value: Number) {
            jsonArray.add(value)
        }

        /**
         * 添加布尔元素
         * @param value 布尔值
         */
        fun add(value: Boolean) {
            jsonArray.add(value)
        }

        /**
         * 添加JSON对象元素
         * @param block 对象构建块的lambda表达式
         */
        fun addObject(block: JsonObjectBuilder.() -> Unit) {
            val objectBuilder = JsonObjectBuilder().apply(block)
            jsonArray.add(objectBuilder.build())
        }

        /**
         * 添加原始JSON元素
         * @param element JSON元素
         */
        fun add(element: JsonElement) {
            jsonArray.add(element)
        }

        /**
         * 构建最终的JsonArray
         */
        fun build(): JsonArray {
            return jsonArray
        }
    }

    // 构建JSON对象
//    val jsonObject = JsonBuilder.build {
//        addProperty("cmd", "heartBeat")
//        addProperty("signal", 13)
//
//        // 添加数组
//        addArray("stateList") {
//            for (state in stateList) {
//                addObject {
//                    addProperty("smoke", state.smoke)
//                    addProperty("capacity", state.capacity)
//                    addProperty("irState", state.irState)
//                    addProperty("weigh", state.weigh)
//                    addProperty("doorStatus", state.doorStatus)
//                    addProperty("cabinId", state.cabinId)
//                }
//            }
//        }
//    }
//
//    // 转换为字节数组
//    val byteArray = JsonBuilder.toByteArray(jsonObject)
//    Loge.e("调试socket 发送心跳数据：$jsonObject")

    // 动态字段示例
//    val dynamicFields = mapOf(
//        "field1" to "value1",
//        "field2" to 123,
//        "field3" to true
//    )
//
//    val jsonObject = JsonBuilder.build {
//        addProperty("cmd", "heartBeat")
//        addProperty("signal", 13)
//
//        // 动态添加字段
//        dynamicFields.forEach { (key, value) ->
//            when (value) {
//                is String -> addProperty(key, value)
//                is Number -> addProperty(key, value)
//                is Boolean -> addProperty(key, value)
//            }
//        }
//
//        // 添加数组
//        addArray("stateList") {
//            for (state in stateList) {
//                addObject {
//                    // 动态添加状态字段
//                    val stateFields = mapOf(
//                        "smoke" to state.smoke,
//                        "capacity" to state.capacity,
//                        "irState" to state.irState,
//                        "weigh" to state.weigh,
//                        "doorStatus" to state.doorStatus,
//                        "cabinId" to state.cabinId
//                    )
//
//                    stateFields.forEach { (key, value) ->
//                        when (value) {
//                            is String -> addProperty(key, value)
//                            is Number -> addProperty(key, value)
//                            is Boolean -> addProperty(key, value)
//                        }
//                    }
//                }
//            }
//        }
//    }

    // 复杂嵌套结构示例
//    val jsonObject = JsonBuilder.build {
//        addProperty("timestamp", System.currentTimeMillis())
//        addProperty("deviceId", "device-12345")
//
//        addObject("metadata") {
//            addProperty("version", "1.0")
//            addProperty("type", "heartbeat")
//        }
//
//        addArray("sensors") {
//            addObject {
//                addProperty("name", "temperature")
//                addProperty("value", 23.5)
//                addProperty("unit", "celsius")
//            }
//            addObject {
//                addProperty("name", "humidity")
//                addProperty("value", 45.2)
//                addProperty("unit", "percent")
//            }
//        }
//
//        addArray("stateList") {
//            for (state in stateList) {
//                addObject {
//                    addProperty("smoke", state.smoke)
//                    addProperty("capacity", state.capacity)
//                    addProperty("irState", state.irState)
//                    addProperty("weigh", state.weigh)
//                    addProperty("doorStatus", state.doorStatus)
//                    addProperty("cabinId", state.cabinId)
//                }
//            }
//        }
//    }
}