package com.example.gestorliga

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PartidoAdapter(
    private val partidos: List<Partido>,
    private val onGuardarResultado: (Partido, Int, Int) -> Unit
) : RecyclerView.Adapter<PartidoAdapter.PartidoViewHolder>() {

    class PartidoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvLocal: TextView = view.findViewById(R.id.tvLocal)
        val tvVisitante: TextView = view.findViewById(R.id.tvVisitante)
        val etGolesLocal: EditText = view.findViewById(R.id.etGolesLocal)
        val etGolesVisitante: EditText = view.findViewById(R.id.etGolesVisitante)
        val btnGuardar: Button = view.findViewById(R.id.btnGuardarResultado)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PartidoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_partido, parent, false)
        return PartidoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PartidoViewHolder, position: Int) {
        val partido = partidos[position]
        holder.tvLocal.text = partido.equipoLocalNombre
        holder.tvVisitante.text = partido.equipoVisitanteNombre
        holder.etGolesLocal.setText(partido.golesLocal?.toString() ?: "")
        holder.etGolesVisitante.setText(partido.golesVisitante?.toString() ?: "")

        holder.btnGuardar.setOnClickListener {
            val gl = holder.etGolesLocal.text.toString().toIntOrNull()
            val gv = holder.etGolesVisitante.text.toString().toIntOrNull()
            if (gl != null && gv != null) {
                onGuardarResultado(partido, gl, gv)
            }
        }
    }

    override fun getItemCount(): Int = partidos.size
}