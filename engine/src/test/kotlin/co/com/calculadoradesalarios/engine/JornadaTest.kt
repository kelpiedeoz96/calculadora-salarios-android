package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class JornadaTest {
    @ParameterizedTest
    @CsvSource(
        "8:30, 08:30",
        "0830, 08:30",
        "14, 14:00",
        "25, NULL",
        "8:75, NULL",
        "'', ''",
        "'   ', ''"
    )
    fun normTime_DebeNormalizarCasosSegunEspecificacion(input: String, expected: String?) {
        val exp = if (expected == "NULL") null else expected
        val result = JornadaHelper.NormTime(input)
        assertEquals(exp, result)
    }

    @Test
    fun editDay_DebeCrearRegistroSiNoExiste_YRespetarDescansoPorDefecto() {
        val emp = Employee(name = "Operario 1", restDay = 0)
        val domingo = LocalDate.of(2026, 10, 4)
        JornadaHelper.EditDay(emp, domingo) { rec ->
            rec.in1 = "08:00"
            rec.out1 = "12:00"
        }
        val key = Engine.Key(domingo)
        assertTrue(emp.days.containsKey(key))
        assertEquals("08:00", emp.days[key]?.in1)
        assertEquals("12:00", emp.days[key]?.out1)
        assertEquals(true, emp.days[key]?.desc) // Descanso asignado automáticamente
    }

    @Test
    fun editDay_DebeBorrarRegistroAlQuedarVacio() {
        val emp = Employee(name = "Operario 2", restDay = 3)
        val lunes = LocalDate.of(2026, 10, 5)
        JornadaHelper.EditDay(emp, lunes) { rec ->
            rec.in1 = "09:00"
            rec.out1 = "17:00"
        }
        val key = Engine.Key(lunes)
        assertTrue(emp.days.containsKey(key))
        JornadaHelper.EditDay(emp, lunes) { rec ->
            rec.in1 = ""
            rec.out1 = ""
            rec.in2 = ""
            rec.out2 = ""
            rec.brk = 0.0
            rec.desc = false
        }
        assertFalse(emp.days.containsKey(key))
    }

    @Test
    fun calculateWeekGauge_DebeCalcularProporcionesExactas() {
        val cap = 2520
        val worked = 2880
        val extra = 360
        val gauge = JornadaHelper.CalculateWeekGauge(worked, extra, cap)
        assertEquals(2520, gauge.ordMin)
        assertEquals(360, gauge.extraMin)
        assertEquals(2880.0, (gauge.ordMin + gauge.extraMin).toDouble())
        assertEquals(2520.0 / 2880.0, gauge.ordRatio)
        assertEquals(360.0 / 2880.0, gauge.extraRatio)
        assertEquals(2520.0 / 2880.0, gauge.capRatio)
    }
}
