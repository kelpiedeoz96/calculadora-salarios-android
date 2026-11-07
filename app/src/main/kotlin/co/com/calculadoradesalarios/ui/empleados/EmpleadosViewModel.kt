package co.com.calculadoradesalarios.ui.empleados

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.datos.NominaRepository
import co.com.calculadoradesalarios.datos.TipoMensaje
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.engine.EmpSummary
import co.com.calculadoradesalarios.engine.EmpleadosCalc
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.Fmt
import co.com.calculadoradesalarios.engine.PeriodoCalc
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.InputStream
import java.time.LocalDate

/** Una fila de EmpleadosView del escritorio (EmpleadoRowViewModel). */
data class EmpleadoFila(
    val id: String,
    val nombre: String,
    val iniciales: String,
    val cargo: String,
    val foto: String?,
    val tono: Int,
    val horas: String,
    val nocturnas: String,
    val domFest: String,
    val extra: String,
    val recargos: String,
    val basico: String,
    val auxilio: String,
    val descuentos: String,
    val neto: String,
)

data class TotalesEmpleados(
    val horas: String = "0:00",
    val nocturnas: String = "0:00",
    val domFest: String = "0:00",
    val extra: String = "0:00",
    val recargos: String = "$ 0",
    val basico: String = "$ 0",
    val auxilio: String = "$ 0",
    val descuentos: String = "- $ 0",
    val neto: String = "$ 0",
)

/** "Datos del empleado" de LiquidacionView en el escritorio. */
data class FichaEmpleado(
    val id: String,
    val nombre: String,
    val cedula: String,
    val cargo: String,
    val salarioTexto: String,
    val diaDescanso: Int?,
    val auxilio: Boolean,
    val foto: String?,
    val iniciales: String,
    val tono: Int,
    val avisoSalario: String?,
    val placeholderSalario: String,
)

enum class CampoEmpleado { Nombre, Cedula, Cargo, Salario }

data class EmpleadosEstado(
    val cargando: Boolean = true,
    val periodo: String = "",
    val busqueda: String = "",
    val inclSueldo: Boolean = false,
    val filas: List<EmpleadoFila> = emptyList(),
    val totales: TotalesEmpleados = TotalesEmpleados(),
    val hayEmpleados: Boolean = false,
    val ficha: FichaEmpleado? = null,
)

