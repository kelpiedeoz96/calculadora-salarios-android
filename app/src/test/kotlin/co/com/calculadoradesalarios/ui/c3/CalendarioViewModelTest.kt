package co.com.calculadoradesalarios.ui.c3

import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.ui.HoyPrueba
import co.com.calculadoradesalarios.ui.c3.Oraculo.bool
import co.com.calculadoradesalarios.ui.c3.Oraculo.int
import co.com.calculadoradesalarios.ui.c3.Oraculo.str
import co.com.calculadoradesalarios.ui.calendario.CalendarioViewModel
import co.com.calculadoradesalarios.ui.repoFalso
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarioViewModelTest {
    @get:Rule val main = ReglaMain()

    /**
     * Celdas del mes contra salida_esperada.json para los 5 escenarios y los 6 empleados: horas,
     * dominical/festivo, fuera del periodo y la barra de horas extra (CalendarioCalc).
     */
    @Test
    fun celdas_CoincidenConElOraculo() = runTest {
        for (sc in Oraculo.escenarios) for (emp in Oraculo.empleados(sc)) {
            val nombre = emp.str("nombre")
            val ctx = "${sc.str("escenario")} · $nombre"
            val vm = CalendarioViewModel(repoFalso(Oraculo.almacen(sc, nombre)), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
            val e = vm.estado.value
            val (desde, hasta) = Oraculo.periodo(sc)
            val pDesde = LocalDate.parse(desde)
            val mes = YearMonth.from(pDesde)
            assertEquals(ctx, nombre, e.titulo)
            assertEquals(ctx, mes.toString(), e.mes)
            assertEquals(ctx, (Engine.Weekday(mes.atDay(1)) + 6) % 7, e.relleno)
            assertEquals(ctx, mes.lengthOfMonth(), e.celdas.size)

            val dias = Oraculo.empleados(sc).first { it.str("nombre") == nombre }["dias"]!!.let { it as kotlinx.serialization.json.JsonArray }
                .map { it as kotlinx.serialization.json.JsonObject }.associateBy { it.str("fecha") }
            for (c in e.celdas) {
                val dc = "$ctx ${c.clave}"
                val f = LocalDate.parse(c.clave)
                assertEquals(dc, f < pDesde || f > LocalDate.parse(hasta), c.fuera)
                val d = dias[c.clave]
                if (d == null) {
                    assertEquals(dc, "", c.horas); assertFalse(dc, c.dominical || c.festivo); assertEquals(dc, 0f, c.fraccion)
                    continue
                }
                val dayMin = d.int("dayMin")
                val extra = d.int("extraMin")
                assertEquals(dc, if (dayMin > 0) Engine.FmtHM(dayMin) else "", c.horas)
                assertEquals(dc, d.bool("dominical"), c.dominical)
                assertEquals(dc, d.bool("festivo"), c.festivo)
                assertEquals(dc, extra > 0, c.barraExtra)
                assertEquals(dc, if (extra > 0) extra / 480f else if (dayMin > 0) 1f else 0f, c.fraccion, 1e-6f)
            }
        }
    }

    @Test
    fun hoy_SeMarca_YLaSemanaEmpiezaElLunes() = runTest {
        val vm = CalendarioViewModel(repoFalso(Oraculo.almacen(Oraculo.escenarios[0])), { LocalDate.of(2026, 10, 7) }, UnconfinedTestDispatcher(testScheduler))
        val e = vm.estado.value
        assertEquals(listOf("2026-10-07"), e.celdas.filter { it.hoy }.map { it.clave })
        assertEquals("1/10/2026 es jueves: 3 casillas vacías antes", 3, e.relleno)
    }

    @Test
    fun verDia_DejaSoloEseDia_VaALiquidacion_YAvisa() = runTest {
        val repo = repoFalso(Oraculo.almacen(Oraculo.escenarios[0]))
        val vm = CalendarioViewModel(repo, { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        val mensaje = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { repo.mensajes.first() }
        vm.verDia("2026-10-12")
        val ui = repo.datos.value!!.ui
        assertEquals(listOf("custom", "2026-10-12", "2026-10-12", "liq"), listOf(ui.mode, ui.from, ui.to, ui.tab))
        assertEquals("Mostrando solo 12/10/2026. Usa el selector de periodo para volver a la quincena.", mensaje.await().texto)
        assertEquals("ver un día no deja copia en el historial", 0, repo.datos.value!!.history.size)
    }

    @Test
    fun seleccionar_YSinEmpleados() = runTest {
        val repo = repoFalso(Oraculo.almacen(Oraculo.escenarios[0]))
        val vm = CalendarioViewModel(repo, { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        val otro = vm.estado.value.empleados[3]
        vm.seleccionar(otro.id)
        assertEquals(otro.nombre, vm.estado.value.titulo)
        assertTrue(vm.estado.value.empleados[3].seleccionado)

        val vacio = AlmacenFalso().apply { inicial = inicial.copy(state = inicial.state.clone().apply { emps.clear() }) }
        val vm2 = CalendarioViewModel(repoFalso(vacio), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        assertFalse(vm2.estado.value.hayEmpleado)
        assertEquals("Sin empleados", vm2.estado.value.titulo)
    }
}

