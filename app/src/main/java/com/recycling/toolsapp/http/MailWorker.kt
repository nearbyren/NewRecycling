package com.recycling.toolsapp.http

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File

class MailWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val config = MailConfig.Builder().apply {
                host = inputData.getString("host") ?: ""
                port = inputData.getInt("port", 587)
                username = inputData.getString("username") ?: ""
                inputData.getString("password")?.let { setPassword(it) }
                inputData.getString("oauthToken")?.let { setOauthToken(it) }

                inputData.getStringArray("recipients")?.forEach { setRecipient(it) }
                subject = inputData.getString("subject") ?: ""
                body = inputData.getString("body") ?: ""

                inputData.getStringArray("attachments")?.forEach {
                    setAttach(File(it))
                }
            }.build()

            when (MailSender.sendDirectly(config)) {
                is MailSender.Result.Success -> Result.success()
                is MailSender.Result.Failure -> Result.failure()
            }
        } catch (e: Exception) {
            Result.failure(workDataOf("error" to e.message))
        }
    }
}
