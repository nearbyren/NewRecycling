package com.recycling.toolsapp.utils

/***
 *
 */
enum class EnumSignal(val desc: String, val code: String) {
    EXCELLENT("优秀", "0"),
    GOOD("良好","1"),
    MODERATE("一般", "2"),
    POOR("差", "3"),
    NO_SERVICE("无服务", "4");


    companion object {

        fun findByCode(desc: String): EnumSignal? {
            return EnumSignal.values().firstOrNull { it.desc == desc }
        }

        fun getDescByCode(desc: String): String {
            return findByCode(desc)?.code ?: "异常"
        }
    }
}

