package com.tefbanesco.yappy

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.tefbanesco.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Activity que muestra el QR de Yappy y gestiona el flujo de pago
 */
class YappyPaymentActivity : AppCompatActivity(), YappyFlow.YappyFlowCallbacks {

    private val TAG = "YappyPaymentActivity"
    private lateinit var yappyFlow: YappyFlow

    // Vistas
    private lateinit var ivQrCode: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvYappyUser: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnCancel: Button

    // Datos de la transacción
    private var amount: Double = 0.0
    private var transactionId: Int? = null
    private var currencyIso: String? = null
    private var transactionType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yappy_payment)

        // Inicializar vistas
        ivQrCode = findViewById(R.id.ivQrCode)
        tvStatus = findViewById(R.id.tvStatus)
        tvYappyUser = findViewById(R.id.tvYappyUser)
        progressBar = findViewById(R.id.progressBar)
        btnCancel = findViewById(R.id.btnCancelYappy)

        // Mostrar usuario Yappy actual
        updateYappyUserDisplay()

        // Obtener datos de la transacción
        intent.extras?.let { extras ->
            amount = extras.getDouble("amount", 0.0)
            transactionId = extras.getInt("transactionId", 0)
            currencyIso = extras.getString("currencyIso")
            transactionType = extras.getString("transactionType")
        }

        // Validar datos mínimos
        if (amount <= 0.0 || transactionId == 0) {
            finishWithError("Datos de transacción incompletos o inválidos")
            return
        }

        // Configurar botón cancelar
        btnCancel.setOnClickListener {
            yappyFlow.requestCancellation()
            btnCancel.isEnabled = false
            tvStatus.text = getString(R.string.yappy_cancelling)
        }

        // Inicializar YappyFlow
        yappyFlow = YappyFlow(
            context = this,
            coroutineScope = lifecycleScope,
            callbacks = this
        )

        // Iniciar proceso de pago
        startPayment()
    }

    private fun startPayment() {
        try {
            // Crear objeto PaymentIntentData
            val paymentData = YappyFlow.PaymentIntentData(
                amount = amount,
                transactionId = transactionId
            )

            // Iniciar flujo de pago
            yappyFlow.startPaymentProcess(paymentData)

        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando pago Yappy", e)
            finishWithError("Error iniciando pago: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpiar recursos
        if (::yappyFlow.isInitialized) {
            yappyFlow.cleanup()
        }
    }

    // Implementación de callbacks de YappyFlow

    override fun onLoadingStateChanged(isLoading: Boolean) {
        runOnUiThread {
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    override fun onQrReady(hash: String, localOrderId: String, yappyTransactionId: String, amount: Double) {
        runOnUiThread {
            // Generar y mostrar código QR
            try {
                val bitMatrix = generateQRCode(hash)
                ivQrCode.setImageBitmap(createBitmap(bitMatrix))
                ivQrCode.visibility = View.VISIBLE

                // Actualizar estado
                tvStatus.text = "${getString(R.string.yappy_scan_qr)}\n${getString(R.string.yappy_amount_format, amount)}"

                // Activar botón de cancelar
                btnCancel.isEnabled = true

            } catch (e: Exception) {
                Log.e(TAG, "Error generando QR", e)
                tvStatus.text = "Error generando código QR: ${e.message}"
            }
        }
    }

    override fun onPollingStatusUpdate(status: String, message: String, isFinal: Boolean) {
        runOnUiThread {
            tvStatus.text = message
            if (isFinal) {
                btnCancel.isEnabled = false
            }
        }
    }

    override fun onTransactionSuccess(yappyTransactionId: String, localOrderId: String) {
        runOnUiThread {
            // Mostrar mensaje de éxito
            tvStatus.text = getString(R.string.yappy_success)
            btnCancel.isEnabled = false

            // Devolver resultado exitoso a PaymentActivity
            val data = Intent()
            data.putExtra("yappyTransactionId", yappyTransactionId)
            data.putExtra("localOrderId", localOrderId)
            setResult(Activity.RESULT_OK, data)

            // Esperar un momento para que el usuario vea el mensaje
            lifecycleScope.launch {
                kotlinx.coroutines.delay(2000)
                finish()
            }
        }
    }

    override fun onTransactionFailure(yappyTransactionId: String?, localOrderId: String?, errorMessage: String) {
        runOnUiThread {
            finishWithError(errorMessage, yappyTransactionId, localOrderId)
        }
    }

    override fun onSessionClosed() {
        // No es necesario hacer nada específico aquí
    }

    /**
     * Finaliza la actividad con error
     */
    private fun finishWithError(
        errorMessage: String,
        yappyTransactionId: String? = null,
        localOrderId: String? = null
    ) {
        // Preparar intent con datos del error
        val data = Intent()
        data.putExtra("errorMessage", errorMessage)

        if (!yappyTransactionId.isNullOrBlank()) {
            data.putExtra("yappyTransactionId", yappyTransactionId)
        }

        if (!localOrderId.isNullOrBlank()) {
            data.putExtra("localOrderId", localOrderId)
        }

        // Establecer resultado y finalizar
        setResult(Activity.RESULT_CANCELED, data)
        finish()
    }

    /**
     * Genera el código QR a partir del hash
     */
    private fun generateQRCode(data: String): BitMatrix {
        val multiFormatWriter = MultiFormatWriter()
        return multiFormatWriter.encode(
            data,
            BarcodeFormat.QR_CODE,
            500,
            500
        )
    }

    /**
     * Crea un bitmap a partir de una BitMatrix
     */
    private fun createBitmap(matrix: BitMatrix): android.graphics.Bitmap {
        val width = matrix.width
        val height = matrix.height
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }

        return bitmap
    }

    /**
     * Actualiza la vista que muestra el usuario Yappy actual
     */
    private fun updateYappyUserDisplay() {
        // Obtener el nombre de usuario actual
        val username = com.tefbanesco.storage.YappyLocalStorage.getYappyCurrentUsername(this)

        if (username != null) {
            tvYappyUser.text = "Usuario activo: $username"
        } else {
            // Si no hay usuario configurado, usar el fallback
            if (com.tefbanesco.yappy.YappyApiConfig.isYappyConfigured(this)) {
                // Si hay configuración pero no hay username, usar "admin" (fallback)
                tvYappyUser.text = "Usuario activo: ${com.tefbanesco.yappy.DefaultYappyConfig.FALLBACK_USERNAME}"
            } else {
                tvYappyUser.text = "Usuario: No configurado"
            }
        }
    }
}