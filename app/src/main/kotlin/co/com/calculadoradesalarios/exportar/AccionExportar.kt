package co.com.calculadoradesalarios.exportar

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import co.com.calculadoradesalarios.engine.exportar.ArchivoExportado
import co.com.calculadoradesalarios.engine.exportar.ExportadorNomina
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Estado y acción de exportar para una pantalla: genera, y luego comparte o guarda con el selector del sistema. */
class Exportacion internal constructor(val ocupado: TipoExportacion?, val exportar: (TipoExportacion, ModoExportacion) -> Unit)

@Composable
fun recordarExportacion(snackbar: SnackbarHostState, generar: suspend (TipoExportacion) -> ArchivoExportado): Exportacion {
    val context = LocalContext.current
    val servicio = remember(context) { ServicioExportacion(context) }
    val alcance = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf<TipoExportacion?>(null) }
    var pendiente by remember { mutableStateOf<ArchivoExportado?>(null) }

    val alGuardar: (android.net.Uri?) -> Unit = { uri ->
        val a = pendiente
        pendiente = null
        if (uri != null && a != null) alcance.launch {
            try {
                withContext(Dispatchers.IO) { servicio.copiar(a, context.contentResolver.openOutputStream(uri)!!) }
                snackbar.showSnackbar(if (a.mime == ExportadorNomina.MimePdf) "PDF guardado." else "Excel guardado.")
            } catch (e: Exception) {
                snackbar.showSnackbar("No se pudo guardar: ${e.message}")
            }
        }
    }
    val guardarPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportadorNomina.MimePdf), alGuardar)
    val guardarXlsx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ExportadorNomina.MimeXlsx), alGuardar)

    return Exportacion(ocupado) { tipo, modo ->
        alcance.launch {
            ocupado = tipo
            try {
                val a = generar(tipo)
                when (modo) {
                    ModoExportacion.Compartir -> {
                        val f = withContext(Dispatchers.IO) { servicio.preparar(a) }
                        context.startActivity(servicio.intentCompartir(f, a.mime))
                    }
                    ModoExportacion.Guardar -> {
                        pendiente = a
                        (if (a.mime == ExportadorNomina.MimePdf) guardarPdf else guardarXlsx).launch(a.nombre)
                    }
                }
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: "No se pudo exportar.")
            } finally {
                ocupado = null
            }
        }
    }
}
