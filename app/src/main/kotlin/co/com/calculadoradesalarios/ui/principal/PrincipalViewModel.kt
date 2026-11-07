package co.com.calculadoradesalarios.ui.principal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.com.calculadoradesalarios.datos.NominaRepository
import co.com.calculadoradesalarios.datos.TipoMensaje
import co.com.calculadoradesalarios.engine.PeriodoCalc
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** Pestañas con los mismos ids que ui.tab del escritorio, para que data.json sea intercambiable. */
enum class Pestana(val id: String, val titulo: String) {
    Panel("dashboard", "Panel"),
    Liquidacion("liq", "Liquidación"),
    Empleados("all", "Empleados"),
    Calendario("calendar", "Calendario"),
    Historial("history", "Historial"),
    Ajustes("cfg", "Ajustes");

    /** Las pestañas que usan el periodo muestran el selector en la barra superior. */
    val usaPeriodo: Boolean get() = this == Panel || this == Liquidacion || this == Empleados || this == Calendario

    companion object {
        val Barra = listOf(Panel, Liquidacion, Empleados, Calendario, Historial)
        fun deId(id: String?): Pestana = entries.firstOrNull { it.id == id } ?: Panel
    }
}

data class PrincipalEstado(
    val cargando: Boolean = true,
    val pestana: Pestana = Pestana.Panel,
    val tema: String = "system",
    val etiquetaPeriodo: String = "",
    val mes: String = "",
    val modo: String = "q1",
    val desde: String = "",
    val hasta: String = "",
) {
    val puedeDesplazar: Boolean get() = modo != "custom"
}

class PrincipalViewModel(
    private val repo: NominaRepository,
    private val hoy: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private var anteriorAAjustes: Pestana = Pestana.Panel

    val estado: StateFlow<PrincipalEstado> = repo.datos.map { d ->
        if (d == null) PrincipalEstado() else PrincipalEstado(
            cargando = false,
            pestana = Pestana.deId(d.ui.tab),
            tema = d.ui.themeMode,
            etiquetaPeriodo = PeriodoCalc.Etiqueta(d.ui, hoy()),
            mes = d.ui.month,
            modo = d.ui.mode,
            desde = d.ui.from,
            hasta = d.ui.to,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PrincipalEstado())

    fun navegar(p: Pestana) {
        val actual = estado.value.pestana
        if (actual == p) return
        if (p == Pestana.Ajustes) anteriorAAjustes = actual
        repo.editarUi { it.tab = p.id }
    }

    /** Atrás desde Ajustes vuelve a la pestaña de antes. Devuelve false si no había a dónde volver. */
    fun atras(): Boolean {
        if (estado.value.pestana != Pestana.Ajustes) return false
        repo.editarUi { it.tab = anteriorAAjustes.id }
        return true
    }

    fun desplazarPeriodo(dir: Int) = repo.editarUi { PeriodoCalc.Desplazar(it, dir, hoy()) }

    /** Devuelve el mensaje de error, o null si se aplicó. */
    fun aplicarPeriodo(mes: String, modo: String, desde: String, hasta: String): String? {
        var error: String? = null
        repo.editarUi { error = PeriodoCalc.Aplicar(it, mes, modo, desde, hasta) }
        if (error == null) repo.avisar("Periodo actualizado.", TipoMensaje.Exito)
        return error
    }
}
