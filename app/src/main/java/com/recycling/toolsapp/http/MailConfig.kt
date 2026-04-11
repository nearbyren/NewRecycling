package com.recycling.toolsapp.http

import java.io.File

class MailConfig private constructor(
    val host: String,
    val port: Int,
    val username: String,
    val password: String?,
    val oauthToken: String?,
    val recipients: List<String>,
    val subject: String,
    val body: String,
    val attachments: List<File>
) {
    class Builder {
         lateinit var host: String
         var port: Int = 587
         lateinit var username: String
         var password: String? = null
         var oauthToken: String? = null
         val recipients = mutableListOf<String>()
         var subject: String = ""
         var body: String = ""
         val attachments = mutableListOf<File>()

        fun setHost(host: String) = apply { this.host = host }
        fun setPort(port: Int) = apply { this.port = port }
        fun setUsername(username: String) = apply { this.username = username }
        fun setPassword(password: String) = apply {
            this.password = password
            this.oauthToken = null
        }
        fun setOauthToken(token: String) = apply {
            this.oauthToken = token
            this.password = null
        }
        fun setRecipient(email: String) = apply { recipients.add(email) }
        fun setSubject(subject: String) = apply { this.subject = subject }
        fun setBody(body: String) = apply { this.body = body }
        fun setAttach(file: File) = apply { attachments.add(file) }

        fun build(): MailConfig {
            // 修复后的验证逻辑
            require(this::host.isInitialized) { "SMTP host must be set" }
            require(this::username.isInitialized) { "Username must be set" }
            require(recipients.isNotEmpty()) { "At least one recipient required" }
            require(password != null || oauthToken != null) {
                "Authentication method required: set either password or OAuth token"
            }

            return MailConfig(
                host, port, username, password, oauthToken,
                recipients, subject, body, attachments
            )
        }
    }
}
