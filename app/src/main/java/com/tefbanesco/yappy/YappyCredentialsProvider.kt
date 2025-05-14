package com.tefbanesco.yappy

/**
 * Interfaz para proveer las credenciales de API Key y Secret Key para Yappy.
 * El módulo consumidor debe implementar esta interfaz si desea proporcionar
 * credenciales dinámicamente o en tiempo de ejecución.
 */
interface YappyCredentialsProvider {
    /**
     * Devuelve la API Key de Yappy.
     * @return La API Key como String, o null si no está disponible.
     */
    fun getApiKey(): String?

    /**
     * Devuelve la Secret Key de Yappy.
     * @return La Secret Key como String, o null si no está disponible.
     */
    fun getSecretKey(): String?
    
    /**
     * Devuelve el ID del dispositivo para Yappy.
     * @return El Device ID como String, o null si no está disponible.
     */
    fun getDeviceId(): String?
    
    /**
     * Devuelve el nombre del dispositivo para Yappy (opcional).
     * @return El Device Name como String, o null si no está disponible.
     */
    fun getDeviceName(): String?
    
    /**
     * Devuelve el usuario del dispositivo para Yappy (opcional).
     * @return El Device User como String, o null si no está disponible.
     */
    fun getDeviceUser(): String?
    
    /**
     * Devuelve el ID del grupo para Yappy.
     * @return El Group ID como String, o null si no está disponible.
     */
    fun getGroupId(): String?
    
    /**
     * Devuelve la URL base de la API de Yappy.
     * @return La URL base como String, o null para usar la URL por defecto.
     */
    fun getBaseUrl(): String?
}