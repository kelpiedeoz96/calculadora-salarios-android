package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class PeriodoCalcTest {
    private val hoy = LocalDate.of(2026, 11, 7)
    private fun d(s: String) = LocalDate.parse(s)

    @ParameterizedTest
    @CsvSource("'',2026-11,q1", "2026-13,2026-11,q1", "abc,2026-11,q1", "2026-1,2026-11,q1")
    fun normalizar_MesInvalido_UsaHoy(mes: String, esperadoMes: String, esperadoModo: String) {
        val ui = UiState(month = mes, mode = "q2")
        PeriodoCalc.Normalizar(ui, emptyList(), hoy)
        assertEquals(esperadoMes, ui.month)
        assertEquals(esperadoModo, ui.mode)
    }

    @Test
    fun normalizar_SegundaQuincena_YModoInvalido_YSel() {
        val ui = UiState(month = "", sel = "x")
        PeriodoCalc.Normalizar(ui, listOf(Employee(id = "e1"), Employee(id = "e2")), LocalDate.of(2026, 2, 16))
        assertEquals("2026-02", ui.month); assertEquals("q2", ui.mode); assertEquals("e1", ui.sel)

        val ui2 = UiState(month = "2026-05", mode = "mensual", sel = "e2")
        PeriodoCalc.Normalizar(ui2, listOf(Employee(id = "e1"), Employee(id = "e2")), hoy)
        assertEquals("2026-05", ui2.month); assertEquals("q1", ui2.mode); assertEquals("e2", ui2.sel)
    }

    @Test
    fun periodo_Quincenas_YFebreroBisiesto() {
        assertEquals(d("2026-10-01") to d("2026-10-15"), PeriodoCalc.Periodo(UiState(month = "2026-10", mode = "q1"), hoy))
        assertEquals(d("2026-10-16") to d("2026-10-31"), PeriodoCalc.Periodo(UiState(month = "2026-10", mode = "q2"), hoy))
        assertEquals(d("2028-02-16") to d("2028-02-29"), PeriodoCalc.Periodo(UiState(month = "2028-02", mode = "q2"), hoy))
    }

    @Test
    fun periodo_Personalizado_OrdenaYSiEsInvalidoCaeAQuincena() {
        val ui = UiState(month = "2026-10", mode = "custom", from = "2026-10-20", to = "2026-10-05")
        assertEquals(d("2026-10-05") to d("2026-10-20"), PeriodoCalc.Periodo(ui, hoy))
        ui.to = "2026-02-30"
        assertEquals(d("2026-10-01") to d("2026-10-15"), PeriodoCalc.Periodo(ui, hoy))
        assertEquals("01/10/2026 – 15/10/2026", PeriodoCalc.Etiqueta(ui, hoy))
    }

    @Test
    fun desplazar_CruzaMesesYAnios_YNoMueveElPersonalizado() {
        val ui = UiState(month = "2026-12", mode = "q2")
        assertTrue(PeriodoCalc.Desplazar(ui, 1, hoy)); assertEquals("2027-01" to "q1", ui.month to ui.mode)
        assertTrue(PeriodoCalc.Desplazar(ui, -1, hoy)); assertEquals("2026-12" to "q2", ui.month to ui.mode)
        assertTrue(PeriodoCalc.Desplazar(ui, -1, hoy)); assertEquals("2026-12" to "q1", ui.month to ui.mode)
        val c = UiState(month = "2026-10", mode = "custom", from = "2026-10-01", to = "2026-10-03")
        assertFalse(PeriodoCalc.Desplazar(c, 1, hoy)); assertEquals("2026-10", c.month)
    }

    @Test
    fun aplicar_ValidaComoElEscritorio() {
        val ui = UiState(month = "2026-10", mode = "q1")
        assertEquals("El mes debe ser AAAA-MM, por ejemplo 2026-10.", PeriodoCalc.Aplicar(ui, "10-2026", "q1", "", ""))
        assertEquals("Define un rango válido: AAAA-MM-DD en Desde y Hasta.", PeriodoCalc.Aplicar(ui, "2026-10", "custom", "2026-10-01", "2026-02-30"))
        assertEquals("2026-10" to "q1", ui.month to ui.mode) // un error no cambia nada
        assertNull(PeriodoCalc.Aplicar(ui, " 2026-11 ", "custom", "2026-11-03", "2026-11-09"))
        assertEquals(listOf("2026-11", "custom", "2026-11-03", "2026-11-09"), listOf(ui.month, ui.mode, ui.from, ui.to))
    }
}
