package com.example.gestorliga

import com.google.firebase.firestore.DocumentId

data class Torneo(
    @DocumentId val id: String = "",
    val nombre: String = "",
    val organizadorUid: String = "",
    val fechaCreacion: Long = System.currentTimeMillis(),
    val estado: String = "armando" // "armando" | "en_curso" | "finalizado"
)

/**
 * organizadorUid está DENORMALIZADO acá a propósito.
 *
 * El dato ya vive en el documento del torneo padre, así que repetirlo parece un
 * error de normalización. En una base relacional lo sería. En Firestore es la
 * optimización central del TP3: permite que las reglas de seguridad validen el
 * permiso comparando contra un campo del propio documento, en lugar de hacer un
 * get() al torneo. Ese get() cuesta una lectura facturable por operación y suma
 * latencia.
 *
 * El campo se escribe desde ahora, pero las reglas que lo usan se publican
 * después (patrón de escritura dual): así los documentos nuevos ya son
 * compatibles con las reglas viejas y con las nuevas.
 */
data class Equipo(
    @DocumentId val id: String = "",
    val nombre: String = "",
    val organizadorUid: String = ""
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
    val jugado: Boolean = false,
    val organizadorUid: String = ""
)