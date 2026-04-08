package com.recycling.toolsapps.utils


import com.google.gson.JsonParser
import com.serial.port.utils.Loge

class CommandParser {
    companion object {
        private val validCommands = setOf("heartBeat", "ota", "update", "status")

        fun parseCommand(jsonString: String): String? {
            return try {
                val jsonElement = JsonParser.parseString(jsonString)
                if (!jsonElement.isJsonObject) {
                    return null
                }

                val jsonObj = jsonElement.asJsonObject
                if (!jsonObj.has("cmd")) {
                    return null
                }

                jsonObj.get("cmd").asString
            } catch (e: Exception) {
                null
            }
        }
    }
}

fun main() {
    val json = """{"cmd":"ota","version":"1.285","url":"","md5":"","sn":"0136004ST00041"}"""
    Loge.e("Extracted command: ${CommandParser.parseCommand(json)}")
}
