package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class CalendarioCalcTest {
    private fun loadState(fileName: String): AppState {
        val stream = javaClass.classLoader.getResourceAsStream("TestData/$fileName")
            ?: java.io.File("engine/src/test/resources/TestData/$fileName").inputStream()
        val json = stream.bufferedReader().use { it.readText() }
        return JsonConfig.json.decodeFromString(AppState.serializer(), json)
    }

    @Test
    fun calendarioCalc_Octubre2026_Empleado1_ReflejaElOraculo() {
        val state = loadState("respaldo_sintetico.json")
        val festSet = state.festivos.toSet()
        val emp1 = state.emps[0]
        val from = LocalDate.of(2026, 10, 1)
        val to = LocalDate.of(2026, 10, 15)
        val today = LocalDate.of(2026, 10, 8)
        val analysis = Engine.Analyze(emp1, state.cfg, festSet, from, to)
        val mes = CalendarioCalc.Construir(2026, 10, analysis.rows, from, to, today)

        assertEquals(3, mes.pad0)
        assertEquals(5, mes.semanas)
        assertEquals(31, mes.dias.size)

        val d3 = mes.dias[2]
        assertEquals(3, d3.dia)
        assertTrue(d3.tieneFila)
        assertEquals(540, d3.dayMin)
        assertEquals(180, d3.extraMin)
        assertTrue(d3.barraExtra)
        assertEquals(0.375, d3.fraccion, 1e-6)
        assertFalse(d3.dom)
        assertFalse(d3.festivo)
        assertTrue(d3.enPeriodo)

        val d4 = mes.dias[3]
        assertEquals(240, d4.dayMin)
        assertEquals(240, d4.extraMin)
        assertTrue(d4.barraExtra)
        assertEquals(0.5, d4.fraccion, 1e-6)

        val d12 = mes.dias[11]
        assertTrue(d12.festivo)
        assertTrue(d12.dom)
        assertEquals(540, d12.dayMin)
        assertEquals(0, d12.extraMin)
        assertFalse(d12.barraExtra)
        assertEquals(1.0, d12.fraccion, 1e-6)

        assertTrue(mes.dias[7].esHoy)
        assertFalse(d3.esHoy)
        assertFalse(d12.esHoy)
        assertTrue(mes.dias[14].enPeriodo)
        assertFalse(mes.dias[15].enPeriodo)

        val d31 = mes.dias[30]
        assertFalse(d31.tieneFila)
        assertEquals(0, d31.dayMin)
        assertEquals(0.0, d31.fraccion, 1e-6)
        assertEquals("", d31.turnos)
    }

    @Test
    fun calendarioCalc_MesesDeControl_SinFilas() {
        val emptyRows = emptyList<DayRow>()
        val today = LocalDate.of(2027, 2, 10)
        val feb2027 = CalendarioCalc.Construir(2027, 2, emptyRows, LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28), today)
        assertEquals(0, feb2027.pad0)
        assertEquals(4, feb2027.semanas)
        assertEquals(28, feb2027.dias.size)

        val nov2026 = CalendarioCalc.Construir(2026, 11, emptyRows, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), today)
        assertEquals(6, nov2026.pad0)
        assertEquals(6, nov2026.semanas)
        assertEquals(30, nov2026.dias.size)

        val feb2028 = CalendarioCalc.Construir(2028, 2, emptyRows, LocalDate.of(2028, 2, 1), LocalDate.of(2028, 2, 29), today)
        assertEquals(29, feb2028.dias.size)
    }

    @Test
    fun obtenerTextoTurnos_SoloParesCompletos() {
        val r1 = DayRecord(in1 = "12:00", out1 = "16:00", in2 = "17:00", out2 = "21:00")
        assertEquals("12:00-16:00 / 17:00-21:00", CalendarioCalc.ObtenerTextoTurnos(r1))
        val r2 = DayRecord(in1 = "12:00", out1 = "16:00")
        assertEquals("12:00-16:00", CalendarioCalc.ObtenerTextoTurnos(r2))
        val r3 = DayRecord(in1 = "12:00", out1 = "")
        assertEquals("", CalendarioCalc.ObtenerTextoTurnos(r3))
        assertEquals("", CalendarioCalc.ObtenerTextoTurnos(null))
    }
}
