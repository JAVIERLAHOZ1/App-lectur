package com.javierlahoz.lectur.dict

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Una acepcion: el tipo de palabra y sus definiciones. */
data class DictionaryEntry(
    val partOfSpeech: String,
    val definitions: List<String>
)

sealed interface DictionaryResult {
    data class Found(val word: String, val entries: List<DictionaryEntry>) : DictionaryResult
    data class NotFound(val word: String) : DictionaryResult
    data class Failed(val word: String, val message: String) : DictionaryResult
}

/**
 * Definiciones sacadas del Wikcionario (Wikimedia). Es la unica parte de la app
 * que usa internet, y solo cuando se pulsa una palabra.
 */
object DictionaryClient {

    private const val ENDPOINT = "https://es.wiktionary.org/api/rest_v1/page/definition/"
    private const val USER_AGENT = "Lectur/1.0 (lector de PDF personal)"
    private const val TIMEOUT_MS = 12_000

    suspend fun define(word: String): DictionaryResult = withContext(Dispatchers.IO) {
        if (word.isBlank()) return@withContext DictionaryResult.NotFound(word)

        val url = URL(ENDPOINT + URLEncoder.encode(word, "UTF-8").replace("+", "%20"))
        var connection: HttpURLConnection? = null
        try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }

            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val entries = parse(body)
                    if (entries.isEmpty()) {
                        DictionaryResult.NotFound(word)
                    } else {
                        DictionaryResult.Found(word, entries)
                    }
                }

                HttpURLConnection.HTTP_NOT_FOUND -> DictionaryResult.NotFound(word)

                else -> DictionaryResult.Failed(word, "El diccionario respondio $code")
            }
        } catch (t: Throwable) {
            DictionaryResult.Failed(word, t.message ?: "No hay conexion")
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * La respuesta trae las acepciones agrupadas por idioma ("es", "en"...).
     * Nos quedamos con el castellano y, si no lo hay, con el primer idioma.
     */
    private fun parse(body: String): List<DictionaryEntry> {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return emptyList()
        val language = when {
            root.has("es") -> "es"
            root.keys().hasNext() -> root.keys().next()
            else -> return emptyList()
        }

        val array = root.optJSONArray(language) ?: return emptyList()
        val entries = ArrayList<DictionaryEntry>()

        for (i in 0 until minOf(array.length(), MAX_ENTRIES)) {
            val item = array.optJSONObject(i) ?: continue
            val definitions = ArrayList<String>()
            val list = item.optJSONArray("definitions")
            if (list != null) {
                for (j in 0 until minOf(list.length(), MAX_DEFINITIONS)) {
                    val definition = list.optJSONObject(j)?.optString("definition").orEmpty()
                    val clean = stripHtml(definition)
                    if (clean.isNotBlank()) definitions += clean
                }
            }
            if (definitions.isNotEmpty()) {
                entries += DictionaryEntry(
                    partOfSpeech = item.optString("partOfSpeech").ifBlank { "Definicion" },
                    definitions = definitions
                )
            }
        }
        return entries
    }

    private fun stripHtml(html: String): String =
        Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()

    private const val MAX_ENTRIES = 4
    private const val MAX_DEFINITIONS = 5
}
