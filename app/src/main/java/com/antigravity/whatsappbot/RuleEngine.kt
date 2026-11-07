package com.antigravity.whatsappbot

class RuleEngine {

    fun evaluate(contactName: String, incomingText: String): String {
        val cleanName = if (contactName.isNotBlank()) contactName else "amigo(a)"
        val text = incomingText.trim().lowercase()

        // 1. Saludos cotidianos
        if (Regex("^(hola|buenas|que mas|buen dia|buenos dias|buenas tardes|buenas noches|holis|que onda)($|\\b)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return "¡Hola $cleanName! 👋 ¿Cómo vas? Todo bien por acá."
        }

        // 2. ¿Cómo estás? / Estado
        if (Regex("(como estas|que tal|como te va|como andas|como va todo|todo bien\\?|bien y tu)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return "¡Bien, gracias a Dios! Trabajando y dándole a unos pendientes por acá. ¿Y tú qué tal, cómo va todo?"
        }

        // 3. ¿Qué haces? / ¿En qué andas?
        if (Regex("(que haces|que haciendo|en que andas|que estas haciendo|estas ocupado|estas libre)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return "Ando aquí concentrado con unas cosas, pero dime, ¿en qué te puedo colaborar o qué necesitas?"
        }

        // 4. ¿Qué necesitas? / Pregunta de intención
        if (Regex("(que necesitas|para que me escribes|de que se trata|dime|cuentame|que paso)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return "Solo pasaba a saludar y ver cómo ibas. Si necesitas algo puntual de mi parte, déjamelo por aquí y te respondo en cuanto me desocupe."
        }

        // 5. ¿Qué cuentas? / Novedades
        if (Regex("(que cuentas|que hay de nuevo|que me cuentas|que mas de cosas)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return "Todo tranquilo por acá, en la rutina de siempre. ¿Y por tus lados qué hay de nuevo?"
        }

        // 6. ¿Quién es? / ¿Quién habla?
        if (Regex("(quien habla|quien eres|de parte de quien|con quien hablo)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return "¡Hola! Soy yo, te tengo acá guardado en mis contactos. ¿Cómo vas?"
        }

        // 7. Confirmaciones casuales
        if (Regex("^(listo|dale|de una|perfecto|va|ok|vale|entendido|de acuerdo)($|\\b)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return "De una 👍 Quedamos así."
        }

        // 8. Despedidas y Agradecimientos
        if (Regex("(gracias|muchas gracias|mil gracias|hablamos|chao|hasta luego|cuidate|nos vemos)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return "¡Dale $cleanName, con gusto! Un abrazo y cualquier cosa estamos hablando."
        }

        // 9. Asuntos urgentes
        if (Regex("(urgente|emergencia|llamada|llamame)", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return "Si es algo muy urgente o prioritario, pégame una timbrada al teléfono directo. Si no, déjame el mensaje detallado y lo reviso apenas termine esto."
        }

        // 10. Fallback dinámico por defecto
        return "¡Hola $cleanName! Ahorita ando algo embolatado/ocupado 📱. Déjame el mensaje con lo que necesitas y te respondo apenas me desocupe 👍"
    }
}
