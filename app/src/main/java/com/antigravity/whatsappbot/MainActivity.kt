package com.antigravity.whatsappbot

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: AppPreferences
    private lateinit var geminiManager: GeminiManager
    private lateinit var scheduleManager: ScheduleManager
    private val ruleEngine = RuleEngine()

    private var currentRulesFilter: String = ""

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra(WhatsAppNotificationListener.EXTRA_LOG_MESSAGE) ?: return
            runOnUiThread {
                val currentText = binding.tvLogs.text.toString()
                binding.tvLogs.text = "$currentText\n$message"
                binding.scrollLogs.post { binding.scrollLogs.fullScroll(View.FOCUS_DOWN) }
                updateHeaderStats()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPreferences(this)
        geminiManager = GeminiManager(prefs)
        scheduleManager = ScheduleManager(prefs)

        setupHeader()
        setupBottomNavigation()
        setupCopilotUI()
        setupRulesUI()
        setupScheduledUI()
        setupTesterUI()
        setupSettingsUI()

        loadPreferences()
        updateHeaderStatus()
        updateHeaderStats()
        updateStatusUI()
        renderCopilotChat()
        renderCustomRules()
        renderScheduledMessages()
    }

    override fun onResume() {
        super.onResume()
        checkNotificationPermission()
        updateHeaderStatus()
        updateHeaderStats()

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
    // 1. TOP HEADER & QUICK CONTROLS
    // =========================================================================
    private fun setupHeader() {
        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.isBotEnabled = isChecked
            updateHeaderStatus()
            Toast.makeText(this, if (isChecked) "🟢 Bot activado" else "⏸️ Bot pausado", Toast.LENGTH_SHORT).show()
        }

        binding.btnQuickSnooze.setOnClickListener {
            if (prefs.isSnoozed()) {
                AlertDialog.Builder(this)
                    .setTitle("Modo Siesta Activo")
                    .setMessage("El bot está silenciado temporalmente. ¿Deseas reactivarlo ahora?")
                    .setPositiveButton("Reactivar Bot") { _, _ ->
                        prefs.clearSnooze()
                        updateHeaderStatus()
                        Toast.makeText(this, "🟢 Bot reactivado", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Mantener en pausa", null)
                    .show()
            } else {
                val options = arrayOf("Pausar por 1 hora", "Pausar por 2 horas", "Pausar por 4 horas")
                AlertDialog.Builder(this)
                    .setTitle("💤 Modo Siesta / No Molestar")
                    .setItems(options) { _, which ->
                        val hours = when (which) {
                            0 -> 1
                            1 -> 2
                            else -> 4
                        }
                        prefs.setSnoozeHours(hours)
                        updateHeaderStatus()
                        Toast.makeText(this, "💤 Bot pausado por $hours hora(s)", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
    }

    private fun updateHeaderStatus() {
        if (!prefs.isBotEnabled) {
            binding.tvHeaderStatusBadge.text = "🔴 Apagado"
            binding.tvHeaderStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.error))
        } else if (prefs.isSnoozed()) {
            val remainingMins = Math.max(1, ((prefs.snoozeUntil - System.currentTimeMillis()) / 60000).toInt())
            binding.tvHeaderStatusBadge.text = "💤 Siesta ($remainingMins min)"
            binding.tvHeaderStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.warning))
        } else {
            binding.tvHeaderStatusBadge.text = "🟢 Activo"
            binding.tvHeaderStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.primary))
        }
    }

    private fun updateHeaderStats() {
        val count = prefs.getTodayRepliesCount()
        binding.tvHeaderStats.text = "• $count respuestas hoy"
    }

    // =========================================================================
    // 2. BOTTOM NAVIGATION BAR
    // =========================================================================
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_copilot -> {
                    showTab(copilot = true, rules = false, tester = false, settings = false)
                    true
                }
                R.id.nav_rules -> {
                    showTab(copilot = false, rules = true, scheduled = false, tester = false, settings = false)
                    renderCustomRules()
                    true
                }
                R.id.nav_scheduled -> {
                    showTab(copilot = false, rules = false, scheduled = true, tester = false, settings = false)
                    renderScheduledMessages()
                    true
                }
                R.id.nav_tester -> {
                    showTab(copilot = false, rules = false, scheduled = false, tester = true, settings = false)
                    true
                }
                R.id.nav_settings -> {
                    showTab(copilot = false, rules = false, scheduled = false, tester = false, settings = true)
                    updateSettingsUIFromPrefs()
                    true
                }
                else -> false
            }
        }
    }

    private fun showTab(copilot: Boolean, rules: Boolean, scheduled: Boolean, tester: Boolean, settings: Boolean) {
        binding.layoutCopilotTab.visibility = if (copilot) View.VISIBLE else View.GONE
        binding.layoutRulesTab.visibility = if (rules) View.VISIBLE else View.GONE
        binding.layoutScheduledTab.visibility = if (scheduled) View.VISIBLE else View.GONE
        binding.layoutTesterTab.visibility = if (tester) View.VISIBLE else View.GONE
        binding.layoutSettingsTab.visibility = if (settings) View.VISIBLE else View.GONE
    }

    // =========================================================================
    // 3. TAB 1: COPILOTO IA & ESTADO DEL DÍA
    // =========================================================================
    private fun setupCopilotUI() {
        binding.btnResetStatus.setOnClickListener {
            prefs.clearCurrentStatus()
            updateStatusUI()
            prefs.addCopilotMessage("copilot", "🧹 Has limpiado tu estado del día. Ahora responderé con tus reglas y personalidad habitual.")
            renderCopilotChat()
            Toast.makeText(this, "Estado del día restablecido", Toast.LENGTH_SHORT).show()
        }

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
            val welcomeCard = MaterialCardView(this).apply {
                radius = 36f
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
                setContentPadding(36, 32, 36, 32)
                setStrokeColor(ContextCompat.getColor(context, R.color.chip_border))
                setStrokeWidth(2)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(12, 16, 12, 16)
                }
                layoutParams = lp

                val tv = TextView(context).apply {
                    text = "👋 ¡Hola Eduardo! Soy tu Copiloto de IA.\n\nCuéntame si subiste una foto a tus estados de WhatsApp, qué andas haciendo hoy o qué responder si preguntan cosas puntuales.\n\nYo actualizaré tu perfil y prepararé tus respuestas automáticamente."
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 13f
                    setLineSpacing(6f, 1f)
                }
                addView(tv)
            }
            binding.llCopilotMessages.addView(welcomeCard)
            return
        }

        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        for (msg in history) {
            val isUser = msg.sender == "user"

            val container = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = if (isUser) Gravity.END else Gravity.START
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 6, 0, 6)
                }
                layoutParams = lp
            }

            val bubble = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val cardLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (isUser) setMargins(70, 0, 0, 0) else setMargins(0, 0, 70, 0)
                }
                layoutParams = cardLp
                setPadding(26, 18, 26, 16)
                background = ContextCompat.getDrawable(context, R.drawable.ic_launcher)?.apply {
                    setTint(ContextCompat.getColor(context, if (isUser) R.color.user_bubble else R.color.copilot_bubble))
                }
            }

            // Sender Label & Text
            val tvText = TextView(this).apply {
                text = if (isUser) msg.text else "🤖 ${msg.text}"
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 13f
                setLineSpacing(4f, 1f)
            }
            bubble.addView(tvText)

            // Timestamp
            val tvTime = TextView(this).apply {
                text = timeFormat.format(Date(msg.timestamp))
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 9f
                gravity = if (isUser) Gravity.END else Gravity.START
                setPadding(0, 6, 0, 0)
            }
            bubble.addView(tvTime)

            container.addView(bubble)
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
                prefs.addCopilotMessage("copilot", "⚠️ Error al procesar con IA: ${e.message}. Guardé tu instrucción localmente.")
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
    // 4. TAB 2: REGLAS DINÁMICAS & FAQ
    // =========================================================================
    private fun setupRulesUI() {
        binding.btnAddRule.setOnClickListener {
            showAddRuleDialog()
        }

        binding.etSearchRules.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentRulesFilter = s?.toString()?.trim() ?: ""
                renderCustomRules()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun renderCustomRules() {
        binding.llRulesContainer.removeAllViews()
        val allRules = prefs.getCustomRules()

        val activeCount = allRules.count { it.enabled }
        binding.tvRulesCounter.text = "$activeCount de ${allRules.size} reglas activadas"

        val filteredRules = if (currentRulesFilter.isBlank()) {
            allRules
        } else {
            allRules.filter {
                it.trigger.contains(currentRulesFilter, ignoreCase = true) ||
                        it.response.contains(currentRulesFilter, ignoreCase = true)
            }
        }

        if (filteredRules.isEmpty()) {
            binding.tvEmptyRules.visibility = View.VISIBLE
            binding.tvEmptyRules.text = if (currentRulesFilter.isBlank()) {
                "Aún no tienes reglas fijas creadas.\n\nPuedes pulsar el botón '+ Regla'\no simplemente decírselo al Copiloto en el chat:\n(ej: 'Si preguntan por la moto vale 5 millones')."
            } else {
                "No se encontraron reglas que coincidan con \"$currentRulesFilter\"."
            }
            return
        }

        binding.tvEmptyRules.visibility = View.GONE

        for (rule in filteredRules) {
            val card = MaterialCardView(this).apply {
                radius = 32f
                cardElevation = 2f
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
                setStrokeColor(ContextCompat.getColor(context, R.color.chip_border))
                setStrokeWidth(1)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 14)
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
                        binding.tvRulesCounter.text = "${prefs.getCustomRules().count { it.enabled }} de ${prefs.getCustomRules().size} reglas activadas"
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
    // 5. TAB 3: MENSAJES PROGRAMADOS
    // =========================================================================
    private fun setupScheduledUI() {
        binding.btnAddScheduledMessage.setOnClickListener {
            showAddScheduledMessageDialog()
        }
    }

    private fun renderScheduledMessages() {
        binding.llScheduledContainer.removeAllViews()
        val list = prefs.getScheduledMessages()

        if (list.isEmpty()) {
            binding.tvEmptyScheduled.visibility = View.VISIBLE
            return
        }

        binding.tvEmptyScheduled.visibility = View.GONE
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy - hh:mm a", Locale.getDefault())

        for (item in list) {
            val card = MaterialCardView(this).apply {
                radius = 32f
                cardElevation = 2f
                setCardBackgroundColor(ContextCompat.getColor(context, R.color.card_bg))
                setStrokeColor(ContextCompat.getColor(context, if (item.isSent) R.color.chip_border else R.color.primary))
                setStrokeWidth(1)
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, 0, 14)
                }
                layoutParams = lp

                val innerLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 20, 24, 20)
                }

                // Row 1: Contact & Status Badge & Delete
                val topRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val tvContact = TextView(context).apply {
                    val lpContact = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = lpContact
                    text = "👤 ${item.contactNameOrPhone}"
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                }
                topRow.addView(tvContact)

                val tvStatus = TextView(context).apply {
                    text = if (item.isSent) "✅ Enviado" else "⏳ Pendiente"
                    setTextColor(ContextCompat.getColor(context, if (item.isSent) R.color.text_secondary else R.color.primary))
                    textSize = 11f
                    setPadding(12, 4, 12, 4)
                }
                topRow.addView(tvStatus)

                val btnDelete = ImageView(context).apply {
                    setImageResource(R.drawable.ic_delete)
                    setPadding(12, 12, 12, 12)
                    setOnClickListener {
                        cancelAlarm(item.id)
                        prefs.deleteScheduledMessage(item.id)
                        renderScheduledMessages()
                        Toast.makeText(context, "Mensaje programado eliminado", Toast.LENGTH_SHORT).show()
                    }
                }
                topRow.addView(btnDelete)

                innerLayout.addView(topRow)

                // Row 2: Message preview
                val tvMessage = TextView(context).apply {
                    text = "💬 \"${item.messageText}\""
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    textSize = 13f
                    setPadding(0, 8, 0, 0)
                }
                innerLayout.addView(tvMessage)

                // Row 3: Scheduled Date & Action
                val bottomRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 12, 0, 0)
                }

                val tvDate = TextView(context).apply {
                    val lpDate = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    layoutParams = lpDate
                    text = "⏰ ${dateFormat.format(Date(item.scheduledTimestamp))}"
                    setTextColor(ContextCompat.getColor(context, R.color.accent))
                    textSize = 11f
                }
                bottomRow.addView(tvDate)

                val btnSendNow = MaterialButton(context).apply {
                    text = "Enviar Ahora"
                    textSize = 11f
                    setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.wa_bubble_out))
                    setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    setOnClickListener {
                        openWhatsAppMessage(item.contactNameOrPhone, item.messageText)
                        prefs.markScheduledMessageSent(item.id)
                        renderScheduledMessages()
                    }
                }
                bottomRow.addView(btnSendNow)

                innerLayout.addView(bottomRow)
                addView(innerLayout)
            }

            binding.llScheduledContainer.addView(card)
        }
    }

    private fun showAddScheduledMessageDialog() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 24, 40, 16)
        }

        val tvContactLabel = TextView(this).apply {
            text = "Contacto o Teléfono (ej: Carlos o +573001234567):"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 12f
        }
        dialogView.addView(tvContactLabel)

        val etContact = EditText(this).apply {
            hint = "Nombre del contacto o número"
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 13f
        }
        dialogView.addView(etContact)

        val tvMessageLabel = TextView(this).apply {
            text = "Mensaje a enviar:"
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 12f
            setPadding(0, 20, 0, 0)
        }
        dialogView.addView(tvMessageLabel)

        val etMessage = EditText(this).apply {
            hint = "Escribe el mensaje programado..."
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            textSize = 13f
            minLines = 2
        }
        dialogView.addView(etMessage)

        val calendar = Calendar.getInstance().apply {
            add(Calendar.MINUTE, 5)
        }
        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy - hh:mm a", Locale.getDefault())

        val btnPickDateTime = MaterialButton(this).apply {
            text = "Fecha y Hora: ${dateTimeFormat.format(calendar.time)}"
            textSize = 12f
            setStrokeColor(ContextCompat.getColorStateList(context, R.color.primary))
            setStrokeWidth(2)
            setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.card_bg))
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 20, 0, 0)
            }
            layoutParams = lp
            setOnClickListener {
                DatePickerDialog(this@MainActivity, { _, year, month, dayOfMonth ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                    TimePickerDialog(this@MainActivity, { _, hourOfDay, minute ->
                        calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        calendar.set(Calendar.MINUTE, minute)
                        calendar.set(Calendar.SECOND, 0)
                        text = "Fecha y Hora: ${dateTimeFormat.format(calendar.time)}"
                    }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), false).show()

                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        dialogView.addView(btnPickDateTime)

        AlertDialog.Builder(this)
            .setTitle("Programar Mensaje")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val contact = etContact.text.toString().trim()
                val text = etMessage.text.toString().trim()
                val timestamp = calendar.timeInMillis

                if (contact.isNotBlank() && text.isNotBlank()) {
                    val scheduledMsg = ScheduledMessage(
                        contactNameOrPhone = contact,
                        messageText = text,
                        scheduledTimestamp = timestamp,
                        isSent = false,
                        isAutoReplyTrigger = true
                    )
                    prefs.addScheduledMessage(scheduledMsg)
                    scheduleAlarm(scheduledMsg)
                    renderScheduledMessages()
                    Toast.makeText(this, "✅ Mensaje programado con éxito", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Debes llenar el contacto y el mensaje", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun scheduleAlarm(scheduledMessage: ScheduledMessage) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, ScheduledMessageReceiver::class.java).apply {
            putExtra(ScheduledMessageReceiver.EXTRA_MESSAGE_ID, scheduledMessage.id)
            putExtra(ScheduledMessageReceiver.EXTRA_CONTACT, scheduledMessage.contactNameOrPhone)
            putExtra(ScheduledMessageReceiver.EXTRA_TEXT, scheduledMessage.messageText)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(this, scheduledMessage.id.hashCode(), intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    scheduledMessage.scheduledTimestamp,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    scheduledMessage.scheduledTimestamp,
                    pendingIntent
                )
            }
        } catch (e: Exception) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                scheduledMessage.scheduledTimestamp,
                pendingIntent
            )
        }
    }

    private fun cancelAlarm(scheduledMessageId: String) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(this, ScheduledMessageReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(this, scheduledMessageId.hashCode(), intent, flags)
        alarmManager.cancel(pendingIntent)
    }

    private fun openWhatsAppMessage(contact: String, text: String) {
        val cleanPhone = contact.filter { it.isDigit() || it == '+' }
        val encodedText = try {
            URLEncoder.encode(text, "UTF-8")
        } catch (e: Exception) {
            text
        }

        val intent = if (cleanPhone.length >= 7) {
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
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir WhatsApp: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // =========================================================================
    // 5. TAB 3: SIMULADOR DE RESPUESTAS EN VIVO
    // =========================================================================
    private fun setupTesterUI() {
        binding.chipSampleStory.setOnClickListener {
            binding.etTestMessage.setText("Uff qué delicia hermano, ¿dónde es eso? 🔥")
        }
        binding.chipSampleVoiceNote.setOnClickListener {
            binding.etTestMessage.setText("🎤 Nota de voz (0:15)")
        }
        binding.chipSampleQuestion.setOnClickListener {
            binding.etTestMessage.setText("¿Qué haces hoy bro, estás libre o qué?")
        }

        binding.btnRunSimulation.setOnClickListener {
            runSimulation()
        }
    }

    private fun runSimulation() {
        val contact = binding.etTestContact.text.toString().trim().ifBlank { "Carlos" }
        val msg = binding.etTestMessage.text.toString().trim()

        if (msg.isBlank()) {
            Toast.makeText(this, "Escribe un mensaje de prueba primero", Toast.LENGTH_SHORT).show()
            return
        }

        binding.pbSimulationLoading.visibility = View.VISIBLE
        binding.btnRunSimulation.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    geminiManager.simulateBotReply(contact, msg, scheduleManager, ruleEngine)
                }

                binding.cardSimulationResult.visibility = View.VISIBLE
                binding.tvSimSourceBadge.text = result.source
                binding.tvSimIncomingText.text = "$contact:\n$msg"
                binding.tvSimOutgoingText.text = "Eduardo (Bot):\n${result.reply}"

            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error en simulador: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                binding.pbSimulationLoading.visibility = View.GONE
                binding.btnRunSimulation.isEnabled = true
            }
        }
    }

    // =========================================================================
    // 6. TAB 4: AJUSTES & CONSOLA EN VIVO
    // =========================================================================
    private fun setupSettingsUI() {
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

        // Manual Pause selector buttons
        binding.btnPause1m.setOnClickListener { setManualPauseMinutes(1) }
        binding.btnPause5m.setOnClickListener { setManualPauseMinutes(5) }
        binding.btnPause15m.setOnClickListener { setManualPauseMinutes(15) }

        // Ignored Contacts manager
        binding.btnManageIgnored.setOnClickListener {
            showManageIgnoredDialog()
        }

        // Clear Logs
        binding.btnClearLogs.setOnClickListener {
            binding.tvLogs.text = "Esperando mensajes entrantes en WhatsApp..."
            Toast.makeText(this, "Consola limpiada", Toast.LENGTH_SHORT).show()
        }

        binding.btnSave.setOnClickListener {
            savePreferences()
            Toast.makeText(this, "✅ Ajustes guardados con éxito", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setManualPauseMinutes(mins: Int) {
        prefs.manualPauseMinutes = mins
        updateManualPauseButtons()
        Toast.makeText(this, "Pausa manual fijada en $mins minuto(s)", Toast.LENGTH_SHORT).show()
    }

    private fun updateManualPauseButtons() {
        val current = prefs.manualPauseMinutes
        stylePauseButton(binding.btnPause1m, current == 1)
        stylePauseButton(binding.btnPause5m, current == 5)
        stylePauseButton(binding.btnPause15m, current == 15)
    }

    private fun stylePauseButton(button: MaterialButton, isSelected: Boolean) {
        if (isSelected) {
            button.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
            button.setTextColor(ContextCompat.getColor(this, R.color.background))
        } else {
            button.setBackgroundColor(ContextCompat.getColor(this, R.color.card_bg))
            button.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun updateIgnoredContactsText() {
        val list = prefs.getIgnoredContacts()
        if (list.isEmpty()) {
            binding.tvIgnoredContactsList.text = "(Ningún contacto ignorado configurado)"
            binding.tvIgnoredContactsList.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        } else {
            binding.tvIgnoredContactsList.text = list.joinToString(", ")
            binding.tvIgnoredContactsList.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        }
    }

    private fun showManageIgnoredDialog() {
        val list = prefs.getIgnoredContacts()
        val items = list.toTypedArray()

        val builder = AlertDialog.Builder(this)
            .setTitle("🚫 Contactos Ignorados")

        if (items.isEmpty()) {
            builder.setMessage("No tienes contactos en la lista de excepciones.\n\nEl bot responderá a todos los chats individuales.")
        } else {
            builder.setItems(items) { _, which ->
                val selected = items[which]
                AlertDialog.Builder(this)
                    .setTitle("Eliminar Excepción")
                    .setMessage("¿Deseas quitar a \"$selected\" de los contactos ignorados?")
                    .setPositiveButton("Quitar") { _, _ ->
                        prefs.removeIgnoredContact(selected)
                        updateIgnoredContactsText()
                        Toast.makeText(this, "Contacto eliminado de excepciones", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }

        builder.setPositiveButton("+ Agregar Contacto") { _, _ ->
            val input = EditText(this).apply {
                hint = "Nombre exacto o palabra (ej: Mamá, Jefe)"
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
            }
            AlertDialog.Builder(this)
                .setTitle("Agregar Contacto Ignorado")
                .setMessage("Escribe el nombre o palabra clave para no responderle nunca:")
                .setView(input)
                .setPositiveButton("Agregar") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotBlank()) {
                        prefs.addIgnoredContact(name)
                        updateIgnoredContactsText()
                        Toast.makeText(this, "✅ $name agregado a excepciones", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        builder.setNeutralButton("Cerrar", null)
        builder.show()
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

        binding.switchVoiceNote.isChecked = prefs.autoReplyToVoiceNotes
        binding.etVoiceNoteMsg.setText(prefs.voiceNoteMessage)

        updateTimeButtons()
        updateManualPauseButtons()
        updateIgnoredContactsText()
    }

    private fun updateSettingsUIFromPrefs() {
        loadPreferences()
    }

    private fun savePreferences() {
        prefs.isBotEnabled = binding.switchMaster.isChecked
        prefs.isAiMode = binding.switchAi.isChecked
        prefs.isScheduleEnabled = binding.switchSchedule.isChecked
        prefs.outOfOfficeMessage = binding.etOutOfOfficeMsg.text.toString()
        prefs.geminiApiKey = binding.etApiKey.text.toString().trim()
        prefs.systemPrompt = binding.etSystemPrompt.text.toString().trim()

        prefs.autoReplyToVoiceNotes = binding.switchVoiceNote.isChecked
        prefs.voiceNoteMessage = binding.etVoiceNoteMsg.text.toString().trim()

        updateHeaderStatus()
    }
}
