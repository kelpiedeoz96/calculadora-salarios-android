package co.com.calculadoradesalarios.ui.liquidacion

import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.DayRecord
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.engine.UiState

/**
 * Sección 25 de la especificación: salario 1.750.905, auxilio, descanso fijo el miércoles,
 * configuración por defecto, semana del lunes 12 (festivo) al domingo 18 de octubre de 2026.
 */
object EjemploResuelto {
    fun almacen(incl: Boolean = true): AlmacenFalso {
        val emp = Employee(id = "ej25", name = "Laura Gómez", cargo = "Supervisora", salario = 1_750_905.0, aux = true, restDay = 3)
        mapOf(
            "2026-10-12" to ("11:00" to "16:00"),
            "2026-10-13" to ("12:00" to "21:00"),
            "2026-10-15" to ("12:00" to "21:00"),
            "2026-10-16" to ("12:00" to "21:00"),
            "2026-10-17" to ("11:00" to "21:30"),
            "2026-10-18" to ("12:00" to "21:45"),
        ).forEach { (k, v) -> emp.days[k] = DayRecord(in1 = v.first, out1 = v.second) }
        val st = AppState().apply { emps.add(emp) }
        return AlmacenFalso(DatosNomina(st, UiState(month = "2026-10", mode = "custom", from = "2026-10-12", to = "2026-10-18", sel = "ej25", incl = incl), emptyList()))
    }
}
