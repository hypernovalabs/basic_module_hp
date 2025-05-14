package com.tefbanesco

import android.app.Application
import com.tefbanesco.yappy.BuildConfig
import timber.log.Timber

/**
 * Clase Application para inicializar componentes globales
 */
class ModuleApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Inicializar Timber para logging
        initializeTimber()
    }
    
    /**
     * Inicializa Timber para logging detallado
     */
    private fun initializeTimber() {
        // Solo plantar árbol de debug en versiones de desarrollo
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                /**
                 * Personaliza el formato del tag para incluir un prefijo común
                 * y la clase/línea de código que generó el log
                 */
                override fun createStackElementTag(element: StackTraceElement): String {
                    // Format: YAPPY:[ClaseCorta]:LineaNumero
                    return String.format(
                        "YAPPY:[%s]:%s",
                        element.className.substringAfterLast('.'),
                        element.lineNumber
                    )
                }
            })
            Timber.i("Timber inicializado en modo DEBUG")
        } else {
            // En producción, podemos usar una versión que solo muestre errores críticos
            // o envíe informes de accidentes a un servicio
            Timber.plant(object : Timber.Tree() {
                override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                    // En producción, podríamos limitar el logging o redirigirlo a un servicio
                    // de informes de errores como Firebase Crashlytics
                    if (priority >= android.util.Log.ERROR) {
                        // Solo registrar errores graves en producción
                        android.util.Log.e("YAPPY_PROD", message, t)
                    }
                }
            })
            Timber.i("Timber inicializado en modo RELEASE (solo errores críticos)")
        }
    }
}