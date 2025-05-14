package com.tefbanesco.yappy

import com.tefbanesco.yappy.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Servicio para comunicación con la API de Yappy QR
 * Proporciona métodos para todas las operaciones de la API
 * según la documentación oficial
 */
object YappyApiService {
    // No necesitamos TAG constante con Timber, se deriva automáticamente

    /**
     * Método genérico para hacer peticiones HTTP a la API de Yappy
     * @param urlString URL completa para la petición
     * @param method Método HTTP (GET, POST, PUT, DELETE)
     * @param headers Cabeceras HTTP
     * @param body Cuerpo de la petición (opcional, para POST y PUT)
     * @return Respuesta en formato String (JSON)
     */
    private suspend fun makeYappyRequest(
        urlString: String,
        method: String,
        headers: Map<String, String>,
        body: JSONObject? = null
    ): String = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            // 1. Logging de petición detallado
            Timber.d("⬆️ ENVIANDO [$method] a $urlString")

            // Log de cabeceras (ofuscando algunos valores sensibles)
            val headersForLog = headers.entries.map { (key, value) ->
                val displayValue = when {
                    key.equals("api-key", ignoreCase = true) -> "${value.take(4)}...${value.takeLast(4)}"
                    key.equals("secret-key", ignoreCase = true) -> "${value.take(4)}...${value.takeLast(4)}"
                    key.equals("Authorization", ignoreCase = true) && value.startsWith("Bearer ") ->
                        "Bearer ${value.substringAfter("Bearer ").take(4)}...${value.takeLast(4)}"
                    else -> value
                }
                "$key: $displayValue"
            }.joinToString(", ")
            Timber.d("⬆️ Headers: $headersForLog")

            // Log de cuerpo (si existe)
            if (body != null) {
                Timber.d("⬆️ Request body: \n${body.toString(4)}")
            }

            // 2. Configurar conexión
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = method
                doInput = true
                doOutput = (body != null)
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Pragma", "no-cache")

