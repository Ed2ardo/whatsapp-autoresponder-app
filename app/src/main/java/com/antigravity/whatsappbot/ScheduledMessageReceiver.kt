package com.antigravity.whatsappbot

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import java.net.URLEncoder

class ScheduledMessageReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_CONTACT = "extra_contact"
        const val EXTRA_TEXT = "extra_text"
        const val CHANNEL_ID = "scheduled_messages_channel"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val msgId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: ""
        val contact = intent.getStringExtra(EXTRA_CONTACT) ?: "Contacto"
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""

        val prefs = AppPreferences(context)
        prefs.markScheduledMessageSent(msgId)

        // Broadcast to in-app live console
        val logIntent = Intent(WhatsAppNotificationListener.ACTION_NEW_LOG).apply {
            putExtra(WhatsAppNotificationListener.EXTRA_LOG_MESSAGE, "⏰ Hora alcanzada: Mensaje programado para $contact listo para enviar.")
            setPackage(context.packageName)
        }
        context.sendBroadcast(logIntent)

        // Create WhatsApp intent
        val cleanPhone = contact.filter { it.isDigit() || it == '+' }
        val encodedText = try {
            URLEncoder.encode(text, "UTF-8")
        } catch (e: Exception) {
            text
        }

        val sendIntent = if (cleanPhone.length >= 7) {
            val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=$encodedText")
            Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getActivity(context, msgId.hashCode(), sendIntent, flags)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mensajes Programados",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones para enviar mensajes programados de WhatsApp"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_calendar)
            .setContentTitle("⏰ Enviar a $contact")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_send, "🚀 Enviar por WhatsApp", pendingIntent)
            .build()

        notificationManager.notify(msgId.hashCode(), notification)
    }
}
