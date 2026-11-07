package com.antigravity.whatsappbot

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat

object NotificationUtils {

    data class ReplyAction(
        val pendingIntent: PendingIntent,
        val remoteInput: RemoteInput
    )

    /**
     * Traverses the notification and its Wearable extensions to extract
     * the "Reply / Responder" action and its corresponding RemoteInput.
     */
    fun extractReplyAction(notification: Notification): ReplyAction? {
        val actions = notification.actions ?: return null

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (ri in remoteInputs) {
                // Find input that accepts free-form text or matches reply result keys
                if (ri.allowFreeFormInput || ri.resultKey != null) {
                    val pIntent = action.actionIntent ?: continue
                    return ReplyAction(pIntent, ri)
                }
            }
        }

        // Fallback: Check WearableExtender
        val wearableExtender = NotificationCompat.WearableExtender(notification)
        for (action in wearableExtender.actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (ri in remoteInputs) {
                if (ri.allowFreeFormInput || ri.resultKey != null) {
                    val pIntent = action.actionIntent ?: continue
                    // Convert androidx RemoteInput to framework RemoteInput if needed
                    val frameworkInput = RemoteInput.Builder(ri.resultKey)
                        .setLabel(ri.label)
                        .setChoices(ri.choices)
                        .setAllowFreeFormInput(ri.allowFreeFormInput)
                        .addExtras(ri.extras)
                        .build()
                    return ReplyAction(pIntent, frameworkInput)
                }
            }
        }

        return null
    }

    /**
     * Simulates the native Android Quick Reply by attaching the text into
     * RemoteInput and dispatching the action PendingIntent.
     */
    fun sendReply(context: Context, replyAction: ReplyAction, replyText: CharSequence): Boolean {
        return try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(replyAction.remoteInput.resultKey, replyText)
            RemoteInput.addResultsToIntent(arrayOf(replyAction.remoteInput), intent, bundle)
            replyAction.pendingIntent.send(context, 0, intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
