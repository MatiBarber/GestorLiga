package com.example.gestorliga

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * TP7 - Integracion con Firebase Remote Config.
 *
 * QUE PROBLEMA RESUELVE
 * ---------------------
 * El TP4 define un modelo freemium con un tope de torneos en el plan gratuito.
 * Ese numero no puede estar escrito en el codigo: cambiarlo obligaria a
 * publicar una version nueva y a esperar a que cada usuario la instale, con lo
 * cual una decision comercial quedaria atada a un ciclo de release.
 *
 * Remote Config lo convierte en un parametro que se ajusta desde la consola de
 * Firebase y que la aplicacion recoge sola en el siguiente arranque.
 *
 * COMO FUNCIONA
 * -------------
 * El SDK mantiene tres capas de valores, en orden de prioridad:
 *   1. Los valores traidos del servidor y activados.
 *   2. Los valores por defecto declarados en la aplicacion.
 *   3. Cero o vacio, si no hay ninguno de los anteriores.
 *
 * Declarar defaults es obligatorio en la practica: garantizan que la primera
 * ejecucion, o una sin conexion, tenga un valor coherente.
 */
object ConfiguracionRemota {

    const val CLAVE_MAX_TORNEOS = "max_torneos_gratis"

    /** Valor usado mientras no haya respuesta del servidor. */
    private const val MAX_TORNEOS_POR_DEFECTO = 3L

    private val remoto: FirebaseRemoteConfig by lazy {
        FirebaseRemoteConfig.getInstance().apply {
            // Intervalo minimo entre consultas al servidor.
            //
            // En produccion corresponde un valor de varias horas, para no
            // castigar la bateria ni los datos del usuario. Durante el
            // desarrollo se usa cero para poder cambiar el parametro en la
            // consola y verificarlo en la app de inmediato.
            val ajustes = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0)
                .build()
            setConfigSettingsAsync(ajustes)

            setDefaultsAsync(
                mapOf<String, Any>(CLAVE_MAX_TORNEOS to MAX_TORNEOS_POR_DEFECTO)
            )
        }
    }

    /**
     * Trae los valores del servidor y los activa.
     *
     * [alTerminar] se invoca siempre, haya exito o no: si la consulta falla,
     * siguen vigentes los defaults y la aplicacion debe continuar igual.
     */
    fun actualizar(alTerminar: (exito: Boolean) -> Unit) {
        remoto.fetchAndActivate()
            .addOnCompleteListener { tarea -> alTerminar(tarea.isSuccessful) }
    }

    /** Cantidad maxima de torneos que admite el plan gratuito. */
    fun maxTorneosGratis(): Int = remoto.getLong(CLAVE_MAX_TORNEOS).toInt()
}