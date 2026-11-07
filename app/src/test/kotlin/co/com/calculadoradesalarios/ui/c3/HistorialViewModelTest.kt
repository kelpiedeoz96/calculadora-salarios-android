package co.com.calculadoradesalarios.ui.c3

import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.assertMismoJson
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.HistoryEntry
import co.com.calculadoradesalarios.engine.JsonConfig
import co.com.calculadoradesalarios.engine.UiState
import co.com.calculadoradesalarios.ui.HoyPrueba
import co.com.calculadoradesalarios.ui.historial.HistorialViewModel
import co.com.calculadoradesalarios.ui.panel.PanelViewModel
import co.com.calculadoradesalarios.ui.repoFalso
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId

class HistorialViewModelTest {
    @get:Rule val main = ReglaMain()

    private val bogota = ZoneId.of("America/Bogota")
    private val ts = 1_791_000_000_000L // 2026-10-03 04:00 UTC = 2026-10-02 23:00 en Bogotá (UTC-5)

    /** Estado con 1 empleado y una copia en el historial cuyo payload es el respaldo sintético del oráculo. */
    private fun almacen() = AlmacenFalso().apply {
        val payload = JsonConfig.exportJson.encodeToString(AppState.serializer(), Oraculo.estadoSintetico())
        inicial = inicial.copy(
            ui = UiState(month = "2026-10", mode = "q1", sel = "e1"),
            history = listOf(HistoryEntry(ts, "Edición de datos", payload), HistoryEntry(ts - 60_000, "", "{ dañado")),
        )
    }

    private suspend fun TestScope.crear(a: AlmacenFalso = almacen()) =
        repoFalso(a, reloj = { ts + 3_600_000 }).let { it to HistorialViewModel(it, { bogota }, UnconfinedTestDispatcher(testScheduler)) }

    @Test
    fun filas_ConFechaLocal_YMotivoPorDefecto() = runTest {
        val (_, vm) = crear()
        val f = vm.estado.value.filas
        assertEquals(listOf("Edición de datos", "Cambio"), f.map { it.motivo })
        assertEquals("02/10/2026 23:00", f[0].cuando)
    }

    /** Restaurar la copia (payload = respaldo sintético) y comprobar con el Panel las cifras del escenario A. */
    @Test
    fun restaurar_DejaCopiaAntes_YLasCifrasSonLasDelOraculo() = runTest {
        val (repo, vm) = crear()
        vm.restaurar(0)
        val d = repo.datos.value!!
        assertEquals(6, d.state.emps.size)
        assertEquals(listOf("Antes de restaurar", "Edición de datos", ""), d.history.map { it.reason })
        assertEquals("la copia guarda el estado de antes (1 empleado)", 1, JsonConfig.json.decodeFromString(AppState.serializer(), d.history[0].payload).emps.size)
        val panel = PanelViewModel(repo, { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))
        assertEquals("$ 2.804.543", panel.estado.value.recargos) // escenario A de salida_esperada.json
    }

    @Test
    fun restaurarCopiaDañada_Avisa_YNoCambiaNada() = runTest {
        val (repo, vm) = crear()
        val mensaje = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { repo.mensajes.first() }
        vm.restaurar(1)
        assertEquals("No se pudo restaurar.", mensaje.await().texto)
        assertEquals(1, repo.datos.value!!.state.emps.size)
        assertEquals(2, repo.datos.value!!.history.size)
    }

    @Test
    fun descargarRespaldo_EsIgualAlExportJsonDelEscritorio() = runTest {
        val a = AlmacenFalso(DatosNomina(Oraculo.estadoSintetico(), UiState(month = "2026-10"), emptyList()))
        val (_, vm) = crear(a)
        val salida = ByteArrayOutputStream()
        vm.descargarRespaldo { salida }
        // CompletarAnios agrega los festivos de 2027 al cargar, como el escritorio; se comparan con eso en cuenta.
        val esperado = JsonConfig.json.parseToJsonElement(Oraculo.recursoTexto("respaldo_sintetico.exportado_por_escritorio.json"))
        val obtenido = JsonConfig.json.parseToJsonElement(salida.toString(Charsets.UTF_8.name())) as kotlinx.serialization.json.JsonObject
        val sinFestivos = { o: kotlinx.serialization.json.JsonElement -> kotlinx.serialization.json.JsonObject((o as kotlinx.serialization.json.JsonObject) - "festivos") }
        assertMismoJson(sinFestivos(esperado).toString(), sinFestivos(obtenido).toString())
    }

    @Test
    fun cargarRespaldo_PlanoOCompleto_ConCopiaAntes_YRechazaLoInvalido() = runTest {
        val (repo, vm) = crear()
        val mensajes = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { repo.mensajes.take(3).toList() }
        vm.cargarRespaldo { ByteArrayInputStream(Oraculo.textoSintetico().toByteArray()) }
        assertEquals(6, repo.datos.value!!.state.emps.size)
        assertEquals("Antes de cargar respaldo", repo.datos.value!!.history.first().reason)
        vm.cargarRespaldo { ByteArrayInputStream("{\"no\":\"es\"}".toByteArray()) }
        vm.cargarRespaldo { ByteArrayInputStream(Oraculo.recursoTexto("data_formato_actual_v1.json").toByteArray()) } // data.json completo
        assertEquals(listOf("Respaldo cargado: 6 empleados.", "El archivo no es un respaldo válido.", "Respaldo cargado: 6 empleados."), mensajes.await().map { it.texto })
    }

    @Test
    fun borrarTodo_EstadoInicialDelEscritorio_ConCopiaReinicio() = runTest {
        val (repo, vm) = crear()
        vm.borrarTodo()
        val d = repo.datos.value!!
        assertEquals((1..6).map { "Empleado $it" }, d.state.emps.map { it.name })
        assertEquals(3, d.state.emps[3].restDay)
        assertEquals("Reinicio", d.history.first().reason)
        assertEquals(d.state.emps[0].id, d.ui.sel)
    }
}
