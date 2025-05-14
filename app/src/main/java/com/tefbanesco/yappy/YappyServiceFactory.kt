package com.tefbanesco.yappy

import android.content.Context
import android.util.Log
import com.tefbanesco.storage.YappyLocalStorage

/**
 * Fábrica para obtener acceso a las API de Yappy
 * Proporciona métodos para obtener instancias con diferentes configuraciones
 * 
 * Nota: Esta clase se mantiene por compatibilidad con código existente,
 * pero YappyApiService ahora es un objeto singleton y no requiere instanciación.
 */
object YappyServiceFactory {
    private const val TAG = "YappyServiceFactory"
    
    /**
     * Obtiene un proveedor de credenciales basado en preferencias almacenadas
     * @param context Contexto de la aplicación
     * @return Proveedor de credenciales Yappy
     */
    fun getCredentialsProvider(context: Context): YappyCredentialsProvider {
        Log.d(TAG, "Obteniendo proveedor de credenciales desde almacenamiento local")
        return DefaultYappyCredentialsProvider(context)
    }
    
    /**
     * Configura y retorna servicio API con credenciales almacenadas
     * @param context Contexto de la aplicación
     * @return Referencia a YappyApiService (para compatibilidad)
     */
    fun getWithStoredCredentials(context: Context): YappyApiService {
        Log.d(TAG, "Inicializando con credenciales almacenadas")
        
        // Verificar si hay configuración almacenada
        if (!YappyApiConfig.isYappyConfigured(context)) {
            Log.w(TAG, "No hay configuración válida almacenada para Yappy")
        } else {
            Log.i(TAG, "Usando configuración almacenada para Yappy")
        }
        
        // YappyApiService ahora es un objeto, pero devolvemos esta referencia por compatibilidad
        return YappyApiService
    }
    
    /**
     * Guarda credenciales personalizadas y retorna servicio API
     * @param context Contexto de la aplicación
     * @param apiKey API Key de Yappy
     * @param secretKey Secret Key de Yappy
     * @param deviceId Device ID de Yappy
     * @param deviceName Nombre del dispositivo (opcional)
     * @param deviceUser Usuario del dispositivo (opcional)
     * @param groupId Group ID de Yappy
     * @param endpoint URL base de la API (opcional)
     * @return Referencia a YappyApiService (para compatibilidad)
     */
    fun getWithCustomCredentials(
        context: Context,
        apiKey: String,
        secretKey: String,
        deviceId: String,
        deviceName: String = "Default Device",
        deviceUser: String = "Default User",
        groupId: String,
        endpoint: String = "https://api.yappy.test"
    ): YappyApiService {
        Log.d(TAG, "Guardando y usando credenciales personalizadas")
        
        // Guardar configuración
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
        
        Log.i(TAG, "Credenciales personalizadas guardadas correctamente")
        
        // YappyApiService ahora es un objeto, pero devolvemos esta referencia por compatibilidad
        return YappyApiService
    }
}