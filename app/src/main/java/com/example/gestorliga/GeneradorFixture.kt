package com.example.gestorliga

object GeneradorFixture {

    /**
     * Genera el fixture "todos contra todos" (solo ida).
     * Con N equipos (par) hay N-1 fechas; con N impar hay N fechas y
     * en cada una un equipo descansa.
     */
    fun generar(equipos: List<Equipo>): List<Partido> {
        if (equipos.size < 2) return emptyList()

        // El sorteo: mezclamos el orden de los equipos al azar
        val lista: MutableList<Equipo?> = equipos.shuffled().toMutableList()

        // Si son impares, agregamos un "fantasma" (null) que representa la fecha libre
        if (lista.size % 2 != 0) lista.add(null)

        val n = lista.size
        val cantidadFechas = n - 1
        val partidos = mutableListOf<Partido>()

        for (fecha in 0 until cantidadFechas) {
            for (i in 0 until n / 2) {
                val a = lista[i]
                val b = lista[n - 1 - i]

                // Si alguno es el fantasma, ese equipo descansa: no se crea partido
                if (a != null && b != null) {
                    // Alternamos localía para repartir mejor los locales
                    val (local, visitante) = if ((fecha + i) % 2 == 0) a to b else b to a

                    partidos.add(
                        Partido(
                            fechaNumero = fecha + 1,
                            equipoLocalId = local.id,
                            equipoLocalNombre = local.nombre,
                            equipoVisitanteId = visitante.id,
                            equipoVisitanteNombre = visitante.nombre
                        )
                    )
                }
            }

            // Rotación: el primero queda fijo y el último pasa a la posición 1
            val ultimo = lista.removeAt(n - 1)
            lista.add(1, ultimo)
        }

        return partidos
    }
}