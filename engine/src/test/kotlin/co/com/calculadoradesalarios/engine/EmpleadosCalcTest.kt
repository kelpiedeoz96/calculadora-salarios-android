package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class EmpleadosCalcTest {
    private fun loadState(fileName: String): AppState {
        val stream = javaClass.classLoader.getResourceAsStream("TestData/$fileName")
            ?: java.io.File("engine/src/test/resources/TestData/$fileName").inputStream()
        val json = stream.bufferedReader().use { it.readText() }
        return JsonConfig.json.decodeFromString(AppState.serializer(), json)
    }

    @ParameterizedTest
    @CsvSource(
        "Empleado 1, 1001, Operario, empleado, true",
        "Empleado 2, 1002, Cajero, 1002, true",
        "Empleado 3, 1003, Administrador, admin, true",
        "Empleado 4, 1004, Cocina, GERENTE, false",
        "Empleado 5, 1005, Auxiliar, '', true",
        "Empleado 6, 1006, Operario, '   ', true"
    )
    fun matches_DebeFiltrarPorNombreCedulaCargo_InsensibleAMayusculasYEspacios(
        nombre: String, cedula: String, cargo: String, query: String, expected: Boolean
    ) {
        val emp = Employee(name = nombre, cedula = cedula, cargo = cargo)
        val result = EmpleadosCalc.Matches(emp, query)
        assertEquals(expected, result)
    }

    @Test
    fun sumCats_DebeSumarDomFestYHorasExtraCorrectamente() {
        val state = loadState("respaldo_sintetico.json")
        val festSet = state.festivos.toSet()
        val emp1 = state.emps[0]
        val from = LocalDate.of(2026, 10, 1)
        val to = LocalDate.of(2026, 10, 15)
        val analysis = Engine.Analyze(emp1, state.cfg, festSet, from, to)

        val domFest = EmpleadosCalc.SumCats(analysis, Cat.ODom, Cat.ONocDom)
        assertEquals(540, domFest)

        val extra = EmpleadosCalc.SumCats(analysis, Cat.HED, Cat.HEN, Cat.HEDDom, Cat.HENDom)
        assertEquals(840, extra)
    }

    @Test
    fun calcularTotales_DebeSumarFilasConYSinSueldo() {
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

        val totSin = EmpleadosCalc.CalcularTotales(summaries, inclSueldo = false)
        assertTrue(totSin.totalMin > 0)
        assertTrue(totSin.recargosAmount > 0)
        assertEquals(0L, totSin.basicoQ)
        assertEquals(0L, totSin.neto)

        val totCon = EmpleadosCalc.CalcularTotales(summaries, inclSueldo = true)
        assertEquals(totSin.totalMin, totCon.totalMin)
        assertEquals(totSin.recargosAmount, totCon.recargosAmount)
        assertTrue(totCon.basicoQ > 0)
        assertTrue(totCon.neto > 0)
        assertTrue(totCon.descuentos > 0)
    }
}
