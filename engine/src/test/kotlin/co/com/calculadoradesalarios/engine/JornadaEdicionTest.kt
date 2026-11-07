package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** Edición del registro de jornada que en el escritorio vivía en LiquidacionViewModel. */
class JornadaEdicionTest {
    private val mar13 = LocalDate.of(2026, 10, 13)
    private fun emp() = Employee(id = "e1", restDay = 3)

    @Test
    fun aplicarTurno_PoneTurno1YBorraTurno2_ComoElEscritorio() {
        val e = emp()
        JornadaHelper.EditDay(e, mar13) { it.in2 = "18:00"; it.out2 = "20:00" }
        assertTrue(JornadaHelper.AplicarTurno(e, mar13, "12:00-21:00"))
        val r = e.days["2026-10-13"]!!
        assertEquals(listOf("12:00", "21:00", "", ""), listOf(r.in1, r.out1, r.in2, r.out2))
        assertTrue(JornadaHelper.AplicarTurno(e, mar13, "08:00 a 12:00"))
        assertEquals("08:00", e.days["2026-10-13"]!!.in1)
        assertFalse(JornadaHelper.AplicarTurno(e, mar13, "basura"))
    }

    @Test
    fun parseDescanso_SoloDigitos() {
        assertEquals(30.0, JornadaHelper.ParseDescanso("30"))
        assertEquals(30.0, JornadaHelper.ParseDescanso(" 30 min"))
        assertEquals(0.0, JornadaHelper.ParseDescanso(""))
        assertEquals(0.0, JornadaHelper.ParseDescanso(null))
        assertEquals(15.0, JornadaHelper.ParseDescanso("1,5")) // así lo hace el escritorio; la interfaz lo evita antes
    }

    @Test
    fun editarCelda_DetectaCambios_YUnDiaVacioSeBorra() {
        val e = emp()
        assertTrue(JornadaHelper.EditarCelda(e, mar13, "in1", "12:00"))
        assertFalse(JornadaHelper.EditarCelda(e, mar13, "in1", "12:00"))
        assertTrue(JornadaHelper.EditarCelda(e, mar13, "brk", "30"))
        assertEquals(30.0, e.days["2026-10-13"]!!.brk)
        JornadaHelper.EditarCelda(e, mar13, "in1", "")
        JornadaHelper.EditarCelda(e, mar13, "brk", "")
        assertNull(e.days["2026-10-13"], "sin datos y con el descanso por defecto, el registro desaparece")
    }

    @Test
    fun limpiarDia_YLimpiarPeriodo() {
        val e = emp()
        for (d in 10..20) JornadaHelper.AplicarTurno(e, LocalDate.of(2026, 10, d), "08:00-12:00")
        assertTrue(JornadaHelper.LimpiarDia(e, mar13))
        assertNull(e.days["2026-10-13"])
        assertFalse(JornadaHelper.LimpiarDia(e, mar13))
        assertEquals(4, JornadaHelper.LimpiarPeriodo(e, LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 16)))
        assertEquals(listOf("2026-10-10", "2026-10-11", "2026-10-17", "2026-10-18", "2026-10-19", "2026-10-20"), e.days.keys.sorted())
    }
}
