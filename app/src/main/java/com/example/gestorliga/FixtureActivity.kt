package com.example.gestorliga

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class FixtureActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var torneoId: String
    private lateinit var rvFixture: RecyclerView
    private lateinit var rvTabla: RecyclerView
    private lateinit var tvHeaderTabla: android.widget.LinearLayout
    private val listaPartidos = mutableListOf<Partido>()
    private lateinit var partidoAdapter: PartidoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fixture)

        db = FirebaseFirestore.getInstance()
        torneoId = intent.getStringExtra("EXTRA_TORNEO_ID") ?: run {
            finish(); return
        }
        val nombreTorneo = intent.getStringExtra("EXTRA_TORNEO_NOMBRE") ?: ""
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
    }

    override fun onStart() {
        super.onStart()
        cargarPartidos()
    }

    private fun cargarPartidos() {
        db.collection("torneos").document(torneoId).collection("partidos")
            .orderBy("fechaNumero")
            .get()
            .addOnSuccessListener { resultado ->
                listaPartidos.clear()
                for (doc in resultado) {
                    listaPartidos.add(doc.toObject(Partido::class.java))
                }
                partidoAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al cargar fixture: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
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
                Toast.makeText(this, "Resultado guardado", Toast.LENGTH_SHORT).show()
                cargarPartidos()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al guardar resultado: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
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

    /** Calcula la tabla de posiciones a partir de los partidos jugados (sistema 3-1-0). */
    private fun calcularTabla(): List<FilaTabla> {
        data class Acumulado(
            var puntos: Int = 0,
            var jugados: Int = 0,
            var golesFavor: Int = 0,
            var golesContra: Int = 0
        )

        val tabla = mutableMapOf<String, Acumulado>()

        for (partido in listaPartidos) {
            if (!partido.jugado) continue
            val gl = partido.golesLocal ?: continue
            val gv = partido.golesVisitante ?: continue

            val local = tabla.getOrPut(partido.equipoLocalNombre) { Acumulado() }
            val visitante = tabla.getOrPut(partido.equipoVisitanteNombre) { Acumulado() }

            local.jugados++; visitante.jugados++
            local.golesFavor += gl; local.golesContra += gv
            visitante.golesFavor += gv; visitante.golesContra += gl

            when {
                gl > gv -> local.puntos += 3
                gl < gv -> visitante.puntos += 3
                else -> { local.puntos += 1; visitante.puntos += 1 }
            }
        }

        // Aseguramos que TODOS los equipos aparezcan en la tabla, jugaron o no
        for (partido in listaPartidos) {
            tabla.getOrPut(partido.equipoLocalNombre) { Acumulado() }
            tabla.getOrPut(partido.equipoVisitanteNombre) { Acumulado() }
        }

        return tabla.map { (nombre, datos) ->
            FilaTabla(
                nombreEquipo = nombre,
                puntos = datos.puntos,
                jugados = datos.jugados,
                diferenciaGol = datos.golesFavor - datos.golesContra
            )
        }.sortedWith(compareByDescending<FilaTabla> { it.puntos }.thenByDescending { it.diferenciaGol })
    }
}