package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Reglas de MainViewModel.Snapshot del escritorio: fusión si mismo motivo y < 2 min, máximo 25. */
class HistorialSnapshotTest {
    private val t0 = 1_790_000_000_000L

    private fun snap(h: MutableList<HistoryEntry>, reason: String, ts: Long, payload: String = "p$ts") =
        HistorialCalc.Snapshot(h, reason, ts, payload)

    @Test
    fun mismoMotivo_MenosDe2Minutos_SeFusiona() {
        val h = mutableListOf<HistoryEntry>()
        snap(h, "Edición de datos", t0)
        snap(h, "Edición de datos", t0 + 119_999)
        assertEquals(1, h.size)
        assertEquals(t0 + 119_999, h[0].ts)
        assertEquals("p${t0 + 119_999}", h[0].payload)
    }

    @Test
    fun mismoMotivo_Justo2Minutos_NoSeFusiona() {
        val h = mutableListOf<HistoryEntry>()
        snap(h, "Edición de datos", t0)
        snap(h, "Edición de datos", t0 + 120_000)
        assertEquals(listOf(t0 + 120_000, t0), h.map { it.ts })
    }

    @Test
    fun otroMotivo_NoSeFusiona_YElMasNuevoVaPrimero() {
        val h = mutableListOf<HistoryEntry>()
        snap(h, "Edición de datos", t0)
        snap(h, "Empleado añadido", t0 + 1_000)
        snap(h, "Edición de datos", t0 + 2_000)
        assertEquals(listOf("Edición de datos", "Empleado añadido", "Edición de datos"), h.map { it.reason })
    }

    @Test
    fun soloSeComparaConLaPrimeraEntrada() {
        val h = mutableListOf<HistoryEntry>()
        snap(h, "A", t0)
        snap(h, "B", t0 + 1)
        snap(h, "A", t0 + 2)   // la primera es B: no se fusiona con la A de más abajo
        assertEquals(3, h.size)
    }

    @Test
    fun seConservanSolo25_DescartandoLosMasViejos() {
        val h = mutableListOf<HistoryEntry>()
        for (i in 0 until 30) snap(h, "Edición $i", t0 + i)
        assertEquals(HistorialCalc.MaxEntradas, h.size)
        assertEquals("Edición 29", h.first().reason)
        assertEquals("Edición 5", h.last().reason)
    }
}
