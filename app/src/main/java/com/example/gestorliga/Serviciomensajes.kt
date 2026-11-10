package com.example.gestorliga

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * TP6 - Receptor de las notificaciones push.
 *
 * Android entrega los mensajes en dos caminos distintos segun el estado de
 * la aplicacion, y conviene tenerlo presente:
 *
 *   - Con la app en segundo plano, el SDK publica el aviso por su cuenta en
 *     el canal declarado en el manifiesto. Este servicio no se ejecuta.
 *   - Con la app abierta, el SDK no publica nada y llama a onMessageReceived.
 *     Si esta clase no existiera, un aviso recibido con la app en pantalla
 *     se perderia sin dejar rastro.
 *
 * Por eso el servicio no es opcional aunque la consola funcione sin el.
 */
class ServicioMensajes : FirebaseMessagingService() {

    companion object {
        private const val TAG = "GestorLigaPush"
        private const val ID_AVISO = 2001
    }

    override fun onMessageReceived(mensaje: RemoteMessage) {
        val titulo = mensaje.notification?.title
            ?: mensaje.data["title"]
            ?: "GestorLiga"
        val texto = mensaje.notification?.body
            ?: mensaje.data["body"]
            ?: return

        Log.d(TAG, "Mensaje recibido en primer plano: $titulo / $texto")
        publicar(titulo, texto)
    }

    /**
     * El token cambia cuando se reinstala la app, se borran sus datos o se
     * restaura el telefono. Una aplicacion con servidor propio tendria que
     * reenviarlo en ese momento; aca solo se registra, porque los envios
     * salen de la consola.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Token nuevo: $token")
    }

    private fun publicar(titulo: String, texto: String) {
        val intent = Intent(this, TorneosActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendiente = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val aviso = NotificationCompat.Builder(this, AvisosPush.CANAL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setContentIntent(pendiente)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        getSystemService(NotificationManager::class.java)
            ?.notify(ID_AVISO, aviso)
    }
}