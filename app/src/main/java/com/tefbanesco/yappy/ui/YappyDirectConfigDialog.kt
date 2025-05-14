package com.tefbanesco.yappy.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.textfield.TextInputEditText
import com.tefbanesco.R
import com.tefbanesco.yappy.YappyServiceFactory

/**
 * Diálogo para configurar las credenciales de Yappy directamente en la aplicación.
 * Permite al usuario ingresar API Key, Secret Key, Device ID y Group ID.
 */
class YappyDirectConfigDialog(
    context: Context,
    private val onConfigSaved: () -> Unit,
    private val onCancel: () -> Unit
) : Dialog(context) {
    
    private lateinit var etApiKey: TextInputEditText
    private lateinit var etSecretKey: TextInputEditText
    private lateinit var etDeviceId: TextInputEditText
    private lateinit var etGroupId: TextInputEditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Inflar la vista
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_yappy_direct_config, null)
        setContentView(view)
        
        // Inicializar vistas
        etApiKey = view.findViewById(R.id.etYappyApiKey)
        etSecretKey = view.findViewById(R.id.etYappySecretKey)
        etDeviceId = view.findViewById(R.id.etYappyDeviceId)
        etGroupId = view.findViewById(R.id.etYappyGroupId)
        btnSave = view.findViewById(R.id.btnSaveYappyConfig)
        btnCancel = view.findViewById(R.id.btnCancelYappyConfig)
        
        // Configurar botones
        btnSave.setOnClickListener { saveConfiguration() }
        btnCancel.setOnClickListener { 
            dismiss()
            onCancel()
        }
        
        // Obtener credenciales actuales si existen
        loadCurrentCredentials()
    }
    
    /**
     * Carga las credenciales actuales si están disponibles
     */
    private fun loadCurrentCredentials() {
        val credentialsProvider = YappyServiceFactory.getCredentialsProvider(context)
        
        // Si hay credenciales personalizadas, las cargamos
        credentialsProvider.getApiKey()?.let { etApiKey.setText(it) }
        credentialsProvider.getSecretKey()?.let { etSecretKey.setText(it) }
        credentialsProvider.getDeviceId()?.let { etDeviceId.setText(it) }
        credentialsProvider.getGroupId()?.let { etGroupId.setText(it) }
    }
    
    /**
     * Valida y guarda la configuración
     */
    private fun saveConfiguration() {
        // Obtener valores
        val apiKey = etApiKey.text.toString().trim()
        val secretKey = etSecretKey.text.toString().trim()
        val deviceId = etDeviceId.text.toString().trim()
        val groupId = etGroupId.text.toString().trim()
        
        // Validar valores
        if (apiKey.isEmpty() || secretKey.isEmpty() || deviceId.isEmpty() || groupId.isEmpty()) {
            Toast.makeText(context, "Todos los campos son obligatorios", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Guardar configuración
        YappyServiceFactory.getWithCustomCredentials(
            context = context,
            apiKey = apiKey,
            secretKey = secretKey,
            deviceId = deviceId,
            groupId = groupId
        )
        
        // Mostrar mensaje de éxito
        Toast.makeText(context, "Configuración guardada exitosamente", Toast.LENGTH_SHORT).show()
        
        // Cerrar diálogo y notificar
        dismiss()
        onConfigSaved()
    }
    
    companion object {
        /**
         * Muestra el diálogo de configuración de Yappy
         */
        fun show(
            context: Context,
            onConfigSaved: () -> Unit = {},
            onCancel: () -> Unit = {}
        ) {
            YappyDirectConfigDialog(context, onConfigSaved, onCancel).show()
        }
        
        /**
         * Muestra un diálogo de confirmación para elegir entre usar
         * configuración almacenada o configuración personalizada
         */
        fun showConfigurationChoice(
            context: Context,
            onStoredCredentials: () -> Unit,
            onCustomCredentials: () -> Unit
        ) {
            AlertDialog.Builder(context)
                .setTitle("Configuración Yappy")
                .setMessage("¿Qué tipo de configuración desea usar para Yappy?")
                .setPositiveButton("Configuración Almacenada") { dialog, _ ->
                    dialog.dismiss()
                    // Usar credenciales almacenadas
                    YappyServiceFactory.getWithStoredCredentials(context)
                    onStoredCredentials()
                }
                .setNegativeButton("Configuración Personalizada") { dialog, _ ->
                    dialog.dismiss()
                    // Mostrar diálogo para credenciales personalizadas
                    show(context, onCustomCredentials)
                }
                .setCancelable(false)
                .show()
        }
    }
}