package com.recycling.toolsapp.http

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
import javax.activation.DataHandler
import javax.activation.FileDataSource
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart


object MailSender {

    fun enqueueBackgroundSend(context: Context, config: MailConfig) {
        val data = workDataOf(
            "host" to config.host,
            "port" to config.port,
            "username" to config.username,
            "password" to config.password,
            "oauthToken" to config.oauthToken,
            "recipients" to config.recipients.toTypedArray(),
            "subject" to config.subject,
            "body" to config.body,
            "attachments" to config.attachments.map { it.absolutePath }.toTypedArray()
        )

        WorkManager.getInstance(context)
            .enqueue(
                OneTimeWorkRequestBuilder<MailWorker>()
                    .setInputData(data)
                    .build()
            )
    }

    suspend fun sendDirectly(config: MailConfig): Result = withContext(Dispatchers.IO) {
        try {
            val session = createMailSession(config)
            val message = createEmailMessage(session, config)
            Transport.send(message)
            Result.Success
        } catch (e: Exception) {
            Result.Failure(e)
        }
    }

    private fun createMailSession(config: MailConfig): Session {
        val props = Properties().apply {
            put("mail.smtp.auth", "true")
            put("mail.smtp.starttls.enable", "true")
            put("mail.smtp.host", config.host)
            put("mail.smtp.port", config.port.toString())

            config.oauthToken?.let {
                put("mail.smtp.auth.mechanisms", "XOAUTH2")
                put("mail.smtp.sasl.enable", "true")
                put("mail.smtp.sasl.mechanisms", "XOAUTH2")
                put("mail.smtp.auth.login.disable", "true")
                put("mail.smtp.auth.plain.disable", "true")
            }
        }

        return Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return if (config.oauthToken != null) {
                    PasswordAuthentication("\u0000${config.username}\u0000${config.oauthToken}", "")
                } else {
                    PasswordAuthentication(config.username, config.password ?: "")
                }
            }
        })
    }

    private fun createEmailMessage(session: Session, config: MailConfig): MimeMessage {
        return MimeMessage(session).apply {
            setFrom(InternetAddress(config.username))
            config.recipients.forEach {
                addRecipient(Message.RecipientType.TO, InternetAddress(it))
            }
            subject = config.subject

            val multipart = MimeMultipart().apply {
                addBodyPart(MimeBodyPart().apply {
                    setText(config.body, "utf-8")
                })

                config.attachments.forEach { file ->
                    if (file.exists()) {
                        addBodyPart(MimeBodyPart().apply {
                            dataHandler = DataHandler(FileDataSource(file))
                            fileName = file.name
                        })
                    }
                }
            }

            setContent(multipart)
        }
    }

    sealed class Result {
        object Success : Result()
        data class Failure(val exception: Throwable) : Result()
    }
}

