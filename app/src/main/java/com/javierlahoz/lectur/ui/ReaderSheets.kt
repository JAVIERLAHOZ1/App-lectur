package com.javierlahoz.lectur.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.javierlahoz.lectur.dict.DictionaryResult
import com.javierlahoz.lectur.pdf.OutlineEntry

/** Estado de la consulta al diccionario. */
sealed interface LookupState {
    data object Hidden : LookupState
    data class Searching(val word: String) : LookupState
    data class Ready(val result: DictionaryResult) : LookupState
    data object NoWord : LookupState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineSheet(
    entries: List<OutlineEntry>?,
    pageCount: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(
                text = "Indice del libro",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))

            when {
                entries == null -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "Leyendo el indice...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                entries.isEmpty() -> Text(
                    text = "Este PDF no trae indice. Puedes moverte con la barra de paginas " +
                        "de abajo (tiene $pageCount).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )

                else -> LazyColumn(modifier = Modifier.heightIn(max = 460.dp)) {
                    items(entries) { entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(entry.pageIndex) }
                                .padding(
                                    start = (entry.level * 16).dp,
                                    top = 10.dp,
                                    bottom = 10.dp
                                )
                        ) {
                            Text(
                                text = entry.title,
                                style = if (entry.level == 0) {
                                    MaterialTheme.typography.titleMedium
                                } else {
                                    MaterialTheme.typography.bodyMedium
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Pagina ${entry.pageIndex + 1}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionarySheet(
    state: LookupState,
    onDismiss: () -> Unit,
    onSearchOnWeb: (String) -> Unit
) {
    if (state is LookupState.Hidden) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .padding(horizontal = 20.dp)
        ) {
            when (state) {
                is LookupState.Hidden -> Unit

                is LookupState.NoWord -> {
                    Text(
                        text = "No he encontrado ninguna palabra ahi",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Prueba a mantener pulsado justo encima de la palabra. En PDF " +
                            "escaneados (paginas que son fotos) no hay texto que consultar.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is LookupState.Searching -> {
                    if (state.word.isNotBlank()) {
                        Text(
                            text = state.word,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(16.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = if (state.word.isBlank()) {
                                "Buscando la palabra en la pagina..."
                            } else {
                                "Buscando la definicion..."
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is LookupState.Ready -> DictionaryBody(
                    result = state.result,
                    onSearchOnWeb = onSearchOnWeb
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun DictionaryBody(
    result: DictionaryResult,
    onSearchOnWeb: (String) -> Unit
) {
    val word = when (result) {
        is DictionaryResult.Found -> result.word
        is DictionaryResult.NotFound -> result.word
        is DictionaryResult.Failed -> result.word
    }

    Text(
        text = word,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(12.dp))

    when (result) {
        is DictionaryResult.Found -> {
            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                items(result.entries) { entry ->
                    Text(
                        text = entry.partOfSpeech,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                    )
                    entry.definitions.forEachIndexed { index, definition ->
                        Text(
                            text = "${index + 1}. $definition",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                }
            }
        }

        is DictionaryResult.NotFound -> Text(
            text = "No esta en el diccionario. Puede ser un nombre propio, una palabra en " +
                "otro idioma o una forma poco habitual.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        is DictionaryResult.Failed -> Text(
            text = "No se ha podido consultar: ${result.message}.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(12.dp))
    TextButton(onClick = { onSearchOnWeb(word) }) {
        Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("Buscarla en la RAE")
    }
}
