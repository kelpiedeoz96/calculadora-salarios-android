package co.com.calculadoradesalarios.ui.historial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.com.calculadoradesalarios.datos.NominaRepository
import co.com.calculadoradesalarios.datos.TipoMensaje
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.HistorialCalc
import co.com.calculadoradesalarios.engine.JsonConfig
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.InputStream
import java.io.OutputStream
import java.time.ZoneId

/** HistorialRowViewModel del escritorio. */
data class FilaHistorial(val indice: Int, val motivo: String, val cuando: String)

data class HistorialEstado(val cargando: Boolean = true, val filas: List<FilaHistorial> = emptyList())

/** HistorialViewModel del escritorio (copias del historial, respaldo JSON y borrar todo). */
class HistorialViewModel(
    private val repo: NominaRepository,
    private val zona: () -> ZoneId = ZoneId::systemDefault,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    val estado: StateFlow<HistorialEstado> = repo.datos.filterNotNull().map { d ->
        HistorialEstado(false, d.history.mapIndexed { i, h ->
            // El escritorio muestra la hora local (TimeZoneInfo.Local).
            FilaHistorial(i, h.reason.ifBlank { "Cambio" }, HistorialCalc.FormatearFecha(h.ts, zona()))
        })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistorialEstado())

    /** RestaurarCopiaAsync: copia "Antes de restaurar" y ReplaceState con el payload. */
    fun restaurar(indice: Int) {
        val entrada = repo.datos.value?.history?.getOrNull(indice) ?: return
        val nuevo = HistorialCalc.ParsePayload(entrada.payload)
            ?: return repo.avisar("No se pudo restaurar.", TipoMensaje.Error)
        repo.reemplazarConCopia("Antes de restaurar", nuevo)
        repo.avisar("Estado restaurado.", TipoMensaje.Exito)
    }

    /** DescargarRespaldoAsync: el estado en el formato de ExportJson del escritorio. */
    fun descargarRespaldo(destino: () -> OutputStream) = viewModelScope.launch {
        try {
            val json = repo.exportarJson()
            withContext(io) { destino().use { it.write(json.toByteArray()) } }
            repo.avisar("Respaldo guardado.", TipoMensaje.Exito)
        } catch (e: Exception) {
            repo.avisar("No se pudo guardar el respaldo: ${e.message}", TipoMensaje.Error)
        }
    }

    /** CargarRespaldoAsync: copia "Antes de cargar respaldo" y ReplaceState. */
    fun cargarRespaldo(abrir: () -> InputStream) = viewModelScope.launch {
        val importado = try {
            LeerEstado(withContext(io) { abrir().use { it.readBytes().decodeToString() } })
        } catch (e: Exception) {
            return@launch repo.avisar("El archivo no es un respaldo válido.", TipoMensaje.Error)
        }
        repo.reemplazarConCopia("Antes de cargar respaldo", importado)
        repo.avisar("Respaldo cargado: ${importado.emps.size} empleados.", TipoMensaje.Exito)
    }

    /** BorrarTodoAsync: copia "Reinicio" y HistorialCalc.EstadoInicial(). */
    fun borrarTodo() {
        repo.reemplazarConCopia("Reinicio", HistorialCalc.EstadoInicial())
        repo.avisar("Datos reiniciados. Queda una copia en el historial.", TipoMensaje.Exito)
    }

    companion object {
        /**
         * Backup.Load del escritorio lee el estado plano (lo que produce "Descargar respaldo JSON").
         * Además se acepta un data.json completo ({"state":…}), para poder traer el archivo de datos del
         * escritorio. Un JSON sin empleados ni configuración no se acepta.
         */
        fun LeerEstado(texto: String): AppState {
            val root = JsonConfig.json.parseToJsonElement(texto) as JsonObject
            val nodo = root["state"] ?: root
            require(nodo is JsonObject && ("emps" in nodo || "cfg" in nodo)) { "sin estado" }
            return HistorialCalc.ParsePayload(nodo.toString()) ?: error("sin estado")
        }
    }
}
