package com.javierlahoz.lectur.data

/**
 * Un libro de la biblioteca. El PDF se copia dentro del almacenamiento privado
 * de la app, asi que el libro sigue disponible aunque se mueva o borre el
 * archivo original.
 */
data class Book(
    val id: String,
    val title: String,
    val pageCount: Int,
    val lastPage: Int,
    val addedAt: Long,
    val lastOpenedAt: Long
) {
    /** Progreso entre 0f y 1f, contando la pagina actual como leida. */
    val progress: Float
        get() = if (pageCount <= 0) 0f else ((lastPage + 1).toFloat() / pageCount).coerceIn(0f, 1f)

    val percent: Int
        get() = Math.round(progress * 100f)

    val isFinished: Boolean
        get() = pageCount > 0 && lastPage >= pageCount - 1

    val isStarted: Boolean
        get() = lastPage > 0
}
