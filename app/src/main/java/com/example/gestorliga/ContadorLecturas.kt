package com.example.gestorliga

import android.util.Log

/**
 * Contador de operaciones de Firestore del lado del cliente.
 *
 * ¿Para qué sirve? Para medir, no para suponer. Cada consulta de la app registra
 * acá cuántos documentos trajo. Al final comparamos este número con el total que
 * informa la pestaña "Uso" de la consola de Firebase.
 *
 * La diferencia entre los dos números es el dato interesante del TP3:
 *
 *   - Este contador mide los documentos que LEE LA APP.
 *   - La consola mide TODAS las lecturas facturadas, incluidas las que generan
 *     las reglas de seguridad al hacer get() al documento del torneo. Esas son
 *     invisibles desde el cliente: el teléfono nunca las ve.
 *
 * Si la consola informa más lecturas que este contador, la diferencia son las
 * reglas. Eso responde empíricamente algo que la documentación de Firestore no
 * aclara: si un get() repetido al mismo documento se factura una vez o N veces.
 *
 * Es código de instrumentación: se usa para medir y después se saca.
 */
object ContadorLecturas {

    const val TAG = "GestorLigaMetricas"

    private val lecturasPorOrigen = linkedMapOf<String, Int>()
    private val consultasPorOrigen = linkedMapOf<String, Int>()
    private var totalLecturas = 0
    private var totalConsultas = 0

    /**
     * @param origen    de dónde salió la consulta, p. ej. "TorneosActivity.cargarTorneos"
     * @param documentos cuántos documentos devolvió
     */
    fun registrar(origen: String, documentos: Int) {
        lecturasPorOrigen[origen] = (lecturasPorOrigen[origen] ?: 0) + documentos
        consultasPorOrigen[origen] = (consultasPorOrigen[origen] ?: 0) + 1
        totalLecturas += documentos
        totalConsultas++
        Log.d(TAG, "lectura | $origen | +$documentos doc | acumulado = $totalLecturas")
    }

    fun reiniciar() {
        lecturasPorOrigen.clear()
        consultasPorOrigen.clear()
        totalLecturas = 0
        totalConsultas = 0
        Log.i(TAG, "===== contador reiniciado =====")
    }

    fun resumen(): String {
        if (lecturasPorOrigen.isEmpty()) return "Sin lecturas registradas todavía."

        val sb = StringBuilder()
        sb.appendLine("===== LECTURAS DE FIRESTORE (cliente) =====")
        for ((origen, docs) in lecturasPorOrigen) {
            val consultas = consultasPorOrigen[origen] ?: 0
            val promedio = if (consultas > 0) docs.toDouble() / consultas else 0.0
            sb.appendLine("$origen")
            sb.appendLine("    $consultas consulta(s) | $docs documento(s) | ${"%.1f".format(promedio)} doc/consulta")
        }
        sb.appendLine("-------------------------------------------")
        sb.appendLine("TOTAL: $totalConsultas consultas | $totalLecturas documentos")
        sb.appendLine("Comparar contra la pestaña Uso de la consola.")
        return sb.toString()
    }

    /** Vuelca el resumen a Logcat. Filtrar por el tag GestorLigaMetricas. */
    fun volcarResumen() {
        Log.i(TAG, "\n" + resumen())
    }
}