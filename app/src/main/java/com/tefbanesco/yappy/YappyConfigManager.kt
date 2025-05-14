package com.tefbanesco.yappy

import android.content.Context
import android.util.Log
import com.tefbanesco.storage.YappyLocalStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import java.util.Base64

/**
 * Gestor de configuración de Yappy.
 * Se encarga de obtener la configuración desde un servidor remoto usando credenciales
 * y guardarla localmente.
 */
class YappyConfigManager {
    private val TAG = "YappyConfigManager"
    
    /**
     * Interface para manejar la respuesta de la obtención de configuración
     */
    interface ConfigResultCallback {
        fun onConfigProcessed(success: Boolean, username: String, errorMessage: String? = null)
    }

    /**
     * Descifra los datos AES con modo ECB usando la clave proporcionada
     * @param encryptedDataB64 Datos cifrados en formato Base64 URL-safe
     * @param encryptionKeyB64 Clave de cifrado en formato Base64 URL-safe
     * @return Datos descifrados en formato String o null si hay error
     */
    private fun decryptAESECB(encryptedDataB64: String, encryptionKeyB64: String): String? {
        return try {
            // Decodificar Base64 URL-safe
            val keyBytes = Base64.getUrlDecoder().decode(encryptionKeyB64)
            val encryptedBytes = Base64.getUrlDecoder().decode(encryptedDataB64)

            // Configurar cifrado AES en modo ECB
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)

            // Descifrar y convertir a String
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error al descifrar AES: ${e.message}", e)
            null
        }
    }
    
    /**
     * Procesa y guarda la configuración recibida como JSON
     * @param context Contexto de la aplicación
     * @param configJsonString JSON con los datos de configuración
     * @param username Nombre de usuario asociado a esta configuración
     * @return true si el procesado y guardado fue exitoso, false en caso contrario
     */
    private fun processAndSaveConfig(
        context: Context,
        configJsonString: String,
        username: String
    ): Boolean {
        return try {
            val configJson = JSONObject(configJsonString)
            
            // Extraer datos de configuración
            val configObject = configJson.getJSONObject("config")
            val endpointUrl = configObject.getString("endpoint")
            val apiKey = configObject.getString("api-key")  // Con guión en el JSON
            val secretKey = configObject.getString("secret-key")
            
            val bodyObject = configJson.getJSONObject("body")
            val deviceObject = bodyObject.getJSONObject("device")
            val deviceId = deviceObject.getString("id")
            val deviceName = deviceObject.optString("name", "DefaultDevice")
            val deviceUser = deviceObject.optString("user", "DefaultUser")
            val groupId = bodyObject.getString("group_id")
            
            Log.d(TAG, "Configuración a usar (Usuario: $username):")
            Log.d(TAG, "Endpoint: $endpointUrl, APIKey: $apiKey, DeviceId: $deviceId, GroupId: $groupId")
            
            // Guardar configuración en almacenamiento local
            YappyLocalStorage.saveYappyConfig(
                context = context,
                endpoint = endpointUrl,
                apiKey = apiKey,
                secretKey = secretKey,
                deviceId = deviceId,
                deviceName = deviceName,
                deviceUser = deviceUser,
                groupId = groupId
            )
            
            // Guardar nombre de usuario actual
            YappyLocalStorage.saveYappyCurrentUsername(context, username)
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando configuración JSON", e)
            false
        }
    }
    
    /**
     * Utiliza los valores de fallback para configurar Yappy cuando falla la conexión
     * @param context Contexto de la aplicación
     * @return true si la configuración de fallback se guardó correctamente
     */
    fun useFallbackConfig(context: Context): Boolean {
        Log.w(TAG, "Usando configuración de fallback para Yappy (usuario: ${DefaultYappyConfig.FALLBACK_USERNAME})")
        return processAndSaveConfig(
            context = context,
            configJsonString = DefaultYappyConfig.FALLBACK_DECRYPTED_JSON_STRING,
            username = DefaultYappyConfig.FALLBACK_USERNAME
        )
    }
    
    /**
     * Obtiene la configuración de Yappy desde un servidor y la guarda localmente
     * @param context Contexto de la aplicación
     * @param configServerUrl URL del servidor de configuración
     * @param username Nombre de usuario para autenticación
     * @param password Contraseña para autenticación
     * @param callback Callback para notificar resultados
     * @return true si la operación fue exitosa, false en caso contrario
     */
    suspend fun fetchAndSaveYappyConfig(
        context: Context,
        configServerUrl: String,
        username: String,
        password: String,
        callback: ConfigResultCallback? = null
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Obteniendo config de Yappy usando POST desde: $configServerUrl")

                // 1. Crear el cuerpo JSON de la solicitud
                val jsonRequestBody = JSONObject()
                try {
                    jsonRequestBody.put("username", username)
                    jsonRequestBody.put("password", password)
                } catch (e: JSONException) {
                    Log.e(TAG, "Error creando JSON para el body", e)
                    
                    // Usar fallback
                    val fallbackSuccess = useFallbackConfig(context)
                    callback?.onConfigProcessed(
                        fallbackSuccess,
                        DefaultYappyConfig.FALLBACK_USERNAME,
                        "Error preparando solicitud. Usando configuración por defecto."
                    )
                    
                    return@withContext fallbackSuccess
                }

                // 2. Configurar el cliente HTTP con timeouts
                val client = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .build()

                // 3. Preparar la solicitud POST con el cuerpo JSON
                val mediaTypeJson = "application/json; charset=utf-8".toMediaType()
                val requestBody = jsonRequestBody.toString().toRequestBody(mediaTypeJson)

                val request = Request.Builder()
                    .url(configServerUrl)
                    .post(requestBody)
                    .addHeader("Accept", "application/json")
                    .build()

                Log.d(TAG, "Realizando POST a: $configServerUrl")
                Log.d(TAG, "Body JSON: ${jsonRequestBody.toString()}")

                // 4. Ejecutar la llamada de forma síncrona (estamos en un withContext Dispatchers.IO)
                val response = client.newCall(request).execute()

                response.use { resp ->
                    val responseCode = resp.code
                    Log.d(TAG, "Código de respuesta: $responseCode")

                    if (resp.isSuccessful) {
                        val responseBody = resp.body?.string()
                        if (responseBody != null) {
                            Log.d(TAG, "Respuesta del servidor: $responseBody")

                            try {
                                val responseJson = JSONObject(responseBody)
                                
                                // Verificar si la respuesta contiene datos cifrados
                                val encryptedData = responseJson.optString("encrypted_data", "")
                                val encryptionKey = responseJson.optString("encryption_key", "")
                                
                                if (encryptedData.isNotEmpty() && encryptionKey.isNotEmpty()) {
                                    // Caso 1: Datos cifrados - descifrar primero
                                    Log.d(TAG, "Recibidos datos cifrados, intentando descifrar")
                                    
                                    val decryptedConfig = decryptAESECB(encryptedData, encryptionKey)
                                    if (decryptedConfig != null) {
                                        Log.d(TAG, "Datos descifrados correctamente: $decryptedConfig")
                                        
                                        // Procesar y guardar la configuración descifrada
                                        val success = processAndSaveConfig(context, decryptedConfig, username)
                                        
                                        // Invocar callback si existe
                                        callback?.onConfigProcessed(
                                            success, 
                                            username, 
                                            if (!success) "Error procesando datos descifrados" else null
                                        )
                                        
                                        return@withContext success
                                    } else {
                                        Log.e(TAG, "Error al descifrar los datos. Usando fallback.")
                                        
                                        // Usar valores de fallback
                                        val fallbackSuccess = useFallbackConfig(context)
                                        
                                        // Invocar callback si existe
                                        callback?.onConfigProcessed(
                                            fallbackSuccess, 
                                            DefaultYappyConfig.FALLBACK_USERNAME, 
                                            "Error al descifrar los datos. Usando configuración por defecto."
                                        )
                                        
                                        return@withContext fallbackSuccess
                                    }
                                } else {
                                    // Caso 2: Datos en texto plano (formato antiguo)
                                    Log.d(TAG, "Recibida configuración en texto plano")
                                    
                                    try {
                                        // Extraer configuración del JSON
                                        val endpoint = responseJson.getString("yappy_endpoint")
                                        val apiKey = responseJson.getString("yappy_api_key")
                                        val secretKey = responseJson.getString("yappy_secret_key")
                                        val deviceId = responseJson.getString("yappy_device_id")
                                        val deviceName = responseJson.optString("yappy_device_name", "DefaultDevice")
                                        val deviceUser = responseJson.optString("yappy_device_user", "DefaultUser")
                                        val groupId = responseJson.getString("yappy_group_id")

                                        // Guardar configuración localmente
                                        YappyLocalStorage.saveYappyConfig(
                                            context = context,
                                            endpoint = endpoint,
                                            apiKey = apiKey,
                                            secretKey = secretKey,
                                            deviceId = deviceId,
                                            deviceName = deviceName,
                                            deviceUser = deviceUser,
                                            groupId = groupId
                                        )
                                        
                                        // Guardar nombre de usuario actual
                                        YappyLocalStorage.saveYappyCurrentUsername(context, username)
                                        
                                        // Invocar callback si existe
                                        callback?.onConfigProcessed(true, username, null)
                                        
                                        return@withContext true
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error procesando configuración en texto plano", e)
                                        
                                        // Usar valores de fallback
                                        val fallbackSuccess = useFallbackConfig(context)
                                        
                                        // Invocar callback si existe
                                        callback?.onConfigProcessed(
                                            fallbackSuccess, 
                                            DefaultYappyConfig.FALLBACK_USERNAME, 
                                            "Error en formato de datos. Usando configuración por defecto."
                                        )
                                        
                                        return@withContext fallbackSuccess
                                    }
                                }
                            } catch (e: JSONException) {
                                Log.e(TAG, "Error al parsear JSON de respuesta", e)
                                
                                // Usar valores de fallback
                                val fallbackSuccess = useFallbackConfig(context)
                                
                                // Invocar callback si existe
                                callback?.onConfigProcessed(
                                    fallbackSuccess, 
                                    DefaultYappyConfig.FALLBACK_USERNAME, 
                                    "Error en formato del servidor. Usando configuración por defecto."
                                )
                                
                                return@withContext fallbackSuccess
                            }
                        } else {
                            Log.e(TAG, "Respuesta vacía del servidor")
                            
                            // Usar valores de fallback
                            val fallbackSuccess = useFallbackConfig(context)
                            
                            // Invocar callback si existe
                            callback?.onConfigProcessed(
                                fallbackSuccess, 
                                DefaultYappyConfig.FALLBACK_USERNAME, 
                                "Respuesta vacía del servidor. Usando configuración por defecto."
                            )
                            
                            return@withContext fallbackSuccess
                        }
                    } else {
                        // Leer mensaje de error si existe
                        val errorBody = resp.body?.string() ?: "Sin mensaje de error"
                        Log.e(TAG, "Error obteniendo config Yappy: $responseCode")
                        Log.e(TAG, "Detalles del error: $errorBody")
                        
                        // Usar valores de fallback
                        val fallbackSuccess = useFallbackConfig(context)
                        
                        // Invocar callback si existe
                        callback?.onConfigProcessed(
                            fallbackSuccess, 
                            DefaultYappyConfig.FALLBACK_USERNAME, 
                            "Error del servidor ($responseCode). Usando configuración por defecto."
                        )
                        
                        return@withContext fallbackSuccess
                    }
                }
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "Error: No se puede resolver el host. Verifique la URL y la conexión a internet.", e)
                
                // Usar valores de fallback
                val fallbackSuccess = useFallbackConfig(context)
                
                // Invocar callback si existe
                callback?.onConfigProcessed(
                    fallbackSuccess, 
                    DefaultYappyConfig.FALLBACK_USERNAME, 
                    "Error de conexión: No se puede resolver host. Usando configuración por defecto."
                )
                
                return@withContext fallbackSuccess
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "Error: No se puede conectar al servidor. Verifique que el servidor esté en ejecución y accesible.", e)
                
                // Usar valores de fallback
                val fallbackSuccess = useFallbackConfig(context)
                
                // Invocar callback si existe
                callback?.onConfigProcessed(
                    fallbackSuccess, 
                    DefaultYappyConfig.FALLBACK_USERNAME, 
                    "Error de conexión: Servidor inaccesible. Usando configuración por defecto."
                )
                
                return@withContext fallbackSuccess
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "Error: Tiempo de espera agotado. La conexión tardó demasiado.", e)
                
                // Usar valores de fallback
                val fallbackSuccess = useFallbackConfig(context)
                
                // Invocar callback si existe
                callback?.onConfigProcessed(
                    fallbackSuccess, 
                    DefaultYappyConfig.FALLBACK_USERNAME, 
                    "Error de conexión: Tiempo de espera agotado. Usando configuración por defecto."
                )
                
                return@withContext fallbackSuccess
            } catch (e: Exception) {
                Log.e(TAG, "Excepción obteniendo config Yappy", e)
                Log.e(TAG, "Tipo de excepción: ${e.javaClass.name}")
                Log.e(TAG, "Mensaje de excepción: ${e.message}")
                e.printStackTrace()
                
                // Usar valores de fallback
                val fallbackSuccess = useFallbackConfig(context)
                
                // Invocar callback si existe
                callback?.onConfigProcessed(
                    fallbackSuccess, 
                    DefaultYappyConfig.FALLBACK_USERNAME, 
                    "Error inesperado: ${e.message}. Usando configuración por defecto."
                )
                
                return@withContext fallbackSuccess
            }
        }
    }
}