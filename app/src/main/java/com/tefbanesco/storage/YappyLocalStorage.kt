package com.tefbanesco.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Clase para manejo seguro de almacenamiento local para la configuración de Yappy
 * Utiliza EncryptedSharedPreferences para cifrar tanto claves como valores
 */
object YappyLocalStorage {
    private const val TAG = "YappyLocalStorage"
    private const val YAPPY_PREFS_NAME = "yappy_secure_config_prefs"

    // Claves específicas para la configuración de Yappy
    const val KEY_YAPPY_ENDPOINT = "yappy_endpoint"
    const val KEY_YAPPY_API_KEY = "yappy_api_key"
    const val KEY_YAPPY_SECRET_KEY = "yappy_secret_key"
    const val KEY_YAPPY_DEVICE_ID = "yappy_device_id"
    const val KEY_YAPPY_DEVICE_NAME = "yappy_device_name"
    const val KEY_YAPPY_DEVICE_USER = "yappy_device_user"
    const val KEY_YAPPY_GROUP_ID = "yappy_group_id"
    const val KEY_YAPPY_SESSION_TOKEN = "yappy_session_token"
    const val KEY_YAPPY_CURRENT_USERNAME = "yappy_current_username"

    /**
     * Obtiene las preferencias encriptadas
     * Usa EncryptedSharedPreferences para mayor seguridad
     */
    private fun getSecurePrefs(context: Context): SharedPreferences {
        return try {
            // Crear especificación para la clave maestra
            val keySpec = KeyGenParameterSpec.Builder(
                MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(MasterKey.DEFAULT_AES_GCM_MASTER_KEY_SIZE)
                .build()

            // Crear clave maestra con la especificación
            val masterKey = MasterKey.Builder(context)
                .setKeyGenParameterSpec(keySpec)
                .build()

            // Crear EncryptedSharedPreferences que cifra tanto claves como valores
            EncryptedSharedPreferences.create(
                context,
                YAPPY_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // En caso de error, log y fallback a SharedPreferences normal
            Log.e(TAG, "Error inicializando EncryptedSharedPreferences para Yappy. Usando SharedPreferences regular.", e)
            context.getSharedPreferences(YAPPY_PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveYappyConfig(
        context: Context,
        endpoint: String,
        apiKey: String,
        secretKey: String,
        deviceId: String,
        deviceName: String,
        deviceUser: String,
        groupId: String
    ) {
        try {
            getSecurePrefs(context).edit()
                .putString(KEY_YAPPY_ENDPOINT, endpoint)
                .putString(KEY_YAPPY_API_KEY, apiKey)
                .putString(KEY_YAPPY_SECRET_KEY, secretKey)
                .putString(KEY_YAPPY_DEVICE_ID, deviceId)
                .putString(KEY_YAPPY_DEVICE_NAME, deviceName)
                .putString(KEY_YAPPY_DEVICE_USER, deviceUser)
                .putString(KEY_YAPPY_GROUP_ID, groupId)
                .apply()
            Log.d(TAG, "Configuración de Yappy guardada de forma segura.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar configuración de Yappy", e)
        }
    }

    fun getYappyConfig(context: Context): Map<String, String> {
        return try {
            val prefs = getSecurePrefs(context)
            mapOf(
                KEY_YAPPY_ENDPOINT to (prefs.getString(KEY_YAPPY_ENDPOINT, "") ?: ""),
                KEY_YAPPY_API_KEY to (prefs.getString(KEY_YAPPY_API_KEY, "") ?: ""),
                KEY_YAPPY_SECRET_KEY to (prefs.getString(KEY_YAPPY_SECRET_KEY, "") ?: ""),
                KEY_YAPPY_DEVICE_ID to (prefs.getString(KEY_YAPPY_DEVICE_ID, "") ?: ""),
                KEY_YAPPY_DEVICE_NAME to (prefs.getString(KEY_YAPPY_DEVICE_NAME, "") ?: ""),
                KEY_YAPPY_DEVICE_USER to (prefs.getString(KEY_YAPPY_DEVICE_USER, "") ?: ""),
                KEY_YAPPY_GROUP_ID to (prefs.getString(KEY_YAPPY_GROUP_ID, "") ?: ""),
                KEY_YAPPY_SESSION_TOKEN to (prefs.getString(KEY_YAPPY_SESSION_TOKEN, "") ?: "")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener configuración de Yappy", e)
            emptyMap()
        }
    }

    fun saveYappySessionToken(context: Context, token: String) {
        try {
            getSecurePrefs(context).edit()
                .putString(KEY_YAPPY_SESSION_TOKEN, token)
                .apply()
            Log.d(TAG, "Token de sesión de Yappy guardado.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar token de sesión de Yappy.", e)
        }
    }

    fun clearYappyConfig(context: Context) {
        try {
            getSecurePrefs(context).edit().clear().apply()
            Log.d(TAG, "Configuración de Yappy limpiada.")
        } catch (e: Exception) {
            Log.e(TAG, "Error al limpiar configuración de Yappy.", e)
        }
    }

    fun isYappyConfigured(context: Context): Boolean {
        val config = getYappyConfig(context)
        return config[KEY_YAPPY_ENDPOINT]?.isNotBlank() == true &&
               config[KEY_YAPPY_API_KEY]?.isNotBlank() == true &&
               config[KEY_YAPPY_SECRET_KEY]?.isNotBlank() == true &&
               config[KEY_YAPPY_DEVICE_ID]?.isNotBlank() == true
    }

    /**
     * Guarda el nombre de usuario actual para Yappy
     * @param context Contexto de la aplicación
     * @param username Nombre de usuario
     */
    fun saveYappyCurrentUsername(context: Context, username: String) {
        try {
            getSecurePrefs(context).edit()
                .putString(KEY_YAPPY_CURRENT_USERNAME, username)
                .apply()
            Log.d(TAG, "Nombre de usuario Yappy guardado: $username")
        } catch (e: Exception) {
            Log.e(TAG, "Error al guardar nombre de usuario Yappy: $username", e)
        }
    }

    /**
     * Obtiene el nombre de usuario actual para Yappy
     * @param context Contexto de la aplicación
     * @return Nombre de usuario o null si no está definido
     */
    fun getYappyCurrentUsername(context: Context): String? {
        return try {
            val username = getSecurePrefs(context).getString(KEY_YAPPY_CURRENT_USERNAME, null)
            if (username.isNullOrBlank()) null else username
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener nombre de usuario Yappy", e)
            null
        }
    }
}