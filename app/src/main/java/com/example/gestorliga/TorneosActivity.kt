package com.example.gestorliga

import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent

class TorneosActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var rvTorneos: RecyclerView
    private lateinit var tvSinTorneos: TextView
    private val listaTorneos = mutableListOf<Torneo>()
    private lateinit var adapter: TorneoAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_torneos)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        rvTorneos = findViewById(R.id.rvTorneos)
        tvSinTorneos = findViewById(R.id.tvSinTorneos)
        val btnNuevoTorneo: Button = findViewById(R.id.btnNuevoTorneo)

        adapter = TorneoAdapter(listaTorneos) { torneo ->
            val intent = Intent(this, TorneoDetalleActivity::class.java)
            intent.putExtra("EXTRA_TORNEO_ID", torneo.id)
            intent.putExtra("EXTRA_TORNEO_NOMBRE", torneo.nombre)
            startActivity(intent)
        }
        rvTorneos.layoutManager = LinearLayoutManager(this)
        rvTorneos.adapter = adapter

        btnNuevoTorneo.setOnClickListener { mostrarDialogoNuevoTorneo() }
    }

    override fun onStart() {
        super.onStart()
        cargarTorneos()
    }

    private fun cargarTorneos() {
        val uid = auth.currentUser?.uid ?: return

        db.collection("torneos")
            .whereEqualTo("organizadorUid", uid)
            .get()
            .addOnSuccessListener { resultado ->
                listaTorneos.clear()
                for (doc in resultado) {
                    listaTorneos.add(doc.toObject(Torneo::class.java))
                }
                adapter.notifyDataSetChanged()
                tvSinTorneos.visibility = if (listaTorneos.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al cargar torneos: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }

    private fun mostrarDialogoNuevoTorneo() {
        val input = EditText(this)
        input.hint = "Nombre del torneo"
        input.inputType = InputType.TYPE_CLASS_TEXT

        AlertDialog.Builder(this)
            .setTitle("Nuevo torneo")
            .setView(input)
            .setPositiveButton("Crear") { _, _ ->
                val nombre = input.text.toString().trim()
                if (nombre.isEmpty()) {
                    Toast.makeText(this, "El nombre no puede estar vacío", Toast.LENGTH_SHORT).show()
                } else {
                    crearTorneo(nombre)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun crearTorneo(nombre: String) {
        val uid = auth.currentUser?.uid ?: return
        val nuevoTorneo = hashMapOf(
            "nombre" to nombre,
            "organizadorUid" to uid,
            "fechaCreacion" to System.currentTimeMillis(),
            "estado" to "armando"
        )

        db.collection("torneos")
            .add(nuevoTorneo)
            .addOnSuccessListener {
                Toast.makeText(this, "Torneo creado", Toast.LENGTH_SHORT).show()
                cargarTorneos()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, "Error al crear torneo: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
            }
    }
}