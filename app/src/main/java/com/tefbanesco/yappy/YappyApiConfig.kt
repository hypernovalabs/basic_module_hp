package com.tefbanesco.yappy

import android.content.Context
import android.util.Log
import com.tefbanesco.storage.YappyLocalStorage

/**
 * Configuración de API para Yappy QR
 */
object YappyApiConfig {
    private const val TAG = "YappyApiConfig"

    // Endpoint por defecto para pruebas (se reemplazará por el valor guardado en YappyLocalStorage)
    private const val DEFAULT_ENDPOINT = "https://api.yappy.test"

    /**
     * Obtiene la URL base para las llamadas a la API de Yappy
     * @param context Contexto de la aplicación
     * @return URL base de la API de Yappy
     */
    fun getBaseUrl(context: Context): String {
        val config = YappyLocalStorage.getYappyConfig(context)
        val endpoint = config[YappyLocalStorage.KEY_YAPPY_ENDPOINT]
        if (endpoint.isNullOrBlank()) {
            Log.w(TAG, "Endpoint de Yappy no configurado, usando valor por defecto")
            return DEFAULT_ENDPOINT
        }
        return endpoint
    }

    /**
     * Verifica si Yappy está configurado correctamente
     * @param context Contexto de la aplicación
     * @return true si Yappy está configurado, false en caso contrario
     */
    fun isYappyConfigured(context: Context): Boolean {
        return YappyLocalStorage.isYappyConfigured(context)
    }
}