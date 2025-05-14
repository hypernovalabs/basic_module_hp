package com.tefbanesco.yappy

import android.content.Context
import android.util.Log
import com.tefbanesco.storage.YappyLocalStorage
import com.tefbanesco.yappy.model.YappyError
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Gestiona el flujo de pagos con Yappy QR
 */
class YappyFlow(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val callbacks: YappyFlowCallbacks
) {
    private val TAG = "YappyFlow"

    // Configuración y datos de la transacción
    private var currentPaymentData: PaymentIntentData? = null
    private var currentLocalOrderId: String? = null
    private var currentYappyTransactionId: String? = null
    private var currentSessionToken: String? = null
    private var config: Map<String, String> = emptyMap()

    // Control de polling
    private var pollingJob: Job? = null
    private var isCancellationRequested = false

    // No necesitamos servicio de API Yappy como propiedad ya que ahora es un objeto
    
    /**
     * Callbacks para notificar eventos del flujo de pago
     */
    interface YappyFlowCallbacks {
        fun onLoadingStateChanged(isLoading: Boolean)
        fun onQrReady(hash: String, localOrderId: String, yappyTransactionId: String, amount: Double)
        fun onPollingStatusUpdate(status: String, message: String, isFinal: Boolean)
        fun onTransactionSuccess(yappyTransactionId: String, localOrderId: String)
        fun onTransactionFailure(yappyTransactionId: String?, localOrderId: String?, errorMessage: String)
        fun onSessionClosed()
    }
    
    /**
     * Datos del pago a procesar
     */
    data class PaymentIntentData(
        val amount: Double,
        val transactionId: Int? = null
    )
    
    /**
     * Inicia el proceso de pago con Yappy QR
     */
    fun startPaymentProcess(paymentData: PaymentIntentData) {
        Log.d(TAG, "Iniciando proceso de pago: monto=${paymentData.amount}, id=${paymentData.transactionId}")
        
        this.currentPaymentData = paymentData
        this.config = YappyLocalStorage.getYappyConfig(context)
        this.isCancellationRequested = false
        
        // Validar configuración
        if (!YappyApiConfig.isYappyConfigured(context)) {
            callbacks.onTransactionFailure(null, null, "Error: Yappy no está configurado.")
            return
        }
        
        // Validar monto
        if (paymentData.amount <= 0.0) {
            callbacks.onTransactionFailure(null, null, "Error: Monto inválido.")
            return
        }
        
        // Iniciar proceso
        coroutineScope.launch {
            callbacks.onLoadingStateChanged(true)
            
            try {
                // 1. Abrir sesión
                Log.d(TAG, "Abriendo sesión Yappy...")
                currentSessionToken = openSession()
                
                if (currentSessionToken.isNullOrBlank()) {
                    callbacks.onTransactionFailure(null, null, "Error al abrir sesión Yappy.")
                    return@launch
                }
                
                // Guardar token
                YappyLocalStorage.saveYappySessionToken(context, currentSessionToken!!)
                
                // 2. Generar QR
                Log.d(TAG, "Generando QR para monto ${paymentData.amount}...")
                val (localId, yappyId, responseJson) = generateQr(paymentData.amount)
                currentLocalOrderId = localId
                currentYappyTransactionId = yappyId
                
                val json = JSONObject(responseJson)
                if (!json.has("body") || yappyId.isBlank()) {
                    val errorMsg = json.optString("message", "Error generando QR.")
                    callbacks.onTransactionFailure(yappyId, localId, errorMsg)
                    return@launch
                }
                
                // 3. Extraer hash del QR
                val body = json.getJSONObject("body")
                val hash = body.optString("hash")
                
                if (hash.isBlank()) {
                    callbacks.onTransactionFailure(yappyId, localId, "Hash de QR vacío.")
                    return@launch
                }
                
                // 4. Notificar QR listo
                callbacks.onQrReady(hash, localId, yappyId, paymentData.amount)
                
                // 5. Iniciar polling de estado
                startPollingStatus()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error en proceso de pago", e)
                callbacks.onTransactionFailure(
                    currentYappyTransactionId,
                    currentLocalOrderId,
                    "Error en proceso de pago: ${e.message}"
                )
            } finally {
                callbacks.onLoadingStateChanged(false)
            }
        }
    }
    
    /**
     * Abre una sesión con el servidor de Yappy
     * Utiliza YappyApiService para hacer la petición
     * Endpoint: POST /session/device
     */
    private suspend fun openSession(): String = withContext(Dispatchers.IO) {
        try {
            // Validar configuración
            if (config[YappyLocalStorage.KEY_YAPPY_API_KEY].isNullOrBlank() ||
                config[YappyLocalStorage.KEY_YAPPY_SECRET_KEY].isNullOrBlank() ||
                config[YappyLocalStorage.KEY_YAPPY_DEVICE_ID].isNullOrBlank() ||
                config[YappyLocalStorage.KEY_YAPPY_GROUP_ID].isNullOrBlank()) {
                Log.e(TAG, "Configuración incompleta para sesión Yappy")
                return@withContext ""
            }

            // Obtener respuesta usando YappyApiService con el endpoint correcto (/session/device)
            val responseBody = YappyApiService.openSession(
                baseUrl = YappyApiConfig.getBaseUrl(context),
                apiKey = config[YappyLocalStorage.KEY_YAPPY_API_KEY] ?: "",
                secretKey = config[YappyLocalStorage.KEY_YAPPY_SECRET_KEY] ?: "",
                deviceId = config[YappyLocalStorage.KEY_YAPPY_DEVICE_ID] ?: "",
                deviceName = config[YappyLocalStorage.KEY_YAPPY_DEVICE_NAME] ?: "DefaultDevice",
                deviceUser = config[YappyLocalStorage.KEY_YAPPY_DEVICE_USER] ?: "DefaultUser",
                groupId = config[YappyLocalStorage.KEY_YAPPY_GROUP_ID] ?: ""
            )

            // Procesar respuesta según formato esperado de OpenSessionResponse
            val responseJson = JSONObject(responseBody)

            // Intentar extraer el token según diferentes estructuras posibles de respuesta
            val token = when {
                // Estructura con body anidado
                responseJson.has("body") -> {
                    val body = responseJson.optJSONObject("body")
                    body?.optString("token", "") ?: ""
                }
                // Estructura sin body anidado (directo en raíz)
                responseJson.has("token") -> {
                    responseJson.optString("token", "")
                }
                // No se encontró token
                else -> ""
            }

            // Log detallado de la respuesta
            Log.d(TAG, "Respuesta de openSession: ${responseJson.toString(2)}")
            Log.d(TAG, "Token extraído: ${if (token.isNotBlank()) "OK (${token.length} caracteres)" else "VACÍO"}")

            // Extraer información adicional para debug
            val status = responseJson.optString("status", "")
            val openedDate = responseJson.optString("opened_date", "")
            if (status.isNotBlank() || openedDate.isNotBlank()) {
                Log.d(TAG, "Sesión Yappy abierta: estado=$status, fecha=$openedDate")
            }

            return@withContext token
        } catch (e: Exception) {
            // Manejo específico de errores de Yappy
            if (e is YappyApiService.YappyApiException) {
                // Obtener descripción amigable del error
                val errorDescription = YappyError.getErrorDescription(e.errorCode)

                when (e.errorCode) {
                    YappyError.ERROR_SESSION_ALREADY_OPEN -> {
                        Log.w(TAG, "La sesión ya estaba abierta: ${e.message}")
                        // Podríamos intentar reutilizar el token existente o recuperarlo de otra manera
                    }
                    YappyError.ERROR_MISSING_REQUIRED_FIELDS -> {
                        // Error YP-0009: Campos obligatorios faltantes
                        Log.e(TAG, "Error YP-0009: ${e.message}")
                        Log.e(TAG, "Verificar estructura JSON en YappyApiService.openSession")
                    }
                    YappyError.ERROR_OPEN_SESSION -> {
                        Log.e(TAG, "Error al abrir sesión Yappy: ${e.message}")
                    }
                    else -> {
                        Log.e(TAG, "Error de Yappy (${e.errorCode}): $errorDescription - ${e.message}")
                    }
                }
            } else {
                Log.e(TAG, "Excepción en openSession", e)
            }
            return@withContext ""
        }
    }

    /**
     * Genera un código QR para el pago
     * Utiliza YappyApiService para hacer la petición
     */
    private suspend fun generateQr(amount: Double): Triple<String, String, String> = withContext(Dispatchers.IO) {
        try {
            // Validar token
            if (currentSessionToken.isNullOrBlank()) {
                Log.e(TAG, "No hay token de sesión para generateQr")
                return@withContext Triple(System.currentTimeMillis().toString(), "", "{}")
            }

            // Generar ID de orden local
            val localOrderId = System.currentTimeMillis().toString()

            // Obtener credenciales de la configuración
            val apiKey = config[YappyLocalStorage.KEY_YAPPY_API_KEY] ?: ""
            val secretKey = config[YappyLocalStorage.KEY_YAPPY_SECRET_KEY] ?: ""

            // Obtener respuesta usando YappyApiService con estructura completa según la API
            val responseBody = YappyApiService.generateQr(
                baseUrl = YappyApiConfig.getBaseUrl(context),
                sessionToken = currentSessionToken!!,
                apiKey = apiKey,
                secretKey = secretKey,
                subTotal = amount,
                tax = 0.0,
                tip = 0.0,
                discount = 0.0,
                total = amount,
                orderId = localOrderId,
                description = "Pago HioPOS #${currentPaymentData?.transactionId}"
            )

            // Procesar respuesta con manejo de diferentes estructuras posibles
            val responseJson = JSONObject(responseBody)

            // Log de respuesta completa para debug
            Log.d(TAG, "Respuesta generateQr: ${responseJson.toString(2)}")

            // Intentar extraer el ID de transacción y el hash según diferentes estructuras
            val yappyId = when {
                // Estructura con body anidado
                responseJson.has("body") -> {
                    val body = responseJson.optJSONObject("body")
                    body?.optString("id", "") ?:
                    body?.optString("transactionId", "") ?: ""
                }
                // Estructura sin body anidado (directo en raíz)
                responseJson.has("id") -> {
                    responseJson.optString("id", "")
                }
                responseJson.has("transactionId") -> {
                    responseJson.optString("transactionId", "")
                }
                // No se encontró ID
                else -> ""
            }

            // Verificar resultado de extracción
            if (yappyId.isBlank()) {
                Log.w(TAG, "No se pudo extraer ID de transacción Yappy de la respuesta")
            } else {
                Log.d(TAG, "ID de transacción Yappy extraído: $yappyId")
            }

            return@withContext Triple(localOrderId, yappyId, responseBody)
        } catch (e: Exception) {
            Log.e(TAG, "Excepción en generateQr", e)
            return@withContext Triple(System.currentTimeMillis().toString(), "", "{}")
        }
    }
    
    /**
     * Inicia el polling de estado de la transacción
     */
    private fun startPollingStatus() {
        // Cancelar polling previo si existe
        pollingJob?.cancel()
        
        // Validar datos para polling
        if (currentYappyTransactionId.isNullOrBlank() || currentSessionToken.isNullOrBlank()) {
            callbacks.onPollingStatusUpdate("ERROR", "Error: Faltan datos para consultar estado.", true)
            callbacks.onTransactionFailure(null, null, "Error: Faltan datos para consultar estado.")
            return
        }
        
        // Iniciar nuevo polling
        pollingJob = coroutineScope.launch {
            callbacks.onLoadingStateChanged(true)
            
            var retryCount = 0
            val maxRetries = 24 // 24 intentos * 5 segundos = 2 minutos
            var pollInterval = 5000L // 5 segundos
            
            while (isActive && !isCancellationRequested && retryCount < maxRetries) {
                try {
                    // Consultar estado
                    Log.d(TAG, "Consultando estado (intento ${retryCount + 1})...")
                    val status = getTransactionStatus()
                    
                    // Procesar respuesta
                    val isFinal = when (status.uppercase(Locale.US)) {
                        "COMPLETED" -> {
                            callbacks.onPollingStatusUpdate(status, "¡Pago completado!", true)
                            closeSession()
                            callbacks.onTransactionSuccess(currentYappyTransactionId!!, currentLocalOrderId!!)
                            true
                        }
                        "CANCELLED", "FAILED", "EXPIRED" -> {
                            callbacks.onPollingStatusUpdate(status, "Transacción ${status.lowercase()}.", true)
                            closeSession()
                            callbacks.onTransactionFailure(
                                currentYappyTransactionId,
                                currentLocalOrderId,
                                "Transacción ${status.lowercase()}."
                            )
                            true
                        }
                        else -> {
                            callbacks.onPollingStatusUpdate(status, "Estado: ${status.lowercase()}", false)
                            false
                        }
                    }
                    
                    // Si el estado es final, terminamos
                    if (isFinal) break
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error consultando estado", e)
                    callbacks.onPollingStatusUpdate("ERROR", "Error consultando estado: ${e.message}", false)
                }
                
                // Incrementar contador y esperar
                retryCount++
                delay(pollInterval)
            }
            
            // Si llegamos al límite de intentos
            if (isActive && !isCancellationRequested && retryCount >= maxRetries) {
                callbacks.onPollingStatusUpdate("TIMEOUT", "Tiempo de espera agotado", true)
                closeSession()
                callbacks.onTransactionFailure(
                    currentYappyTransactionId,
                    currentLocalOrderId,
                    "Tiempo de espera agotado."
                )
            }
            
            callbacks.onLoadingStateChanged(false)
        }
    }
    
    /**
     * Consulta el estado actual de la transacción
     * Utiliza YappyApiService para hacer la petición
     */
    private suspend fun getTransactionStatus(): String = withContext(Dispatchers.IO) {
        try {
            // Obtener credenciales de la configuración
            val apiKey = config[YappyLocalStorage.KEY_YAPPY_API_KEY] ?: ""
            val secretKey = config[YappyLocalStorage.KEY_YAPPY_SECRET_KEY] ?: ""

            // Obtener respuesta usando YappyApiService con endpoint correcto
            val responseBody = YappyApiService.getTransactionStatus(
                baseUrl = YappyApiConfig.getBaseUrl(context),
                sessionToken = currentSessionToken ?: "",
                apiKey = apiKey,
                secretKey = secretKey,
                transactionId = currentYappyTransactionId ?: ""
            )

            // Procesar respuesta
            val responseJson = JSONObject(responseBody)
            val status = responseJson.optString("status", "UNKNOWN")

            // Log detallado de estado
            Log.d(TAG, "Estado de transacción: $status")

            return@withContext status
        } catch (e: Exception) {
            Log.e(TAG, "Excepción consultando estado", e)
            return@withContext "ERROR"
        }
    }
    
    /**
     * Solicita la cancelación de la transacción
     */
    fun requestCancellation() {
        // Verificar si hay una transacción activa
        if (currentYappyTransactionId.isNullOrBlank() || currentSessionToken.isNullOrBlank()) {
            callbacks.onTransactionFailure(null, null, "No hay transacción activa para cancelar.")
            return
        }
        
        // Marcar como cancelado y detener polling
        isCancellationRequested = true
        pollingJob?.cancel()
        
        // Solicitar cancelación al servidor
        coroutineScope.launch {
            callbacks.onLoadingStateChanged(true)
            
            try {
                // Obtener credenciales de la configuración
                val apiKey = config[YappyLocalStorage.KEY_YAPPY_API_KEY] ?: ""
                val secretKey = config[YappyLocalStorage.KEY_YAPPY_SECRET_KEY] ?: ""

                // Anular transacción usando el endpoint y método correcto (PUT)
                YappyApiService.voidTransaction(
                    baseUrl = YappyApiConfig.getBaseUrl(context),
                    sessionToken = currentSessionToken ?: "",
                    apiKey = apiKey,
                    secretKey = secretKey,
                    transactionId = currentYappyTransactionId ?: ""
                )

                // Procesar respuesta exitosa
                Log.d(TAG, "Transacción anulada con éxito")

                // Verificar el estado final después de anular
                val finalStatus = getTransactionStatus()
                if (finalStatus == "VOIDED") {
                    Log.d(TAG, "Estado final confirmado: VOIDED")
                } else {
                    Log.w(TAG, "Estado después de anular: $finalStatus (esperado: VOIDED)")
                }

                callbacks.onTransactionFailure(
                    currentYappyTransactionId,
                    currentLocalOrderId,
                    "Transacción cancelada por el usuario."
                )
            } catch (e: Exception) {
                Log.e(TAG, "Excepción cancelando transacción", e)
                callbacks.onTransactionFailure(
                    currentYappyTransactionId,
                    currentLocalOrderId,
                    "Error al cancelar transacción: ${e.message}"
                )
            } finally {
                closeSession()
                callbacks.onLoadingStateChanged(false)
            }
        }
    }
    
    /**
     * Cierra la sesión con el servidor
     */
    private suspend fun closeSession() {
        // Verificar si hay una sesión activa
        if (currentSessionToken.isNullOrBlank()) {
            callbacks.onSessionClosed()
            return
        }
        
        // Guardar token para cerrar (para evitar problemas de concurrencia)
        val tokenToClose = currentSessionToken
        currentSessionToken = null
        
        withContext(Dispatchers.IO) {
            try {
                // Obtener credenciales de la configuración
                val apiKey = config[YappyLocalStorage.KEY_YAPPY_API_KEY] ?: ""
                val secretKey = config[YappyLocalStorage.KEY_YAPPY_SECRET_KEY] ?: ""

                // Cerrar sesión usando YappyApiService con método DELETE y endpoint correcto
                YappyApiService.closeSession(
                    baseUrl = YappyApiConfig.getBaseUrl(context),
                    sessionToken = tokenToClose ?: "",
                    apiKey = apiKey,
                    secretKey = secretKey
                )

                Log.d(TAG, "Sesión cerrada correctamente")
            } catch (e: Exception) {
                // Verificar si es error de "sesión ya cerrada"
                if (e is YappyApiService.YappyApiException && e.errorCode == YappyError.ERROR_SESSION_ALREADY_CLOSED) {
                    Log.w(TAG, "La sesión ya estaba cerrada: ${e.message}")
                } else {
                    Log.e(TAG, "Excepción cerrando sesión", e)
                }
            } finally {
                // Limpiar token
                YappyLocalStorage.saveYappySessionToken(context, "")
                callbacks.onSessionClosed()
            }
        }
    }
    
    /**
     * Limpia recursos al destruir la instancia
     */
    fun cleanup() {
        pollingJob?.cancel()
        isCancellationRequested = true
        Log.d(TAG, "Recursos liberados")
    }
}