package co.com.calculadoradesalarios.ui.liquidacion

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.datos.NominaRepository
import co.com.calculadoradesalarios.datos.TipoMensaje
import co.com.calculadoradesalarios.engine.Analysis
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.engine.DayRow
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.Fmt
import co.com.calculadoradesalarios.engine.JornadaHelper
import co.com.calculadoradesalarios.engine.PeriodoCalc
import co.com.calculadoradesalarios.engine.WeekGaugeData
import co.com.calculadoradesalarios.engine.exportar.ArchivoExportado
import co.com.calculadoradesalarios.engine.exportar.ExportadorNomina
import co.com.calculadoradesalarios.exportar.FuentesLato
import co.com.calculadoradesalarios.exportar.TipoExportacion
import co.com.calculadoradesalarios.ui.comun.EntradaNumerica
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.util.Locale

data class ChipEmpleadoLiq(val id: String, val nombre: String, val iniciales: String, val tono: Int, val foto: String?, val pago: String, val seleccionado: Boolean)

/** WeekRowViewModel del escritorio + los datos del medidor (JornadaHelper.CalculateWeekGauge). */
data class SemanaFila(val rango: String, val horas: String, val medidor: WeekGaugeData, val tieneExtra: Boolean)

/** DayRowViewModel del escritorio. */
data class DiaFila(
    val clave: String,
    val etiqueta: String,
    val festivo: Boolean,
    val dominical: Boolean,
    val enPeriodo: Boolean,
    val descanso: Boolean,
    val estado: String,
    val trabajoDescanso: Boolean,
    val in1: String,
    val out1: String,
    val in2: String,
    val out2: String,
    val descansoMin: String,
    val horas: String,
    val monto: String,
    val categorias: List<Pair<Cat, String>>,
)

/** AuditRowViewModel del escritorio. */
data class FilaAuditoria(val cat: Cat, val concepto: String, val horas: String, val valorHora: String, val total: String, val porcentaje: String)

data class NominaTexto(
    val basico: String = "$ 0",
    val auxilio: String = "$ 0",
    val recargos: String = "$ 0",
    val devengado: String = "$ 0",
    val saludEtiqueta: String = "Salud",
    val salud: String = "- $ 0",
    val pensionEtiqueta: String = "Pensión",
    val pension: String = "- $ 0",
    val neto: String = "$ 0",
    val nota: String = "",
)

enum class CampoDia(val clave: String, val etiqueta: String) {
    Entrada1("in1", "Entrada 1"), Salida1("out1", "Salida 1"), Entrada2("in2", "Entrada 2"), Salida2("out2", "Salida 2"), Descanso("brk", "Descanso (min)")
}

data class LiquidacionEstado(
    val cargando: Boolean = true,
    val hayEmpleados: Boolean = false,
    val chips: List<ChipEmpleadoLiq> = emptyList(),
    val empleadoId: String = "",
    val empleadoNombre: String = "",
    val periodo: String = "",
    val inclSueldo: Boolean = false,
    val valorHora: String = "",
    val totalHoras: String = "0:00",
    val totalRecargos: String = "$ 0",
    val nomina: NominaTexto = NominaTexto(),
    val alertas: List<String> = emptyList(),
    val semanas: List<SemanaFila> = emptyList(),
    val textoTope: String = "",
    val dias: List<DiaFila> = emptyList(),
    val auditoria: List<FilaAuditoria> = emptyList(),
    val turnosRapidos: List<String> = emptyList(),
    val diaAbierto: DiaFila? = null,
    val erroresDia: Map<CampoDia, String> = emptyMap(),
)

