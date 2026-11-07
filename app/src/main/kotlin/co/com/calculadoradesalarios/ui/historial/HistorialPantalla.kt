package co.com.calculadoradesalarios.ui.historial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import co.com.calculadoradesalarios.ui.comun.EstadoVacio
import co.com.calculadoradesalarios.ui.comun.Seccion

class AccionesHistorial(
    val restaurar: (Int) -> Unit = {},
    val descargar: () -> Unit = {},
    val cargar: () -> Unit = {},
    val borrarTodo: () -> Unit = {},
)

@Composable
fun HistorialContenido(e: HistorialEstado, acciones: AccionesHistorial, modifier: Modifier = Modifier) {
    var restaurarIndice by rememberSaveable { mutableStateOf<Int?>(null) }
    var borrar by rememberSaveable { mutableStateOf(false) }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Seccion("Copias de seguridad automáticas") {
                Text(
                    "Se guardan hasta 25 estados anteriores en este teléfono. Puedes restaurarlos cuando lo necesites.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = acciones.descargar) { Boton(Icons.Outlined.FileUpload, "Descargar respaldo JSON") }
                    OutlinedButton(onClick = acciones.cargar) { Boton(Icons.Outlined.FileDownload, "Cargar respaldo") }
                    OutlinedButton(
                        onClick = { borrar = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Boton(Icons.Outlined.DeleteForever, "Borrar todo") }
                }
                Text(
                    "El respaldo JSON sirve para pasar los datos al escritorio o traerlos de allí.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!e.cargando && e.filas.isEmpty()) {
            item { EstadoVacio(Icons.Outlined.History, "Sin copias guardadas", "Se crean automáticamente al hacer cambios importantes.") }
        }
        items(e.filas, key = { it.indice }) { f ->
            ListItem(
                headlineContent = { Text(f.motivo) },
                supportingContent = { Text(f.cuando) },
                leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                trailingContent = { TextButton(onClick = { restaurarIndice = f.indice }) { Text("Restaurar") } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
            )
        }
    }

    restaurarIndice?.let { i ->
        AlertDialog(
            onDismissRequest = { restaurarIndice = null },
            icon = { Icon(Icons.Outlined.Restore, contentDescription = null) },
            title = { Text("Restaurar copia") },
            text = { Text("¿Restaurar este estado? Se perderán los cambios posteriores.") },
            confirmButton = { TextButton(onClick = { restaurarIndice = null; acciones.restaurar(i) }) { Text("Restaurar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { restaurarIndice = null }) { Text("Cancelar") } },
        )
    }
    if (borrar) {
        AlertDialog(
            onDismissRequest = { borrar = false },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null) },
            title = { Text("Borrar todo") },
            text = { Text("Se borran empleados, horas y ajustes. ¿Continuar?") },
            confirmButton = { TextButton(onClick = { borrar = false; acciones.borrarTodo() }) { Text("Borrar todo", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { borrar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Boton(icono: ImageVector, texto: String) {
    Icon(icono, contentDescription = null, modifier = Modifier.size(18.dp))
    Text(texto, Modifier.padding(start = 8.dp))
}
