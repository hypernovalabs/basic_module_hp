package com.tefbanesco.yappy

/**
 * Implementación personalizable de YappyCredentialsProvider que permite
 * establecer credenciales de forma programática desde el módulo consumidor.
 */
class CustomYappyCredentialsProvider : YappyCredentialsProvider {
    
    private var apiKey: String? = null
    private var secretKey: String? = null
    private var deviceId: String? = null
    private var deviceName: String? = null
    private var deviceUser: String? = null
    private var groupId: String? = null
    private var baseUrl: String? = null
    
    /**
     * Establece todas las credenciales de Yappy.
     */
    fun setCredentials(
        apiKey: String,
        secretKey: String,
        deviceId: String,
        groupId: String,
        deviceName: String? = null,
        deviceUser: String? = null,
        baseUrl: String? = null
    ) {
        this.apiKey = apiKey
        this.secretKey = secretKey
        this.deviceId = deviceId
        this.deviceName = deviceName
        this.deviceUser = deviceUser
        this.groupId = groupId
        this.baseUrl = baseUrl
    }
    
    /**
     * Establece solo la API Key.
     */
    fun setApiKey(apiKey: String) {
        this.apiKey = apiKey
    }
    
    /**
     * Establece solo la Secret Key.
     */
    fun setSecretKey(secretKey: String) {
        this.secretKey = secretKey
    }
    
    /**
     * Establece solo el Device ID.
     */
    fun setDeviceId(deviceId: String) {
        this.deviceId = deviceId
    }
    
    /**
     * Establece solo el Device Name.
     */
    fun setDeviceName(deviceName: String) {
        this.deviceName = deviceName
    }
    
    /**
     * Establece solo el Device User.
     */
    fun setDeviceUser(deviceUser: String) {
        this.deviceUser = deviceUser
    }
    
    /**
     * Establece solo el Group ID.
     */
    fun setGroupId(groupId: String) {
        this.groupId = groupId
    }
    
    /**
     * Establece solo la URL base.
     */
    fun setBaseUrl(baseUrl: String) {
        this.baseUrl = baseUrl
    }
    
    override fun getApiKey(): String? = apiKey
    
    override fun getSecretKey(): String? = secretKey
    
    override fun getDeviceId(): String? = deviceId
    
    override fun getDeviceName(): String? = deviceName
    
    override fun getDeviceUser(): String? = deviceUser
    
    override fun getGroupId(): String? = groupId
    
    override fun getBaseUrl(): String? = baseUrl
}