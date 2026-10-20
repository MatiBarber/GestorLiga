package com.example.gestorliga

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.random.Random

/**
 * Genera datos de prueba para las mediciones de carga del TP3.
 *
 * El capítulo 16 cuenta el caso del backend que aseguraba soportar 100.000
 * usuarios simultáneos, nunca hizo pruebas de carga reales y colapsó a los
 * 40.000 en la primera transmisión en vivo. La lección del capítulo es explícita:
 * hay que hacer pruebas de carga intensivas para encontrar los cuellos de botella
 * antes que los usuarios. Esto es esa prueba, a la escala de este TP.
 *
 * Es código de instrumentación: se usa para medir y después se saca.
 */
object SembradorDatos {

    /** Un WriteBatch admite hasta 500 operaciones. Dejamos margen. */
    private const val TAMANO_TANDA = 400

    /**
     * Crea un torneo completo: equipos, fixture sorteado y una parte de los
     * resultados ya cargados.
     *
     * @param cantidadEquipos  N equipos => N*(N-1)/2 partidos
     * @param porcentajeJugados qué proporción de partidos viene con resultado
     */
    fun sembrarTorneo(
        db: FirebaseFirestore,
        uid: String,
        nombreTorneo: String,
        cantidadEquipos: Int,
        porcentajeJugados: Int = 70,
        onProgreso: (String) -> Unit,
        onListo: (equipos: Int, partidos: Int) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (cantidadEquipos < 2) {
            onError(IllegalArgumentException("Hacen falta al menos 2 equipos"))
            return
        }

        onProgreso("Creando torneo…")

        val datosTorneo = hashMapOf(
            "nombre" to nombreTorneo,
            "organizadorUid" to uid,
            "fechaCreacion" to System.currentTimeMillis(),
            "estado" to "armando"
        )

        db.collection("torneos")
            .add(datosTorneo)
            .addOnSuccessListener { torneoRef ->
                val coleccionEquipos = torneoRef.collection("equipos")

                // Pre-generamos las referencias para conocer los IDs ANTES de
                // escribir: el fixture necesita los IDs de los equipos.
                val refsEquipos = (0 until cantidadEquipos).map { coleccionEquipos.document() }
                val equipos = refsEquipos.mapIndexed { i, ref ->
                    Equipo(
                        id = ref.id,
                        nombre = nombreEquipo(i),
                        organizadorUid = uid
                    )
                }

                onProgreso("Escribiendo $cantidadEquipos equipos…")

                val batchEquipos = db.batch()
                for (i in equipos.indices) {
                    batchEquipos.set(refsEquipos[i], equipos[i])
                }

                batchEquipos.commit()
                    .addOnSuccessListener {
                        // El mismo generador que usa la app: los datos de prueba
                        // salen del código real, no de un atajo.
                        val partidos = GeneradorFixture.generar(equipos)
                            .map { partido ->
                                val jugado = Random.nextInt(100) < porcentajeJugados
                                partido.copy(
                                    organizadorUid = uid,
                                    jugado = jugado,
                                    golesLocal = if (jugado) Random.nextInt(0, 5) else null,
                                    golesVisitante = if (jugado) Random.nextInt(0, 5) else null
                                )
                            }

                        onProgreso("Escribiendo ${partidos.size} partidos…")

                        escribirPartidosEnTandas(
                            db = db,
                            coleccion = torneoRef.collection("partidos"),
                            partidos = partidos,
                            desde = 0,
                            onProgreso = { hechos, total ->
                                onProgreso("Partidos: $hechos / $total")
                            },
                            onListo = {
                                db.collection("torneos").document(torneoRef.id)
                                    .update("estado", "en_curso")
                                    .addOnSuccessListener {
                                        onListo(equipos.size, partidos.size)
                                    }
                                    .addOnFailureListener(onError)
                            },
                            onError = onError
                        )
                    }
                    .addOnFailureListener(onError)
            }
            .addOnFailureListener(onError)
    }

    /**
     * Escribe los partidos en tandas de [TAMANO_TANDA].
     *
     * Con 20 equipos son 190 partidos y entrarían en un solo batch, pero con 40
     * equipos serían 780 y el batch fallaría por exceder las 500 operaciones.
     * Partir en tandas hace que el sembrador escale con N.
     */
    private fun escribirPartidosEnTandas(
        db: FirebaseFirestore,
        coleccion: CollectionReference,
        partidos: List<Partido>,
        desde: Int,
        onProgreso: (hechos: Int, total: Int) -> Unit,
        onListo: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (desde >= partidos.size) {
            onListo()
            return
        }

        val hasta = minOf(desde + TAMANO_TANDA, partidos.size)
        val batch = db.batch()
        for (i in desde until hasta) {
            batch.set(coleccion.document(), partidos[i])
        }

        batch.commit()
            .addOnSuccessListener {
                onProgreso(hasta, partidos.size)
                escribirPartidosEnTandas(
                    db, coleccion, partidos, hasta, onProgreso, onListo, onError
                )
            }
            .addOnFailureListener(onError)
    }

    /** Nombres determinísticos: "Equipo A", "Equipo B", … "Equipo AA". */
    private fun nombreEquipo(indice: Int): String {
        val letras = StringBuilder()
        var n = indice
        do {
            letras.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
        } while (n >= 0)
        return "Equipo $letras"
    }
}