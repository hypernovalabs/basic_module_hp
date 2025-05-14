package com.tefbanesco.yappy.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.tefbanesco.R
import com.tefbanesco.yappy.YappyConfigManager
import com.tefbanesco.yappy.YappyServiceFactory
import kotlinx.coroutines.launch

/**
 * Clase que gestiona la interfaz de configuración de Yappy
 * Muestra un diálogo con campos para ingresar usuario, contraseña y URL del servidor
 */
class YappyConfigScreen(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val configManager: YappyConfigManager,
    private val onSaveSuccess: () -> Unit,
    private val onCancel: () -> Unit
) {

    private lateinit var dialog: AlertDialog
    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfigServer: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvMessage: TextView
    
    // Para guardar referencia al usuario ingresado
    private var enteredUsername: String = ""

    /**
     * Muestra la pantalla de configuración como un diálogo
     */
    fun show() {
        // Crear vista del diálogo
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_yappy_config, null)
        
        // Inicializar componentes
        etUsername = dialogView.findViewById(R.id.etUsername)
        etPassword = dialogView.findViewById(R.id.etPassword)
        etConfigServer = dialogView.findViewById(R.id.etConfigServer)
        btnSave = dialogView.findViewById(R.id.btnSave)
        btnCancel = dialogView.findViewById(R.id.btnCancel)
        progressBar = dialogView.findViewById(R.id.progressBar)
        tvMessage = dialogView.findViewById(R.id.tvMessage)
        
        // Configurar valores por defecto
        etConfigServer.setText("http://192.168.0.154:5000/get-config")
        
        // Configurar botones
        btnSave.setOnClickListener { saveConfig() }
        btnCancel.setOnClickListener { 
            dialog.dismiss()
            onCancel()
        }
        
        // Crear y mostrar diálogo
        dialog = AlertDialog.Builder(context)
            .setTitle("Configuración Yappy")
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        dialog.show()
    }
    
    /**
     * Guarda la configuración llamando al servidor
     */
    private fun saveConfig() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val serverUrl = etConfigServer.text.toString().trim()
        
        // Guardar el username ingresado para comparaciones posteriores
        enteredUsername = username
        
        // Validar campos
        if (username.isEmpty() || password.isEmpty() || serverUrl.isEmpty()) {
            tvMessage.text = "Por favor complete todos los campos"
            tvMessage.visibility = View.VISIBLE
            return
        }
        
        // Validar URL - Para HTTP, solo se permite la IP 192.168.0.154
        if (serverUrl.startsWith("http://") && !serverUrl.contains("192.168.0.154")) {
            tvMessage.text = "Por motivos de seguridad, solo se permite conexión HTTP a 192.168.0.154.\n" +
                            "Para otros servidores, debe usar HTTPS."
            tvMessage.visibility = View.VISIBLE
            return
        }
        
        // Mostrar progreso
        progressBar.visibility = View.VISIBLE
        btnSave.isEnabled = false
        btnCancel.isEnabled = false
        tvMessage.visibility = View.GONE
        
        // Definir el callback para manejar el resultado
        val callback = object : YappyConfigManager.ConfigResultCallback {
            override fun onConfigProcessed(success: Boolean, username: String, errorMessage: String?) {
                // Ejecutar en hilo principal
                (context as? android.app.Activity)?.runOnUiThread {
                    // Actualizar UI
                    progressBar.visibility = View.GONE
                    btnSave.isEnabled = true
                    btnCancel.isEnabled = true
                    
                    if (success) {
                        val userMsg = if (username != enteredUsername)
                            "Configuración para usuario '$username' guardada correctamente"
                        else
                            "Configuración guardada correctamente"
                        
                        Toast.makeText(context, userMsg, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        onSaveSuccess()
                    } else {
                        // Mensaje de error detallado
                        val errorDetail = errorMessage ?: "Error desconocido al obtener configuración"
                        val userMsg = if (username != enteredUsername)
                            "Se está usando la configuración para usuario '$username'\n\n$errorDetail"
                        else
                            errorDetail
                            
                        tvMessage.text = userMsg
                        tvMessage.visibility = View.VISIBLE
                        
                        // Mostrar en toast para mejor visibilidad
                        Toast.makeText(context,
                            "Usando configuración de $username. Ver detalles.", 
                            Toast.LENGTH_LONG).show()
                        
                        // Log para depuración
                        android.util.Log.e("YappyConfigScreen", 
                            "Error al conectar a: $serverUrl. Usando config de usuario: $username")
                    }
                }
            }
        }
        
        // Llamar al servidor en coroutine
        lifecycleOwner.lifecycleScope.launch {
            configManager.fetchAndSaveYappyConfig(
                context = context,
                configServerUrl = serverUrl,
                username = username,
                password = password,
                callback = callback
            )
        }
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
            YappyConfigScreen(context, 
                (context as LifecycleOwner), 
                YappyConfigManager(), 
                onConfigSaved, 
                onCancel).show()
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