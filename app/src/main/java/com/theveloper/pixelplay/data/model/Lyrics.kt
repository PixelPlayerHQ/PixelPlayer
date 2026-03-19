package com.theveloper.pixelplay.data.model

import kotlinx.serialization.Serializable

/**
 * Modelo de datos para las letras de las canciones.
 *
 * @param plain Lista de líneas de texto plano (no sincronizadas).
 * @param synced Lista de pares (milisegundos, línea) para letras sincronizadas.
 * @param areFromRemote Indica si las letras se obtuvieron de una fuente remota.
 */
@Serializable
data class Lyrics(
    val plain: List<String>? = null,
    val synced: List<SyncedLine>? = null,
    val areFromRemote: Boolean = false
)

@Serializable
data class SyncedLine(
    val time: Int,
    val line: String,
    val words: List<SyncedWord>? = null, // Null if not a word-by-word synced lyric
    val translation: String? = null // Translation text paired by identical timestamp
)

@Serializable
data class SyncedWord(val time: Int, val word: String)