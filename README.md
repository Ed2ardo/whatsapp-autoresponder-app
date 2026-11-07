# 📱 WhatsApp AutoResponder AI (App Nativa para Android / Galaxy A15)

Aplicación nativa para Android que responde automáticamente mensajes de WhatsApp y WhatsApp Business utilizando **Inteligencia Artificial (Gemini 3.5 Flash Lite)** y un **motor de reglas cotidianas**, con **modo horario dinámico**, **memoria de conversación** y **protección anti-baneo**.

---

## 🌟 Características Principales

1. **100% Autónoma en tu Celular:**  
   Funciona directamente en tu Samsung Galaxy A15 sin necesidad de mantener encendido tu computador ni contratar servidores.
2. **Cero Riesgo de Baneo:**  
   A diferencia de las emulaciones web, esta app utiliza el servicio nativo de **`NotificationListenerService`** y la función oficial de **«Respuesta Rápida»** de Android. Para los servidores de Meta, es tu propio teléfono respondiendo desde la app oficial.
3. **Filtro Estricto de Grupos:**  
   Detecta automáticamente si el mensaje proviene de un grupo de WhatsApp y lo descarta para responder únicamente a **chats individuales**.
4. **Modo Horario Dinámico y Natural:**  
   * **Dentro del horario:** Responde con tu personalidad de IA o reglas normales.
   * **Fuera de horario (Noches / Descanso):** Envía un mensaje natural informando que no estás disponible y que responderás apenas te desocupes.
5. **Memoria de Conversación (Contexto):**  
   Mantiene en memoria los últimos mensajes del chat para que Gemini entienda el hilo de la conversación y no dé respuestas aisladas.
6. **Protección Anti-Spam:**  
   * Cooldown de 15 segundos por contacto.
   * Retardo humano aleatorio de 3.0 a 5.0 segundos antes de enviar cada respuesta.
7. **Consola en Vivo:**  
   La pantalla de la app muestra en tiempo real los mensajes recibidos, retardos aplicados y respuestas enviadas.

---

## 🚀 ¿Cómo compilar el archivo `.apk` para instalarlo en tu Galaxy A15?

### Opción 1: Compilación Automática en GitHub (Recomendada - Cero programas en el PC)

El proyecto ya incluye el archivo de flujo `.github/workflows/build-apk.yml`.
1. Crea un repositorio privado o público en tu cuenta de **[GitHub](https://github.com)**.
2. Sube esta carpeta (`WhatsAppAutoResponderApp`) a tu repositorio.
3. Al hacer el `push`, GitHub iniciará automáticamente una acción (**Actions**) que compilará el archivo APK en 2 minutos.
4. Entra a la pestaña **Actions** > Clic en la última compilación > Descarga el archivo **`WhatsAppAutoResponder-APK.zip`**.
5. Descomprímelo y pásate el archivo **`app-debug.apk`** a tu celular (o descárgalo directamente desde el navegador de tu Galaxy A15).

### Opción 2: Usando Android Studio
1. Abre **Android Studio** en tu PC.
2. Selecciona **Open** y abre la carpeta `C:\Users\eduro\Documents\WhatsAppAutoResponderApp`.
3. Ve al menú superior **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
4. Conecta tu Galaxy A15 por cable USB y haz clic en **Run** (ícono verde de play).

---

## ⚙️ Configuración Importante en tu Samsung Galaxy A15 (Android 14 / One UI 6)

Para que Android permita que la app responda siempre en segundo plano:

### 1. Activar Permiso de Acceso a Notificaciones
* Al abrir la app, toca el botón amarillo **«Activar Permiso en Ajustes»**.
* Busca **WhatsApp AutoResponder AI** y activa el interruptor.

> [!IMPORTANT]
> **En Android 14 (Ajustes Restringidos):**  
> Si el interruptor aparece en gris y dice *"Ajuste restringido"*:  
> 1. Ve a los **Ajustes** de tu celular > **Aplicaciones**.  
> 2. Busca **WhatsApp AutoResponder AI**.  
> 3. Toca los **tres puntos** arriba a la derecha y selecciona **«Permitir ajustes restringidos»**.  
> 4. Vuelve a la app y ya te dejará activar el permiso normalmente.

### 2. Evitar que Samsung ponga la app en reposo (Batería)
1. En **Ajustes** > **Aplicaciones** > **WhatsApp AutoResponder AI**.
2. Toca en **Batería**.
3. Selecciona la opción **«Sin restricciones»** (Unrestricted).  
   *Esto garantiza que One UI no cierre el bot cuando la pantalla del teléfono esté apagada.*
