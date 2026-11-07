package co.com.calculadoradesalarios.ui.calendario

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.datos.NominaRepository
import co.com.calculadoradesalarios.datos.TipoMensaje
import co.com.calculadoradesalarios.engine.CalendarioCalc
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.Fmt
import co.com.calculadoradesalarios.engine.PeriodoCalc
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** DiaCeldaViewModel del escritorio. */
data class CeldaDia(
    val clave: String,
    val dia: Int,
    val horas: String,
    val dominical: Boolean,
    val festivo: Boolean,
    val fuera: Boolean,
    val hoy: Boolean,
    val fraccion: Float,
    val barraExtra: Boolean,
    val detalle: String,
)

data class EmpleadoCal(val id: String, val nombre: String, val iniciales: String, val tono: Int, val foto: String?, val seleccionado: Boolean)

data class CalendarioEstado(
    val cargando: Boolean = true,
    val hayEmpleado: Boolean = false,
    val titulo: String = "",
    val mes: String = "",
    val empleados: List<EmpleadoCal> = emptyList(),
    /** Celdas vacías antes del día 1 (la semana empieza el lunes). */
    val relleno: Int = 0,
    val celdas: List<CeldaDia> = emptyList(),
)

/** CalendarioViewModel del escritorio: CalendarioCalc.Construir del mes en que empieza el periodo. */
class CalendarioViewModel(
    private val repo: NominaRepository,
    private val hoy: () -> LocalDate = LocalDate::now,
    calculo: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val estado: StateFlow<CalendarioEstado> = repo.datos.filterNotNull().map(::construir)
        .flowOn(calculo).stateIn(viewModelScope, SharingStarted.Eagerly, CalendarioEstado())

    private fun construir(d: DatosNomina): CalendarioEstado {
        val emps = d.state.emps
        val emp = emps.firstOrNull { it.id == d.ui.sel } ?: emps.firstOrNull()
            ?: return CalendarioEstado(cargando = false, titulo = "Sin empleados")
        val (f, t) = PeriodoCalc.Periodo(d.ui, hoy())
        val a = Engine.Analyze(emp, d.state.cfg, d.state.festivos.toHashSet(), f, t)
        val mes = CalendarioCalc.Construir(f.year, f.monthValue, a.rows, f, t, hoy())
        return CalendarioEstado(
            cargando = false,
            hayEmpleado = true,
            titulo = emp.name.ifBlank { "Sin nombre" },
            mes = "%04d-%02d".format(f.year, f.monthValue),
            empleados = emps.map { EmpleadoCal(it.id, it.name.ifBlank { "Sin nombre" }, Fmt.Initials(it.name), Fmt.HueOf(it.name), it.photoRelPath, it.id == emp.id) },
            relleno = mes.pad0,
            celdas = mes.dias.map { c ->
                CeldaDia(
                    clave = c.clave, dia = c.dia,
                    horas = if (c.dayMin > 0) Engine.FmtHM(c.dayMin) else "",
                    dominical = c.dom, festivo = c.festivo, fuera = !c.enPeriodo, hoy = c.esHoy,
                    fraccion = c.fraccion.toFloat(), barraExtra = c.barraExtra,
                    detalle = c.clave + " · " + c.turnos.ifEmpty { "sin horas" },
                )
            },
        )
    }

    fun seleccionar(id: String) = repo.editarUi { it.sel = id }

    /** OnDiaSeleccionado -> ShowSingleDay del escritorio: solo ese día, y a Liquidación. */
    fun verDia(clave: String) {
        val fecha = PeriodoCalc.ParseFecha(clave) ?: return
        repo.editarUi { PeriodoCalc.SoloDia(it, fecha); it.tab = "liq" }
        repo.avisar("Mostrando solo ${Fmt.DmyFull(fecha)}. Usa el selector de periodo para volver a la quincena.", TipoMensaje.Exito)
    }
}