class EmpleadosViewModel(
    private val repo: NominaRepository,
    private val guardado: SavedStateHandle,
    private val hoy: () -> LocalDate = LocalDate::now,
    calculo: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private data class Eliminado(val indice: Int, val emp: Employee)
    private var ultimoEliminado: Eliminado? = null

    val estado: StateFlow<EmpleadosEstado> =
        combine(repo.datos, guardado.getStateFlow<String?>(ClaveFicha, null)) { d, fichaId ->
            if (d == null) EmpleadosEstado() else construir(d, fichaId)
        }.flowOn(calculo).stateIn(viewModelScope, SharingStarted.Eagerly, EmpleadosEstado())

    private fun construir(d: DatosNomina, fichaId: String?): EmpleadosEstado {
        val (f, t) = PeriodoCalc.Periodo(d.ui, hoy())
        val cfg = d.state.cfg
        val fest = d.state.festivos.toHashSet()
        val resumenes = d.state.emps.map { e ->
            val a = Engine.Analyze(e, cfg, fest, f, t)
            val p = Engine.GetPayroll(e, cfg, a.totalAmount)
            EmpSummary(e, a, p, if (d.ui.incl) p.neto else a.totalAmount)
        }
        val visibles = resumenes.filter { EmpleadosCalc.Matches(it.emp, d.ui.search) }
        val tot = EmpleadosCalc.CalcularTotales(visibles, d.ui.incl)
        return EmpleadosEstado(
            cargando = false,
            periodo = PeriodoCalc.Etiqueta(d.ui, hoy()),
            busqueda = d.ui.search,
            inclSueldo = d.ui.incl,
            filas = visibles.map(::fila),
            totales = TotalesEmpleados(
                horas = Engine.FmtHM(tot.totalMin),
                nocturnas = Engine.FmtHM(tot.nocMin),
                domFest = Engine.FmtHM(tot.domMin),
                extra = Engine.FmtHM(tot.extraMin),
                recargos = Fmt.Money(tot.recargosAmount),
                basico = Fmt.Money(tot.basicoQ),
                auxilio = Fmt.Money(tot.auxQ),
                descuentos = "- " + Fmt.Money(tot.descuentos),
                neto = Fmt.Money(tot.neto),
            ),
            hayEmpleados = d.state.emps.isNotEmpty(),
            ficha = d.state.emps.firstOrNull { it.id == fichaId }?.let { ficha(it, cfg.smmlv) },
        )
    }

    private fun fila(s: EmpSummary) = EmpleadoFila(
        id = s.emp.id,
        nombre = s.emp.name.ifBlank { "Sin nombre" },
        iniciales = Fmt.Initials(s.emp.name),
        cargo = s.emp.cargo,
        foto = s.emp.photoRelPath,
        tono = Fmt.HueOf(s.emp.name),
        horas = Engine.FmtHM(s.r.totalMin),
        nocturnas = Engine.FmtHM(EmpleadosCalc.SumCats(s.r, Cat.ONoc)),
        domFest = Engine.FmtHM(EmpleadosCalc.SumCats(s.r, Cat.ODom, Cat.ONocDom)),
        extra = Engine.FmtHM(EmpleadosCalc.SumCats(s.r, Cat.HED, Cat.HEN, Cat.HEDDom, Cat.HENDom)),
        recargos = Fmt.Money(s.r.totalAmount),
        basico = Fmt.Money(s.p.basicoQ),
        auxilio = Fmt.Money(s.p.auxQ),
        descuentos = "- " + Fmt.Money(s.p.descuentos),
        neto = Fmt.Money(s.p.neto),
    )

    private fun ficha(e: Employee, smmlv: Double): FichaEmpleado {
        val aviso = if (e.salario > 0 && e.salario < smmlv)
            "El salario es inferior al mínimo vigente (${Fmt.Money(smmlv.toLong())})." else null
        return FichaEmpleado(
            id = e.id, nombre = e.name, cedula = e.cedula, cargo = e.cargo,
            salarioTexto = if (e.salario > 0) e.salario.toLong().toString() else "",
            diaDescanso = e.restDay, auxilio = e.aux, foto = e.photoRelPath,
            iniciales = Fmt.Initials(e.name), tono = Fmt.HueOf(e.name), avisoSalario = aviso,
            placeholderSalario = "${Fmt.Money(smmlv.toLong()).removePrefix("$ ")} (salario mínimo)",
        )
    }

    fun buscar(texto: String) = repo.editarUi { it.search = texto }

    fun fijarInclSueldo(valor: Boolean) = repo.editarUi { it.incl = valor }

    fun abrirFicha(id: String) {
        guardado[ClaveFicha] = id
    }

    fun cerrarFicha() {
        guardado[ClaveFicha] = null
    }

    /** AddEmployee del escritorio: queda seleccionado y se abre su ficha. */
    fun agregar() {
        val nuevo = Employee(name = "Nuevo empleado", restDay = null, aux = true)
        repo.editar("Empleado añadido") { st, ui -> st.emps.add(nuevo); ui.sel = nuevo.id; true }
        abrirFicha(nuevo.id)
        repo.avisar("Empleado añadido.", TipoMensaje.Exito)
    }

    /** CommitEmpField del escritorio. */
    fun confirmar(id: String, campo: CampoEmpleado, texto: String) = editarEmpleado(id) { e ->
        when (campo) {
            CampoEmpleado.Nombre -> cambiar(e.name, texto.trim()) { e.name = it }
            CampoEmpleado.Cedula -> cambiar(e.cedula, texto.trim()) { e.cedula = it }
            CampoEmpleado.Cargo -> cambiar(e.cargo, texto.trim()) { e.cargo = it }
            CampoEmpleado.Salario -> cambiar(e.salario, ParseSalario(texto)) { e.salario = it }
        }
    }

    /** null = "Sin definir"; 0 = domingo … 6 = sábado (Employee.restDay). */
    fun fijarDiaDescanso(id: String, dia: Int?) = editarEmpleado(id) { e -> cambiar(e.restDay, dia) { e.restDay = it } }

    fun fijarAuxilio(id: String, valor: Boolean) = editarEmpleado(id) { e -> cambiar(e.aux, valor) { e.aux = it } }

    private fun editarEmpleado(id: String, cambio: (Employee) -> Boolean) =
        repo.editar("Edición de datos") { st, _ -> st.emps.firstOrNull { it.id == id }?.let(cambio) ?: false }

    private inline fun <T> cambiar(actual: T, nuevo: T, aplicar: (T) -> Unit): Boolean =
        if (actual == nuevo) false else { aplicar(nuevo); true }

    fun elegirFoto(id: String, abrir: () -> InputStream, extension: String) = viewModelScope.launch {
        try {
            repo.guardarFotoEmpleado(id, abrir, extension)
            repo.avisar("Foto actualizada.", TipoMensaje.Exito)
        } catch (e: Exception) {
            repo.avisar(e.message ?: "No se pudo guardar la foto.", TipoMensaje.Error)
        }
    }

    fun quitarFoto(id: String) {
        if (editarEmpleado(id) { e -> if (e.photoRelPath.isNullOrEmpty()) false else { e.photoRelPath = null; true } })
            repo.avisar("Foto quitada.", TipoMensaje.Info)
    }

    /** Borra y guarda lo necesario para deshacer. El aviso con "Deshacer" lo muestra la pantalla. */
    fun eliminar(id: String): String? {
        var nombre: String? = null
        repo.editar("Empleado eliminado") { st, ui ->
            val i = st.emps.indexOfFirst { it.id == id }
            if (i < 0) return@editar false
            val e = st.emps.removeAt(i)
            ultimoEliminado = Eliminado(i, e)
            nombre = e.name
            if (ui.sel == id) ui.sel = st.emps.firstOrNull()?.id ?: ""
            true
        }
        if (nombre != null && guardado.get<String?>(ClaveFicha) == id) cerrarFicha()
        return nombre
    }

    fun deshacerEliminacion() {
        val e = ultimoEliminado ?: return
        ultimoEliminado = null
        repo.editar("Empleado restaurado") { st, _ ->
            if (st.emps.any { it.id == e.emp.id }) false
            else { st.emps.add(e.indice.coerceIn(0, st.emps.size), e.emp); true }
        }
    }

    companion object {
        private const val ClaveFicha = "ficha_empleado"

        /** ParseSalario del escritorio: solo los dígitos ("$ 1.750.905" -> 1750905). */
        fun ParseSalario(texto: String?): Double = (texto ?: "").filter { it in '0'..'9' }.toDoubleOrNull() ?: 0.0
    }
}
