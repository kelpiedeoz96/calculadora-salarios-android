package co.com.calculadoradesalarios.datos

import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.FestivosCalc
import co.com.calculadoradesalarios.engine.HistorialCalc
import co.com.calculadoradesalarios.engine.JsonConfig
import co.com.calculadoradesalarios.engine.PeriodoCalc
import co.com.calculadoradesalarios.engine.ResultadoRespaldo
import co.com.calculadoradesalarios.engine.UiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.time.LocalDate

enum class TipoMensaje { Exito, Info, Aviso, Error }

data class Mensaje(val texto: String, val tipo: TipoMensaje = TipoMensaje.Info)

/**
 * Estado único de la app (equivale a State/Ui/History del MainViewModel del escritorio).
 *
 * - Cada cambio trabaja sobre una copia y publica un objeto nuevo en [datos]; lo publicado no
 *   se vuelve a modificar, así el hilo que guarda nunca ve un objeto a medio cambiar.
 * - Guardar ocurre en [io], nunca en el hilo que pidió el cambio. Si llegan varios cambios
 *   seguidos, solo se escribe el último (canal CONFLATED).
 * - Los cambios de datos (no los de navegación o periodo) dejan una copia en el historial con las
 *   reglas del escritorio ([HistorialCalc.Snapshot]).
 */
