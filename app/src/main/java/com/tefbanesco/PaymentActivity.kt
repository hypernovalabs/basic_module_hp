package com.tefbanesco

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Constantes para las acciones (para evitar errores de tipeo y mayor claridad)
const val ACTION_PREFIX = "icg.actions.electronicpayment.tefbanesco."
const val ACTION_INITIALIZE = ACTION_PREFIX + "INITIALIZE"
const val ACTION_TRANSACTION = ACTION_PREFIX + "TRANSACTION"
const val ACTION_GET_BEHAVIOR = ACTION_PREFIX + "GET_BEHAVIOR"

// Constantes para los extras (claves) de los Intents según la documentación
object ExtraKeys {
    // Entrada INITIALIZE
    const val PARAMETERS = "Parameters" // String (XML)
    const val TOKEN = "Token" // String (UUID para auditoría)

    // Salida INITIALIZE / Común para errores
    const val ERROR_MESSAGE = "ErrorMessage" // String

    // Salida GET_BEHAVIOR (Booleanos)
    const val SUPPORTS_TRANSACTION_VOID = "SupportsTransactionVoid"
    const val SUPPORTS_TRANSACTION_QUERY = "SupportsTransactionQuery"
    const val SUPPORTS_NEGATIVE_SALES = "SupportsNegativeSales"
    const val SUPPORTS_PARTIAL_REFUND = "SupportsPartialRefund"
    const val SUPPORTS_BATCH_CLOSE = "SupportsBatchClose"
    const val SUPPORTS_TIP_ADJUSTMENT = "SupportsTipAdjustment"
    const val ONLY_CREDIT_FOR_TIP_ADJUSTMENT = "OnlyCreditForTipAdjustment"
    const val SUPPORTS_CREDIT = "SupportsCredit"
    const val SUPPORTS_DEBIT = "SupportsDebit"
    const val SUPPORTS_EBT_FOODSTAMP = "SupportsEBTFoodstamp"
    const val HAS_CUSTOM_PARAMS = "HasCustomParams"
    const val CAN_CHARGE_CARD = "CanChargeCard"
    const val CAN_AUDIT = "canAudit" // Nótese la minúscula inicial, según documentación
    const val EXECUTE_VOID_WHEN_AVAILABLE = "ExecuteVoidWhenAvailable"
    const val SAVE_LOYALTY_CARD_NUM = "SaveLoyaltyCardNum"
    const val CAN_PRINT = "CanPrint"
    const val READ_CARD_FROM_API = "ReadCardFromApi"
    const val ONLY_USE_DOCUMENT_PATH = "OnlyUseDocumentPath" // Nuevo en API v3.7

    // Entrada TRANSACTION
    const val TRANSACTION_TYPE = "TransactionType" // String (SALE, REFUND, etc.)
    const val AMOUNT = "Amount" // String (entero, ej: "1234" para 12.34)
    const val CURRENCY_ISO = "CurrencyISO" // String (ej: "EUR")
    const val LANGUAGE_ISO = "LanguageISO" // String (ej: "es")
    const val TRANSACTION_ID_HIO = "TransactionId" // String (ID de HioPos)
    const val SHOP_DATA = "ShopData" // String (XML)
    const val SELLER_DATA = "SellerData" // String (XML)
    const val TAX_DETAIL = "TaxDetail" // String (XML)
    const val RECEIPT_PRINTER_COLUMNS = "ReceiptPrinterColumns" // Int
    const val DOCUMENT_DATA = "DocumentData" // String (XML del documento de venta)
    const val DOCUMENT_PATH = "DocumentPath" // String (Ruta a fichero XML del doc. venta)
    const val OVER_PAYMENT_TYPE = "OverPaymentType" // Int

    // Salida TRANSACTION
    const val TRANSACTION_RESULT = "TransactionResult" // String (ACCEPTED, FAILED, UNKNOWN_RESULT)
    // TRANSACTION_TYPE (reutiliza la constante de entrada)
    // AMOUNT (reutiliza la constante de entrada)
    const val TRANSACTION_DATA_MODULE = "TransactionData" // String (datos del módulo, max 250 chars)
    // SHOP_DATA (reutiliza constante de entrada, puede ser devuelto)
    // SELLER_DATA (reutiliza constante de entrada, puede ser devuelto)
    const val AUTHORIZATION_ID = "AuthorizationId" // String (varchar(40))
    const val CARD_HOLDER = "CardHolder" // String (varchar(255))
    const val CARD_TYPE = "CardType" // String (varchar(30))
    const val CARD_NUM = "CardNum" // String (VarChar(100), ofuscado)
    const val ERROR_MESSAGE_TITLE = "ErrorMessageTitle" // String
    const val DOCUMENT_ID = "DocumentId" // String (nuevo en API v3.5 para salida)
}

class PaymentActivity : AppCompatActivity() {

    private val TAG = "PaymentActivity"
    private val REQUEST_CODE_YAPPY_PAYMENT = 1001 // Código para identificar resultados de Yappy

