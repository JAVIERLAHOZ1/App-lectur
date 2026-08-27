package com.javierlahoz.lectur.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Normalizer

/**
 * Diccionario de espanol que viaja dentro de la app: una base SQLite generada
 * desde el Wikcionario (CC BY-SA). No hace falta conexion para consultarlo.
 *
 * La primera consulta copia la base desde los assets al almacenamiento de la
 * app, porque SQLite necesita un fichero de verdad; despues es instantaneo.
 */
object OfflineDictionary {

    private const val ASSET_NAME = "diccionario.db"

    /** Subir esto obliga a recopiar la base cuando cambia el diccionario. */
    private const val VERSION = 1

    private val lock = Mutex()
    private var database: SQLiteDatabase? = null
    private var missing = false

    suspend fun define(context: Context, word: String): DictionaryResult =
        withContext(Dispatchers.IO) {
            val clean = word.trim()
            if (clean.isEmpty()) return@withContext DictionaryResult.NotFound(word)

            val db = database(context.applicationContext)
                ?: return@withContext DictionaryResult.Failed(
                    clean,
                    "esta version de la app no lleva el diccionario"
                )

            for (candidate in candidates(clean)) {
                val entries = runCatching { lookup(db, candidate) }.getOrDefault(emptyList())
                if (entries.isNotEmpty()) {
                    val shown = entries.first().word
                    return@withContext DictionaryResult.Found(
                        word = shown,
                        entries = expand(db, entries)
                    )
                }
            }

            DictionaryResult.NotFound(clean)
        }

    /**
     * Formas que se prueban, en orden: la palabra tal cual y algunos plurales
     * habituales, por si el diccionario solo tiene el singular.
     */
    private fun candidates(word: String): List<String> {
        val key = key(word)
        if (key.isEmpty()) return emptyList()

        val list = mutableListOf(key)
        when {
            key.endsWith("ces") && key.length > 4 -> list += key.dropLast(3) + "z"
            key.endsWith("es") && key.length > 3 -> list += key.dropLast(2)
            key.endsWith("s") && key.length > 2 -> list += key.dropLast(1)
        }
        return list
    }

    /** Minusculas y sin tildes, igual que la columna `clave` de la base. */
    private fun key(word: String): String {
        val lower = word.lowercase()
        val decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD)
        return decomposed.filter { it.code < 0x300 || it.code > 0x36F }
            .filter { it.isLetter() || it == '-' || it == '\'' }
    }

    private data class Row(
        val word: String,
        val partOfSpeech: String,
        val definitions: List<String>
    )

    private fun lookup(db: SQLiteDatabase, key: String): List<Row> {
        val rows = mutableListOf<Row>()
        db.rawQuery(
            "SELECT palabra, categoria, acepciones FROM entradas WHERE clave = ? LIMIT 6",
            arrayOf(key)
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val definitions = cursor.getString(2)
                    .split('\n')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (definitions.isNotEmpty()) {
                    rows += Row(
                        word = cursor.getString(0),
                        partOfSpeech = cursor.getString(1),
                        definitions = definitions
                    )
                }
            }
        }
        return rows
    }

    /**
     * Si la palabra solo dice "Forma verbal de decir", se busca tambien "decir"
     * y se anaden sus acepciones: es lo que de verdad quiere saber quien lee.
     */
    private fun expand(db: SQLiteDatabase, rows: List<Row>): List<DictionaryEntry> {
        val entries = rows.map { DictionaryEntry(it.partOfSpeech, it.definitions) }

        val lemma = rows.asSequence()
            .flatMap { it.definitions.asSequence() }
            .mapNotNull { LEMMA_POINTER.find(it)?.groupValues?.getOrNull(1) }
            .firstOrNull()
            ?.trim()
            ?: return entries

        val lemmaRows = runCatching { lookup(db, key(lemma)) }.getOrDefault(emptyList())
        if (lemmaRows.isEmpty()) return entries

        return entries + lemmaRows.map {
            DictionaryEntry("${it.partOfSpeech} - ${it.word}", it.definitions)
        }
    }

    private suspend fun database(context: Context): SQLiteDatabase? = lock.withLock {
        database?.let { return@withLock it }
        if (missing) return@withLock null

        val file = copyFromAssets(context)
        if (file == null) {
            missing = true
            return@withLock null
        }

        val opened = runCatching {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        }.getOrNull()

        if (opened == null) {
            file.delete()
            missing = true
        } else {
            database = opened
        }
        opened
    }

    private fun copyFromAssets(context: Context): File? {
        val target = File(context.filesDir, "diccionario_v$VERSION.db")
        if (target.exists() && target.length() > 0L) return target

        // Limpia versiones anteriores del diccionario.
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith("diccionario_v") }
            ?.forEach { it.delete() }

        val temporary = File(context.filesDir, "diccionario_v$VERSION.db.tmp")
        return runCatching {
            context.assets.open(ASSET_NAME).use { input ->
                temporary.outputStream().use { output -> input.copyTo(output) }
            }
            if (!temporary.renameTo(target)) throw IllegalStateException("no se pudo guardar")
            target
        }.getOrElse {
            temporary.delete()
            null
        }
    }

    private val LEMMA_POINTER = Regex(
        "(?:forma(?: verbal| del adjetivo| sustantiva)?|plural|gerundio|participio|" +
            "diminutivo|aumentativo|superlativo|despectivo|variante) de ([\\p{L}'-]+)",
        RegexOption.IGNORE_CASE
    )
}
