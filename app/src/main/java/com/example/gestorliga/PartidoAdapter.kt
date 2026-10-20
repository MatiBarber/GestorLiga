package com.example.gestorliga

import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.RecyclerView

class PartidoAdapter(
    private val partidos: List<Partido>,
    private val onGuardarResultado: (Partido, Int, Int) -> Unit
) : RecyclerView.Adapter<PartidoAdapter.PartidoViewHolder>() {

    /**
     * Lo que el usuario tiene tipeado y todavía no guardó, por ID de partido.
     *
     * RecyclerView recicla las vistas: cuando un partido sale de pantalla, su
     * ViewHolder se reutiliza y onBindViewHolder lo sobrescribe con los datos
     * del partido que entra. Sin este mapa, escribir un resultado y scrollear
     * borraba lo tipeado (y, peor, podía mostrarlo en la fila equivocada).
     */
    private val borradores = mutableMapOf<String, Pair<String, String>>()

    /** Se llama cuando el resultado ya quedó guardado en Firestore. */
    fun limpiarBorrador(partidoId: String) {
        borradores.remove(partidoId)
    }

    class PartidoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvFecha: TextView = view.findViewById(R.id.tvFechaHeader)
        val tvLocal: TextView = view.findViewById(R.id.tvLocal)
        val tvVisitante: TextView = view.findViewById(R.id.tvVisitante)
        val etGolesLocal: EditText = view.findViewById(R.id.etGolesLocal)
        val etGolesVisitante: EditText = view.findViewById(R.id.etGolesVisitante)
        val btnGuardar: Button = view.findViewById(R.id.btnGuardarResultado)

        var watcherLocal: TextWatcher? = null
        var watcherVisitante: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartidoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_partido, parent, false)
        return PartidoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PartidoViewHolder, position: Int) {
        val partido = partidos[position]

        // Encabezado "Fecha N": solo en el primer partido de cada fecha, para que
        // el fixture se vea agrupado por fecha como dice el enunciado.
        val fechaAnterior = if (position > 0) partidos[position - 1].fechaNumero else -1
        if (partido.fechaNumero != fechaAnterior) {
            holder.tvFecha.text = "Fecha ${partido.fechaNumero}"
            holder.tvFecha.visibility = View.VISIBLE
        } else {
            holder.tvFecha.visibility = View.GONE
        }

        holder.tvLocal.text = partido.equipoLocalNombre
        holder.tvVisitante.text = partido.equipoVisitanteNombre

        // Importante: sacamos los watchers ANTES de setText. Si no, el setText
        // dispararía el watcher del partido anterior y le pisaría el borrador.
        holder.watcherLocal?.let { holder.etGolesLocal.removeTextChangedListener(it) }
        holder.watcherVisitante?.let { holder.etGolesVisitante.removeTextChangedListener(it) }

        val borrador = borradores[partido.id]
        holder.etGolesLocal.setText(borrador?.first ?: partido.golesLocal?.toString() ?: "")
        holder.etGolesVisitante.setText(borrador?.second ?: partido.golesVisitante?.toString() ?: "")

        holder.watcherLocal = holder.etGolesLocal.doAfterTextChanged {
            borradores[partido.id] =
                holder.etGolesLocal.text.toString() to holder.etGolesVisitante.text.toString()
        }
        holder.watcherVisitante = holder.etGolesVisitante.doAfterTextChanged {
            borradores[partido.id] =
                holder.etGolesLocal.text.toString() to holder.etGolesVisitante.text.toString()
        }

        holder.btnGuardar.setOnClickListener {
            val gl = holder.etGolesLocal.text.toString().toIntOrNull()
            val gv = holder.etGolesVisitante.text.toString().toIntOrNull()
            if (gl == null || gv == null) {
                holder.btnGuardar.context.let {
                    android.widget.Toast
                        .makeText(it, "Cargá ambos resultados", android.widget.Toast.LENGTH_SHORT)
                        .show()
                }
                return@setOnClickListener
            }
            onGuardarResultado(partido, gl, gv)
        }
    }

    override fun getItemCount(): Int = partidos.size
}