    // Variables para almacenar datos de la transacción actual
    private var currentTransactionIdHio: Int? = 0
    private var currentAmount: String? = null
    private var currentCurrencyIso: String? = null
    private var currentTransactionType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Intent action = ${intent.action}")
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent: Intent action = ${intent?.action}")
        intent?.let { handleIntent(it) }
    }

    private fun handleIntent(intent: Intent) {
        // Loguear los extras del intent para todos los tipos de acciones
        logIntentExtras(intent.action, intent)

        when (intent.action) {
            ACTION_INITIALIZE -> handleInitialize(intent)
            ACTION_GET_BEHAVIOR -> handleGetBehavior(intent)
            ACTION_TRANSACTION -> handleTransaction(intent)
            else -> respondUnsupported(intent)
        }
    }

    /**
     * Función auxiliar para loguear todos los extras del intent recibido
     *
     * @param action Acción del intent que se está procesando
     * @param intent Intent recibido con todos sus extras
     */
    private fun logIntentExtras(action: String?, intent: Intent) {
        Log.d(TAG, "╔═════════════════════════════════════════")
        Log.d(TAG, "║ RECIBIDO: $action")
        Log.d(TAG, "╠═════════════════════════════════════════")

        if (intent.extras == null || intent.extras!!.isEmpty) {
            Log.d(TAG, "║ No hay extras en el intent")
            Log.d(TAG, "╚═════════════════════════════════════════")
            return
        }

        // Iterar sobre todos los extras y loguerarlos
        intent.extras!!.keySet().forEach { key ->
            val value = intent.extras!!.get(key)

            // Formatea valores largos (XML) para mejor legibilidad
            when {
                key.equals(ExtraKeys.SHOP_DATA, ignoreCase = true) ||
                key.equals(ExtraKeys.SELLER_DATA, ignoreCase = true) ||
                key.equals(ExtraKeys.TAX_DETAIL, ignoreCase = true) ||
                key.equals(ExtraKeys.DOCUMENT_DATA, ignoreCase = true) -> {
                    val xmlValue = value?.toString() ?: "null"
                    if (xmlValue.length > 200) {
                        // Para XML largos, muestra el inicio y el final
                        Log.d(TAG, "║ $key: ${xmlValue.substring(0, 100)}...")
                        Log.d(TAG, "║    ...${xmlValue.substring(xmlValue.length - 100)}")
                        Log.d(TAG, "║    (Total: ${xmlValue.length} caracteres)")
                    } else {
                        Log.d(TAG, "║ $key: $xmlValue")
                    }
                }
                else -> {
                    Log.d(TAG, "║ $key: $value")
                }
            }
        }
        Log.d(TAG, "╚═════════════════════════════════════════")
    }

    private fun respondUnsupported(intent: Intent) {
        Log.e(TAG, "Acción desconocida o no manejada: ${intent.action}")
        // Devolver un error genérico si la acción no es reconocida
        val resultIntent = Intent(intent.action) // Devolver la misma acción filtrada
        resultIntent.putExtra(ExtraKeys.ERROR_MESSAGE, "Acción no soportada: ${intent.action}")
        setResult(Activity.RESULT_CANCELED, resultIntent)
        finish()
    }

    // --- Implementación de INITIALIZE ---
    private fun handleInitialize(intent: Intent) {
        Log.d(TAG, "Manejando INITIALIZE")

        // Validar parámetros de la inicialización
        val missingParams = mutableListOf<String>()

        // Verificar Parameters (XML)
        val parametersXml = intent.getStringExtra(ExtraKeys.PARAMETERS)
        if (parametersXml == null) {
            missingParams.add("Parameters (XML)")
            Log.w(TAG, "INITIALIZE: Parámetro Parameters (XML) no recibido")
        } else if (parametersXml.isBlank()) {
            Log.w(TAG, "INITIALIZE: Parámetro Parameters (XML) está vacío")
        }

        // Verificar Token (opcional pero recomendado para auditoría)
        val token = intent.getStringExtra(ExtraKeys.TOKEN)
        if (token == null) {
            Log.w(TAG, "INITIALIZE: Parámetro Token no recibido (recomendado para auditoría)")
        } else if (token.isBlank()) {
            Log.w(TAG, "INITIALIZE: Parámetro Token está vacío")
        } else {
            Log.i(TAG, "INITIALIZE: Token para auditoría recibido: $token")
        }

        // Para INITIALIZE, el token no es obligatorio, por lo que sólo validamos Parameters
        if (missingParams.isNotEmpty()) {
            val errorMessage = "Parámetros de inicialización incompletos: ${missingParams.joinToString(", ")}."
            Log.e(TAG, "INITIALIZE_ERROR: $errorMessage")

            val resultIntent = Intent(ACTION_INITIALIZE)
            resultIntent.putExtra(ExtraKeys.ERROR_MESSAGE, errorMessage)
            setResult(Activity.RESULT_CANCELED, resultIntent)
            finish()
            return
        }

        // Simulación de parseo y guardado de parámetros
        // En una app real, parsearías el XML y guardarías en SharedPreferences o DB.
        try {
            // Aquí se procesaría y guardaría la configuración
            // En el prototipo, simplemente simulamos éxito
            Log.i(TAG, "INITIALIZE: Procesando parámetros XML recibidos")
            val success = true // Simular éxito

            val resultIntent = Intent(ACTION_INITIALIZE) // Acción filtrada como respuesta
            if (success) {
                Log.i(TAG, "INITIALIZE exitoso")
                setResult(Activity.RESULT_OK, resultIntent)
            } else {
                val errorMessage = "Error al guardar la configuración remota."
                resultIntent.putExtra(ExtraKeys.ERROR_MESSAGE, errorMessage)
                Log.e(TAG, "INITIALIZE fallido: $errorMessage")
                setResult(Activity.RESULT_CANCELED, resultIntent)
            }
        } catch (e: Exception) {
            // Si hay un error al procesar los parámetros
            Log.e(TAG, "INITIALIZE: Error al procesar parámetros", e)
            val resultIntent = Intent(ACTION_INITIALIZE)
            resultIntent.putExtra(ExtraKeys.ERROR_MESSAGE, "Error al procesar la configuración: ${e.message}")
            setResult(Activity.RESULT_CANCELED, resultIntent)
        }

        finish() // Finalizar la activity después de procesar INITIALIZE
    }

    // --- Implementación de GET_BEHAVIOR ---
    private fun handleGetBehavior(intent: Intent) {
        Log.d(TAG, "Manejando GET_BEHAVIOR")

        try {
            val resultIntent = Intent(ACTION_GET_BEHAVIOR) // Acción filtrada

            // Configurar las capacidades del módulo según la API
            // Para mejor depuración, log cada una de las capacidades con su valor
            Log.i(TAG, "GET_BEHAVIOR: Configurando capacidades del módulo:")

            // Capacidades relacionadas con tipo de transacciones
            val supportsTransactionVoid = false
            resultIntent.putExtra(ExtraKeys.SUPPORTS_TRANSACTION_VOID, supportsTransactionVoid)
            Log.i(TAG, "- SupportsTransactionVoid: $supportsTransactionVoid")

            val supportsTransactionQuery = false
            resultIntent.putExtra(ExtraKeys.SUPPORTS_TRANSACTION_QUERY, supportsTransactionQuery)
            Log.i(TAG, "- SupportsTransactionQuery: $supportsTransactionQuery")

            val supportsNegativeSales = false
            resultIntent.putExtra(ExtraKeys.SUPPORTS_NEGATIVE_SALES, supportsNegativeSales)
            Log.i(TAG, "- SupportsNegativeSales: $supportsNegativeSales")

            val supportsPartialRefund = false
            resultIntent.putExtra(ExtraKeys.SUPPORTS_PARTIAL_REFUND, supportsPartialRefund)
            Log.i(TAG, "- SupportsPartialRefund: $supportsPartialRefund")

            val supportsBatchClose = false
            resultIntent.putExtra(ExtraKeys.SUPPORTS_BATCH_CLOSE, supportsBatchClose)
            Log.i(TAG, "- SupportsBatchClose: $supportsBatchClose")

            // Capacidades relacionadas con propinas
            val supportsTipAdjustment = false
            resultIntent.putExtra(ExtraKeys.SUPPORTS_TIP_ADJUSTMENT, supportsTipAdjustment)
            Log.i(TAG, "- SupportsTipAdjustment: $supportsTipAdjustment")

            val onlyCreditForTipAdjustment = false
            resultIntent.putExtra(ExtraKeys.ONLY_CREDIT_FOR_TIP_ADJUSTMENT, onlyCreditForTipAdjustment)
            Log.i(TAG, "- OnlyCreditForTipAdjustment: $onlyCreditForTipAdjustment")

            // Capacidades relacionadas con tipos de pago
            val supportsCredit = true // Asumimos que el pago es tipo crédito
            resultIntent.putExtra(ExtraKeys.SUPPORTS_CREDIT, supportsCredit)
            Log.i(TAG, "- SupportsCredit: $supportsCredit")

            val supportsDebit = false
            resultIntent.putExtra(ExtraKeys.SUPPORTS_DEBIT, supportsDebit)
            Log.i(TAG, "- SupportsDebit: $supportsDebit")

            val supportsEbtFoodstamp = false
            resultIntent.putExtra(ExtraKeys.SUPPORTS_EBT_FOODSTAMP, supportsEbtFoodstamp)
            Log.i(TAG, "- SupportsEBTFoodstamp: $supportsEbtFoodstamp")

            // Otras capacidades
            val hasCustomParams = false // No hay logo/nombre personalizado
            resultIntent.putExtra(ExtraKeys.HAS_CUSTOM_PARAMS, hasCustomParams)
            Log.i(TAG, "- HasCustomParams: $hasCustomParams")

            val canChargeCard = false // No usamos ReadCard/ChargeCard
            resultIntent.putExtra(ExtraKeys.CAN_CHARGE_CARD, canChargeCard)
            Log.i(TAG, "- CanChargeCard: $canChargeCard")

            val canAudit = false // No implementamos auditoría activa
            resultIntent.putExtra(ExtraKeys.CAN_AUDIT, canAudit)
            Log.i(TAG, "- canAudit: $canAudit")

            val executeVoidWhenAvailable = false
            resultIntent.putExtra(ExtraKeys.EXECUTE_VOID_WHEN_AVAILABLE, executeVoidWhenAvailable)
            Log.i(TAG, "- ExecuteVoidWhenAvailable: $executeVoidWhenAvailable")

            val saveLoyaltyCardNum = false
            resultIntent.putExtra(ExtraKeys.SAVE_LOYALTY_CARD_NUM, saveLoyaltyCardNum)
            Log.i(TAG, "- SaveLoyaltyCardNum: $saveLoyaltyCardNum")

            val canPrint = false // No imprimimos
            resultIntent.putExtra(ExtraKeys.CAN_PRINT, canPrint)
            Log.i(TAG, "- CanPrint: $canPrint")

            val readCardFromApi = false
            resultIntent.putExtra(ExtraKeys.READ_CARD_FROM_API, readCardFromApi)
            Log.i(TAG, "- ReadCardFromApi: $readCardFromApi")

            val onlyUseDocumentPath = false
            resultIntent.putExtra(ExtraKeys.ONLY_USE_DOCUMENT_PATH, onlyUseDocumentPath)
            Log.i(TAG, "- OnlyUseDocumentPath: $onlyUseDocumentPath")

            Log.i(TAG, "GET_BEHAVIOR configurado y listo para devolver.")
            setResult(Activity.RESULT_OK, resultIntent)
        } catch (e: Exception) {
            // Manejo de errores inesperados
            Log.e(TAG, "Error al configurar GET_BEHAVIOR: ${e.message}", e)
            val errorIntent = Intent(ACTION_GET_BEHAVIOR)
            errorIntent.putExtra(ExtraKeys.ERROR_MESSAGE, "Error inesperado al procesar capacidades: ${e.message}")
            setResult(Activity.RESULT_CANCELED, errorIntent)
        }

        finish() // Finalizar la activity después de procesar GET_BEHAVIOR
    }

    // --- Implementación de TRANSACTION ---
    private fun handleTransaction(intent: Intent) {
        Log.d(TAG, "Manejando TRANSACTION")

        // 1. Validar parámetros obligatorios antes de extraerlos
        val missingParams = mutableListOf<String>()

        // Validar TransactionType
        val transactionTypeStr = intent.getStringExtra(ExtraKeys.TRANSACTION_TYPE)
        if (transactionTypeStr == null || transactionTypeStr.isBlank()) {
            missingParams.add("TransactionType")
        }

        // Validar Amount
        val amountStr = intent.getStringExtra(ExtraKeys.AMOUNT)
        if (amountStr == null || amountStr.isBlank()) {
            missingParams.add("Amount")
        } else {
            try {
                // Validar que Amount sea un número entero válido
                amountStr.trim().toInt()
            } catch (e: NumberFormatException) {
                missingParams.add("Amount (formato inválido, debe ser un número entero)")
            }
        }

        // Validar TransactionId HIO
        val transactionIdHioStr = intent.getIntExtra(ExtraKeys.TRANSACTION_ID_HIO,0)
        println("TransactionIdHioStr: $transactionIdHioStr")
        if (transactionIdHioStr == 0) {
            missingParams.add("TransactionId (HioPos)")
        }

        // Validar CurrencyISO (También es importante para la transacción)
        val currencyIsoStr = intent.getStringExtra(ExtraKeys.CURRENCY_ISO)
        if (currencyIsoStr == null || currencyIsoStr.isBlank()) {
            missingParams.add("CurrencyISO")
        }

        // Si hay parámetros faltantes, responder con error específico
        if (missingParams.isNotEmpty()) {
            val errorMessage = "Parámetros de entrada incompletos o inválidos: ${missingParams.joinToString(", ")}."
            Log.e(TAG, "TRANSACTION_ERROR: $errorMessage")

            val errorResult = Intent(ACTION_TRANSACTION)
            errorResult.putExtra(ExtraKeys.TRANSACTION_RESULT, "FAILED")
            errorResult.putExtra(ExtraKeys.ERROR_MESSAGE, errorMessage)
            errorResult.putExtra(ExtraKeys.ERROR_MESSAGE_TITLE, "Error de Datos - Módulo TEF")

            setResult(Activity.RESULT_OK, errorResult) // RESULT_OK porque el módulo responde, aunque la transacción falle
            finish()
            return
        }

        // 2. Si la validación es exitosa, extraer los parámetros
        currentTransactionType = transactionTypeStr
        currentAmount = amountStr
        currentCurrencyIso = currencyIsoStr
        currentTransactionIdHio = transactionIdHioStr

        // Extraer parámetros adicionales no críticos
        val languageIso = intent.getStringExtra(ExtraKeys.LANGUAGE_ISO)
        val shopDataXml = intent.getStringExtra(ExtraKeys.SHOP_DATA)
        val sellerDataXml = intent.getStringExtra(ExtraKeys.SELLER_DATA)
        val taxDetailXml = intent.getStringExtra(ExtraKeys.TAX_DETAIL)
        val printerCols = intent.getIntExtra(ExtraKeys.RECEIPT_PRINTER_COLUMNS, 42)
        val documentDataXml = intent.getStringExtra(ExtraKeys.DOCUMENT_DATA) // XML de la venta
        val documentPath = intent.getStringExtra(ExtraKeys.DOCUMENT_PATH) // Ruta al XML de la venta
        val overPaymentType = intent.getIntExtra(ExtraKeys.OVER_PAYMENT_TYPE, -1)

        // Registrar detalles específicos de los parámetros más importantes
        Log.i(TAG, "Datos principales de la transacción validados correctamente:")
        Log.i(TAG, "- TransactionType: $currentTransactionType")
        Log.i(TAG, "- Amount: $currentAmount")
        Log.i(TAG, "- CurrencyISO: $currentCurrencyIso")
        Log.i(TAG, "- TransactionId: $currentTransactionIdHio")

        // 2. Mostrar UI con botones
        setContentView(R.layout.activity_payment_ui)

        val tvAmount = findViewById<TextView>(R.id.tvAmountInfo)
        val tvCurrentYappyUser = findViewById<TextView>(R.id.tvCurrentYappyUser)
        val btnAccept = findViewById<Button>(R.id.btnAcceptPayment)
        val btnCancel = findViewById<Button>(R.id.btnCancelPayment)
        val btnYappy = findViewById<Button>(R.id.btnYappyPayment)

        // Formatear el monto para mostrar en la UI - convertir de "1234" a "12.34"
        val formattedAmount = formatAmount(currentAmount!!)
        tvAmount.text = getString(R.string.payment_amount_format, formattedAmount, currentCurrencyIso)

        // Actualizar información del usuario Yappy
        updateYappyUserDisplay(tvCurrentYappyUser)

        btnAccept.setOnClickListener {
            Log.i(TAG, "Pago ACEPTADO por el usuario.")
            sendTransactionResponse(true, shopDataXml, sellerDataXml)
        }

        btnCancel.setOnClickListener {
            Log.i(TAG, "Pago CANCELADO por el usuario.")
            sendTransactionResponse(false, shopDataXml, sellerDataXml)
        }

        // Configurar botón de Yappy - siempre visible
        btnYappy.visibility = View.VISIBLE
        btnYappy.setOnClickListener {
            Log.i(TAG, "Pago con Yappy seleccionado.")
            handleYappyPayment()
        }
    }
    
    /**
     * Actualiza el TextView que muestra el usuario Yappy actual
     * @param textView TextView donde mostrar la información
     */
    private fun updateYappyUserDisplay(textView: TextView) {
        // Obtener el nombre de usuario actual
        val username = com.tefbanesco.storage.YappyLocalStorage.getYappyCurrentUsername(this)

        if (username != null) {
            textView.text = "Usuario Yappy: $username"
            textView.visibility = View.VISIBLE
        } else {
            // Si no hay usuario configurado, usar el fallback
            if (com.tefbanesco.yappy.YappyApiConfig.isYappyConfigured(this)) {
                // Si hay configuración pero no hay username, usar "admin" (fallback)
                textView.text = "Usuario Yappy: ${com.tefbanesco.yappy.DefaultYappyConfig.FALLBACK_USERNAME}"
            } else {
                textView.text = "Usuario Yappy: No configurado"
            }
            textView.visibility = View.VISIBLE
        }
    }

    /**
     * Formatea un monto de "1234" a "12.34" para mostrar en la UI
     *
     * @param amountStr Monto en formato string "1234" (12.34)
     * @return Monto formateado "12.34"
     */
    private fun formatAmount(amountStr: String): String {
        try {
            // Eliminar espacios y verificar si es un número válido
            val trimmedAmount = amountStr.trim()
            if (trimmedAmount.isEmpty()) {
                Log.e(TAG, "Monto vacío")
                return "0.00"
            }
            
            // Verificar si hay signo negativo
            val isNegative = trimmedAmount.startsWith("-")
            val amountToProcess = if (isNegative) trimmedAmount.substring(1) else trimmedAmount
            
            // Convertir a entero
            val amount = amountToProcess.toInt()
            
            // Formatear según sea menor o mayor que 100
            val formatted = if (amount < 100) {
                String.format("0.%02d", amount)
            } else {
                String.format("%d.%02d", amount / 100, amount % 100)
            }
            
            // Agregar signo negativo si corresponde
            return if (isNegative) "-$formatted" else formatted
            
        } catch (e: NumberFormatException) {
            Log.e(TAG, "Error al formatear monto: $amountStr", e)
            return amountStr // Devolver el original si hay error
        } catch (e: Exception) {
            Log.e(TAG, "Error inesperado al formatear monto: $amountStr", e)
            return "0.00" // Valor por defecto ante errores inesperados
        }
    }

