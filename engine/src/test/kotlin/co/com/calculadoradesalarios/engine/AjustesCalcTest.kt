package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class AjustesCalcTest {
    @ParameterizedTest
    @CsvSource(
        "42, 42.0",
        "' 7,5 ', 7.5",
        "7.5, 7.5",
        "-3, -3.0",
        "'', 0.0",
        "'   ', 0.0"
    )
    fun tryParseNumero_acepta_validos(texto: String, esperado: Double) {
        val (ok, v) = AjustesCalc.TryParseNumero(texto)
        assertTrue(ok)
        assertEquals(esperado, v, 1e-6)
    }

    @ParameterizedTest
    @ValueSource(strings = ["abc", "NaN", "Infinity", "1,2,3", "12x"])
    fun tryParseNumero_rechaza_invalidos(texto: String) {
        val (ok, _) = AjustesCalc.TryParseNumero(texto)
        assertFalse(ok)
    }

    @Test
    fun tryParseNumero_null_es_cero() {
        val (ok, v) = AjustesCalc.TryParseNumero(null)
        assertTrue(ok)
        assertEquals(0.0, v)
    }

    @Test
    fun formatearNumero_usa_cultura_invariante() {
        assertEquals("7.5", AjustesCalc.FormatearNumero(7.5))
        assertEquals("1750905", AjustesCalc.FormatearNumero(1750905.0))
    }

    @ParameterizedTest
    @CsvSource(
        "6:00, 06:00",
        "06:00, 06:00",
        "600, 06:00",
        "0600, 06:00",
        "21:30, 21:30",
        "23:59, 23:59",
        "' 7:05 ', 07:05"
    )
    fun tryParseHora_normaliza(texto: String, esperado: String) {
        val (ok, h) = AjustesCalc.TryParseHora(texto)
        assertTrue(ok)
        assertEquals(esperado, h)
    }

    @ParameterizedTest
    @CsvSource(
        "''",
        "'24:00'",
        "'12:60'",
        "'abc'",
        "'6'"
    )
    fun tryParseHora_rechaza(texto: String) {
        val (ok, _) = AjustesCalc.TryParseHora(texto)
        assertFalse(ok)
    }

    @Test
    fun tryParseHora_rechaza_null() {
        val (ok, _) = AjustesCalc.TryParseHora(null)
        assertFalse(ok)
    }

    @Test
    fun parseTurnos_filtra_invalidos_y_conserva_orden_y_duplicados() {
        val txt = "06:00-14:00\r\nbasura\n14:00-22:00\n25:00-26:00\n06:00-14:00\n  22:00-06:00  \n12:60-13:00\n6:00-14:00"
        val r = AjustesCalc.ParseTurnos(txt)
        assertEquals(listOf("06:00-14:00", "14:00-22:00", "06:00-14:00", "22:00-06:00"), r)
    }

    @Test
    fun parseTurnos_vacio_da_lista_vacia() {
        assertTrue(AjustesCalc.ParseTurnos(null).isEmpty())
        assertTrue(AjustesCalc.ParseTurnos("  \n ").isEmpty())
    }

    @Test
    fun parseFestivos_ordena_deduplica_y_descarta_fechas_inexistentes() {
        val txt = "2026-12-25\n2026-01-01, 2026-02-30\n2026-12-25\nfoo 2026-07-20 bar\n2026-13-01"
        val r = AjustesCalc.ParseFestivos(txt)
        assertEquals(listOf("2026-01-01", "2026-07-20", "2026-12-25"), r)
    }

    @Test
    fun parseFestivos_vacio() {
        assertTrue(AjustesCalc.ParseFestivos(null).isEmpty())
        assertTrue(AjustesCalc.ParseFestivos("").isEmpty())
    }

    @Test
    fun unirLineas_une_con_salto_y_acepta_null() {
        assertEquals("a\nb", AjustesCalc.UnirLineas(listOf("a", "b")))
        assertEquals("", AjustesCalc.UnirLineas(null))
    }

    @Test
    fun ida_y_vuelta_de_turnos_por_defecto_es_estable() {
        val def = Config().turnos
        val texto = AjustesCalc.UnirLineas(def)
        assertEquals(def, AjustesCalc.ParseTurnos(texto))
    }

    @Test
    fun ida_y_vuelta_de_festivos_por_defecto_es_estable() {
        val def = AppState().festivos
        val texto = AjustesCalc.UnirLineas(def)
        assertEquals(def.sorted(), AjustesCalc.ParseFestivos(texto))
    }
}
