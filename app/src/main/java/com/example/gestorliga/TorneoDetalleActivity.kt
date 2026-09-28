package com.example.gestorliga

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore

class TorneoDetalleActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var torneoId: String
    private lateinit var rvEquipos: RecyclerView
    private lateinit var btnSortearFixture: Button
    private lateinit var btnVerFixture: Button
    private val listaEquipos = mutableListOf<Equipo>()
    private lateinit var adapter: EquipoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_torneo_detalle)

        db = FirebaseFirestore.getInstance()
        torneoId = intent.getStringExtra("EXTRA_TORNEO_ID") ?: run {
            Toast.makeText(this, "Torneo no encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val nombreTorneo = intent.getStringExtra("EXTRA_TORNEO_NOMBRE") ?: ""

        findViewById<TextView>(R.id.tvNombreTorneoDetalle).text = nombreTorneo

        rvEquipos = findViewById(R.id.rvEquipos)
        btnSortearFixture = findViewById(R.id.btnSortearFixture)
        btnVerFixture = findViewById(R.id.btnVerFixture)
        val etNombreEquipo: EditText = findViewById(R.id.etNombreEquipo)
        val btnAgregarEquipo: Button = findViewById(R.id.btnAgregarEquipo)

        adapter = EquipoAdapter(listaEquipos)
        rvEquipos.layoutManager = LinearLayoutManager(this)
        rvEquipos.adapter = adapter

        btnAgregarEquipo.setOnClickListener {
            val nombre = etNombreEquipo.text.toString().trim()
            if (nombre.isEmpty()) {
                Toast.makeText(this, "Ingresá el nombre del equipo", Toast.LENGTH_SHORT).show()
            } else {
                agregarEquipo(nombre)
                etNombreEquipo.text.clear()
            }
        }

        btnSortearFixture.setOnClickListener { sortearFixture() }

        btnVerFixture.setOnClickListener {
            val intent = Intent(this, FixtureActivity::class.java)
            intent.putExtra("EXTRA_TORNEO_ID", torneoId)
            intent.putExtra("EXTRA_TORNEO_NOMBRE", nombreTorneo)
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        cargarEquipos()
        cargarEstadoTorneo()
    }

    private fun cargarEquipos() {
        db.collection("torneos").document(torneoId).collection("equipos")
            .get()
            .addOnSuccessListener { resultado ->
                listaEquipos.clear()
                for (doc in resultado) {
                    listaEquipos.add(doc.toObject(Equipo::class.java))
                }
                adapter.notifyDataSetChanged()
                btnSortearFixture.visibility =
                    if (listaEquipos.size >= 2) android.view.View.VISIBLE else android.view.View.GONE
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al cargar equipos: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun cargarEstadoTorneo() {
        db.collection("torneos").document(torneoId)
            .get()
            .addOnSuccessListener { doc ->
                val torneo = doc.toObject(Torneo::class.java)
                if (torneo?.estado == "en_curso") {
                    btnSortearFixture.visibility = android.view.View.GONE
                    btnVerFixture.visibility = android.view.View.VISIBLE
                }
            }
    }

    private fun agregarEquipo(nombre: String) {
        val nuevoEquipo = hashMapOf("nombre" to nombre)

        db.collection("torneos").document(torneoId).collection("equipos")
            .add(nuevoEquipo)
            .addOnSuccessListener { cargarEquipos() }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al agregar equipo: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun sortearFixture() {
        if (listaEquipos.size < 2) {
            Toast.makeText(this, "Necesitás al menos 2 equipos", Toast.LENGTH_SHORT).show()
            return
        }

        val partidos = GeneradorFixture.generar(listaEquipos)
        val batch = db.batch()
        val coleccionPartidos = db.collection("torneos").document(torneoId).collection("partidos")

        for (partido in partidos) {
            val nuevoDoc = coleccionPartidos.document()
            batch.set(nuevoDoc, partido)
        }

        val torneoRef = db.collection("torneos").document(torneoId)
        batch.update(torneoRef, "estado", "en_curso")

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Fixture sorteado (${partidos.size} partidos)", Toast.LENGTH_LONG).show()
                btnSortearFixture.visibility = android.view.View.GONE
                btnVerFixture.visibility = android.view.View.VISIBLE
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al sortear fixture: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }
}