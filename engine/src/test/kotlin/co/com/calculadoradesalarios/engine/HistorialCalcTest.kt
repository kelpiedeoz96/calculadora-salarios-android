package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate
import java.time.ZoneOffset

class HistorialCalcTest {
    private fun loadState(fileName: String): AppState {
        val stream = javaClass.classLoader.getResourceAsStream("TestData/$fileName")
            ?: java.io.File("engine/src/test/resources/TestData/$fileName").inputStream()
        val json = stream.bufferedReader().use { it.readText() }
        return JsonConfig.json.decodeFromString(AppState.serializer(), json)
    }

    @Test
    fun parsePayload_IdaYVuelta_ConservaEmpleadosYResultadosDelOraculo() {
        val state = loadState("respaldo_sintetico.json")
        val payload = JsonConfig.exportJson.encodeToString(AppState.serializer(), state)
        val restaurado = HistorialCalc.ParsePayload(payload)

        assertNotNull(restaurado)
        assertEquals(6, restaurado!!.emps.size)
        assertEquals(state.emps.map { it.name }, restaurado.emps.map { it.name })

        val from = LocalDate.of(2026, 10, 1)
        val to = LocalDate.of(2026, 10, 15)
        val a1 = Engine.Analyze(state.emps[0], state.cfg, state.festivos.toSet(), from, to)
        val a2 = Engine.Analyze(restaurado.emps[0], restaurado.cfg, restaurado.festivos.toSet(), from, to)

        assertEquals(282660L, a1.totalAmount)
        assertEquals(6420, a1.totalMin)
        assertEquals(a1.totalAmount, a2.totalAmount)
        assertEquals(a1.totalMin, a2.totalMin)
    }

    @ParameterizedTest
    @CsvSource(
        "NULL",
        "''",
        "'   '",
        "'{no es json'",
        "'[1,2'",
        "'12345'"
    )
    fun parsePayload_Invalido_DevuelveNullSinLanzar(payload: String) {
        val p = if (payload == "NULL") null else payload
        assertNull(HistorialCalc.ParsePayload(p))
    }

    @Test
    fun estadoInicial_TieneSeisEmpleadosYElCuartoDescansaElMiercoles() {
        val st = HistorialCalc.EstadoInicial()
        assertEquals(6, st.emps.size)
        for (i in 0 until 6) {
            assertEquals("Empleado ${i + 1}", st.emps[i].name)
        }
        assertEquals(3, st.emps[3].restDay)
        assertNull(st.emps[0].restDay)
        assertTrue(st.festivos.isNotEmpty())
    }

    @Test
    fun formatearFecha_UsaFormatoInvariantEnLaZonaIndicada() {
        assertEquals("30/10/2026 20:35", HistorialCalc.FormatearFecha(1793392500000L, ZoneOffset.UTC))
    }

    @ParameterizedTest
    @CsvSource(
        "10, q1",
        "15, q1",
        "16, q2",
        "20, q2"
    )
    fun normalizar_MesVacio_FijaMesYQuincenaDeHoy(dia: Int, modoEsperado: String) {
        val st = HistorialCalc.EstadoInicial()
        val ui = UiState(month = "", mode = "")
        HistorialCalc.Normalizar(st, ui, LocalDate.of(2026, 10, dia))
        assertEquals("2026-10", ui.month)
        assertEquals(modoEsperado, ui.mode)
    }

    @Test
    fun normalizar_MesYaDefinido_NoLoCambia() {
        val st = HistorialCalc.EstadoInicial()
        val ui = UiState(month = "2026-03", mode = "q2")
        HistorialCalc.Normalizar(st, ui, LocalDate.of(2026, 10, 10))
        assertEquals("2026-03", ui.month)
        assertEquals("q2", ui.mode)
    }

    @Test
    fun normalizar_SelInexistente_PasaAlPrimerEmpleado() {
        val st = HistorialCalc.EstadoInicial()
        val ui = UiState(month = "2026-10", mode = "q1", sel = "no_existe")
        HistorialCalc.Normalizar(st, ui, LocalDate.of(2026, 10, 1))
        assertEquals(st.emps[0].id, ui.sel)
    }

    @Test
    fun normalizar_SelValido_NoCambia() {
        val st = HistorialCalc.EstadoInicial()
        val ui = UiState(month = "2026-10", mode = "q1", sel = st.emps[2].id)
        HistorialCalc.Normalizar(st, ui, LocalDate.of(2026, 10, 1))
        assertEquals(st.emps[2].id, ui.sel)
    }

    @Test
    fun normalizar_SinEmpleados_DejaSelVacio() {
        val st = AppState()
        val ui = UiState(month = "2026-10", mode = "q1", sel = "x")
        HistorialCalc.Normalizar(st, ui, LocalDate.of(2026, 10, 1))
        assertEquals("", ui.sel)
    }
}
