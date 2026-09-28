package com.example.gestorliga

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TorneoAdapter(
    private val torneos: List<Torneo>,
    private val onClick: (Torneo) -> Unit
) : RecyclerView.Adapter<TorneoAdapter.TorneoViewHolder>() {

    class TorneoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreTorneo)
        val tvEstado: TextView = view.findViewById(R.id.tvEstadoTorneo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TorneoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_torneo, parent, false)
        return TorneoViewHolder(view)
    }

    override fun onBindViewHolder(holder: TorneoViewHolder, position: Int) {
        val torneo = torneos[position]
        holder.tvNombre.text = torneo.nombre
        holder.tvEstado.text = "Estado: ${torneo.estado}"
        holder.itemView.setOnClickListener { onClick(torneo) }
    }

    override fun getItemCount(): Int = torneos.size
}