package co.com.calculadoradesalarios.ui.liquidacion

import androidx.lifecycle.SavedStateHandle
import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.ui.HoyPrueba
import co.com.calculadoradesalarios.ui.comun.EntradaNumerica
import co.com.calculadoradesalarios.ui.repoFalso
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LiquidacionViewModelTest {
    @get:Rule val main = ReglaMain()

    private suspend fun TestScope.crear(almacen: AlmacenFalso = EjemploResuelto.almacen(), guardado: SavedStateHandle = SavedStateHandle()) =
        repoFalso(almacen).let { it to LiquidacionViewModel(it, guardado, { HoyPrueba }, UnconfinedTestDispatcher(testScheduler)) }

    @Test
    fun ejemploResuelto_Seccion25_RecargosNetoSemanaYAuditoria() = runTest {
        val (_, vm) = crear()
        val e = vm.estado.value
        assertEquals("$ 181.249", e.totalRecargos)
        assertEquals("$ 1.111.213", e.nomina.neto)
        assertEquals("$ 875.452", e.nomina.basico)
        assertEquals("$ 124.548", e.nomina.auxilio)
        assertEquals("$ 1.181.249", e.nomina.devengado)
        assertEquals("- $ 35.018", e.nomina.salud)
        assertEquals("Valor hora base: $ 8.338 · salario ÷ 210", e.valorHora)
        assertEquals(listOf("$ 37.520", "$ 5.836", "", "$ 5.836", "$ 5.836", "$ 13.132", "$ 113.089"), e.dias.map { it.monto })
        assertEquals("Descansó", e.dias[2].estado)
        assertTrue(e.dias[0].festivo)

        val s = e.semanas.single()
        assertEquals("12/10 – 18/10", s.rango)
        assertEquals("52:15 / 42:00 · extra 10:15", s.horas)
        assertEquals(615, s.medidor.extraMin)

        val aud = e.auditoria.associate { it.cat to (it.horas to it.total) }
        assertEquals(mapOf(
            Cat.ODiu to ("29:00" to "$ 0"), Cat.ONoc to ("8:00" to "$ 23.344"), Cat.ODom to ("5:00" to "$ 37.520"),
            Cat.HED to ("7:00" to "$ 72.961"), Cat.HEN to ("3:15" to "$ 47.424"),
        ), aud)
        assertEquals("$ 2.918 (solo recargo)", e.auditoria.first { it.cat == Cat.ONoc }.valorHora)
        assertEquals("$ 10.423 (valor completo)", e.auditoria.first { it.cat == Cat.HED }.valorHora)
        assertEquals("40.3%", e.auditoria.first { it.cat == Cat.HED }.porcentaje) // 72.961 / 181.249
        assertEquals(listOf("Más de 2 horas extra el dom 18/10 (9:45)."), e.alertas)
    }

    @Test
    fun horas_PasanPorNormTime_YAceptanPuntoOComa() = runTest {
        val (repo, vm) = crear()
        vm.confirmar("2026-10-14", CampoDia.Entrada1, "8.30")
        vm.confirmar("2026-10-14", CampoDia.Salida1, "1215")
        val r = repo.datos.value!!.state.emps[0].days["2026-10-14"]!!
        assertEquals("08:30" to "12:15", r.in1 to r.out1)
        vm.confirmar("2026-10-14", CampoDia.Salida1, "12,45")
        assertEquals("12:45", repo.datos.value!!.state.emps[0].days["2026-10-14"]!!.out1)
        assertEquals("Edición de datos", repo.datos.value!!.history.first().reason)
    }

    @Test
    fun horaInvalida_MuestraError_YNoCambiaNada() = runTest {
        val (repo, vm) = crear()
        vm.confirmar("2026-10-13", CampoDia.Entrada1, "25:00")
        assertEquals(LiquidacionViewModel.ErrorHora, vm.estado.value.erroresDia[CampoDia.Entrada1])
        assertEquals("12:00", repo.datos.value!!.state.emps[0].days["2026-10-13"]!!.in1)
        vm.confirmar("2026-10-13", CampoDia.Entrada1, "13")
        assertNull(vm.estado.value.erroresDia[CampoDia.Entrada1])
        assertEquals("13:00", repo.datos.value!!.state.emps[0].days["2026-10-13"]!!.in1)
    }

    @Test
    fun descansoEnMinutos_ConComa_DaErrorClaro() = runTest {
        val (repo, vm) = crear()
        vm.confirmar("2026-10-13", CampoDia.Descanso, "1,5")
        assertEquals(EntradaNumerica.ErrorMinutos, vm.estado.value.erroresDia[CampoDia.Descanso])
        assertEquals(0.0, repo.datos.value!!.state.emps[0].days["2026-10-13"]!!.brk, 0.0)
        vm.confirmar("2026-10-13", CampoDia.Descanso, "30")
        assertEquals(30.0, repo.datos.value!!.state.emps[0].days["2026-10-13"]!!.brk, 0.0)
        assertEquals("30", vm.estado.value.dias[1].descansoMin)
    }

    @Test
    fun turnoRapido_DescansoYLimpiarDia_UsanElMotor() = runTest {
        val (repo, vm) = crear()
        vm.aplicarTurno("2026-10-14", "08:00-12:00")
        val e1 = vm.estado.value.dias[2]
        assertEquals("08:00" to "12:00", e1.in1 to e1.out1)
        assertEquals("Trabajó descanso", e1.estado)
        vm.fijarDescanso("2026-10-14", false)
        assertEquals("", vm.estado.value.dias[2].estado)
        vm.limpiarDia("2026-10-14")
        assertNull(repo.datos.value!!.state.emps[0].days["2026-10-14"])
        assertEquals("Descansó", vm.estado.value.dias[2].estado)
    }

    @Test
    fun limpiarPeriodo_BorraSoloElPeriodo_ConMotivoDelEscritorio() = runTest {
        val (repo, vm) = crear()
        repo.editar("Edición de datos") { st, _ -> st.emps[0].days["2026-10-20"] = co.com.calculadoradesalarios.engine.DayRecord(in1 = "08:00", out1 = "12:00"); true }
        assertEquals("¿Borrar horas de Laura Gómez entre 12/10/2026 y 18/10/2026?", vm.textoConfirmarLimpiar())
        vm.limpiarPeriodo()
        assertEquals(setOf("2026-10-20"), repo.datos.value!!.state.emps[0].days.keys)
        assertEquals("Horas borradas", repo.datos.value!!.history.first().reason)
        assertEquals("$ 0", vm.estado.value.totalRecargos)
    }

    @Test
    fun avisosDeDatosFaltantes_SeMuestran() = runTest {
        // Periodo que empieza el jueves 15: la semana del lunes 12 cruza el inicio y el martes 13 queda sin datos.
        val almacen = EjemploResuelto.almacen().apply {
            inicial = inicial.copy(ui = inicial.ui.copy(from = "2026-10-15", to = "2026-10-18"))
            inicial.state.emps[0].days.remove("2026-10-13")
        }
        val (_, vm) = crear(almacen)
        val avisos = vm.estado.value.alertas
        assertTrue(avisos.toString(), avisos.any { it.startsWith("Semana del 12/10: sin datos en 13/10") })
        assertFalse(vm.estado.value.dias.first().enPeriodo)
    }

    @Test
    fun seleccionar_InclSueldo_DiaAbiertoSobreviveAlProceso_YSinEmpleados() = runTest {
        val guardado = SavedStateHandle()
        val (repo, vm) = crear(guardado = guardado)
        vm.fijarInclSueldo(false)
        assertFalse(repo.datos.value!!.ui.incl)
        assertEquals("$ 181.249", vm.estado.value.chips.single().pago)
        vm.abrirDia("2026-10-17")
        val (_, vm2) = crear(guardado = SavedStateHandle(mapOf("dia_abierto" to guardado.get<String>("dia_abierto"))))
        assertEquals("sáb 17/10", vm2.estado.value.diaAbierto?.etiqueta)

        val vacio = AlmacenFalso().apply { inicial = inicial.copy(state = inicial.state.clone().apply { emps.clear() }) }
        val (repo3, vm3) = crear(vacio)
        assertFalse(vm3.estado.value.hayEmpleados)
        vm3.agregarEmpleado()
        assertEquals("Empleado añadido", repo3.datos.value!!.history.first().reason)
        assertTrue(vm3.estado.value.hayEmpleados)
    }

    @Test
    fun formatoNumero_ComoNet() {
        assertEquals("42", LiquidacionViewModel.num(42.0))
        assertEquals("4.5", LiquidacionViewModel.num(4.5))
        assertEquals("3.75", LiquidacionViewModel.num(3.75))
        assertEquals("0.13", LiquidacionViewModel.num(0.125))
    }
}
