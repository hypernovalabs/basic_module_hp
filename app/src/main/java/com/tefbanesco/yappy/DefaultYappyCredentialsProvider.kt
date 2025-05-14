package com.tefbanesco.yappy

import android.content.Context
import com.tefbanesco.storage.YappyLocalStorage

/**
 * Implementación por defecto de YappyCredentialsProvider que obtiene
 * las credenciales del almacenamiento local del dispositivo.
 */
class DefaultYappyCredentialsProvider(private val context: Context) : YappyCredentialsProvider {
    
    override fun getApiKey(): String? {
        val config = YappyLocalStorage.getYappyConfig(context)
        return config[YappyLocalStorage.KEY_YAPPY_API_KEY]?.takeIf { it.isNotBlank() }
    }

    override fun getSecretKey(): String? {
        val config = YappyLocalStorage.getYappyConfig(context)
        return config[YappyLocalStorage.KEY_YAPPY_SECRET_KEY]?.takeIf { it.isNotBlank() }
    }

    override fun getDeviceId(): String? {
        val config = YappyLocalStorage.getYappyConfig(context)
        return config[YappyLocalStorage.KEY_YAPPY_DEVICE_ID]?.takeIf { it.isNotBlank() }
    }

    override fun getDeviceName(): String? {
        val config = YappyLocalStorage.getYappyConfig(context)
        return config[YappyLocalStorage.KEY_YAPPY_DEVICE_NAME]?.takeIf { it.isNotBlank() } ?: "DefaultDevice"
    }

    override fun getDeviceUser(): String? {
        val config = YappyLocalStorage.getYappyConfig(context)
        return config[YappyLocalStorage.KEY_YAPPY_DEVICE_USER]?.takeIf { it.isNotBlank() } ?: "DefaultUser"
    }

    override fun getGroupId(): String? {
        val config = YappyLocalStorage.getYappyConfig(context)
        return config[YappyLocalStorage.KEY_YAPPY_GROUP_ID]?.takeIf { it.isNotBlank() }
    }

    override fun getBaseUrl(): String? {
        return YappyApiConfig.getBaseUrl(context)
    }
}