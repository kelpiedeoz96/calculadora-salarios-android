package co.com.calculadoradesalarios.exportar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class TipoExportacion { Pdf, Excel }
enum class ModoExportacion { Compartir, Guardar }

/** "PDF" y "Excel", cada uno con "Compartir…" y "Guardar en…". */
@Composable
fun BotonesExportar(
    etiquetaPdf: String,
    etiquetaExcel: String,
    ocupado: TipoExportacion?,
    onExportar: (TipoExportacion, ModoExportacion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Boton(etiquetaPdf, Icons.Outlined.PictureAsPdf, ocupado == TipoExportacion.Pdf, ocupado == null) { onExportar(TipoExportacion.Pdf, it) }
        Boton(etiquetaExcel, Icons.Outlined.TableChart, ocupado == TipoExportacion.Excel, ocupado == null) { onExportar(TipoExportacion.Excel, it) }
    }
}

@Composable
private fun Boton(texto: String, icono: ImageVector, cargando: Boolean, habilitado: Boolean, onModo: (ModoExportacion) -> Unit) {
    var abierto by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { abierto = true }, enabled = habilitado) {
            if (cargando) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(icono, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(texto, Modifier.padding(start = 8.dp))
        }
        DropdownMenu(expanded = abierto, onDismissRequest = { abierto = false }) {
            DropdownMenuItem(text = { Text("Compartir…") }, leadingIcon = { Icon(Icons.Outlined.Share, null) }, onClick = { abierto = false; onModo(ModoExportacion.Compartir) })
            DropdownMenuItem(text = { Text("Guardar en…") }, leadingIcon = { Icon(Icons.Outlined.SaveAlt, null) }, onClick = { abierto = false; onModo(ModoExportacion.Guardar) })
        }
    }
}
