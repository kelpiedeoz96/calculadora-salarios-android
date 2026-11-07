package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FestivosCalcTest {
    @Test
    fun domingoPascua_FechasConocidas() {
        assertEquals(LocalDate.of(2025, 4, 20), FestivosCalc.DomingoPascua(2025))
        assertEquals(LocalDate.of(2026, 4, 5), FestivosCalc.DomingoPascua(2026))
        assertEquals(LocalDate.of(2027, 3, 28), FestivosCalc.DomingoPascua(2027))
        assertEquals(LocalDate.of(2028, 4, 16), FestivosCalc.DomingoPascua(2028))
    }

    @Test
    fun paraAnio_2026_CoincideConLosFestivosPrecargadosDeLaApp() {
        val precargados = AppState().festivos.sorted()
        assertEquals(precargados, FestivosCalc.ParaAnio(2026))
    }

    @Test
    fun paraAnio_2027_TieneLosDieciochoFestivosEsperados() {
        val esperados = listOf(
            "2027-01-01", "2027-01-11", "2027-03-22", "2027-03-25", "2027-03-26", "2027-05-01",
            "2027-05-10", "2027-05-31", "2027-06-07", "2027-07-05", "2027-07-20", "2027-08-07",
            "2027-08-16", "2027-10-18", "2027-11-01", "2027-11-15", "2027-12-08", "2027-12-25"
        )
        assertEquals(esperados, FestivosCalc.ParaAnio(2027))
    }

    @Test
    fun paraAnio_2025_UnificaLosQueCaenElMismoLunes() {
        val f = FestivosCalc.ParaAnio(2025)
        assertEquals(17, f.size)
        assertEquals(1, f.count { it == "2025-06-30" })
    }

    @Test
    fun agregarAnio_NoBorraLoExistente_Ordena_YEsIdempotente() {
        val lista = mutableListOf("2026-12-25", "2026-01-01", "2030-05-05")
        val primera = FestivosCalc.AgregarAnio(lista, 2027)
        assertEquals(18, primera)
        assertTrue(lista.contains("2030-05-05"))
        assertTrue(lista.contains("2026-12-25"))
        assertEquals(lista.sorted(), lista)
        assertEquals(0, FestivosCalc.AgregarAnio(lista, 2027))
    }

    @Test
    fun completarAnios_SoloAgregaAniosQueNoTienenNingunFestivo() {
        val lista = AppState().festivos.toMutableList() // solo 2026
        val agregados = FestivosCalc.CompletarAnios(lista, listOf(2026, 2027))
        assertEquals(18, agregados)
        assertEquals(36, lista.size)
    }

    @Test
    fun completarAnios_RespetaUnFestivoQueElUsuarioQuito() {
        val lista = AppState().festivos.toMutableList()
        lista.remove("2026-10-12") // el usuario lo quitó a propósito
        FestivosCalc.CompletarAnios(lista, listOf(2026))
        assertFalse(lista.contains("2026-10-12"))
        assertEquals(17, lista.size)
    }
}
