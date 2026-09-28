package com.example.gestorliga

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class EquipoAdapter(private val equipos: List<Equipo>) :
    RecyclerView.Adapter<EquipoAdapter.EquipoViewHolder>() {

    class EquipoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreEquipo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EquipoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_equipo, parent, false)
        return EquipoViewHolder(view)
    }

    override fun onBindViewHolder(holder: EquipoViewHolder, position: Int) {
        holder.tvNombre.text = equipos[position].nombre
    }

    override fun getItemCount(): Int = equipos.size
}