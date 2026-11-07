package co.com.calculadoradesalarios.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.datos.NominaRepository
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.engine.EmpSummary
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.Fmt
import co.com.calculadoradesalarios.engine.PanelCalc
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

/** CategoriaBarItemViewModel del escritorio. */
data class BarraCategoria(val cat: Cat, val corta: String, val etiqueta: String, val horas: String, val fraccion: Float)

/** TopEmpleadoItemViewModel del escritorio. */
data class TopEmpleado(val id: String, val nombre: String, val iniciales: String, val tono: Int, val foto: String?, val detalle: String)

data class PanelEstado(
    val cargando: Boolean = true,
    val empleados: String = "0",
    val horasTotales: String = "0:00",
    val recargos: String = "$ 0",
    val horasExtra: String = "0:00",
    val horasExtraPct: String = "—",
    val categorias: List<BarraCategoria> = emptyList(),
    val top: List<TopEmpleado> = emptyList(),
    val alertas: List<String> = emptyList(),
    val ordinarias: String = "0:00",
    val nocturnas: String = "0:00",
    val dominicales: String = "0:00",
    val extra: String = "0:00",
    val totalRecargos: String = "$ 0",
    val conExtra: String = "0",
    val promedio: String = "$ 0",
    val totalPagar: String = "$ 0",
)

/** DashboardViewModel del escritorio: todo sale de PanelCalc.Calcular. */
class PanelViewModel(
    private val repo: NominaRepository,
    private val hoy: () -> LocalDate = LocalDate::now,
    calculo: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    val estado: StateFlow<PanelEstado> = repo.datos.filterNotNull().map(::construir)
        .flowOn(calculo).stateIn(viewModelScope, SharingStarted.Eagerly, PanelEstado())

    private fun construir(d: DatosNomina): PanelEstado {
        val (f, t) = PeriodoCalc.Periodo(d.ui, hoy())
        val cfg = d.state.cfg
        val fest = d.state.festivos.toHashSet()
        val res = PanelCalc.Calcular(d.state.emps.map { e ->
            val a = Engine.Analyze(e, cfg, fest, f, t)
            val p = Engine.GetPayroll(e, cfg, a.totalAmount)
            EmpSummary(e, a, p, if (d.ui.incl) p.neto else a.totalAmount)
        })
        return PanelEstado(
            cargando = false,
            empleados = res.empleadosCount.toString(),
            horasTotales = Engine.FmtHM(res.totalMin),
            recargos = Fmt.Money(res.totalAmount),
            horasExtra = Engine.FmtHM(res.extraMin),
            horasExtraPct = res.pctExtraTexto,
            categorias = Engine.Cats.filter { res.minutosPorCategoria[it.ordinal] > 0 }.map { c ->
                val min = res.minutosPorCategoria[c.ordinal]
                BarraCategoria(c, Engine.CatShort.getValue(c), Engine.CatLabel.getValue(c).replace(" (incluida en el salario)", ""),
                    Engine.FmtHM(min), min.toFloat() / res.maxCategoria)
            },
            top = res.top5.map { s ->
                TopEmpleado(s.emp.id, s.emp.name.ifBlank { "Sin nombre" }, Fmt.Initials(s.emp.name), Fmt.HueOf(s.emp.name), s.emp.photoRelPath,
                    "${Engine.FmtHM(s.r.totalMin)} · ${Fmt.Money(s.r.totalAmount)}")
            },
            alertas = res.alertas.take(6),
            ordinarias = Engine.FmtHM(res.totalMin - res.extraMin),
            nocturnas = Engine.FmtHM(res.nightMin),
            dominicales = Engine.FmtHM(res.domMin),
            extra = Engine.FmtHM(res.extraMin),
            totalRecargos = Fmt.Money(res.totalAmount),
            conExtra = res.conExtra.toString(),
            promedio = Fmt.Money(res.promedio),
            // Como el escritorio: "Total a pagar" muestra el total de recargos + extras.
            totalPagar = Fmt.Money(res.totalAmount),
        )
    }

    /** OnAbrirEmpleado del escritorio: selecciona y va a Liquidación. */
    fun abrirEmpleado(id: String) = repo.editarUi { it.sel = id; it.tab = "liq" }

    fun irAEmpleados() = repo.editarUi { it.tab = "all" }
}
