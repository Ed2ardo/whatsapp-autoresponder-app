package com.antigravity.whatsappbot

import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.antigravity.whatsappbot.databinding.ActivityMainBinding
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra(WhatsAppNotificationListener.EXTRA_LOG_MESSAGE) ?: return
            runOnUiThread {
                val currentText = binding.tvLogs.text.toString()
                binding.tvLogs.text = "$currentText\n$message"
                binding.scrollLogs.post { binding.scrollLogs.fullScroll(View.FOCUS_DOWN) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)

        setupUI()
        loadPreferences()
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()

        val filter = IntentFilter(WhatsAppNotificationListener.ACTION_NEW_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(logReceiver)
        } catch (e: Exception) {}
    }

    private fun checkNotificationPermission() {
        val isGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        if (isGranted) {
            binding.cardPermission.visibility = View.GONE
        } else {
            binding.cardPermission.visibility = View.VISIBLE
        }
    }

    private fun setupUI() {
        binding.btnGrantPermission.setOnClickListener {
            // Opens Android Notification Access Settings
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        binding.btnStartTime.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                prefs.startHour = hour
                prefs.startMinute = minute
                updateTimeButtons()
            }, prefs.startHour, prefs.startMinute, false).show()
        }

        binding.btnEndTime.setOnClickListener {
            TimePickerDialog(this, { _, hour, minute ->
                prefs.endHour = hour
                prefs.endMinute = minute
                updateTimeButtons()
            }, prefs.endHour, prefs.endMinute, false).show()
        }

        binding.btnSave.setOnClickListener {
            savePreferences()
            Toast.makeText(this, "✅ Configuración guardada con éxito", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateTimeButtons() {
        binding.btnStartTime.text = String.format(Locale.getDefault(), "Inicio: %02d:%02d", prefs.startHour, prefs.startMinute)
        binding.btnEndTime.text = String.format(Locale.getDefault(), "Fin: %02d:%02d", prefs.endHour, prefs.endMinute)
    }

    private fun loadPreferences() {
        binding.switchMaster.isChecked = prefs.isBotEnabled
        binding.switchAi.isChecked = prefs.isAiMode
        binding.switchSchedule.isChecked = prefs.isScheduleEnabled
        binding.etOutOfOfficeMsg.setText(prefs.outOfOfficeMessage)
        binding.etApiKey.setText(prefs.geminiApiKey)
        binding.etSystemPrompt.setText(prefs.systemPrompt)

        updateTimeButtons()
    }

    private fun savePreferences() {
        prefs.isBotEnabled = binding.switchMaster.isChecked
        prefs.isAiMode = binding.switchAi.isChecked
        prefs.isScheduleEnabled = binding.switchSchedule.isChecked
        prefs.outOfOfficeMessage = binding.etOutOfOfficeMsg.text.toString()
        prefs.geminiApiKey = binding.etApiKey.text.toString().trim()
        prefs.systemPrompt = binding.etSystemPrompt.text.toString().trim()
    }
}
