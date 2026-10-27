package com.example.gestorliga

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * TP7 - Receptor que publica el aviso cuando vence la alarma.
 *
 * El sistema lo despierta aunque la aplicacion este cerrada. Se declara en el
 * manifiesto con exported="false" porque solo lo invoca la propia aplicacion.
 */
class RecordatorioReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val titulo = intent.getStringExtra(RecordatorioPartido.EXTRA_TITULO) ?: "GestorLiga"
        val texto = intent.getStringExtra(RecordatorioPartido.EXTRA_TEXTO) ?: ""
        val id = intent.getIntExtra(RecordatorioPartido.EXTRA_ID, 0)

        // El canal puede no existir si el proceso fue recreado desde cero.
        RecordatorioPartido.crearCanal(context)

        // Al tocar el aviso se abre la aplicacion.
        val abrir = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendienteAbrir = PendingIntent.getActivity(
            context, id, abrir, PendingIntent.FLAG_IMMUTABLE
        )

        val aviso = NotificationCompat.Builder(context, RecordatorioPartido.CANAL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendienteAbrir)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(id, aviso)
        } catch (e: SecurityException) {
            // Android 13 o posterior sin el permiso POST_NOTIFICATIONS concedido.
        }
    }
}