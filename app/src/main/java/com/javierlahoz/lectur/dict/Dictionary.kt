package com.javierlahoz.lectur.dict

/** Una acepcion: el tipo de palabra y sus definiciones. */
data class DictionaryEntry(
    val partOfSpeech: String,
    val definitions: List<String>
)

sealed interface DictionaryResult {
    /** La palabra esta en el diccionario. [word] es como aparece alli, con tildes. */
    data class Found(val word: String, val entries: List<DictionaryEntry>) : DictionaryResult

    /** El diccionario funciona, pero esa palabra no esta. */
    data class NotFound(val word: String) : DictionaryResult

    /** No se ha podido consultar (diccionario no incluido o ilegible). */
    data class Failed(val word: String, val message: String) : DictionaryResult
}
