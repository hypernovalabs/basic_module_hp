package com.tefbanesco.yappy.model

/**
 * Modelos de datos para la API de Yappy
 * Contiene las data classes para solicitudes y respuestas
 */

/**
 * Modelo para solicitud de apertura de sesión
 */
data class OpenSessionRequest(
    val device: Device,
    val group_id: String
) {
    data class Device(
        val id: String,
        val name: String? = null,
        val user: String? = null
    )
}

/**
 * Modelo para respuesta de apertura de sesión
 */
data class OpenSessionResponse(
    val status: String,
    val opened_date: String,
    val token: String
)

/**
 * Modelo para solicitud de generación de QR
 */
data class QRRequest(
    val charge_amount: ChargeAmount,
    val order_id: String,
    val description: String? = null
) {
    data class ChargeAmount(
        val sub_total: Double,
        val tax: Double = 0.0,
        val tip: Double = 0.0,
        val discount: Double = 0.0,
        val total: Double
    ) {
        /**
         * Verifica que los montos sean consistentes:
         * sub_total + tax + tip - discount == total
         */
        fun isValid(): Boolean {
            val calculatedTotal = sub_total + tax + tip - discount
            // Usar una pequeña tolerancia para comparación de flotantes
            return Math.abs(calculatedTotal - total) < 0.001
        }
    }
}

/**
 * Modelo para respuesta de generación de QR
 */
data class QRResponse(
    val date: String,
    val transaction_id: String,
    val hash: String? = null,
    val qr_data: String? = null
)

/**
 * Modelo para respuesta de consulta de transacción
 */
data class TransactionResponse(
    val status: String,
    val transaction_id: String,
    val amount: Double,
    val date: String
)

/**
 * Estados posibles de una transacción
 */
enum class TransactionStatus {
    PENDING,
    COMPLETED,
    DECLINED,
    EXPIRED,
    FAILED,
    VOIDED,
    UNKNOWN;
    
    companion object {
        fun fromString(status: String): TransactionStatus {
            return try {
                valueOf(status.uppercase())
            } catch (e: Exception) {
                UNKNOWN
            }
        }
    }
}

/**
 * Modelo para respuesta de anulación de transacción
 */
data class VoidTransactionResponse(
    val status: String,
    val transaction_id: String
)

/**
 * Modelo para respuesta de cierre de sesión
 */
data class CloseSessionResponse(
    val status: String,
    val opened_date: String,
    val closed_date: String,
    val summary: Summary
) {
    data class Summary(
        val total_transactions: Int,
        val completed_transactions: Int,
        val declined_transactions: Int,
        val expired_transactions: Int,
        val failed_transactions: Int,
        val voided_transactions: Int
    )
}

/**
 * Modelo para errores de la API
 */
data class YappyError(
    val code: String,
    val message: String
) {
    companion object {
        // Errores comunes para manejo específico

        // Errores de sesión
        const val ERROR_OPEN_SESSION = "YP-0007"  // Actualizado según la versión más reciente
        const val ERROR_SESSION_ALREADY_OPEN = "YP-0004"  // Actualizado según la versión más reciente
        const val ERROR_CLOSE_SESSION = "YP-0400"
        const val ERROR_SESSION_ALREADY_CLOSED = "YP-0001"  // Actualizado según la versión más reciente
        const val ERROR_INVALID_SESSION_TOKEN = "YP-0006"

        // Errores de datos
        const val ERROR_INVALID_DATA = "YP-0010"
        const val ERROR_MISSING_REQUIRED_FIELDS = "YP-0009"  // Error para campos obligatorios faltantes
        const val ERROR_AMOUNTS_MISMATCH = "YP-0405"

        // Errores de transacción
        const val ERROR_TRANSACTION_ALREADY_VOIDED = "YP-0016"
        const val ERROR_TRANSACTION_ALREADY_SETTLED = "YP-0014"
        const val ERROR_VOID_TRANSACTION = "YP-0013"

        /**
         * Obtiene una descripción amigable para un código de error de Yappy
         */
        fun getErrorDescription(errorCode: String): String {
            return when(errorCode) {
                ERROR_OPEN_SESSION -> "Error al abrir sesión"
                ERROR_SESSION_ALREADY_OPEN -> "La sesión ya estaba abierta"
                ERROR_CLOSE_SESSION -> "Error al cerrar sesión"
                ERROR_SESSION_ALREADY_CLOSED -> "La sesión ya estaba cerrada"
                ERROR_INVALID_SESSION_TOKEN -> "Token de sesión inválido"
                ERROR_INVALID_DATA -> "Datos inválidos"
                ERROR_MISSING_REQUIRED_FIELDS -> "Faltan campos obligatorios en la solicitud. Verifique la estructura JSON."
                ERROR_AMOUNTS_MISMATCH -> "Los montos no coinciden"
                ERROR_TRANSACTION_ALREADY_VOIDED -> "La transacción ya fue anulada"
                ERROR_TRANSACTION_ALREADY_SETTLED -> "La transacción ya fue liquidada"
                ERROR_VOID_TRANSACTION -> "Error al anular la transacción"
                else -> "Error desconocido ($errorCode)"
            }
        }
    }
}