# 📱 WhatsApp AutoResponder AI (App Nativa para Android / Galaxy A15)

Aplicación nativa para Android que responde automáticamente mensajes de WhatsApp y WhatsApp Business utilizando **Inteligencia Artificial (Gemini 3.5 Flash Lite)**, un **Copiloto de IA conversacional**, un **motor de reglas y FAQ dinámicas**, **modo horario dinámico**, **memoria de conversación** y **protección anti-baneo**.

---

## 🌟 Novedades y Características Principales

1. **💬 Chat Copiloto IA (Novedades & Estados de WhatsApp):**
   * Chatea directamente con tu bot dentro de la app para contarle qué subiste a tus estados de WhatsApp o qué estás haciendo hoy:
     * *"Subí foto en Toro Burger comiendo hamburguesas"*
     * *"Si me preguntan por la moto, diles que ya la vendí en 5 millones"*
     * *"Hoy ando estudiando para la DIAN, no salgo"*
   * El Copiloto asimila la información, actualiza tu contexto activo y te confirma cómo responderá si tus contactos reaccionan o preguntan.

2. **📌 Tarjeta de Estado Activo de Hoy:**
   * Muestra en tiempo real el contexto que el bot tiene en memoria sobre tu día.
   * Cuenta con un botón de **«Limpiar»** para restablecer el contexto al terminar el día o cambiar de actividad.

3. **🎯 Reglas Dinámicas & FAQ («Si preguntan X → responder Y»):**
   * Configura respuestas exactas para precios, disponibilidad, direcciones o avisos.
   * Se evalúan al instante y también nutren a Gemini como datos oficiales.
   * Puedes crearlas manualmente desde el botón **«+ Nueva Regla»** o decírselo al Copiloto en el chat.

4. **100% Autónoma en tu Celular:**  
   Funciona directamente en tu Samsung Galaxy A15 sin necesidad de mantener encendido tu computador ni contratar servidores.

5. **Cero Riesgo de Baneo:**  
   Utiliza el servicio nativo de **`NotificationListenerService`** y la función oficial de **«Respuesta Rápida»** de Android. Para los servidores de Meta, es tu propio teléfono respondiendo desde la app oficial.

6. **Filtro Estricto de Grupos:**  
   Detecta automáticamente si el mensaje proviene de un grupo de WhatsApp y lo descarta para responder únicamente a **chats individuales**.

7. **Modo Horario Dinámico y Natural:**  
   * **Dentro del horario:** Responde con tu personalidad de IA o reglas normales.
   * **Fuera de horario (Noches / Descanso):** Envía un mensaje natural informando que no estás disponible y que responderás apenas te desocupes.

8. **Memoria de Conversación (Contexto Multi-turno):**  
   Mantiene en memoria los últimos mensajes del chat para que Gemini entienda el hilo de la conversación y no dé respuestas aisladas.

9. **Protección Anti-Spam y Anti-Ban:**  
   * Cooldown por contacto para evitar bucles.
   * Retardo humano aleatorio de 3.0 a 5.0 segundos antes de enviar cada respuesta.
   * Pausa automática si detecta que tú mismo respondiste desde WhatsApp.

10. **Consola en Vivo:**  
    Muestra en tiempo real los mensajes recibidos, retardos aplicados y respuestas enviadas.

---

## 🚀 ¿Cómo compilar el nuevo archivo `.apk` para instalarlo en tu Galaxy A15?

### Opción 1: Compilación Automática en GitHub (Recomendada - Cero programas en el PC)

El proyecto ya incluye el archivo de flujo `.github/workflows/build-apk.yml`.
1. Sube los cambios de esta carpeta (`WhatsAppAutoResponderApp`) a tu repositorio en **[GitHub](https://github.com)**:
   ```bash
   git add .
   git commit -m "feat: Copiloto IA de Estados y Reglas Dinámicas FAQ"
   git push
   ```
2. Al hacer el `push`, GitHub iniciará automáticamente una acción (**Actions**) que compilará el archivo APK en 2 minutos.
3. Entra a la pestaña **Actions** de tu repositorio > Clic en la última compilación > Descarga el archivo **`WhatsAppAutoResponder-APK.zip`**.
4. Descomprímelo y pásate el archivo **`app-debug.apk`** a tu celular (o descárgalo directamente desde el navegador de tu Galaxy A15).

### Opción 2: Usando Android Studio
1. Abre **Android Studio** en tu PC.
2. Selecciona **Open** y abre la carpeta `C:\Users\eduro\Documents\WhatsAppAutoResponderApp`.
3. Ve al menú superior **Build** > **Build Bundle(s) / APK(s)** > **Build APK(s)**.
4. Conecta tu Galaxy A15 por cable USB y haz clic en **Run** (ícono verde de play).

---

## ⚙️ Configuración Importante en tu Samsung Galaxy A15 (Android 14 / One UI 6)

Para que Android permita que la app responda siempre en segundo plano:

### 1. Activar Permiso de Acceso a Notificaciones
* Al abrir la app, ve a la pestaña **«Ajustes»** y toca el botón amarillo **«Activar Permiso en Ajustes»**.
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
