package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PanelCalcTest {
    private fun loadState(fileName: String): AppState {
        val stream = javaClass.classLoader.getResourceAsStream("TestData/$fileName")
            ?: java.io.File("engine/src/test/resources/TestData/$fileName").inputStream()
        val json = stream.bufferedReader().use { it.readText() }
        return JsonConfig.json.decodeFromString(AppState.serializer(), json)
    }

    private fun Fake(name: String, amount: Long, min: Int = 60): EmpSummary {
        val a = Analysis(totalAmount = amount, totalMin = min)
        return EmpSummary(Employee(name = name), a, Payroll(), amount)
    }

    @Test
    fun panelCalc_OraculoEscenarioA_KpisDistribucionYTop5() {
        val state = loadState("respaldo_sintetico.json")
        val festSet = state.festivos.toSet()
        val from = LocalDate.of(2026, 10, 1)
        val to = LocalDate.of(2026, 10, 15)
        val summaries = mutableListOf<EmpSummary>()

        for (emp in state.emps) {
            val a = Engine.Analyze(emp, state.cfg, festSet, from, to)
            val p = Engine.GetPayroll(emp, state.cfg, a.totalAmount)
            summaries.add(EmpSummary(emp, a, p, a.totalAmount))
        }

        val res = PanelCalc.Calcular(summaries)
        assertEquals(6, res.empleadosCount)
        assertEquals(29934, res.totalMin)
        assertEquals(2804543L, res.totalAmount)
        assertEquals(7214, res.extraMin)
        assertEquals(5, res.conExtra)
        assertEquals(467424L, res.promedio)
        assertEquals(3614, res.nightMin)
        assertEquals(2130, res.domMin)
        assertArrayEquals(intArrayOf(17306, 3284, 1800, 330, 5474, 1740, 0, 0), res.minutosPorCategoria)
        assertEquals(17306, res.maxCategoria)
        assertEquals(
            listOf("Empleado 2", "Empleado 3", "Empleado 1", "Empleado 4", "Empleado 6"),
            res.top5.map { it.emp.name }
        )
        assertEquals(1270267L, res.top5[0].r.totalAmount)
        assertEquals(22, res.alertas.size)
        assertTrue(res.alertas.all { it.startsWith("Empleado ") })
        assertTrue(res.alertas[0].startsWith("Empleado 1: Más de 2 horas extra el sáb 03/10"))
        assertEquals("24.1% del total", res.pctExtraTexto)
    }

    @Test
    fun panelCalc_SinEmpleados_DevuelveCerosYGuion() {
        val res = PanelCalc.Calcular(emptyList())
        assertEquals(0, res.empleadosCount)
        assertEquals(0, res.totalMin)
        assertEquals(0L, res.totalAmount)
        assertEquals(0L, res.promedio)
        assertEquals(0, res.conExtra)
        assertEquals("—", res.pctExtraTexto)
        assertTrue(res.top5.isEmpty())
        assertTrue(res.alertas.isEmpty())
        assertEquals(1, res.maxCategoria)
    }

    @Test
    fun panelCalc_Empate_ConservaElOrdenOriginalYLimitaACinco() {
        val list = listOf(
            Fake("A", 100), Fake("B", 100), Fake("C", 500), Fake("D", 100),
            Fake("E", 100), Fake("F", 100), Fake("G", 100)
        )
        val res = PanelCalc.Calcular(list)
        assertEquals(listOf("C", "A", "B", "D", "E"), res.top5.map { it.emp.name })
    }
}
