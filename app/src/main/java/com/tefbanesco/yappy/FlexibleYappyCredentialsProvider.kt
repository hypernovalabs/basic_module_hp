package com.tefbanesco.yappy

import android.content.Context

/**
 * Proveedor flexible de credenciales de Yappy que permite elegir entre
 * credenciales almacenadas localmente o credenciales personalizadas
 * proporcionadas por el módulo consumidor.
 */
class FlexibleYappyCredentialsProvider(
    private val context: Context,
    private var useCustomCredentials: Boolean = false
) : YappyCredentialsProvider {
    
    // Proveedores de credenciales
    private val defaultProvider = DefaultYappyCredentialsProvider(context)
    private val customProvider = CustomYappyCredentialsProvider()
    
    /**
     * Establece si se deben usar las credenciales personalizadas.
     * @param useCustom true para usar credenciales personalizadas, false para usar las almacenadas
     */
    fun setUseCustomCredentials(useCustom: Boolean) {
        this.useCustomCredentials = useCustom
    }
    
    /**
     * Obtiene el proveedor de credenciales personalizado para configurarlo.
     * @return Instancia de CustomYappyCredentialsProvider para configurar
     */
    fun getCustomProvider(): CustomYappyCredentialsProvider {
        return customProvider
    }
    
    /**
     * Configura todas las credenciales personalizadas de una vez y activa su uso.
     */
    fun useCustomCredentials(
        apiKey: String,
        secretKey: String,
        deviceId: String,
        groupId: String,
        deviceName: String? = null,
        deviceUser: String? = null,
        baseUrl: String? = null
    ) {
        customProvider.setCredentials(
            apiKey = apiKey,
            secretKey = secretKey,
            deviceId = deviceId,
            groupId = groupId,
            deviceName = deviceName,
            deviceUser = deviceUser,
            baseUrl = baseUrl
        )
        setUseCustomCredentials(true)
    }
    
    /**
     * Cambia a usar las credenciales almacenadas localmente.
     */
    fun useStoredCredentials() {
        setUseCustomCredentials(false)
    }
    
    // Implementación de YappyCredentialsProvider
    
    override fun getApiKey(): String? {
        return if (useCustomCredentials) customProvider.getApiKey() else defaultProvider.getApiKey()
    }
    
    override fun getSecretKey(): String? {
        return if (useCustomCredentials) customProvider.getSecretKey() else defaultProvider.getSecretKey()
    }
    
    override fun getDeviceId(): String? {
        return if (useCustomCredentials) customProvider.getDeviceId() else defaultProvider.getDeviceId()
    }
    
    override fun getDeviceName(): String? {
        return if (useCustomCredentials) customProvider.getDeviceName() else defaultProvider.getDeviceName()
    }
    
    override fun getDeviceUser(): String? {
        return if (useCustomCredentials) customProvider.getDeviceUser() else defaultProvider.getDeviceUser()
    }
    
    override fun getGroupId(): String? {
        return if (useCustomCredentials) customProvider.getGroupId() else defaultProvider.getGroupId()
    }
    
    override fun getBaseUrl(): String? {
        return if (useCustomCredentials) customProvider.getBaseUrl() else defaultProvider.getBaseUrl()
    }
}