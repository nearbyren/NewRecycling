package com.serial.port

import java.io.File

class ConfigurationSdk(configurationBuilder: ConfigurationBuilder) {

    val device: File = configurationBuilder.device
    val baudRate: Int = configurationBuilder.baudRate

    private var msgHead: ByteArray?=null
    private var msgTail: ByteArray?=null

    //默认配置
    private val sDebug: Boolean
    private val sIncludeThread: Boolean
    private val sLogType: String


    init {
        this.msgHead = configurationBuilder.msgHead
        this.msgTail = configurationBuilder.msgTail
        this.sDebug = configurationBuilder.sDebug
        this.sIncludeThread = configurationBuilder.sIncludeThread
        this.sLogType = configurationBuilder.sLogType
    }

    fun issDebug(): Boolean {
        return sDebug
    }

    fun issIncludeThread(): Boolean {
        return sIncludeThread
    }

    fun getsLogType(): String {
        return sLogType
    }

    class ConfigurationBuilder(internal val device: File, val baudRate: Int) {
        var msgHead: ByteArray?=null
        var msgTail: ByteArray?=null
        var sDebug: Boolean = false
        var sIncludeThread: Boolean = false
        var sLogType: String = "inspection"

        fun log(sLogType: String, sDebug: Boolean, sIncludeThread: Boolean): ConfigurationBuilder {
            this.sLogType = sLogType
            this.sDebug = sDebug
            this.sIncludeThread = sIncludeThread
            return this
        }

        fun msgHead(msgHead: ByteArray): ConfigurationBuilder {
            this.msgHead = msgHead
            return this
        }

        fun msgTail(msgTail: ByteArray): ConfigurationBuilder {
            this.msgTail = msgTail
            return this
        }

        fun build(): ConfigurationSdk {
            return ConfigurationSdk(this)
        }
    }
}