class LiquidacionViewModel(
    private val repo: NominaRepository,
    private val guardado: SavedStateHandle,
    private val hoy: () -> LocalDate = LocalDate::now,
    private val calculo: CoroutineDispatcher = Dispatchers.Default,
    private val exportador: () -> ExportadorNomina = { FuentesLato.exportador() },
) : ViewModel() {
    private val erroresDia = MutableStateFlow<Map<CampoDia, String>>(emptyMap())

    val estado: StateFlow<LiquidacionEstado> =
        combine(repo.datos, guardado.getStateFlow<String?>(ClaveDia, null), erroresDia) { d, dia, err ->
            if (d == null) LiquidacionEstado() else construir(d, dia, err)
        }.flowOn(calculo).stateIn(viewModelScope, SharingStarted.Eagerly, LiquidacionEstado())

    private fun seleccionado(d: DatosNomina): Employee? =
        d.state.emps.firstOrNull { it.id == d.ui.sel } ?: d.state.emps.firstOrNull()

    private fun construir(d: DatosNomina, diaAbierto: String?, err: Map<CampoDia, String>): LiquidacionEstado {
        val (f, t) = PeriodoCalc.Periodo(d.ui, hoy())
        val cfg = d.state.cfg
        val fest = d.state.festivos.toHashSet()
        val emp = seleccionado(d)
        val chips = d.state.emps.map { e ->
            val a = Engine.Analyze(e, cfg, fest, f, t)
            val pago = if (d.ui.incl) Engine.GetPayroll(e, cfg, a.totalAmount).neto else a.totalAmount
            ChipEmpleadoLiq(e.id, e.name.ifBlank { "Sin nombre" }, Fmt.Initials(e.name), Fmt.HueOf(e.name), e.photoRelPath, Fmt.Money(pago), e.id == emp?.id)
        }
        val base = LiquidacionEstado(
            cargando = false,
            hayEmpleados = emp != null,
            chips = chips,
            periodo = PeriodoCalc.Etiqueta(d.ui, hoy()),
            inclSueldo = d.ui.incl,
            turnosRapidos = cfg.turnos.toList(),
        )
        if (emp == null) return base
        val a = Engine.Analyze(emp, cfg, fest, f, t)
        val p = Engine.GetPayroll(emp, cfg, a.totalAmount)
        val capMin = Engine.JsRound(cfg.weeklyCap * 60).toInt()
        val dias = a.rows.map(::dia)
        val baseMode = if (cfg.deductBase == "con_recargos") "básico + recargos" else "mitad del salario"
        return base.copy(
            empleadoId = emp.id,
            empleadoNombre = emp.name,
            valorHora = "Valor hora base: ${Fmt.Money(Engine.JsRound(a.rates.hv).toLong())} · salario ÷ ${num(cfg.divisor)}",
            totalHoras = Engine.FmtHM(a.totalMin),
            totalRecargos = Fmt.Money(a.totalAmount),
            nomina = NominaTexto(
                basico = Fmt.Money(p.basicoQ),
                auxilio = Fmt.Money(p.auxQ),
                recargos = Fmt.Money(p.recargos),
                devengado = Fmt.Money(p.devengado),
                saludEtiqueta = "Salud (${num(cfg.saludPct)}%)",
                salud = "- " + Fmt.Money(p.salud),
                pensionEtiqueta = "Pensión (${num(cfg.pensionPct)}%)",
                pension = "- " + Fmt.Money(p.pension),
                neto = Fmt.Money(p.neto),
                nota = "Descuentos sobre ${Fmt.Money(Engine.JsRound(p.base).toLong())} ($baseMode).",
            ),
            alertas = a.alerts.toList(),
            semanas = a.weeks.map { w ->
                val extra = if (w.extraMin > 0) " · extra ${Engine.FmtHM(w.extraMin)}" else ""
                SemanaFila(
                    rango = "${Fmt.Dmy(w.start)} – ${Fmt.Dmy(w.end)}",
                    horas = "${Engine.FmtHM(w.workedMin)} / ${Engine.FmtHM(capMin)}$extra",
                    medidor = JornadaHelper.CalculateWeekGauge(w.workedMin, w.extraMin, capMin),
                    tieneExtra = w.extraMin > 0,
                )
            },
            textoTope = "La marca vertical es el tope de ${num(cfg.weeklyCap)} h. Lo que lo supera se paga como hora extra.",
            dias = dias,
            auditoria = auditoria(a, cfg.domOrdFull),
            diaAbierto = dias.firstOrNull { it.clave == diaAbierto },
            erroresDia = err,
        )
    }

    private fun dia(r: DayRow): DiaFila {
        val rec = r.rec
        return DiaFila(
            clave = Engine.Key(r.date),
            etiqueta = "${Engine.Dow[r.dow]} ${Fmt.Dmy(r.date)}",
            festivo = r.isFest,
            dominical = r.dom,
            enPeriodo = r.inPeriod,
            descanso = r.rest,
            estado = when {
                r.rest && r.dayMin == 0 -> "Descansó"
                r.rest -> "Trabajó descanso"
                else -> ""
            },
            trabajoDescanso = r.rest && r.dayMin > 0,
            in1 = rec?.in1.orEmpty(), out1 = rec?.out1.orEmpty(), in2 = rec?.in2.orEmpty(), out2 = rec?.out2.orEmpty(),
            descansoMin = if (rec != null && rec.brk > 0) rec.brk.toInt().toString() else "",
            horas = if (r.dayMin > 0) Engine.FmtHM(r.dayMin) else "",
            monto = if (r.dayAmount > 0) Fmt.Money(r.dayAmount) else "",
            categorias = Engine.Cats.filter { r.cats[it.ordinal] > 0 }.map { it to Engine.FmtHM(r.cats[it.ordinal]) },
        )
    }

    /** Tabla de auditoría como RecalculateCore del escritorio. */
    private fun auditoria(a: Analysis, domOrdFull: Boolean): List<FilaAuditoria> = Engine.Cats.mapNotNull { c ->
        val min = a.totMin[c.ordinal]
        val amt = a.totAmount[c.ordinal]
        if (min == 0 && c != Cat.ODiu && c != Cat.ONoc) return@mapNotNull null
        val tipo = when {
            c == Cat.ODiu -> "(incluida en el salario)"
            c == Cat.ONoc || (!domOrdFull && (c == Cat.ODom || c == Cat.ONocDom)) -> "(solo recargo)"
            else -> "(valor completo)"
        }
        FilaAuditoria(
            cat = c,
            concepto = Engine.CatLabel.getValue(c).replace(" (incluida en el salario)", ""),
            horas = Engine.FmtHM(min),
            valorHora = if (c == Cat.ODiu) "—" else "${Fmt.Money(Engine.JsRound(a.rates[c]).toLong())} $tipo",
            total = Fmt.Money(amt),
            porcentaje = if (a.totalAmount != 0L && amt != 0L) String.format(Locale.ROOT, "%.1f%%", amt * 100.0 / a.totalAmount) else "",
        )
    }

    // ---------------- acciones ----------------

    fun seleccionar(id: String) = repo.editarUi { it.sel = id }

    fun fijarInclSueldo(valor: Boolean) = repo.editarUi { it.incl = valor }

    fun abrirDia(clave: String) {
        erroresDia.value = emptyMap()
        guardado[ClaveDia] = clave
    }

    fun cerrarDia() {
        guardado[ClaveDia] = null
    }

    private fun editarDia(clave: String, razon: String = "Edición de datos", cambio: (Employee, LocalDate) -> Boolean): Boolean {
        val fecha = PeriodoCalc.ParseFecha(clave) ?: return false
        return repo.editar(razon) { st, ui ->
            val emp = st.emps.firstOrNull { it.id == ui.sel } ?: st.emps.firstOrNull() ?: return@editar false
            cambio(emp, fecha)
        }
    }

    /** CommitTime/CommitBreak del escritorio: normaliza con NormTime y edita con EditDay. */
    fun confirmar(clave: String, campo: CampoDia, texto: String) {
        val valor = if (campo == CampoDia.Descanso) {
            when (val r = EntradaNumerica.minutos(texto)) {
                is EntradaNumerica.Resultado.Invalido -> return error(campo, r.mensaje)
                is EntradaNumerica.Resultado.Valido -> r.texto
            }
        } else {
            JornadaHelper.NormTime(EntradaNumerica.hora(texto)) ?: return error(campo, ErrorHora)
        }
        erroresDia.value = erroresDia.value - campo
        editarDia(clave) { emp, fecha -> JornadaHelper.EditarCelda(emp, fecha, campo.clave, valor) }
    }

    private fun error(campo: CampoDia, mensaje: String) {
        erroresDia.value = erroresDia.value + (campo to mensaje)
    }

    /** OnRestToggled del escritorio. */
    fun fijarDescanso(clave: String, valor: Boolean) = editarDia(clave) { emp, fecha ->
        JornadaHelper.EditDay(emp, fecha) { it.desc = valor }
        true
    }

    fun aplicarTurno(clave: String, turno: String) = editarDia(clave) { emp, fecha -> JornadaHelper.AplicarTurno(emp, fecha, turno) }

    fun limpiarDia(clave: String) = editarDia(clave) { emp, fecha -> JornadaHelper.LimpiarDia(emp, fecha) }

    /** ClearPeriodHours del escritorio, con el mismo motivo y mensaje. */
    fun limpiarPeriodo() {
        val d = repo.datos.value ?: return
        val (f, t) = PeriodoCalc.Periodo(d.ui, hoy())
        var borrados = 0
        repo.editar("Horas borradas") { st, ui ->
            val emp = st.emps.firstOrNull { it.id == ui.sel } ?: st.emps.firstOrNull() ?: return@editar false
            borrados = JornadaHelper.LimpiarPeriodo(emp, f, t)
            borrados > 0
        }
        repo.avisar("Horas del periodo borradas.", TipoMensaje.Info)
    }

    fun textoConfirmarLimpiar(): String {
        val d = repo.datos.value ?: return ""
        val (f, t) = PeriodoCalc.Periodo(d.ui, hoy())
        val nombre = seleccionado(d)?.name.orEmpty()
        return "¿Borrar horas de $nombre entre ${Fmt.DmyFull(f)} y ${Fmt.DmyFull(t)}?"
    }

    /** ExportPdf / ExportExcel del escritorio: el empleado seleccionado, en el periodo actual. */
    suspend fun exportar(tipo: TipoExportacion): ArchivoExportado {
        val d = repo.datos.value ?: error("Sin datos")
        val emp = seleccionado(d) ?: throw IllegalStateException("No hay empleados para exportar.")
        val (f, t) = PeriodoCalc.Periodo(d.ui, hoy())
        val logo = repo.leerImagen(d.state.cfg.logoCustomRelPath)
        return withContext(calculo) {
            when (tipo) {
                TipoExportacion.Pdf -> exportador().pdf(d.state, d.ui.incl, listOf(emp), f, t, hoy(), logo, ExportadorNomina.nombrePdfEmpleado(emp, f, t))
                TipoExportacion.Excel -> exportador().excel(d.state, d.ui.incl, listOf(emp), f, t)
            }
        }
    }

    /** AddEmployee (en el escritorio el botón vive en Liquidación). */
    fun agregarEmpleado() {
        val nuevo = Employee(name = "Nuevo empleado", restDay = null, aux = true)
        repo.editar("Empleado añadido") { st, ui -> st.emps.add(nuevo); ui.sel = nuevo.id; true }
        repo.avisar("Empleado añadido.", TipoMensaje.Exito)
    }

    companion object {
        private const val ClaveDia = "dia_abierto"
        const val ErrorHora = "Hora no válida: escribe 8:30, 0830 o 14."

        /** Como "{0:0.##}" de .NET: hasta dos decimales y sin ceros sobrantes. */
        fun num(v: Double): String = java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
