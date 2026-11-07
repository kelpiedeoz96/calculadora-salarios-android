package co.com.calculadoradesalarios.ui

import androidx.lifecycle.SavedStateHandle
import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.engine.JsonConfig
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.ui.empleados.CampoEmpleado
import co.com.calculadoradesalarios.ui.empleados.EmpleadosViewModel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream

class EmpleadosViewModelTest {
    @get:Rule val main = ReglaMain()

    /** Los 6 empleados sintéticos del oráculo, en la quincena 1 de octubre de 2026. */
    private fun almacenOraculo() = AlmacenFalso().apply {
        val st = JsonConfig.json.decodeFromString(AppState.serializer(), javaClass.classLoader!!.getResource("TestData/respaldo_sintetico.json")!!.readText())
        inicial = DatosNomina(st, inicial.ui.copy(month = "2026-10", mode = "q1", sel = st.emps[0].id), emptyList())
    }

    private suspend fun TestScope.crear(almacen: AlmacenFalso = almacenOraculo(), guardado: SavedStateHandle = SavedStateHandle()) =
        repoFalso(almacen).let { it to EmpleadosViewModel(it, guardado, { HoyPrueba }, UnconfinedTestDispatcher(testScheduler)) }

    @Test
    fun filas_UsanElMotor_YCoincidenConElOraculo() = runTest {
        val (_, vm) = crear()
        val e = vm.estado.value
        assertEquals(6, e.filas.size)
        assertEquals("01/10/2026 – 15/10/2026", e.periodo)
        // salida_esperada.json, escenario A, Empleado 1: neto 1.212.624
        assertEquals("$ 1.212.624", e.filas.first { it.nombre == "Empleado 1" }.neto)
        assertTrue(e.hayEmpleados)
    }

    @Test
    fun buscar_FiltraYRecalculaTotales_YSeGuardaEnUi() = runTest {
        val (repo, vm) = crear()
        val totalTodos = vm.estado.value.totales.horas
        vm.buscar("empleado 3")
        assertEquals(listOf("Empleado 3"), vm.estado.value.filas.map { it.nombre })
        assertTrue(totalTodos != vm.estado.value.totales.horas)
        assertEquals("empleado 3", repo.datos.value!!.ui.search)
        vm.buscar("nadie")
        assertTrue(vm.estado.value.filas.isEmpty())
        assertEquals("0:00", vm.estado.value.totales.horas)
    }

    @Test
    fun inclSueldo_CambiaLosTotales() = runTest {
        val (repo, vm) = crear()
        vm.fijarInclSueldo(true)
        assertTrue(vm.estado.value.inclSueldo)
        assertTrue(repo.datos.value!!.ui.incl)
        assertTrue(vm.estado.value.totales.neto != "$ 0")
    }

    @Test
    fun agregar_SeleccionaYAbreFicha_ConMotivoDelEscritorio() = runTest {
        val (repo, vm) = crear()
        vm.agregar()
        val d = repo.datos.value!!
        val nuevo = d.state.emps.last()
        assertEquals("Nuevo empleado", nuevo.name)
        assertEquals(nuevo.id, d.ui.sel)
        assertEquals("Empleado añadido", d.history.first().reason)
        assertEquals(nuevo.id, vm.estado.value.ficha?.id)
    }

    @Test
    fun ficha_ConfirmarCampos_ComoCommitEmpField() = runTest {
        val (repo, vm) = crear()
        val id = repo.datos.value!!.state.emps[0].id
        vm.abrirFicha(id)
        vm.confirmar(id, CampoEmpleado.Nombre, "  Ana Pérez ")
        vm.confirmar(id, CampoEmpleado.Salario, "$ 1.500.000")
        vm.fijarDiaDescanso(id, 0)
        vm.fijarAuxilio(id, false)
        val e = repo.datos.value!!.state.emps[0]
        assertEquals("Ana Pérez", e.name)
        assertEquals(1_500_000.0, e.salario, 0.0)
        assertEquals(0, e.restDay)
        assertFalse(e.aux)
        val f = vm.estado.value.ficha!!
        assertEquals("1500000", f.salarioTexto)
        assertEquals("El salario es inferior al mínimo vigente ($ 1.750.905).", f.avisoSalario)
        vm.confirmar(id, CampoEmpleado.Salario, "")
        assertNull(vm.estado.value.ficha!!.avisoSalario)
        assertEquals("", vm.estado.value.ficha!!.salarioTexto)
    }

    @Test
    fun ficha_SobreviveALaMuerteDelProceso() = runTest {
        val almacen = almacenOraculo()
        val guardado = SavedStateHandle()
        val (repo, vm) = crear(almacen, guardado)
        val id = repo.datos.value!!.state.emps[2].id
        vm.abrirFicha(id)
        // Android vuelve a crear el ViewModel con el mismo SavedStateHandle
        val (_, vm2) = crear(almacen, SavedStateHandle(mapOf("ficha_empleado" to guardado.get<String>("ficha_empleado"))))
        assertEquals(id, vm2.estado.value.ficha?.id)
    }

    @Test
    fun eliminar_YDeshacer_VuelveALaMismaPosicion() = runTest {
        val (repo, vm) = crear()
        val antes = repo.datos.value!!.state.emps.map { it.id }
        val tercero = antes[2]
        vm.abrirFicha(tercero)
        assertEquals("Empleado 3", vm.eliminar(tercero))
        assertEquals(5, vm.estado.value.filas.size)
        assertNull("al borrar se cierra su ficha", vm.estado.value.ficha)
        assertEquals("Empleado eliminado", repo.datos.value!!.history.first().reason)
        vm.deshacerEliminacion()
        assertEquals(antes, repo.datos.value!!.state.emps.map { it.id })
        vm.deshacerEliminacion() // no hay nada más que deshacer
        assertEquals(6, repo.datos.value!!.state.emps.size)
    }

    @Test
    fun eliminarAlSeleccionado_PasaLaSeleccionAlPrimero() = runTest {
        val (repo, vm) = crear()
        val sel = repo.datos.value!!.ui.sel
        vm.eliminar(sel)
        assertEquals(repo.datos.value!!.state.emps[0].id, repo.datos.value!!.ui.sel)
    }

    @Test
    fun sinEmpleados_EstadoVacio() = runTest {
        val almacen = AlmacenFalso().apply { inicial = inicial.copy(state = inicial.state.clone().apply { emps.clear() }) }
        val (_, vm) = crear(almacen)
        assertFalse(vm.estado.value.hayEmpleados)
    }

    @Test
    fun foto_GuardarYQuitar() = runTest {
        val (repo, vm) = crear()
        val id = repo.datos.value!!.state.emps[0].id
        vm.elegirFoto(id, { ByteArrayInputStream(byteArrayOf(9)) }, "jpg")
        assertEquals("foto_${id}_1.jpg", repo.datos.value!!.state.emps[0].photoRelPath)
        vm.quitarFoto(id)
        assertNull(repo.datos.value!!.state.emps[0].photoRelPath)
    }

    @Test
    fun parseSalario_SoloDigitos() {
        assertEquals(1_750_905.0, EmpleadosViewModel.ParseSalario("$ 1.750.905"), 0.0)
        assertEquals(0.0, EmpleadosViewModel.ParseSalario("abc"), 0.0)
        assertNotNull(EmpleadosViewModel.ParseSalario(null))
    }
}
