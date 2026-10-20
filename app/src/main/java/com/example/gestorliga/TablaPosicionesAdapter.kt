package com.example.gestorliga

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class FilaTabla(
    val nombreEquipo: String,
    val puntos: Int,
    val jugados: Int,
    val diferenciaGol: Int,
    // Se usa como tercer criterio de desempate en FixtureActivity.calcularTabla().
    // No se muestra en pantalla todavía, pero hace falta para que el orden sea
    // determinístico.
    val golesFavor: Int = 0
)

class TablaPosicionesAdapter(private val filas: List<FilaTabla>) :
    RecyclerView.Adapter<TablaPosicionesAdapter.FilaViewHolder>() {

    class FilaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvPosicion: TextView = view.findViewById(R.id.tvPosicion)
        val tvNombre: TextView = view.findViewById(R.id.tvNombreEquipoTabla)
        val tvPuntos: TextView = view.findViewById(R.id.tvPuntos)
        val tvPJ: TextView = view.findViewById(R.id.tvPJ)
        val tvDG: TextView = view.findViewById(R.id.tvDG)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FilaViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tabla_fila, parent, false)
        return FilaViewHolder(view)
    }

    override fun onBindViewHolder(holder: FilaViewHolder, position: Int) {
        val fila = filas[position]
        holder.tvPosicion.text = (position + 1).toString()
        holder.tvNombre.text = fila.nombreEquipo
        holder.tvPuntos.text = fila.puntos.toString()
        holder.tvPJ.text = fila.jugados.toString()
        holder.tvDG.text = fila.diferenciaGol.toString()
    }

    override fun getItemCount(): Int = filas.size
}