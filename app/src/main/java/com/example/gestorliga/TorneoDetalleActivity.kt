package com.example.gestorliga

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

class TorneoDetalleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TORNEO_ID = "EXTRA_TORNEO_ID"
        const val EXTRA_TORNEO_NOMBRE = "EXTRA_TORNEO_NOMBRE"
        const val EXTRA_TORNEO_ESTADO = "EXTRA_TORNEO_ESTADO"
    }

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var torneoId: String
    private var nombreTorneo: String = ""

    private lateinit var rvEquipos: RecyclerView
    private lateinit var btnSortearFixture: Button
    private lateinit var btnVerFixture: Button
    private lateinit var btnAgregarEquipo: Button
    private lateinit var etNombreEquipo: EditText

    private val listaEquipos = mutableListOf<Equipo>()
    private lateinit var adapter: EquipoAdapter

    // --- Estado de la pantalla -------------------------------------------------
    // Las dos consultas (equipos y torneo) son asincrónicas y vuelven en orden
    // impredecible. Guardamos el resultado de cada una y recién decidimos qué
    // mostrar cuando TENEMOS LAS DOS. Antes, cada callback tocaba la visibilidad
    // de los botones por su cuenta y el resultado dependía de quién contestaba
    // primero (condición de carrera).
    private var estadoTorneo: String? = null
    private var equiposCargados = false
    private var sorteoEnCurso = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_torneo_detalle)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        torneoId = intent.getStringExtra(EXTRA_TORNEO_ID) ?: run {
            Toast.makeText(this, "Torneo no encontrado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        nombreTorneo = intent.getStringExtra(EXTRA_TORNEO_NOMBRE) ?: ""

        findViewById<TextView>(R.id.tvNombreTorneoDetalle).text = nombreTorneo

        rvEquipos = findViewById(R.id.rvEquipos)
        btnSortearFixture = findViewById(R.id.btnSortearFixture)
        btnVerFixture = findViewById(R.id.btnVerFixture)
        etNombreEquipo = findViewById(R.id.etNombreEquipo)
        btnAgregarEquipo = findViewById(R.id.btnAgregarEquipo)

        adapter = EquipoAdapter(listaEquipos)
        rvEquipos.layoutManager = LinearLayoutManager(this)
        rvEquipos.adapter = adapter

        btnAgregarEquipo.setOnClickListener {
            val nombre = etNombreEquipo.text.toString().trim()
            when {
                nombre.isEmpty() ->
                    Toast.makeText(this, "Ingresá el nombre del equipo", Toast.LENGTH_SHORT).show()
                // La tabla de posiciones se agrupa por ID, así que dos equipos con el
                // mismo nombre ya no se fusionan. Igual los rechazamos: un fixture con
                // dos "River" es imposible de leer para el organizador.
                listaEquipos.any { it.nombre.equals(nombre, ignoreCase = true) } ->
                    Toast.makeText(this, "Ya existe un equipo con ese nombre", Toast.LENGTH_SHORT).show()
                else -> {
                    agregarEquipo(nombre)
                    etNombreEquipo.text.clear()
                }
            }
        }

        btnSortearFixture.setOnClickListener { sortearFixture() }

        btnVerFixture.setOnClickListener {
            val intent = Intent(this, FixtureActivity::class.java)
            intent.putExtra(FixtureActivity.EXTRA_TORNEO_ID, torneoId)
            intent.putExtra(FixtureActivity.EXTRA_TORNEO_NOMBRE, nombreTorneo)
            // Le pasamos el estado para que FixtureActivity no tenga que volver a
            // leerlo de Firestore: una lectura menos por cada vez que se abre.
            intent.putExtra(FixtureActivity.EXTRA_TORNEO_ESTADO, estadoTorneo ?: "en_curso")
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        // Si onCreate llamó a finish() (torneo inválido), los campos lateinit no
        // están inicializados y seguir acá sería un crash seguro.
        if (isFinishing) return

        equiposCargados = false
        estadoTorneo = null
        cargarEquipos()
        cargarEstadoTorneo()
    }

    /**
     * Único lugar donde se decide la visibilidad de los botones.
     * No hace nada hasta tener la respuesta de las DOS consultas.
     */
    private fun actualizarBotones() {
        val estado = estadoTorneo
        if (!equiposCargados || estado == null) return

        val yaSorteado = estado != "armando"

        btnSortearFixture.visibility =
            if (!yaSorteado && listaEquipos.size >= 2) View.VISIBLE else View.GONE
        btnVerFixture.visibility =
            if (yaSorteado) View.VISIBLE else View.GONE

        // Una vez sorteado el fixture, agregar equipos lo dejaría inconsistente.
        etNombreEquipo.isEnabled = !yaSorteado
        btnAgregarEquipo.isEnabled = !yaSorteado
    }

    private fun cargarEquipos() {
        db.collection("torneos").document(torneoId).collection("equipos")
            .orderBy("nombre")   // sin orderBy el orden de los equipos era indefinido
            .get()
            .addOnSuccessListener { resultado ->
                ContadorLecturas.registrar(
                    "TorneoDetalleActivity.cargarEquipos", resultado.size()
                )

                listaEquipos.clear()
                for (doc in resultado) {
                    listaEquipos.add(doc.toObject(Equipo::class.java))
                }
                adapter.notifyDataSetChanged()
                equiposCargados = true
                actualizarBotones()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Error al cargar equipos: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun cargarEstadoTorneo() {
        db.collection("torneos").document(torneoId)
            .get()
            .addOnSuccessListener { doc ->
                // Una lectura de un solo documento, pero se paga igual que cualquier otra.
                ContadorLecturas.registrar("TorneoDetalleActivity.cargarEstadoTorneo", 1)

                estadoTorneo = doc.getString("estado") ?: "armando"
                actualizarBotones()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Error al leer el torneo: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun agregarEquipo(nombre: String) {
        val uid = auth.currentUser?.uid ?: return
        // organizadorUid denormalizado: lo escribimos desde ahora para que las
        // reglas optimizadas puedan validar sin hacer get() al torneo.
        val nuevoEquipo = hashMapOf(
            "nombre" to nombre,
            "organizadorUid" to uid
        )

        db.collection("torneos").document(torneoId).collection("equipos")
            .add(nuevoEquipo)
            .addOnSuccessListener { cargarEquipos() }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Error al agregar equipo: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    /**
     * Sortea el fixture dentro de una TRANSACCIÓN.
     *
     * Tres defensas contra el fixture duplicado, de la más débil a la más fuerte:
     *   1. sorteoEnCurso  -> evita el doble toque rápido sobre el botón.
     *   2. actualizarBotones() -> el botón no aparece si el torneo no está "armando".
     *   3. la transacción -> relee el estado en el servidor y aborta si ya no es
     *      "armando". Es la única que protege de verdad: las otras dos son UI.
     */
    private fun sortearFixture() {
        if (sorteoEnCurso) return
        if (listaEquipos.size < 2) {
            Toast.makeText(this, "Necesitás al menos 2 equipos", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: return

        sorteoEnCurso = true
        btnSortearFixture.isEnabled = false

        val partidos = GeneradorFixture.generar(listaEquipos)
            .map { it.copy(organizadorUid = uid) }
        val torneoRef = db.collection("torneos").document(torneoId)
        val coleccionPartidos = torneoRef.collection("partidos")

        // Las referencias se generan FUERA de la transacción: si Firestore la
        // reintenta, se reutilizan los mismos IDs y no se duplican documentos.
        val refs: List<DocumentReference> = partidos.map { coleccionPartidos.document() }

        db.runTransaction { tx ->
            val snap = tx.get(torneoRef)
            if (snap.getString("estado") != "armando") {
                throw FirebaseFirestoreException(
                    "El fixture de este torneo ya fue sorteado.",
                    FirebaseFirestoreException.Code.ABORTED
                )
            }
            for (i in partidos.indices) {
                tx.set(refs[i], partidos[i])
            }
            tx.update(torneoRef, "estado", "en_curso")
            partidos.size
        }
            .addOnSuccessListener { cantidad ->
                sorteoEnCurso = false
                btnSortearFixture.isEnabled = true
                estadoTorneo = "en_curso"
                Toast.makeText(this, "Fixture sorteado ($cantidad partidos)", Toast.LENGTH_LONG).show()
                actualizarBotones()
            }
            .addOnFailureListener { error ->
                sorteoEnCurso = false
                btnSortearFixture.isEnabled = true
                Toast.makeText(
                    this,
                    "No se pudo sortear el fixture: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
                // Releemos el estado real: quizá otro dispositivo ya lo sorteó.
                cargarEstadoTorneo()
            }
    }
}