package com.example.gestorliga

import com.google.firebase.firestore.DocumentId

data class Torneo(
    @DocumentId val id: String = "",
    val nombre: String = "",
    val organizadorUid: String = "",
    val fechaCreacion: Long = System.currentTimeMillis(),
    val estado: String = "armando" // "armando" | "en_curso" | "finalizado"
)

data class Equipo(
    @DocumentId val id: String = "",
    val nombre: String = ""
)

data class Partido(
    @DocumentId val id: String = "",
    val fechaNumero: Int = 0,
    val equipoLocalId: String = "",
    val equipoLocalNombre: String = "",
    val equipoVisitanteId: String = "",
    val equipoVisitanteNombre: String = "",
    val golesLocal: Int? = null,
    val golesVisitante: Int? = null,
    val jugado: Boolean = false
)