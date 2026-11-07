package com.antigravity.whatsappbot

import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.antigravity.whatsappbot.databinding.ActivityMainBinding
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private lateinit var geminiManager: GeminiManager

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
        geminiManager = GeminiManager(prefs)

        setupNavigation()
        setupCopilotUI()
        setupRulesUI()
        setupSettingsUI()

        loadPreferences()
        updateStatusUI()
        renderCopilotChat()
        renderCustomRules()
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()

        val filter = IntentFilter(WhatsAppNotificationListener.ACTION_NEW_LOG)
        ContextCompat.registerReceiver(this, logReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(logReceiver)
        } catch (e: Exception) {}
    }

    private fun checkNotificationPermission() {
        val isGranted = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
        binding.cardPermission.visibility = if (isGranted) View.GONE else View.VISIBLE
    }

    // =========================================================================
    // NAVIGATION TABS
    // =========================================================================
    private fun setupNavigation() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showTab(copilot = true, rules = false, settings = false)
                    1 -> {
                        showTab(copilot = false, rules = true, settings = false)
                        renderCustomRules()
                    }
                    2 -> showTab(copilot = false, rules = false, settings = true)
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun showTab(copilot: Boolean, rules: Boolean, settings: Boolean) {
        binding.layoutCopilotTab.visibility = if (copilot) View.VISIBLE else View.GONE
        binding.layoutRulesTab.visibility = if (rules) View.VISIBLE else View.GONE
        binding.layoutSettingsTab.visibility = if (settings) View.VISIBLE else View.GONE
    }

    // =========================================================================
    // TAB 1: COPILOTO IA & ESTADO DEL DÍA
    // =========================================================================
    private fun setupCopilotUI() {
        // Reset/Clear Status Button
        binding.btnResetStatus.setOnClickListener {
            prefs.clearCurrentStatus()
            updateStatusUI()
            prefs.addCopilotMessage("copilot", "🧹 Has limpiado tu estado del día. Ahora el bot responderá con su personalidad habitual sin contexto especial.")
            renderCopilotChat()
            Toast.makeText(this, "Estado del día restablecido", Toast.LENGTH_SHORT).show()
        }

        // Quick Suggestion Chips
        binding.chipStatusPhoto.setOnClickListener {
            insertChipText("Acabo de subir un estado a WhatsApp con una foto de ")
        }
        binding.chipSelling.setOnClickListener {
            insertChipText("Si me preguntan por lo que estoy vendiendo, diles que ")
        }
        binding.chipLocation.setOnClickListener {
            insertChipText("Ando por acá en ")
        }
        binding.chipBusy.setOnClickListener {
            insertChipText("Hoy ando ocupado todo el día estudiando y no voy a salir.")
        }
        binding.chipIfTheyAsk.setOnClickListener {
            insertChipText("Si preguntan por ")
        }

        // Send Button
        binding.btnSendCopilot.setOnClickListener {
            sendCopilotMessage()
        }
    }

    private fun insertChipText(text: String) {
        binding.etCopilotInput.setText(text)
        binding.etCopilotInput.setSelection(binding.etCopilotInput.text.length)
        binding.etCopilotInput.requestFocus()
    }

    private fun updateStatusUI() {
        val currentStatus = prefs.currentStatusContext
        if (currentStatus.isNotBlank()) {
            binding.tvActiveStatusText.text = "🟢 $currentStatus"
            binding.tvActiveStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            binding.btnResetStatus.visibility = View.VISIBLE
        } else {
            binding.tvActiveStatusText.text = "⚪ No has registrado estados hoy. Escríbele a tu Copiloto aquí abajo para avisarle qué subiste o qué estás haciendo."
            binding.tvActiveStatusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.btnResetStatus.visibility = View.GONE
        }
    }

    private fun renderCopilotChat() {
        binding.llCopilotMessages.removeAllViews()
        val history = prefs.getCopilotHistory()

        if (history.isEmpty()) {
            // Initial Welcome Card
            val welcomeCard = MaterialCardView(this).apply {
                radius = 24f
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
                setContentPadding(32, 28, 32, 28)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(8, 16, 8, 16)
                }
                layoutParams = lp

                val tv = TextView(context).apply {
                    text = "👋 ¡Hola Eduardo! Soy tu Copiloto de IA.\n\nCuéntame si subiste una foto a tus estados de WhatsApp, qué andas haciendo hoy o si quieres que responda algo en específico cuando te pregunten.\n\nYo actualizaré tu perfil y prepararé tus respuestas automáticamente."
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 13f
                    setLineSpacing(6f, 1f)
                }
                addView(tv)
            }
            binding.llCopilotMessages.addView(welcomeCard)
            return
        }

        for (msg in history) {
            val isUser = msg.sender == "user"

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isUser) Gravity.END else Gravity.START
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 8, 0, 8)
                }
                layoutParams = lp
            }

            val card = MaterialCardView(this).apply {
                radius = 24f
                cardElevation = 0f
                setCardBackgroundColor(ContextCompat.getColor(context, if (isUser) R.color.user_bubble else R.color.copilot_bubble))
                setContentPadding(24, 18, 24, 18)

                val cardLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (isUser) setMargins(80, 0, 0, 0) else setMargins(0, 0, 80, 0)
                }
                layoutParams = cardLp

                val tv = TextView(context).apply {
                    text = if (isUser) msg.text else "🤖 ${msg.text}"
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 13f
                    setLineSpacing(4f, 1f)
                }
                addView(tv)
            }

            container.addView(card)
            binding.llCopilotMessages.addView(container)
        }

        binding.scrollCopilotChat.post {
            binding.scrollCopilotChat.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun sendCopilotMessage() {
        val text = binding.etCopilotInput.text.toString().trim()
        if (text.isBlank()) return

        binding.etCopilotInput.setText("")
        prefs.addCopilotMessage("user", text)
        renderCopilotChat()

        binding.pbCopilotLoading.visibility = View.VISIBLE
        binding.btnSendCopilot.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    geminiManager.processCopilotInstruction(text)
                }

                prefs.addCopilotMessage("copilot", response.replyToUser)
                updateStatusUI()

                if (response.newCustomRule != null) {
                    renderCustomRules()
                    Toast.makeText(this@MainActivity, "🎯 Nueva regla agregada: ${response.newCustomRule.trigger}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                prefs.addCopilotMessage("copilot", "⚠️ Error al conectar con IA: ${e.message}. Guardé tu texto como estado local.")
                prefs.currentStatusContext = text
                updateStatusUI()
            } finally {
                binding.pbCopilotLoading.visibility = View.GONE
                binding.btnSendCopilot.isEnabled = true
                renderCopilotChat()
            }
        }
    }

    // =========================================================================
    // TAB 2: REGLAS DINÁMICAS & FAQ ("Si preguntan X -> responder Y")
    // =========================================================================
    private fun setupRulesUI() {
        binding.btnAddRule.setOnClickListener {
            showAddRuleDialog()
        }
    }

    private fun renderCustomRules() {
        binding.llRulesContainer.removeAllViews()
        val rules = prefs.getCustomRules()

        if (rules.isEmpty()) {
            binding.tvEmptyRules.visibility = View.VISIBLE
            return
        }

        binding.tvEmptyRules.visibility = View.GONE

        for (rule in rules) {
            val card = MaterialCardView(this).apply {
                radius = 24f
                cardElevation = 2f
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 16)
                }
                layoutParams = lp

                val innerLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 20, 24, 20)
                }

                // Row 1: Trigger & Switch & Delete
                val topRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val tvTrigger = TextView(context).apply {
                    val lpTrigger = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = lpTrigger
                    text = "🔍 Si contiene: \"${rule.trigger}\""
                    setTextColor(ContextCompat.getColor(context, R.color.accent))
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                }
                topRow.addView(tvTrigger)

                val sw = SwitchMaterial(context).apply {
                    isChecked = rule.enabled
                    setOnCheckedChangeListener { _, isChecked ->
                        prefs.toggleCustomRule(rule.id, isChecked)
                    }
                }
                topRow.addView(sw)

                val btnDelete = ImageView(context).apply {
                    setImageResource(R.drawable.ic_delete)
                    setPadding(12, 12, 12, 12)
                    setOnClickListener {
                        prefs.deleteCustomRule(rule.id)
                        renderCustomRules()
                        Toast.makeText(context, "Regla eliminada", Toast.LENGTH_SHORT).show()
                    }
                }
                topRow.addView(btnDelete)

                innerLayout.addView(topRow)

                // Row 2: Response
                val tvResponse = TextView(context).apply {
                    text = "💬 Responde: \"${rule.response}\""
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 12f
                    setPadding(0, 10, 0, 0)
                }
                innerLayout.addView(tvResponse)

                addView(innerLayout)
            }

            binding.llRulesContainer.addView(card)
        }
    }

    private fun showAddRuleDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }

        val tvTriggerLabel = TextView(this).apply {
            text = "Palabras clave que activan la regla (ej: moto, precio moto):"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 12f
        }
        dialogView.addView(tvTriggerLabel)

        val etTrigger = EditText(this).apply {
            hint = "ej: moto, precio moto, la moto"
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 13f
        }
        dialogView.addView(etTrigger)

        val tvResponseLabel = TextView(this).apply {
            text = "Respuesta oficial que enviará el bot:"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 12f
            setPadding(0, 24, 0, 0)
        }
        dialogView.addView(tvResponseLabel)

        val etResponse = EditText(this).apply {
            hint = "ej: Hola bro, ya la vendí la semana pasada 👍"
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 13f
            minLines = 2
        }
        dialogView.addView(etResponse)

        AlertDialog.Builder(this)
            .setTitle("Nueva Regla Dinámica")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val trigger = etTrigger.text.toString().trim()
                val response = etResponse.text.toString().trim()

                if (trigger.isNotBlank() && response.isNotBlank()) {
                    val newRule = CustomRule(trigger = trigger, response = response, enabled = true)
                    prefs.addOrUpdateCustomRule(newRule)
                    renderCustomRules()
                    Toast.makeText(this, "✅ Regla guardada con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Debes llenar tanto la pregunta como la respuesta", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // =========================================================================
    // TAB 3: AJUSTES & CONSOLA EN VIVO
    // =========================================================================
    private fun setupSettingsUI() {
        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.isBotEnabled = isChecked
            Toast.makeText(this, if (isChecked) "Bot activado" else "Bot pausado", Toast.LENGTH_SHORT).show()
        }

        binding.btnGrantPermission.setOnClickListener {
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
