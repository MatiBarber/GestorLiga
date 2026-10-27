package com.example.gestorliga

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * TP7 - Integracion con las APIs de notificaciones y de alarmas de Android.
 *
 * Programa un aviso local para el dia en que se juega la proxima fecha del
 * torneo. Son dos APIs de plataforma trabajando juntas:
 *
 *   - AlarmManager despierta a la aplicacion en un momento futuro, aunque
 *     este cerrada.
 *   - NotificationManager publica el aviso en la bandeja del sistema.
 *
 * No hay servidor ni dependencia: el aviso se arma y se entrega dentro del
 * propio dispositivo. Esto lo distingue de una notificacion push, que si
 * necesitaria un servicio remoto.
 */
object RecordatorioPartido {

    const val CANAL_ID = "recordatorios_partidos"
    const val EXTRA_TITULO = "EXTRA_TITULO"
    const val EXTRA_TEXTO = "EXTRA_TEXTO"
    const val EXTRA_ID = "EXTRA_ID"

    /** Hora del dia en que se entrega el recordatorio. */
    private const val HORA_AVISO = 10

    /**
     * Los canales son obligatorios desde Android 8. Agrupan avisos del mismo
     * tipo para que el usuario pueda silenciarlos por separado.
     */
    fun crearCanal(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ID,
                "Recordatorios de partidos",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Aviso del dia en que se juega cada fecha del torneo"
            }
            context.getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(canal)
        }
    }

    /**
     * Programa el aviso en [cuandoMillis].
     *
     * Se usa AlarmManager.set() y no setExact(): una alarma inexacta le deja
     * al sistema agrupar despertares y ahorrar bateria, y ademas evita el
     * permiso SCHEDULE_EXACT_ALARM que Android 12 exige para las exactas.
     * Para un aviso matutino, unos minutos de desvio son irrelevantes.
     */
    fun programar(
        context: Context,
        idRecordatorio: Int,
        titulo: String,
        texto: String,
        cuandoMillis: Long
    ) {
        val intent = Intent(context, RecordatorioReceiver::class.java).apply {
            putExtra(EXTRA_TITULO, titulo)
            putExtra(EXTRA_TEXTO, texto)
            putExtra(EXTRA_ID, idRecordatorio)
        }

        // FLAG_IMMUTABLE es obligatorio desde Android 12 para los PendingIntent
        // que no necesitan ser modificados por quien los recibe.
        val pendiente = PendingIntent.getBroadcast(
            context,
            idRecordatorio,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmas = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmas.set(AlarmManager.RTC_WAKEUP, cuandoMillis, pendiente)
    }

    /**
     * Momento en que debe sonar el aviso de la fecha [fechaNumero].
     *
     * El modelo de datos guarda el numero de fecha, no una fecha de
     * calendario, asi que se aplica la misma convencion que el agendado de
     * partidos y el pronostico: la fecha N se juega N dias despues de hoy.
     */
    fun momentoDeLaFecha(fechaNumero: Int): Long {
        return Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, fechaNumero)
            set(Calendar.HOUR_OF_DAY, HORA_AVISO)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}