                // Aplicar cabeceras
                headers.forEach { (key, value) ->
                    setRequestProperty(key, value)
                    Timber.v("Header: '$key' = '$value'") // Log detallado a nivel verbose
                }
            }

            // 3. Enviar cuerpo si existe
            body?.let {
                connection.outputStream.use { os ->
                    os.write(it.toString().toByteArray(Charsets.UTF_8))
                }
                Timber.v("⬆️ Cuerpo enviado: ${it.length()} bytes")
            }

            // 4. Procesar respuesta
            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No response body"
            }

            // 5. Loguear respuesta detallada
            if (responseCode in 200..299) {
                Timber.i("⬇️ ÉXITO [$method] $urlString | Código: $responseCode")
            } else {
                Timber.w("⬇️ ERROR [$method] $urlString | Código: $responseCode")
            }

            // Log del contenido de la respuesta, formateado como JSON si es posible
            try {
                val jsonResponse = JSONObject(responseBody)
                Timber.d("⬇️ Respuesta JSON: \n${jsonResponse.toString(4)}")
            } catch (e: Exception) {
                // Si no es JSON o no se puede formatear, mostrar como texto
                if (responseBody.length <= 1000) {
                    Timber.d("⬇️ Respuesta (texto): $responseBody")
                } else {
                    Timber.d("⬇️ Respuesta (truncada): ${responseBody.take(1000)}...")
                    Timber.v("⬇️ Respuesta completa: $responseBody")
                }
            }

            // 6. Devolver respuesta o lanzar excepción en caso de error
            if (responseCode in 200..299) {
                responseBody
            } else {
                // Intentar extraer código de error de Yappy
                try {
                    val errorJson = JSONObject(responseBody)
                    val yappyErrorCode = errorJson.optString("code", "")
                    val errorMessage = errorJson.optString("message", "Unknown error")
                    val errorDescription = YappyError.getErrorDescription(yappyErrorCode)

                    // Logging detallado del error según el código
                    Timber.e("⬇️ Yappy API Error: [$yappyErrorCode] $errorMessage")
                    Timber.e("⬇️ Descripción: $errorDescription")

                    // Manejo especial para YP-0009 (campos obligatorios faltantes)
                    if (yappyErrorCode == YappyError.ERROR_MISSING_REQUIRED_FIELDS) {
                        val detailedMessage = "Error YP-0009 (Campos obligatorios faltantes). " +
                                "Verificar estructura JSON enviada: \n${body?.toString(4) ?: "No body"}"
                        Timber.e("⬇️ $detailedMessage")
                        throw YappyApiException(yappyErrorCode, detailedMessage)
                    }
                    else if (yappyErrorCode.isNotEmpty()) {
                        throw YappyApiException(yappyErrorCode, "$errorMessage ($errorDescription)")
                    }
                    else {
                        throw Exception("Error Yappy API $responseCode: $errorMessage")
                    }
                } catch (jsonEx: Exception) {
                    // Si no podemos parsear el JSON, lanzamos excepción genérica
                    val errorMsg = "Error Yappy API $responseCode: ${responseBody.take(200)}"
                    Timber.e(jsonEx, "⬇️ Error al procesar respuesta de error: $errorMsg")
                    throw Exception(errorMsg)
                }
            }
        } catch (e: Exception) {
            // Log detallado de excepciones con información de red y stack trace
            when (e) {
                is java.net.UnknownHostException ->
                    Timber.e(e, "⚠️ ERROR DE CONEXIÓN: No se pudo resolver el host. Verifique el endpoint y la conexión.")
                is java.net.SocketTimeoutException ->
                    Timber.e(e, "⚠️ TIMEOUT: La solicitud tomó demasiado tiempo. El servidor puede estar lento o inaccesible.")
                is java.io.IOException ->
                    Timber.e(e, "⚠️ ERROR I/O: Problema leyendo/escribiendo datos en la conexión.")
                is YappyApiException ->
                    Timber.e(e, "⚠️ ERROR YAPPY [${e.errorCode}]: ${e.message}")
                else ->
                    Timber.e(e, "⚠️ ERROR INESPERADO en [$method] $urlString")
            }
            throw e
        } finally {
            connection?.disconnect()
            Timber.v("Conexión liberada para: $urlString")
        }
    }

    /**
     * Clase para excepciones específicas de la API de Yappy
     */
    class YappyApiException(val errorCode: String, message: String) : Exception(message)

    /**
     * Abre una sesión con el servidor de Yappy
     * Endpoint: POST /session/device
     * @return JSON con la respuesta completa, incluyendo token
     *
     * Esta función ha sido eliminada. Utilizar la versión con parámetros explícitos en su lugar.
     * @see openSession(baseUrl: String, apiKey: String, secretKey: String, deviceId: String, deviceName: String, deviceUser: String, groupId: String)
     */
    @Deprecated("Use la versión que requiere todos los parámetros explícitamente",
                ReplaceWith("openSession(baseUrl, apiKey, secretKey, deviceId, deviceName, deviceUser, groupId)"))
    suspend fun openSession(): String {
        throw IllegalStateException("Esta función ha sido eliminada. Utilizar la versión con parámetros explícitos en su lugar.")
    }

    /**
     * Abre una sesión con el servidor de Yappy (versión con parámetros explícitos)
     * Método mantenido para compatibilidad con código existente
     * @return JSON con la respuesta completa, incluyendo token
     */
    suspend fun openSession(
        baseUrl: String,
        apiKey: String,
        secretKey: String,
        deviceId: String,
        deviceName: String = "",
        deviceUser: String = "",
        groupId: String
    ): String {
        Timber.i("🔑 INICIO DE SESIÓN YAPPY (POST /session/device)")
        Timber.d("🔹 Datos para inicio de sesión:")
        Timber.d("🔹 URL Base: $baseUrl")
        Timber.d("🔹 API Key: ${apiKey.take(4)}...${apiKey.takeLast(4)}")
        Timber.d("🔹 Device ID: $deviceId")
        Timber.d("🔹 Device Name: ${deviceName.ifBlank { "[no especificado]" }}")
        Timber.d("🔹 Device User: ${deviceUser.ifBlank { "[no especificado]" }}")
        Timber.d("🔹 Group ID: $groupId")

        // Crear objeto según la estructura requerida por la API (ya correcta, sin anidamiento extra)
        val bodyJson = JSONObject().apply {
            put("device", JSONObject().apply {
                put("id", deviceId)
                if (deviceName.isNotBlank()) put("name", deviceName)
                if (deviceUser.isNotBlank()) put("user", deviceUser)
            })
            put("group_id", groupId)
        }

        // Log detallado para depuración del JSON enviado
        Timber.d("🔹 JSON para abrir sesión (sin anidamiento adicional):")
        Timber.d("🔹 ${bodyJson.toString(4)}")

        val response = makeYappyRequest(
            urlString = "$baseUrl/session/device",
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/json; utf-8",
                "Accept" to "application/json",
                "api-key" to apiKey,
                "secret-key" to secretKey
            ),
            body = bodyJson
        )

        // Log del resultado
        try {
            val responseJson = JSONObject(response)
            val token = if (responseJson.has("token")) {
                responseJson.getString("token")
            } else if (responseJson.has("body") && responseJson.getJSONObject("body").has("token")) {
                responseJson.getJSONObject("body").getString("token")
            } else {
                null
            }

            if (token != null) {
                Timber.i("🔑 SESIÓN ABIERTA EXITOSAMENTE con token: ${token.take(4)}...${token.takeLast(4)}")
            } else {
                Timber.w("🔑 Respuesta recibida pero no se encontró token en el formato esperado")
            }
        } catch (e: Exception) {
            Timber.e(e, "🔑 Error procesando respuesta de inicio de sesión")
        }

        return response
    }
    }

    /**
     * Genera un código QR para el pago
     * Endpoint: POST /qr/generate/DYN
     * @param subTotal Monto base del pago
     * @param tax Impuestos (opcional)
     * @param tip Propina (opcional)
     * @param discount Descuento (opcional)
     * @param total Monto total (debe ser igual a subTotal + tax + tip - discount)
     * @param orderId Identificador de la orden
     * @param description Descripción opcional
     * @return JSON con la respuesta completa
     */
    suspend fun generateQr(
        baseUrl: String,
        sessionToken: String,
        apiKey: String,
        secretKey: String,
        subTotal: Double,
        tax: Double = 0.0,
        tip: Double = 0.0,
        discount: Double = 0.0,
        total: Double? = null,
        orderId: String,
        description: String = "Pago con Yappy"
    ): String {
        // Calcular total si no se proporciona
        val calculatedTotal = total ?: (subTotal + tax + tip - discount)

        // Validar montos
        val calculatedCheck = subTotal + tax + tip - discount
        if (Math.abs(calculatedCheck - calculatedTotal) > 0.001) {
            throw IllegalArgumentException(
                "Error en montos: subTotal($subTotal) + tax($tax) + tip($tip) - discount($discount) = $calculatedCheck, que no coincide con total($calculatedTotal)"
            )
        }

        // Crear objeto según la estructura requerida por la API (directamente en la raíz)
        val bodyJson = JSONObject().apply {
            put("charge_amount", JSONObject().apply {
                put("sub_total", subTotal)
                put("tax", tax)
                put("tip", tip)
                put("discount", discount)
                put("total", calculatedTotal)
            })
            put("order_id", orderId)
            put("description", description)
        }

        // Log detallado para depuración del JSON enviado
        Log.d(TAG, "Enviando JSON para generar QR:")
        Log.d(TAG, bodyJson.toString(4))

        return makeYappyRequest(
            urlString = "$baseUrl/qr/generate/DYN",
            method = "POST",
            headers = mapOf(
                "Content-Type" to "application/json; utf-8",
                "Accept" to "application/json",
                "Authorization" to "Bearer $sessionToken",
                "api-key" to apiKey,
                "secret-key" to secretKey
            ),
            body = bodyJson
        )
    }

    /**
     * Versión simplificada para compatibilidad con implementación previa
     */
    suspend fun generateQr(
        baseUrl: String,
        sessionToken: String,
        apiKey: String,
        secretKey: String,
        amount: Double,
        orderId: String
    ): String {
        return generateQr(
            baseUrl = baseUrl,
            sessionToken = sessionToken,
            apiKey = apiKey,
            secretKey = secretKey,
            subTotal = amount,
            tax = 0.0,
            tip = 0.0,
            discount = 0.0,
            total = amount,
            orderId = orderId
        )
    }

    /**
     * Consulta el estado de una transacción
     * Endpoint: GET /transaction/{transaction_id}
     * @return JSON con la respuesta completa
     */
    suspend fun getTransactionStatus(
        baseUrl: String,
        sessionToken: String,
        apiKey: String,
        secretKey: String,
        transactionId: String
    ): String {
        return makeYappyRequest(
            urlString = "$baseUrl/transaction/$transactionId",
            method = "GET",
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $sessionToken",
                "api-key" to apiKey,
                "secret-key" to secretKey
            )
        )
    }

    /**
     * Versión simplificada para compatibilidad con implementación previa
     */
    suspend fun getTransactionStatus(
        baseUrl: String,
        sessionToken: String,
        transactionId: String
    ): String {
        // Esta implementación es solo para mantener compatibilidad
        // Se recomienda usar la versión completa con apiKey y secretKey
        Log.w(TAG, "Usando versión simplificada de getTransactionStatus sin apiKey y secretKey")
        return makeYappyRequest(
            urlString = "$baseUrl/transaction/$transactionId",
            method = "GET",
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $sessionToken"
            )
        )
    }

    /**
     * Solicita la anulación (reverso) de una transacción
     * Endpoint: PUT /transaction/{transaction_id}
     * @return JSON con la respuesta completa
     */
    suspend fun voidTransaction(
        baseUrl: String,
        sessionToken: String,
        apiKey: String,
        secretKey: String,
        transactionId: String
    ): String {
        return makeYappyRequest(
            urlString = "$baseUrl/transaction/$transactionId",
            method = "PUT",
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $sessionToken",
                "api-key" to apiKey,
                "secret-key" to secretKey
            ),
            body = JSONObject() // Cuerpo vacío
        )
    }

    /**
     * Método de compatibilidad con implementación previa
     */
    suspend fun cancelTransaction(
        baseUrl: String,
        sessionToken: String,
        transactionId: String
    ): String {
        // Esta implementación es solo para mantener compatibilidad
        // Se recomienda usar voidTransaction
        Log.w(TAG, "Usando función deprecada cancelTransaction. Use voidTransaction en su lugar")
        return makeYappyRequest(
            urlString = "$baseUrl/transaction/$transactionId",
            method = "PUT",
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $sessionToken"
            ),
            body = JSONObject() // Cuerpo vacío
        )
    }

    /**
     * Cierra la sesión con el servidor de Yappy
     * Endpoint: DELETE /session/device
     * @return JSON con la respuesta completa
     */
    suspend fun closeSession(
        baseUrl: String,
        sessionToken: String,
        apiKey: String,
        secretKey: String
    ): String {
        return makeYappyRequest(
            urlString = "$baseUrl/session/device",
            method = "DELETE",
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $sessionToken",
                "api-key" to apiKey,
                "secret-key" to secretKey
            )
        )
    }

    /**
     * Versión simplificada para compatibilidad con implementación previa
     */
    suspend fun closeSession(
        baseUrl: String,
        sessionToken: String
    ): String {
        // Esta implementación es solo para mantener compatibilidad
        // Se recomienda usar la versión completa con apiKey y secretKey
        Log.w(TAG, "Usando versión simplificada de closeSession sin apiKey y secretKey")
        return makeYappyRequest(
            urlString = "$baseUrl/session/device",
            method = "DELETE",
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "Bearer $sessionToken"
            )
        )
    }
}