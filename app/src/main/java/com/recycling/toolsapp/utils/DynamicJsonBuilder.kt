package com.recycling.toolsapp.utils


import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.serial.port.utils.Loge

class DynamicJsonBuilder {
    private val root = JsonObject()
    private val gson = GsonBuilder().setPrettyPrinting().create()

    fun addPrimitive(key: String, value: Any): DynamicJsonBuilder {
        when (value) {
            is String -> root.addProperty(key, value)
            is Number -> root.addProperty(key, value)
            is Boolean -> root.addProperty(key, value)
            else -> throw IllegalArgumentException("Unsupported primitive type")
        }
        return this
    }

    fun addObject(key: String, block: DynamicJsonBuilder.() -> Unit): DynamicJsonBuilder {
        val builder = DynamicJsonBuilder().apply(block)
        root.add(key, builder.root)
        return this
    }

    fun addArray(key: String, block: JsonArrayBuilder.() -> Unit): DynamicJsonBuilder {
        val array = JsonArrayBuilder().apply(block).build()
        root.add(key, array)
        return this
    }

    fun build(): String = gson.toJson(root)

    class JsonArrayBuilder {
        private val array = JsonArray()

        fun addElement(value: Any) {
            when (value) {
                is String -> array.add(value)
                is Number -> array.add(value)
                is Boolean -> array.add(value)
                else -> throw IllegalArgumentException("Unsupported array element type")
            }
        }

        fun addObject(block: DynamicJsonBuilder.() -> Unit) {
            array.add(DynamicJsonBuilder().apply(block).root)
        }

        fun build(): JsonArray = array
    }
}

fun main() {
    val json = DynamicJsonBuilder()
        .addPrimitive("cmd", "heartBeat")
        .addPrimitive("signal", 13)
        .addObject("gps") {  }
        .addArray("stateList") {
            addObject {
                addPrimitive("smoke", 0)
                addPrimitive("capacity", 0)
                addPrimitive("irState", 0)
                addPrimitive("weigh", 20.47)
                addPrimitive("doorStatus", 0)
                addPrimitive("cabinId", "20250118161240405726")
            }
        }
        .addPrimitive("timestamp", "1756196615299")
        .build()

    Loge.e(json)
}
