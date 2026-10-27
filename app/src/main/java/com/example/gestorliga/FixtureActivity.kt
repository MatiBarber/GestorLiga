package com.example.gestorliga

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds

class FixtureActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TORNEO_ID = "EXTRA_TORNEO_ID"
        const val EXTRA_TORNEO_NOMBRE = "EXTRA_TORNEO_NOMBRE"
        const val EXTRA_TORNEO_ESTADO = "EXTRA_TORNEO_ESTADO"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var torneoId: String
    private lateinit var rvFixture: RecyclerView
    private lateinit var rvTabla: RecyclerView
    private lateinit var tvHeaderTabla: android.widget.LinearLayout
    private val listaPartidos = mutableListOf<Partido>()
    private lateinit var partidoAdapter: PartidoAdapter
    private lateinit var adViewFixture: AdView
    private lateinit var tvClima: TextView
    private var nombreTorneo: String = ""

    // Llega por Intent desde TorneoDetalleActivity: evita una lectura extra.
    private var estadoTorneo: String = "en_curso"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fixture)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        torneoId = intent.getStringExtra(EXTRA_TORNEO_ID) ?: run {
            finish(); return
        }
        nombreTorneo = intent.getStringExtra(EXTRA_TORNEO_NOMBRE) ?: ""
        estadoTorneo = intent.getStringExtra(EXTRA_TORNEO_ESTADO) ?: "en_curso"
        findViewById<TextView>(R.id.tvNombreTorneoFixture).text = nombreTorneo

        rvFixture = findViewById(R.id.rvFixture)
        rvTabla = findViewById(R.id.rvTabla)
        tvHeaderTabla = findViewById(R.id.tvHeaderTabla)
        val btnMostrarFixture: Button = findViewById(R.id.btnMostrarFixture)
        val btnMostrarTabla: Button = findViewById(R.id.btnMostrarTabla)

        partidoAdapter = PartidoAdapter(listaPartidos) { partido, golesLocal, golesVisitante ->
            guardarResultado(partido, golesLocal, golesVisitante)
        }
        rvFixture.layoutManager = LinearLayoutManager(this)
        rvFixture.adapter = partidoAdapter
        rvTabla.layoutManager = LinearLayoutManager(this)

        btnMostrarFixture.setOnClickListener { mostrarFixture() }
        btnMostrarTabla.setOnClickListener { mostrarTabla() }

        tvClima = findViewById(R.id.tvClima)

        // TP7 - Canal de notificaciones y boton de recordatorio
        RecordatorioPartido.crearCanal(this)
        val btnRecordar = findViewById<Button>(R.id.btnRecordar)
        btnRecordar.setOnClickListener { programarRecordatorio(pruebaInmediata = false) }
        btnRecordar.setOnLongClickListener {
            programarRecordatorio(pruebaInmediata = true)
            true
        }

        // TP4 - SDK de Google Mobile Ads. La inicializacion ocurre una sola vez
        // y debe completarse antes de solicitar cualquier anuncio.
        adViewFixture = findViewById(R.id.adViewFixture)
        MobileAds.initialize(this) {
            adViewFixture.loadAd(AdRequest.Builder().build())
        }
    }

    override fun onDestroy() {
        if (this::adViewFixture.isInitialized) adViewFixture.destroy()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        if (isFinishing) return
        cargarPartidos()
    }

    private fun cargarPartidos() {
        val uid = auth.currentUser?.uid ?: return

        // Filtrar por organizadorUid Y ordenar por fechaNumero son campos
        // distintos, así que esta consulta necesita un ÍNDICE COMPUESTO.
        // Está declarado en firestore.indexes.json, en la raíz del repo.
        db.collection("torneos").document(torneoId).collection("partidos")
            .whereEqualTo("organizadorUid", uid)
            .orderBy("fechaNumero")
            .get()
            .addOnSuccessListener { resultado ->
                // Acá se ve el costo cuadrático: con N equipos son N*(N-1)/2
                // documentos, y se releen enteros en cada entrada a la pantalla.
                ContadorLecturas.registrar(
                    "FixtureActivity.cargarPartidos", resultado.size()
                )

                listaPartidos.clear()
                for (doc in resultado) {
                    listaPartidos.add(doc.toObject(Partido::class.java))
                }
                partidoAdapter.notifyDataSetChanged()

                // Si la tabla está visible, la recalculamos con los datos nuevos.
                if (rvTabla.visibility == View.VISIBLE) {
                    rvTabla.adapter = TablaPosicionesAdapter(calcularTabla())
                }
                actualizarEstadoSegunProgreso()
                cargarPronostico()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Error al cargar fixture: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    /**
     * Cierra el ciclo de vida del torneo: cuando todos los partidos están
     * jugados, el estado pasa a "finalizado".
     *
     * Hasta ahora "finalizado" existía solo como comentario en Modelos.kt:
     * nadie lo escribía nunca y un torneo terminado quedaba "en_curso" para
     * siempre. Solo escribimos si el estado REALMENTE cambia, para no gastar
     * una escritura cada vez que se abre la pantalla.
     */
    private fun actualizarEstadoSegunProgreso() {
        if (listaPartidos.isEmpty()) return

        val nuevoEstado = if (listaPartidos.all { it.jugado }) "finalizado" else "en_curso"
        if (nuevoEstado == estadoTorneo) return

        db.collection("torneos").document(torneoId)
            .update("estado", nuevoEstado)
            .addOnSuccessListener {
                estadoTorneo = nuevoEstado
                if (nuevoEstado == "finalizado") {
                    Toast.makeText(this, "¡Torneo finalizado!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun guardarResultado(partido: Partido, golesLocal: Int, golesVisitante: Int) {
        db.collection("torneos").document(torneoId).collection("partidos").document(partido.id)
            .update(
                mapOf(
                    "golesLocal" to golesLocal,
                    "golesVisitante" to golesVisitante,
                    "jugado" to true
                )
            )
            .addOnSuccessListener {
                // El borrador ya está persistido: lo descartamos para que el
                // adapter vuelva a leer del modelo.
                partidoAdapter.limpiarBorrador(partido.id)
                Toast.makeText(this, "Resultado guardado", Toast.LENGTH_SHORT).show()
                cargarPartidos()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Error al guardar resultado: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ------------------------------------------------------------------
    // TP7 - Consumo de la API REST de pronostico (Open-Meteo)
    // ------------------------------------------------------------------

    /**
     * Muestra el pronostico de la proxima fecha sin jugar.
     *
     * El modelo guarda numero de fecha y no fecha de calendario, asi que se
     * aplica la misma convencion que el recordatorio: la fecha N se juega N
     * dias despues de hoy.
     *
     * Se usa un hilo propio y runOnUiThread en lugar de corrutinas para no
     * sumar dependencias: lo que el trabajo muestra es el consumo de la API,
     * no la biblioteca que lo envuelve.
     */
    private fun cargarPronostico() {
        val proxima = listaPartidos.firstOrNull { !it.jugado }
        if (proxima == null || proxima.fechaNumero > ClimaApi.DIAS_MAXIMOS - 1) {
            tvClima.visibility = View.GONE
            return
        }

        val diasAdelante = proxima.fechaNumero
        tvClima.visibility = View.VISIBLE
        tvClima.text = "Consultando el pronostico..."

        Thread {
            val pronosticos = try {
                ClimaApi.consultarDias(diasAdelante + 1)
            } catch (e: Exception) {
                emptyList()
            }
            val delDia = pronosticos.getOrNull(diasAdelante)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                tvClima.text = if (delDia == null) {
                    "Pronostico no disponible"
                } else {
                    val temperatura =
                        if (delDia.temperaturaMaxima.isNaN()) "s/d"
                        else "${delDia.temperaturaMaxima.toInt()} grados"
                    val lluvia =
                        if (delDia.probabilidadLluvia < 0) "s/d"
                        else "${delDia.probabilidadLluvia}% de lluvia"
                    "Fecha ${proxima.fechaNumero} (${delDia.fechaIso}) \u00b7 $temperatura \u00b7 $lluvia"
                }
            }
        }.start()
    }

    // ------------------------------------------------------------------
    // TP7 - Integracion con las APIs de notificaciones y alarmas
    // ------------------------------------------------------------------

    /**
     * Solicitud del permiso de notificaciones, necesario desde Android 13.
     * En el dispositivo de prueba (Android 11) se concede al instalar y este
     * contrato nunca llega a mostrarse.
     */
    private val permisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (!concedido) {
                Toast.makeText(
                    this,
                    "Sin permiso de notificaciones no se puede avisar",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private fun hayPermisoDeNotificaciones(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val concedido = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!concedido) permisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
        return concedido
    }

    /**
     * Programa un aviso local para el dia de la proxima fecha sin jugar.
     *
     * [pruebaInmediata] dispara el mismo aviso diez segundos despues. Existe
     * para verificar la integracion sin esperar al dia del partido.
     */
    private fun programarRecordatorio(pruebaInmediata: Boolean) {
        val proxima = listaPartidos.firstOrNull { !it.jugado }
        if (proxima == null) {
            Toast.makeText(this, "No quedan fechas por jugar", Toast.LENGTH_SHORT).show()
            return
        }
        if (!hayPermisoDeNotificaciones()) return

        val cuantos = listaPartidos.count { !it.jugado && it.fechaNumero == proxima.fechaNumero }
        val texto = "Hoy se juega la fecha ${proxima.fechaNumero}: $cuantos partido(s)"

        val cuando =
            if (pruebaInmediata) System.currentTimeMillis() + 10_000
            else RecordatorioPartido.momentoDeLaFecha(proxima.fechaNumero)

        RecordatorioPartido.programar(
            context = this,
            idRecordatorio = torneoId.hashCode() and 0x7FFFFFFF,
            titulo = nombreTorneo,
            texto = texto,
            cuandoMillis = cuando
        )

        Toast.makeText(
            this,
            if (pruebaInmediata) "Aviso de prueba en 10 segundos"
            else "Te aviso el dia de la fecha ${proxima.fechaNumero}",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun mostrarFixture() {
        rvFixture.visibility = View.VISIBLE
        rvTabla.visibility = View.GONE
        tvHeaderTabla.visibility = View.GONE
    }

    private fun mostrarTabla() {
        rvFixture.visibility = View.GONE
        rvTabla.visibility = View.VISIBLE
        tvHeaderTabla.visibility = View.VISIBLE
        rvTabla.adapter = TablaPosicionesAdapter(calcularTabla())
    }

    /**
     * Calcula la tabla de posiciones a partir de los partidos jugados (3-1-0).
     *
     * Se agrupa por ID de equipo, NO por nombre. Agrupar por nombre fusionaba en
     * una sola fila a dos equipos homónimos y partía el historial de un equipo
     * renombrado: el nombre es un dato mutable y no único, y por lo tanto no
     * sirve como clave.
     */
    private fun calcularTabla(): List<FilaTabla> {
        data class Acumulado(
            val nombre: String,
            var puntos: Int = 0,
            var jugados: Int = 0,
            var golesFavor: Int = 0,
            var golesContra: Int = 0
        )

        // Fallback al nombre para partidos viejos guardados sin ID de equipo.
        fun clave(id: String, nombre: String) = id.ifEmpty { nombre }

        val tabla = linkedMapOf<String, Acumulado>()

        // 1) Registramos TODOS los equipos del fixture, hayan jugado o no, para
        //    que aparezcan en la tabla con 0 puntos desde el principio.
        for (partido in listaPartidos) {
            tabla.getOrPut(clave(partido.equipoLocalId, partido.equipoLocalNombre)) {
                Acumulado(partido.equipoLocalNombre)
            }
            tabla.getOrPut(clave(partido.equipoVisitanteId, partido.equipoVisitanteNombre)) {
                Acumulado(partido.equipoVisitanteNombre)
            }
        }

        // 2) Acumulamos únicamente los partidos jugados.
        for (partido in listaPartidos) {
            if (!partido.jugado) continue
            val gl = partido.golesLocal ?: continue
            val gv = partido.golesVisitante ?: continue

            val local = tabla[clave(partido.equipoLocalId, partido.equipoLocalNombre)] ?: continue
            val visitante = tabla[clave(partido.equipoVisitanteId, partido.equipoVisitanteNombre)] ?: continue

            local.jugados++; visitante.jugados++
            local.golesFavor += gl; local.golesContra += gv
            visitante.golesFavor += gv; visitante.golesContra += gl

            when {
                gl > gv -> local.puntos += 3
                gl < gv -> visitante.puntos += 3
                else -> { local.puntos += 1; visitante.puntos += 1 }
            }
        }

        // Desempate completo: puntos, diferencia de gol, goles a favor y nombre.
        // El último criterio garantiza un orden estable (antes, dos equipos
        // empatados en todo podían intercambiar posiciones entre pantallas).
        return tabla.values
            .map { datos ->
                FilaTabla(
                    nombreEquipo = datos.nombre,
                    puntos = datos.puntos,
                    jugados = datos.jugados,
                    diferenciaGol = datos.golesFavor - datos.golesContra,
                    golesFavor = datos.golesFavor
                )
            }
            .sortedWith(
                compareByDescending<FilaTabla> { it.puntos }
                    .thenByDescending { it.diferenciaGol }
                    .thenByDescending { it.golesFavor }
                    .thenBy { it.nombreEquipo.lowercase() }
            )
    }
}