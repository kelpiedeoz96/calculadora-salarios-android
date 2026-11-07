package co.com.calculadoradesalarios.ui

import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.ui.ajustes.AjustesViewModel
import co.com.calculadoradesalarios.ui.ajustes.CampoAjuste
import co.com.calculadoradesalarios.ui.comun.EntradaNumerica
import co.com.calculadoradesalarios.ui.comun.EntradaNumerica.Resultado.Invalido
import co.com.calculadoradesalarios.ui.comun.EntradaNumerica.Resultado.Valido
import co.com.calculadoradesalarios.ui.empleados.CampoEmpleado
import co.com.calculadoradesalarios.ui.empleados.EmpleadosViewModel
import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

/** Coma decimal en la interfaz; el motor sigue con cultura invariante como el escritorio. */
class EntradaNumericaTest {
    @get:Rule val main = ReglaMain()

    @Test
    fun decimal_ComaAPunto_YErroresClaros() {
        assertEquals(Valido("4.5"), EntradaNumerica.decimal(" 4,5 "))
        assertEquals(Valido("4.5"), EntradaNumerica.decimal("4.5"))
        assertEquals(Valido("42"), EntradaNumerica.decimal("42"))
        assertEquals(Invalido(EntradaNumerica.ErrorDosSeparadores), EntradaNumerica.decimal("1.800,5"))
        assertEquals(Invalido(EntradaNumerica.ErrorMiles), EntradaNumerica.decimal("1.800.000"))
        assertEquals(Invalido(EntradaNumerica.ErrorMiles), EntradaNumerica.decimal("1,800,000"))
    }

    @Test
    fun salarioMinutosYHora() {
        assertEquals(Valido("1.750.905"), EntradaNumerica.salario("1.750.905"))
        assertEquals(Invalido(EntradaNumerica.ErrorSalario), EntradaNumerica.salario("1.750.905,50"))
        assertEquals(Invalido(EntradaNumerica.ErrorMinutos), EntradaNumerica.minutos("1,5"))
        assertEquals(Valido("30"), EntradaNumerica.minutos("30"))
        assertEquals("8:30", EntradaNumerica.hora(" 8.30 "))
        assertEquals("8:30", EntradaNumerica.hora("8,30"))
    }

    @Test
    fun ajustes_AceptaComaDecimal() = runTest {
        val repo = repoFalso()
        val vm = AjustesViewModel(repo, { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        vm.confirmar(CampoAjuste.SaludPct, "4,5")
        assertNull(vm.estado.value.errores[CampoAjuste.SaludPct])
        assertEquals(4.5, repo.datos.value!!.state.cfg.saludPct, 0.0)
        vm.confirmar(CampoAjuste.Smmlv, "1.800.000")
        assertEquals(EntradaNumerica.ErrorMiles, vm.estado.value.errores[CampoAjuste.Smmlv])
        assertEquals(1_750_905.0, repo.datos.value!!.state.cfg.smmlv, 0.0)
    }

    @Test
    fun empleados_SalarioConComa_DaError_YNoMultiplicaElValor() = runTest {
        val repo = repoFalso()
        val vm = EmpleadosViewModel(repo, SavedStateHandle(), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        vm.abrirFicha("e1")
        vm.confirmar("e1", CampoEmpleado.Salario, "1.750.905,50")
        assertEquals(EntradaNumerica.ErrorSalario, vm.estado.value.ficha!!.errores[CampoEmpleado.Salario])
        assertEquals(1_750_905.0, repo.datos.value!!.state.emps[0].salario, 0.0)
        vm.confirmar("e1", CampoEmpleado.Salario, "1.900.000")
        assertNull(vm.estado.value.ficha!!.errores[CampoEmpleado.Salario])
        assertEquals(1_900_000.0, repo.datos.value!!.state.emps[0].salario, 0.0)
    }
}
