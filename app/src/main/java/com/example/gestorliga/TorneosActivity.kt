package com.example.gestorliga

import android.content.Intent
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
import com.google.firebase.firestore.Query

class TorneosActivity : AppCompatActivity() {

    companion object {
        /** Cuántos torneos se traen por página. */
        const val TAMANO_PAGINA = 20L
    }

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
            intent.putExtra(TorneoDetalleActivity.EXTRA_TORNEO_ID, torneo.id)
            intent.putExtra(TorneoDetalleActivity.EXTRA_TORNEO_NOMBRE, torneo.nombre)
            startActivity(intent)
        }
        rvTorneos.layoutManager = LinearLayoutManager(this)
        rvTorneos.adapter = adapter

        btnNuevoTorneo.setOnClickListener {
            if (superaElPlanGratuito()) mostrarLimiteAlcanzado()
            else mostrarDialogoNuevoTorneo()
        }

        // Menú oculto de instrumentación: pulsación larga sobre "+ Nuevo Torneo".
        // Va acá para no tocar el layout ni dejar botones de prueba a la vista.
        // Se quita junto con el resto del código de medición.
        btnNuevoTorneo.setOnLongClickListener {
            mostrarMenuMediciones()
            true
        }
    }

    override fun onStart() {
        super.onStart()

        // TP7 - Se consulta Remote Config antes de listar, de modo que el
        // tope del plan gratuito este actualizado cuando el organizador
        // intente crear un torneo. Si la consulta falla siguen vigentes los
        // valores por defecto y la pantalla funciona igual.
        ConfiguracionRemota.actualizar { cargarTorneos() }
    }

    private fun cargarTorneos() {
        val uid = auth.currentUser?.uid ?: return

        // Antes esta consulta traía TODOS los torneos del organizador en cada
        // entrada a la pantalla: con 200 torneos, 200 documentos cada vez.
        // Ahora trae una página de TAMANO_PAGINA, ordenada por fecha de creación
        // descendente (los más nuevos primero), que es lo que el organizador
        // quiere ver. El capítulo 16 lo señala entre las buenas prácticas:
        // paginar para cargar los datos en fragmentos pequeños.
        db.collection("torneos")
            .whereEqualTo("organizadorUid", uid)
            .orderBy("fechaCreacion", Query.Direction.DESCENDING)
            .limit(TAMANO_PAGINA)
            .get()
            .addOnSuccessListener { resultado ->
                ContadorLecturas.registrar("TorneosActivity.cargarTorneos", resultado.size())

                listaTorneos.clear()
                for (doc in resultado) {
                    listaTorneos.add(doc.toObject(Torneo::class.java))
                }
                adapter.notifyDataSetChanged()
                tvSinTorneos.visibility =
                    if (listaTorneos.isEmpty()) android.view.View.VISIBLE
                    else android.view.View.GONE

                // Si la página vino llena es probable que haya más torneos.
                // El "cargar más" completo queda como trabajo futuro; lo que
                // importa acá es que la consulta ya no crece sin techo.
                if (listaTorneos.size.toLong() == TAMANO_PAGINA) {
                    Toast.makeText(
                        this,
                        "Mostrando los $TAMANO_PAGINA torneos más recientes",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Error al cargar torneos: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
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
                Toast.makeText(
                    this,
                    "Error al crear torneo: ${error.localizedMessage}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    // ------------------------------------------------------------------------
    // TP7 - Tope del plan gratuito, gobernado por Remote Config
    // ------------------------------------------------------------------------

    private fun superaElPlanGratuito(): Boolean =
        listaTorneos.size >= ConfiguracionRemota.maxTorneosGratis()

    /**
     * Mensaje que se muestra al alcanzar el tope.
     *
     * El numero sale de Remote Config, no del codigo: cambiarlo en la consola
     * de Firebase modifica este texto sin publicar una version nueva.
     */
    private fun mostrarLimiteAlcanzado() {
        val tope = ConfiguracionRemota.maxTorneosGratis()
        AlertDialog.Builder(this)
            .setTitle("Limite del plan gratuito")
            .setMessage(
                "El plan gratuito permite hasta $tope torneo(s) y ya tenes " +
                        "${listaTorneos.size}.\n\n" +
                        "El plan Organizador quita el limite y los anuncios.\n" +
                        "El plan Club agrega ida y vuelta, edicion de torneos y " +
                        "varias categorias."
            )
            .setPositiveButton("Entendido", null)
            .show()
    }

    // ------------------------------------------------------------------------
    // Instrumentación (TP3). Todo lo que sigue se elimina después de medir.
    // ------------------------------------------------------------------------

    private fun mostrarMenuMediciones() {
        val opciones = arrayOf(
            "Sembrar torneo de prueba",
            "Ver resumen de lecturas",
            "Reiniciar contador"
        )

        AlertDialog.Builder(this)
            .setTitle("Mediciones TP3")
            .setItems(opciones) { _, cual ->
                when (cual) {
                    0 -> mostrarDialogoSembrar()
                    1 -> mostrarResumenLecturas()
                    2 -> {
                        ContadorLecturas.reiniciar()
                        Toast.makeText(this, "Contador reiniciado", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun mostrarDialogoSembrar() {
        val input = EditText(this)
        input.hint = "Cantidad de equipos (ej. 20)"
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.setText("20")

        AlertDialog.Builder(this)
            .setTitle("Sembrar torneo de prueba")
            .setMessage("N equipos generan N*(N-1)/2 partidos.\n20 equipos = 190 partidos.")
            .setView(input)
            .setPositiveButton("Sembrar") { _, _ ->
                val cantidad = input.text.toString().toIntOrNull()
                if (cantidad == null || cantidad < 2) {
                    Toast.makeText(this, "Cantidad inválida", Toast.LENGTH_SHORT).show()
                } else {
                    sembrar(cantidad)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun sembrar(cantidadEquipos: Int) {
        val uid = auth.currentUser?.uid ?: return

        val progreso = AlertDialog.Builder(this)
            .setTitle("Sembrando…")
            .setMessage("Preparando")
            .setCancelable(false)
            .create()
        progreso.show()

        SembradorDatos.sembrarTorneo(
            db = db,
            uid = uid,
            nombreTorneo = "Carga $cantidadEquipos equipos",
            cantidadEquipos = cantidadEquipos,
            onProgreso = { texto -> progreso.setMessage(texto) },
            onListo = { equipos, partidos ->
                progreso.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("Listo")
                    .setMessage("$equipos equipos y $partidos partidos escritos.")
                    .setPositiveButton("OK", null)
                    .show()
                cargarTorneos()
            },
            onError = { error ->
                progreso.dismiss()
                AlertDialog.Builder(this)
                    .setTitle("Falló la siembra")
                    .setMessage(error.localizedMessage ?: error.toString())
                    .setPositiveButton("OK", null)
                    .show()
            }
        )
    }

    private fun mostrarResumenLecturas() {
        ContadorLecturas.volcarResumen()
        AlertDialog.Builder(this)
            .setTitle("Lecturas de Firestore")
            .setMessage(ContadorLecturas.resumen())
            .setPositiveButton("OK", null)
            .show()
    }
}