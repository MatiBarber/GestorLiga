package com.example.gestorliga

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * TP7 - Consumo directo de una API REST externa.
 *
 * Open-Meteo (open-meteo.com) publica pronósticos meteorológicos sin exigir
 * clave de API ni registro para uso no comercial. Se consulta por HTTPS y
 * responde JSON.
 *
 * POR QUÉ SIN SDK
 * ---------------
 * El capítulo 10 define el SDK como la librería que se agrega a la aplicación
 * para facilitar el uso de una API en una plataforma concreta. Acá se consume
 * la API SIN su capa de conveniencia: con HttpURLConnection, que viene en
 * Android, y org.json, que también. Cero dependencias nuevas.
 *
 * El costo de esa decisión es visible en este archivo: hay que construir la
 * URL a mano, manejar el código de respuesta, leer el flujo, interpretar el
 * JSON campo por campo y cerrar la conexión. Eso es exactamente lo que un SDK
 * como Retrofit habría resuelto. Tenerlo a la vista sirve para dimensionar qué
 * aporta un SDK y cuándo conviene incorporarlo.
 */
object ClimaApi {

    private const val BASE = "https://api.open-meteo.com/v1/forecast"

    /**
     * Sede del torneo. Se usa una constante en lugar de la ubicación del
     * dispositivo para no pedir permisos de geolocalización, que la
     * funcionalidad no necesita. Una versión futura permitiría al organizador
     * fijar la sede al crear el torneo.
     */
    private const val LAT_SEDE = -31.4257   // San Francisco, Córdoba
    private const val LON_SEDE = -62.0836

    /** La API entrega como máximo 16 días de pronóstico. */
    const val DIAS_MAXIMOS = 16

    data class Pronostico(
        val fechaIso: String,
        val temperaturaMaxima: Double,
        val probabilidadLluvia: Int
    )

    /**
     * Devuelve el pronóstico diario de los próximos [dias] días.
     * Ante cualquier falla devuelve una lista vacía: el pronóstico es
     * información complementaria y su ausencia no debe romper la pantalla.
     *
     * Bloquea el hilo donde se ejecuta, así que debe llamarse fuera del
     * hilo principal.
     */
    fun consultarDias(dias: Int): List<Pronostico> {
        val cantidad = dias.coerceIn(1, DIAS_MAXIMOS)

        val url = URL(
            "$BASE?latitude=$LAT_SEDE&longitude=$LON_SEDE" +
                    "&daily=temperature_2m_max,precipitation_probability_max" +
                    "&timezone=auto&forecast_days=$cantidad"
        )

        val conexion = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
        }

        try {
            if (conexion.responseCode != HttpURLConnection.HTTP_OK) return emptyList()

            val cuerpo = conexion.inputStream.bufferedReader().use { it.readText() }

            // Lectura defensiva: si la API cambia la forma de la respuesta,
            // se devuelve vacío en lugar de lanzar una excepción.
            val diario = JSONObject(cuerpo).optJSONObject("daily") ?: return emptyList()
            val fechas = diario.optJSONArray("time") ?: return emptyList()
            val temperaturas = diario.optJSONArray("temperature_2m_max")
            val lluvias = diario.optJSONArray("precipitation_probability_max")

            val resultado = mutableListOf<Pronostico>()
            for (i in 0 until fechas.length()) {
                resultado.add(
                    Pronostico(
                        fechaIso = fechas.optString(i, ""),
                        temperaturaMaxima = temperaturas?.optDouble(i, Double.NaN) ?: Double.NaN,
                        probabilidadLluvia = lluvias?.optInt(i, -1) ?: -1
                    )
                )
            }
            return resultado
        } finally {
            conexion.disconnect()
        }
    }
}