//    private fun sendTransactionResponse(accepted: Boolean, shopDataXml: String?, sellerDataXml: String?) {
//        val resultIntent = Intent(ACTION_TRANSACTION) // Acción filtrada
//
//        // Parámetros de salida comunes
//        resultIntent.putExtra(ExtraKeys.TRANSACTION_TYPE, currentTransactionType) // El mismo que entró
//        resultIntent.putExtra(ExtraKeys.AMOUNT, currentAmount) // El mismo que entró
//
//        // TransactionData: Campo definido por el integrador (módulo), max 250 chars.
//        val moduleTransactionId = "TEFBANESCO_MOD_ID_${System.currentTimeMillis()}"
//        val transactionDataModule = """
//            {
//                "module_id": "$moduleTransactionId",
//                "hio_id": "$currentTransactionIdHio",
//                "status": "${if (accepted) "ACCEPTED" else "FAILED"}",
//                "timestamp": "${System.currentTimeMillis()}"
//            }
//        """.trimIndent().replace("\n", "")
//        resultIntent.putExtra(ExtraKeys.TRANSACTION_DATA_MODULE, transactionDataModule)
//
//        // ShopData y SellerData: Pueden devolverse los mismos que entraron o modificados.
//        try {
//            // Devolver ShopData si existe
//            shopDataXml?.let {
//                // Aquí podríamos realizar modificaciones al XML si fuera necesario
//                resultIntent.putExtra(ExtraKeys.SHOP_DATA, it)
//                Log.d(TAG, "ShopData incluido en la respuesta")
//            }
//
//            // Devolver SellerData si existe
//            sellerDataXml?.let {
//                // Aquí podríamos realizar modificaciones al XML si fuera necesario
//                resultIntent.putExtra(ExtraKeys.SELLER_DATA, it)
//                Log.d(TAG, "SellerData incluido en la respuesta")
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error al procesar datos XML para la respuesta", e)
//            // No incluir los datos XML en caso de error al procesarlos
//        }
//
//        if (accepted) {
//            resultIntent.putExtra(ExtraKeys.TRANSACTION_RESULT, "ACCEPTED")
//            // Simular datos de tarjeta para una transacción aceptada
//            resultIntent.putExtra(ExtraKeys.AUTHORIZATION_ID, "AUTH_TEF_${System.currentTimeMillis() % 100000}")
//            resultIntent.putExtra(ExtraKeys.CARD_HOLDER, "CLIENTE BANESCO")
//            resultIntent.putExtra(ExtraKeys.CARD_TYPE, "VISA") // O MasterCard, etc.
//            resultIntent.putExtra(ExtraKeys.CARD_NUM, "**** **** **** 1234") // Número ofuscado
//            Log.i(TAG, "Enviando respuesta TRANSACTION: ACCEPTED")
//        } else {
//            resultIntent.putExtra(ExtraKeys.TRANSACTION_RESULT, "FAILED")
//            resultIntent.putExtra(ExtraKeys.ERROR_MESSAGE, getString(R.string.error_payment_canceled))
//            resultIntent.putExtra(ExtraKeys.ERROR_MESSAGE_TITLE, getString(R.string.error_payment_canceled_title))
//            Log.i(TAG, "Enviando respuesta TRANSACTION: FAILED")
//        }
//
//        // IMPORTANTE: HioPosCloud espera RESULT_OK incluso si la transacción (TransactionResult) es FAILED.
//        setResult(Activity.RESULT_OK, resultIntent)
//        finish() // Finalizar la activity de UI para volver a HioPosCloud
//    }
    // Dentro de PaymentActivity.kt

    /**
     * Maneja el pago con Yappy
     * Verifica configuración e inicia el flujo de pago
     */
    private fun handleYappyPayment() {
        // Verificar si Yappy está configurado
        if (!com.tefbanesco.yappy.YappyApiConfig.isYappyConfigured(this)) {
            // Mostrar diálogo de configuración
            showYappyConfigScreen()
            return
        }

        try {
            // Convertir el monto a formato Double para Yappy
            val amount = formatToDouble(currentAmount ?: "0")

            // Iniciar actividad de pago con Yappy
            val intent = Intent(this, com.tefbanesco.yappy.YappyPaymentActivity::class.java)

            // Pasar los datos relevantes
            intent.putExtra("amount", amount)
            intent.putExtra("transactionId", currentTransactionIdHio)
            intent.putExtra("currencyIso", currentCurrencyIso)
            intent.putExtra("transactionType", currentTransactionType)

            // Iniciar actividad esperando resultado
            startActivityForResult(intent, REQUEST_CODE_YAPPY_PAYMENT)

        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando pago con Yappy", e)
            // En caso de error, mostrar mensaje y usar el flujo normal
            showYappyError("Error iniciando pago con Yappy: ${e.message}")
        }
    }

    /**
     * Muestra la pantalla de configuración de Yappy
     */
    private fun showYappyConfigScreen() {
        try {
            // Mostrar diálogo de elección entre configuración almacenada o directa
            com.tefbanesco.yappy.ui.YappyDirectConfigDialog.showConfigurationChoice(
                context = this,
                onStoredCredentials = {
                    // Inicializar con credenciales almacenadas
                    com.tefbanesco.yappy.YappyServiceFactory.getWithStoredCredentials(this)
                    Log.d(TAG, "Usando configuración almacenada para Yappy")

                    // Si se configuró correctamente, intentar pago con Yappy de nuevo
                    if (com.tefbanesco.yappy.YappyApiConfig.isYappyConfigured(this)) {
                        handleYappyPayment()
                    } else {
                        // Si no hay configuración almacenada, mostrar configuración estándar
                        showYappyStandardConfig()
                    }
                },
                onCustomCredentials = {
                    // Al guardar configuración personalizada, intentar pago
                    handleYappyPayment()
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando pantalla de configuración", e)
            showYappyError("Error en la configuración: ${e.message}")
        }
    }

    /**
     * Muestra la pantalla de configuración estándar de Yappy (para compatibilidad)
     */
    private fun showYappyStandardConfig() {
        try {
            val configManager = com.tefbanesco.yappy.YappyConfigManager()
            val configScreen = com.tefbanesco.yappy.ui.YappyConfigScreen(
                context = this,
                lifecycleOwner = this,
                configManager = configManager,
                onSaveSuccess = {
                    // Si se guardó correctamente, intentar pago con Yappy de nuevo
                    if (com.tefbanesco.yappy.YappyApiConfig.isYappyConfigured(this)) {
                        handleYappyPayment()
                    } else {
                        showYappyError("Configuración guardada pero incompleta")
                    }
                },
                onCancel = {
                    // Si canceló, volvemos al flujo normal
                    Log.d(TAG, "Configuración de Yappy cancelada")
                }
            )
            configScreen.show()
        } catch (e: Exception) {
            Log.e(TAG, "Error mostrando pantalla de configuración estándar", e)
            showYappyError("Error en la configuración: ${e.message}")
        }
    }

    /**
     * Convierte un monto de formato "1234" a Double (12.34)
     */
    private fun formatToDouble(amountStr: String): Double {
        try {
            val amount = amountStr.trim().toInt()
            return amount / 100.0
        } catch (e: Exception) {
            Log.e(TAG, "Error convirtiendo monto a Double", e)
            return 0.0
        }
    }

    /**
     * Muestra un error relacionado con Yappy
     */
    private fun showYappyError(message: String) {
        Log.e(TAG, "Error Yappy: $message")
        android.app.AlertDialog.Builder(this)
            .setTitle("Error en pago Yappy")
            .setMessage(message)
            .setPositiveButton("Aceptar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Recibe resultados de actividad Yappy
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_YAPPY_PAYMENT) {
            when (resultCode) {
                Activity.RESULT_OK -> {
                    // Pago exitoso
                    val yappyTransactionId = data?.getStringExtra("yappyTransactionId") ?: ""
                    val localOrderId = data?.getStringExtra("localOrderId") ?: ""

                    Log.i(TAG, "Pago Yappy exitoso. YappyId: $yappyTransactionId, LocalId: $localOrderId")

                    // Enviar respuesta a HioPosCloud
                    sendTransactionResponseYappy(
                        accepted = true,
                        yappyTransactionId = yappyTransactionId,
                        localOrderId = localOrderId
                    )
                }
                Activity.RESULT_CANCELED -> {
                    // Pago cancelado o fallido
                    val errorMessage = data?.getStringExtra("errorMessage") ?: "Pago cancelado"

                    Log.i(TAG, "Pago Yappy fallido: $errorMessage")

                    // Enviar respuesta a HioPosCloud
                    sendTransactionResponseYappy(
                        accepted = false,
                        errorMessage = errorMessage
                    )
                }
                else -> {
                    // Resultado desconocido
                    Log.e(TAG, "Resultado desconocido en pago Yappy: $resultCode")
                    sendTransactionResponseYappy(
                        accepted = false,
                        errorMessage = "Error desconocido en pago Yappy"
                    )
                }
            }
        }
    }

    /**
     * Envía respuesta a HioPosCloud con datos de Yappy
     */
    private fun sendTransactionResponseYappy(
        accepted: Boolean,
        yappyTransactionId: String = "",
        localOrderId: String = "",
        errorMessage: String = ""
    ) {
        val resultIntent = Intent(ACTION_TRANSACTION)

        // Parámetros comunes
        resultIntent.putExtra(ExtraKeys.TRANSACTION_TYPE, currentTransactionType)
        resultIntent.putExtra(ExtraKeys.AMOUNT, currentAmount)

        // TransactionData específico para Yappy
        val moduleTransactionId = localOrderId.ifEmpty { "YAPPY_ID_${System.currentTimeMillis()}" }
        val transactionDataModule = """
        {
            "module_id": "$moduleTransactionId",
            "yappy_id": "$yappyTransactionId",
            "hio_id": "$currentTransactionIdHio",
            "status": "${if (accepted) "ACCEPTED" else "FAILED"}",
            "payment_method": "YAPPY",
            "timestamp": "${System.currentTimeMillis()}"
        }
        """.trimIndent().replace("\n", "")
        resultIntent.putExtra(ExtraKeys.TRANSACTION_DATA_MODULE, transactionDataModule)

        if (accepted) {
            resultIntent.putExtra(ExtraKeys.TRANSACTION_RESULT, "ACCEPTED")
            resultIntent.putExtra(ExtraKeys.AUTHORIZATION_ID, "YAPPY_$yappyTransactionId")
            resultIntent.putExtra(ExtraKeys.CARD_HOLDER, "CLIENTE YAPPY")
            resultIntent.putExtra(ExtraKeys.CARD_TYPE, "YAPPY_QR")
            resultIntent.putExtra(ExtraKeys.CARD_NUM, "**** **** **** YAPP")
            Log.i(TAG, "Enviando respuesta TRANSACTION (Yappy): ACCEPTED")
        } else {
            resultIntent.putExtra(ExtraKeys.TRANSACTION_RESULT, "FAILED")
            resultIntent.putExtra(ExtraKeys.ERROR_MESSAGE, errorMessage)
            resultIntent.putExtra(ExtraKeys.ERROR_MESSAGE_TITLE, "Error en Pago Yappy")
            Log.i(TAG, "Enviando respuesta TRANSACTION (Yappy): FAILED")
        }

        // Logging de respuesta
        logTransactionResponse(resultIntent)

        // Enviar respuesta
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    /**
     * Envía respuesta estándar a HioPosCloud
     */
    private fun sendTransactionResponse(accepted: Boolean, shopDataXml: String?, sellerDataXml: String?) {
        val resultIntent = Intent(ACTION_TRANSACTION) // Acción filtrada

        // Parámetros de salida comunes
        resultIntent.putExtra(ExtraKeys.TRANSACTION_TYPE, currentTransactionType) // El mismo que entró
        resultIntent.putExtra(ExtraKeys.AMOUNT, currentAmount) // El mismo que entró

        // TransactionData: Campo definido por el integrador (módulo), max 250 chars.
        val moduleTransactionId = "TEFBANESCO_MOD_ID_${System.currentTimeMillis()}"
        val transactionDataModule = """
        {
            "module_id": "$moduleTransactionId",
            "hio_id": "$currentTransactionIdHio",
            "status": "${if (accepted) "ACCEPTED" else "FAILED"}",
            "payment_method": "STANDARD",
            "timestamp": "${System.currentTimeMillis()}"
        }
    """.trimIndent().replace("\n", "")
        resultIntent.putExtra(ExtraKeys.TRANSACTION_DATA_MODULE, transactionDataModule)

        // ShopData y SellerData: Pueden devolverse los mismos que entraron o modificados.
        try {
            // Devolver ShopData si existe
            shopDataXml?.let {
                // Aquí podríamos realizar modificaciones al XML si fuera necesario
                resultIntent.putExtra(ExtraKeys.SHOP_DATA, it)
                Log.d(TAG, "ShopData incluido en la respuesta")
            }

            // Devolver SellerData si existe
            sellerDataXml?.let {
                // Aquí podríamos realizar modificaciones al XML si fuera necesario
                resultIntent.putExtra(ExtraKeys.SELLER_DATA, it)
                Log.d(TAG, "SellerData incluido en la respuesta")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al procesar datos XML para la respuesta", e)
            // No incluir los datos XML en caso de error al procesarlos
        }

        if (accepted) {
            resultIntent.putExtra(ExtraKeys.TRANSACTION_RESULT, "ACCEPTED")
            // Simular datos de tarjeta para una transacción aceptada
            resultIntent.putExtra(ExtraKeys.AUTHORIZATION_ID, "AUTH_TEF_${System.currentTimeMillis() % 100000}")
            resultIntent.putExtra(ExtraKeys.CARD_HOLDER, "CLIENTE BANESCO")
            resultIntent.putExtra(ExtraKeys.CARD_TYPE, "VISA") // O MasterCard, etc.
            resultIntent.putExtra(ExtraKeys.CARD_NUM, "**** **** **** 1234") // Número ofuscado
            Log.i(TAG, "Enviando respuesta TRANSACTION: ACCEPTED")
        } else {
            resultIntent.putExtra(ExtraKeys.TRANSACTION_RESULT, "FAILED")
            resultIntent.putExtra(ExtraKeys.ERROR_MESSAGE, getString(R.string.error_payment_canceled))
            resultIntent.putExtra(ExtraKeys.ERROR_MESSAGE_TITLE, getString(R.string.error_payment_canceled_title))
            Log.i(TAG, "Enviando respuesta TRANSACTION: FAILED")
        }

        // Logging de respuesta
        logTransactionResponse(resultIntent)

        // IMPORTANTE: HioPosCloud espera RESULT_OK incluso si la transacción (TransactionResult) es FAILED.
        setResult(Activity.RESULT_OK, resultIntent)
        finish() // Finalizar la activity de UI para volver a HioPosCloud
    }

    /**
     * Registra en el log la respuesta que se enviará a HioPosCloud
     */
    private fun logTransactionResponse(resultIntent: Intent) {
        val responseLogTag = "RESPONSE_TO_HIOPOS" // Un tag específico para estos logs
        Log.i(responseLogTag, "--------------------------------------------------------------------------")
        Log.i(responseLogTag, "Enviando respuesta a HioPosCloud. Acción: ${resultIntent.action}")
        Log.i(responseLogTag, "--------------------------------------------------------------------------")
        resultIntent.extras?.let { bundle ->
            if (bundle.isEmpty) {
                Log.i(responseLogTag, "El Bundle de extras está vacío.")
            } else {
                Log.i(responseLogTag, "Contenido de los Extras del Intent de Salida:")
                for (key in bundle.keySet()) {
                    val value = bundle.get(key)
                    val valueString = value?.toString() ?: "null"

                    // Para XMLs o JSONs largos, podríamos querer un tratamiento especial
                    if (valueString.length > 300 && (key == ExtraKeys.SHOP_DATA || key == ExtraKeys.SELLER_DATA ||
                                                   key == ExtraKeys.DOCUMENT_DATA || key == ExtraKeys.TRANSACTION_DATA_MODULE)) {
                        Log.d(responseLogTag, "  Key: '$key' (XML/JSON largo, mostrando inicio y fin):")
                        Log.d(responseLogTag, "    ${valueString.take(150)} ... ${valueString.takeLast(150)}")
                    } else {
                        Log.d(responseLogTag, "  Key: '$key', Value: '$valueString'")
                    }
                }
            }
        } ?: Log.i(responseLogTag, "El Intent de salida no tiene extras (bundle es null).")
        Log.i(responseLogTag, "--------------------------------------------------------------------------")
    }
}