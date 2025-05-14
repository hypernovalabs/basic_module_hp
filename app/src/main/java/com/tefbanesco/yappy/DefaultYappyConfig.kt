package com.tefbanesco.yappy

/**
 * Valores por defecto para la configuración de Yappy.
 * Utilizado como fallback cuando falla la conexión al servidor.
 */
object DefaultYappyConfig {
    const val FALLBACK_USERNAME = "admin"

    // Estos son los datos de 'response_data' para el usuario 'admin'
    // que obtendrías después de descifrar. Los ponemos aquí directamente
    // como el JSON que esperarías descifrar.
    const val FALLBACK_DECRYPTED_JSON_STRING = """
    {
        "body": {
            "device": {
                "id": "CAJA-02",
                "name": "C",
                "user": "a"
            },
            "group_id": "ID-TESTING-HYPER"
        },
        "config": {
            "endpoint": "https://api-integrationcheckout-uat.yappycloud.com/v1",
            "api-key": "937bfbe7-a29e-4fcb-b155-affe133bd3a6",
            "secret-key": "WVBfQkVCOTZDNzQtMDgxOC0zODVBLTg0ODktNUQxQTNBODVCRjFF"
        }
    }
    """

    // Modelos para la configuración Yappy
    data class YappyFullConfig(
        val body: YappyBody,
        val config: YappyApiDetails
    )
    
    data class YappyBody(
        val device: YappyDevice,
        val group_id: String
    )
    
    data class YappyDevice(
        val id: String,
        val name: String,
        val user: String
    )
    
    data class YappyApiDetails(
        val endpoint: String,
        val apiKey: String,     // Nombrado para acceso como propiedad en Kotlin
        val secretKey: String   // Nombrado para acceso como propiedad en Kotlin
    )

    // Objeto con la configuración por defecto
    val FALLBACK_CONFIG_OBJECT = YappyFullConfig(
        body = YappyBody(
            device = YappyDevice(id = "CAJA-02", name = "C", user = "a"),
            group_id = "ID-TESTING-HYPER"
        ),
        config = YappyApiDetails(
            endpoint = "https://api-integrationcheckout-uat.yappycloud.com/v1",
            apiKey = "937bfbe7-a29e-4fcb-b155-affe133bd3a6",
            secretKey = "WVBfQkVCOTZDNzQtMDgxOC0zODVBLTg0ODktNUQxQTNBODVCRjFF"
        )
    )
}