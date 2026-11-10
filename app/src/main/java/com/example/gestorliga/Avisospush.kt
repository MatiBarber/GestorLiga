package com.example.gestorliga

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging

/**
 * TP6 - Canal e identificacion del dispositivo para notificaciones push.
 *
 * El canal es distinto del que usan los recordatorios de partido del TP7.
 * Esa separacion es deliberada: el capitulo 15 advierte sobre el spam, y
 * tener dos canales le permite al usuario silenciar los avisos generales
 * sin perder el recordatorio del dia del partido. Un unico canal obligaria
 * a aceptar todo o nada.
 */
object AvisosPush {

    /** Debe coincidir con default_notification_channel_id del manifiesto. */
    const val CANAL_ID = "avisos_push"

    fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ID,
                "Novedades del torneo",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Avisos enviados por el organizador de GestorLiga"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(canal)
        }
    }

    /**
     * Devuelve el token de registro de este dispositivo.
     *
     * Es el identificador que la consola de Firebase pide para enviar un
     * mensaje de prueba. Google lo marco como obsoleto en favor del
     * Firebase Installation ID, pero la consola todavia pide este, de modo
     * que es el que se usa aca.
     */
    fun token(alObtenerlo: (String?) -> Unit) {
        FirebaseMessaging.getInstance().token
            .addOnCompleteListener { tarea ->
                alObtenerlo(if (tarea.isSuccessful) tarea.result else null)
            }
    }
}