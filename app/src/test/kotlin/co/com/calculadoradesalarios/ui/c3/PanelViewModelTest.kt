package co.com.calculadoradesalarios.ui.c3

import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.Fmt
import co.com.calculadoradesalarios.ui.HoyPrueba
import co.com.calculadoradesalarios.ui.c3.Oraculo.int
import co.com.calculadoradesalarios.ui.c3.Oraculo.long
import co.com.calculadoradesalarios.ui.c3.Oraculo.str
import co.com.calculadoradesalarios.ui.panel.PanelViewModel
import co.com.calculadoradesalarios.ui.repoFalso
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class PanelViewModelTest {
    @get:Rule val main = ReglaMain()

    /** Lo que muestra el Panel, recalculado a mano desde salida_esperada.json, en los 5 escenarios. */
    @Test
    fun panel_CoincideConElOraculo_EnLos5Escenarios() = runTest {
        for (sc in Oraculo.escenarios) {
            val ctx = sc.str("escenario")
            val vm = PanelViewModel(repoFalso(Oraculo.almacen(sc)), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
            val e = vm.estado.value
            val emps = Oraculo.empleados(sc)
            fun minCat(emp: kotlinx.serialization.json.JsonObject, c: Cat) = emp["totales"]!!.jsonObject[c.name]!!.jsonObject.int("min")
            val totalMin = emps.sumOf { it.int("totalMin") }
            val total = emps.sumOf { it.long("totalRecargos") }
            val extra = emps.sumOf { e2 -> listOf(Cat.HED, Cat.HEN, Cat.HEDDom, Cat.HENDom).sumOf { minCat(e2, it) } }
            val porCat = Cat.entries.associateWith { c -> emps.sumOf { minCat(it, c) } }
            val maxCat = maxOf(1, porCat.values.max())

            assertEquals(ctx, "6", e.empleados)
            assertEquals(ctx, Engine.FmtHM(totalMin), e.horasTotales)
            assertEquals(ctx, Fmt.Money(total), e.recargos)
            assertEquals(ctx, Engine.FmtHM(extra), e.horasExtra)
            assertEquals(ctx, if (totalMin > 0) String.format(Locale.US, "%.1f%% del total", extra * 100.0 / totalMin) else "—", e.horasExtraPct)
            assertEquals(ctx, Cat.entries.filter { porCat.getValue(it) > 0 }, e.categorias.map { it.cat })
            e.categorias.forEach { b ->
                assertEquals("$ctx ${b.cat}", Engine.FmtHM(porCat.getValue(b.cat)), b.horas)
                assertEquals("$ctx ${b.cat}", porCat.getValue(b.cat).toFloat() / maxCat, b.fraccion, 1e-6f)
            }
            val top = emps.sortedByDescending { it.long("totalRecargos") }.take(5)
            assertEquals(ctx, top.map { it.str("nombre") }, e.top.map { it.nombre })
            assertEquals(ctx, top.map { "${Engine.FmtHM(it.int("totalMin"))} · ${Fmt.Money(it.long("totalRecargos"))}" }, e.top.map { it.detalle })
            val avisos = emps.flatMap { emp -> emp["alertas"]!!.jsonArray.map { "${emp.str("nombre")}: ${it.jsonPrimitive.content}" } }.take(6)
            assertEquals(ctx, avisos, e.alertas)
            assertEquals(ctx, Engine.FmtHM(totalMin - extra), e.ordinarias)
            assertEquals(ctx, Engine.FmtHM(porCat.getValue(Cat.ONoc) + porCat.getValue(Cat.ONocDom)), e.nocturnas)
            assertEquals(ctx, Engine.FmtHM(porCat.getValue(Cat.ODom) + porCat.getValue(Cat.ONocDom)), e.dominicales)
            assertEquals(ctx, emps.count { e2 -> listOf(Cat.HED, Cat.HEN, Cat.HEDDom, Cat.HENDom).sumOf { minCat(e2, it) } > 0 }.toString(), e.conExtra)
            assertEquals(ctx, Fmt.Money(Engine.JsRound(total.toDouble() / 6).toLong()), e.promedio)
            assertEquals(ctx, Fmt.Money(total), e.totalPagar)
        }
    }

    /** Las mismas cifras literales que PanelCalcTest (escenario A). */
    @Test
    fun escenarioA_ValoresLiterales() = runTest {
        val vm = PanelViewModel(repoFalso(Oraculo.almacen(Oraculo.escenarios[0])), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        val e = vm.estado.value
        assertEquals(listOf("498:54", "$ 2.804.543", "120:14", "24.1% del total", "5", "$ 467.424"),
            listOf(e.horasTotales, e.recargos, e.horasExtra, e.horasExtraPct, e.conExtra, e.promedio))
        assertEquals("$ 1.270.267", e.top.first().detalle.substringAfter(" · "))
        assertEquals(6, e.alertas.size) // el escritorio muestra solo las 6 primeras de 22
        assertEquals(true, e.alertas[0].startsWith("Empleado 1: Más de 2 horas extra el sáb 03/10"))
    }

    @Test
    fun abrirEmpleado_SeleccionaYVaALiquidacion_YSinEmpleados() = runTest {
        val repo = repoFalso(Oraculo.almacen(Oraculo.escenarios[0]))
        val vm = PanelViewModel(repo, { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        val id = vm.estado.value.top.first().id
        vm.abrirEmpleado(id)
        assertEquals(id to "liq", repo.datos.value!!.ui.sel to repo.datos.value!!.ui.tab)

        val vacio = AlmacenFalso().apply { inicial = inicial.copy(state = inicial.state.clone().apply { emps.clear() }) }
        val repo2 = repoFalso(vacio)
        val vm2 = PanelViewModel(repo2, { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        assertEquals("0", vm2.estado.value.empleados)
        vm2.irAEmpleados()
        assertEquals("all", repo2.datos.value!!.ui.tab)
    }
}