class NominaRepository(
    private val almacen: AlmacenNomina,
    scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val relojMs: () -> Long = System::currentTimeMillis,
    private val hoy: () -> LocalDate = LocalDate::now,
) {
    private val _datos = MutableStateFlow<DatosNomina?>(null)
    val datos: StateFlow<DatosNomina?> = _datos.asStateFlow()

    private val _mensajes = MutableSharedFlow<Mensaje>(extraBufferCapacity = 16)
    val mensajes: SharedFlow<Mensaje> = _mensajes.asSharedFlow()

    private val _infoRespaldos = MutableStateFlow(InfoRespaldos())
    val infoRespaldos: StateFlow<InfoRespaldos> = _infoRespaldos.asStateFlow()

    private val _carpetaExtra = MutableStateFlow<String?>(null)
    val carpetaExtra: StateFlow<String?> = _carpetaExtra.asStateFlow()

    private val porGuardar = Channel<Pair<Long, DatosNomina>>(Channel.CONFLATED)
    @Volatile private var versionPedida = 0L
    private val versionGuardada = MutableStateFlow(0L)

    init {
        scope.launch(io) {
            for ((version, d) in porGuardar) {
                try {
                    almacen.guardar(d)
                } catch (e: Exception) {
                    avisar("Error al guardar datos: ${e.message}", TipoMensaje.Error)
                }
                versionGuardada.value = version
            }
        }
    }

    fun avisar(texto: String, tipo: TipoMensaje = TipoMensaje.Info) {
        _mensajes.tryEmit(Mensaje(texto, tipo))
    }

    suspend fun cargar() {
        val d = withContext(io) { almacen.cargar() }
        val ui = d.ui.copy()
        PeriodoCalc.Normalizar(ui, d.state.emps, hoy())
        val state = d.state.clone()
        val y = hoy().year
        FestivosCalc.CompletarAnios(state.festivos, listOf(y, y + 1))
        _datos.value = DatosNomina(state, ui, d.history)
        refrescarInfo()
    }

    /** Cambio de datos con copia en el historial. [cambio] devuelve false si no cambió nada. */
    fun editar(razon: String, cambio: (AppState, UiState) -> Boolean): Boolean {
        val actual = _datos.value ?: return false
        val state = actual.state.clone()
        val ui = actual.ui.copy()
        if (!cambio(state, ui)) return false
        val history = actual.history.toMutableList()
        HistorialCalc.Snapshot(history, razon, relojMs(), exportarJson(state))
        publicar(DatosNomina(state, ui, history))
        return true
    }

    /** Cambio de navegación, periodo, búsqueda o tema: se guarda, pero no va al historial. */
    fun editarUi(cambio: (UiState) -> Unit) {
        val actual = _datos.value ?: return
        val ui = actual.ui.copy()
        cambio(ui)
        if (ui == actual.ui) return
        publicar(actual.copy(ui = ui))
    }

    /** Equivale a ReplaceState del escritorio: estado nuevo, sin copia en el historial. */
    fun reemplazarEstado(nuevo: AppState) {
        val actual = _datos.value ?: return
        val state = nuevo.clone()
        val ui = actual.ui.copy()
        PeriodoCalc.Normalizar(ui, state.emps, hoy())
        val y = hoy().year
        FestivosCalc.CompletarAnios(state.festivos, listOf(y, y + 1))
        publicar(actual.copy(state = state, ui = ui))
    }

    /**
     * Snapshot(razon) + ReplaceState del escritorio: guarda en el historial una copia de lo que hay
     * ahora y luego pone [nuevo] (restaurar una copia, cargar un respaldo, borrar todo).
     */
    fun reemplazarConCopia(razon: String, nuevo: AppState) {
        val actual = _datos.value ?: return
        val history = actual.history.toMutableList()
        HistorialCalc.Snapshot(history, razon, relojMs(), exportarJson(actual.state))
        _datos.value = actual.copy(history = history)
        reemplazarEstado(nuevo)
    }

    private fun publicar(d: DatosNomina) {
        _datos.value = d
        versionPedida++
        porGuardar.trySend(versionPedida to d)
    }

    /** Suspende hasta que todo lo pedido hasta ahora esté escrito en disco. */
    suspend fun esperarGuardado() {
        val objetivo = versionPedida
        versionGuardada.first { it >= objetivo }
    }

    fun exportarJson(state: AppState = _datos.value!!.state): String =
        JsonConfig.exportJson.encodeToString(AppState.serializer(), state)

    // ---------------- respaldos ----------------

    /** Al abrir la app y al pasar a segundo plano. Nunca lanza: un respaldo fallido no molesta. */
    suspend fun respaldarSiCorresponde() {
        esperarGuardado()
        try {
            withContext(io) { almacen.respaldarSiCorresponde() }
        } catch (_: Exception) {
        }
        refrescarInfo()
    }

    suspend fun respaldarAhora(): ResultadoRespaldo {
        esperarGuardado()
        try {
            return withContext(io) { almacen.respaldarAhora() }
        } finally {
            refrescarInfo()
        }
    }

    /** Respalda lo actual como red de seguridad, restaura el .zip y vuelve a cargar. */
    suspend fun restaurar(abrir: () -> InputStream) {
        esperarGuardado()
        withContext(io) {
            almacen.respaldarAhora()
            almacen.restaurar(abrir)
        }
        cargar()
    }

    suspend fun fijarCarpetaExtra(uri: String?) {
        withContext(io) { almacen.fijarCarpetaExtra(uri) }
        refrescarInfo()
    }

    private suspend fun refrescarInfo() {
        val (info, extra) = withContext(io) { almacen.infoRespaldos() to almacen.carpetaExtra() }
        _infoRespaldos.value = info
        _carpetaExtra.value = extra
    }

    // ---------------- imágenes ----------------

    suspend fun guardarFotoEmpleado(empId: String, abrir: () -> InputStream, extension: String) {
        val rel = withContext(io) { almacen.guardarFotoEmpleado(empId, abrir, extension) }
        editar("Edición de datos") { st, _ ->
            val emp = st.emps.firstOrNull { it.id == empId } ?: return@editar false
            emp.photoRelPath = rel
            true
        }
    }

    suspend fun guardarLogo(abrir: () -> InputStream, extension: String) {
        val rel = withContext(io) { almacen.guardarLogo(abrir, extension) }
        editar("Edición de datos") { st, _ ->
            st.cfg.logoCustomRelPath = rel
            true
        }
    }

    suspend fun leerImagen(relPath: String?): ByteArray? =
        if (relPath.isNullOrBlank()) null else withContext(io) { almacen.leerImagen(relPath) }
}
