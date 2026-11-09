# 📱 WhatsApp AutoResponder AI (App Nativa para Android / Galaxy A15)

Aplicación nativa para Android moderna, rápida y adaptable diseñada con principios **Material 3 / One UI 6**. Responde automáticamente mensajes de WhatsApp y WhatsApp Business utilizando **Inteligencia Artificial (Gemini 3.5 Flash Lite)**, un **Copiloto de IA conversacional**, un **Simulador en Vivo**, **Respuestas a Notas de Voz**, **Filtro de Excepciones VIP**, **modo horario dinámico**, **memoria de conversación de 12 líneas** y **protección anti-baneo**.

---

## 🌟 Novedades y Diseño Mejorado

1. **🎨 Diseño Moderno Material 3 / One UI 6:**
   * **Navegación Ergonómica Inferior (Bottom Navigation Bar):** Diseñada para usarse cómodamente con el pulgar en pantallas grandes (Galaxy A15 de 6.5").
   * **Cabecera Inteligente en Vivo:** Con indicador de estado (*🟢 Activo / 💤 Siesta / 🔴 Apagado*), contador de mensajes atendidos hoy (*"• 14 respuestas hoy"*) y botón rápido de Siesta/Pausa.
   * **Chat Copiloto con estética WhatsApp:** Burbujas con hora exacta, etiquetas de remitente y chips de sugerencias rápidas.

2. **🧪 Simulador de Respuestas en Vivo (In-App Sandbox):**
   * Ya no necesitas probar el bot enviando mensajes desde el teléfono de otra persona.
   * Ve a la pestaña **«Simulador»**, escribe cualquier mensaje de prueba como si fueras un contacto (ej: *"¿Bro a cuánto la moto?"* o *"invita a la hamburguesa 🔥"*) y pulsa **Probar Respuesta**.
   * Verás al instante la respuesta exacta generada y la fuente que la produjo (*Gemini IA, Regla FAQ, Nota de Voz o Ausencia*).

3. **🎤 Respuestas Inteligentes a Notas de Voz:**
   * Detecta automáticamente cuando te envían un audio en WhatsApp y responde cortésmente en texto:  
     *«¡Hola! Ahorita ando algo embolatado y no puedo escuchar audios 🎧. Déjame dicho en texto lo que necesitas y te respondo de una 👍»*.

4. **🚫 Lista de Contactos Ignorados / Excepciones (VIPs):**
   * Configura nombres o palabras clave (ej: *Mamá, Jefe, Novia*) para que el bot **NUNCA** les responda automáticamente y te deje la conversación siempre a ti.

5. **💤 Botón Rápido de «Modo Siesta» (Snooze 1h / 2h / 4h):**
   * Un toque en el ícono de luna en la cabecera para silenciar el bot durante una reunión, siesta o cuando prestas el teléfono.

6. **⏱️ Duración de Suspensión Manual Personalizable:**
   * Si respondes tú mismo en WhatsApp, la auto-respuesta se cancela y puedes elegir cuánto tiempo suspender el chat: **1 minuto**, **5 minutos** o **15 minutos**.

7. **🔍 Buscador de Reglas & FAQ:**
   * Filtra tus reglas y preguntas frecuentes al instante en la pestaña de Reglas.

8. **💬 Chat Copiloto IA (Estados y Novedades del Día):**
   * Cuéntale a tu Copiloto en lenguaje cotidiano qué subiste a tus estados de WhatsApp o qué andas haciendo hoy para que responda coherentemente a las reacciones (🔥, 🤤) y preguntas de tus amigos.

9. **🧠 Memoria Multi-turno de 12 Líneas:**
   * Conserva hasta 12 líneas de conversación previa para no perder el hilo nunca.

10. **🛡️ 100% Cero Riesgo de Baneo:**
    * Funciona a través del servicio oficial de Notificaciones de Android (`NotificationListenerService`). No usa servidores externos ni emulaciones web.

---

## 🚀 ¿Cómo compilar e instalar el nuevo APK en tu Galaxy A15?

### Compilación Automática en GitHub (Recomendada)
1. Los cambios ya están listos en el repositorio.
2. Cada vez que se hace un `push`, GitHub Actions compila automáticamente el archivo APK en ~2 minutos.
3. Entra a la pestaña **Actions** de tu repositorio en GitHub:  
   👉 **[Ver compilaciones en GitHub Actions](https://github.com/Ed2ardo/whatsapp-autoresponder-app/actions)**
4. Haz clic en la última compilación exitosa y en la sección **Artifacts** descarga **`WhatsAppAutoResponder-APK.zip`**.
5. Descomprímelo e instala el archivo **`app-debug.apk`** en tu teléfono.

---

## ⚙️ Permisos Clave en tu Galaxy A15 (Android 14)
* **Acceso a Notificaciones:** Ajustes > Aplicaciones > WhatsApp AutoResponder AI > Permitir notificaciones y servicio de escucha.
* **Batería Sin Restricciones:** Ajustes > Aplicaciones > WhatsApp AutoResponder AI > Batería > Sin restricciones (Unrestricted).
