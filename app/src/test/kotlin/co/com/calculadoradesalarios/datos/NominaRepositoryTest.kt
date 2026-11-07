package co.com.calculadoradesalarios.datos

import co.com.calculadoradesalarios.engine.Employee
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class NominaRepositoryTest {
    private val hoy = LocalDate.of(2026, 11, 7)

    // ---------- guardado fuera del hilo principal ----------

    @Test
    fun guardar_OcurreEnElHiloDeES_NuncaEnElQuePideElCambio() = runBlocking {
        val hiloES = Executors.newSingleThreadExecutor { r -> Thread(r, "es-prueba") }
        val alcance = CoroutineScope(SupervisorJob())
        try {
            val almacen = AlmacenFalso().apply { demoraGuardadoMs = 300 }
            val repo = NominaRepository(almacen, alcance, hiloES.asCoroutineDispatcher(), hoy = { hoy })
            repo.cargar()

            val inicio = System.nanoTime()
            repo.editar("Edición de datos") { st, _ -> st.cfg.nit = "123"; true }
            val msEnPedir = (System.nanoTime() - inicio) / 1_000_000
            assertTrue("pedir el cambio no debe esperar al disco (tardó $msEnPedir ms)", msEnPedir < 150)
            assertEquals("el estado en memoria cambia de inmediato", "123", repo.datos.value!!.state.cfg.nit)

            withTimeout(5_000) { repo.esperarGuardado() }
            assertEquals(listOf("es-prueba"), almacen.hilosDeGuardado.distinct())
            assertNotEquals(Thread.currentThread().name.substringBefore(" @"), almacen.hilosDeGuardado.first())
            assertEquals("123", almacen.ultimoGuardado!!.state.cfg.nit)
        } finally {
            alcance.cancel()
            hiloES.shutdownNow()
        }
    }

    @Test
    fun cambiosSeguidos_MientrasSeGuarda_SoloSeEscribeElUltimo() = runBlocking {
        val hiloES = Executors.newSingleThreadExecutor { r -> Thread(r, "es-prueba") }
        val alcance = CoroutineScope(SupervisorJob())
        try {
            val almacen = AlmacenFalso().apply { demoraGuardadoMs = 200 }
            val repo = NominaRepository(almacen, alcance, hiloES.asCoroutineDispatcher(), hoy = { hoy })
            repo.cargar()
            for (i in 1..20) repo.editarUi { it.search = "b$i" }
            withTimeout(5_000) { repo.esperarGuardado() }
            assertTrue("se escribieron ${almacen.guardados.size} veces", almacen.guardados.size in 1..3)
            assertEquals("b20", almacen.ultimoGuardado!!.ui.search)
        } finally {
            alcance.cancel()
            hiloES.shutdownNow()
        }
    }

    @Test
    fun respaldarSiCorresponde_EsperaElGuardadoYCorreEnElHiloDeES() = runBlocking {
        val hiloES = Executors.newSingleThreadExecutor { r -> Thread(r, "es-prueba") }
        val alcance = CoroutineScope(SupervisorJob())
        try {
            val almacen = AlmacenFalso().apply { demoraGuardadoMs = 200 }
            val repo = NominaRepository(almacen, alcance, hiloES.asCoroutineDispatcher(), hoy = { hoy })
            repo.cargar()
            repo.editar("Edición de datos") { st, _ -> st.cfg.nit = "9"; true }
            withTimeout(5_000) { repo.respaldarSiCorresponde() }
            assertEquals("el respaldo ve los datos ya guardados", 1, almacen.guardados.size)
            assertEquals(listOf("es-prueba"), almacen.hilosDeRespaldo)
            assertEquals(1, repo.infoRespaldos.value.cantidad)
        } finally {
            alcance.cancel()
            hiloES.shutdownNow()
        }
    }

    @Test
    fun errorAlGuardar_SeAvisa_YNoBloqueaLaEspera() = runTest {
        val almacen = AlmacenFalso().apply { fallarAlGuardar = java.io.IOException("disco lleno") }
        val repo = NominaRepository(almacen, backgroundScope, UnconfinedTestDispatcher(testScheduler), hoy = { hoy })
        repo.cargar()
        val mensaje = backgroundScope.async(UnconfinedTestDispatcher(testScheduler)) { repo.mensajes.first() }
        repo.editar("Edición de datos") { st, _ -> st.cfg.nit = "1"; true }
        repo.esperarGuardado()
        assertEquals(Mensaje("Error al guardar datos: disco lleno", TipoMensaje.Error), mensaje.await())
    }

    // ---------- historial de cambios (MainViewModel.Snapshot del escritorio) ----------

    @Test
    fun editar_DejaCopiaEnElHistorial_YFusionaMenosDe2Minutos() = runTest {
        var ahora = 1_790_000_000_000L
        val repo = NominaRepository(AlmacenFalso(), backgroundScope, UnconfinedTestDispatcher(testScheduler), relojMs = { ahora }, hoy = { hoy })
        repo.cargar()
        repo.editar("Edición de datos") { st, _ -> st.cfg.nit = "1"; true }
        ahora += 60_000
        repo.editar("Edición de datos") { st, _ -> st.cfg.nit = "2"; true }
        assertEquals(1, repo.datos.value!!.history.size)
        assertTrue(repo.datos.value!!.history[0].payload.contains("\"nit\": \"2\""))
        ahora += 120_000
        repo.editar("Edición de datos") { st, _ -> st.cfg.nit = "3"; true }
        assertEquals(2, repo.datos.value!!.history.size)
        repo.editar("Empleado añadido") { st, _ -> st.emps.add(Employee(name = "X")); true }
        assertEquals(listOf("Empleado añadido", "Edición de datos", "Edición de datos"), repo.datos.value!!.history.map { it.reason })
    }

    @Test
    fun historial_NoPasaDe25() = runTest {
        var ahora = 1_790_000_000_000L
        val repo = NominaRepository(AlmacenFalso(), backgroundScope, UnconfinedTestDispatcher(testScheduler), relojMs = { ahora }, hoy = { hoy })
        repo.cargar()
        repeat(40) { i -> ahora += 1_000; repo.editar("Cambio $i") { st, _ -> st.cfg.nit = "$i"; true } }
        assertEquals(25, repo.datos.value!!.history.size)
        assertEquals("Cambio 39", repo.datos.value!!.history.first().reason)
    }

    @Test
    fun editarUi_YCambiosQueNoCambianNada_NoVanAlHistorial() = runTest {
        val almacen = AlmacenFalso()
        val repo = NominaRepository(almacen, backgroundScope, UnconfinedTestDispatcher(testScheduler), hoy = { hoy })
        repo.cargar()
        repo.editarUi { it.tab = "all" }
        assertFalse(repo.editar("Edición de datos") { _, _ -> false })
        repo.editarUi { it.tab = "all" } // igual al actual: ni se guarda
        assertEquals(0, repo.datos.value!!.history.size)
        assertEquals(1, almacen.guardados.size)
    }

    @Test
    fun loPublicado_NoSeModificaConCambiosPosteriores() = runTest {
        val repo = NominaRepository(AlmacenFalso(), backgroundScope, UnconfinedTestDispatcher(testScheduler), hoy = { hoy })
        repo.cargar()
        val antes = repo.datos.value!!
        repo.editar("Edición de datos") { st, ui -> st.cfg.nit = "777"; st.emps[0].name = "Otro"; ui.sel = "x"; true }
        assertEquals("", antes.state.cfg.nit)
        assertEquals("Empleado 1", antes.state.emps[0].name)
        assertEquals("e1", antes.ui.sel)
    }

    @Test
    fun cargar_NormalizaPeriodoYCompletaFestivosDeEsteAnioYElSiguiente() = runTest {
        val almacen = AlmacenFalso().apply {
            inicial = inicial.copy(ui = inicial.ui.copy(month = "basura", sel = "no-existe"), state = inicial.state.clone().apply { festivos.clear() })
        }
        val repo = NominaRepository(almacen, backgroundScope, UnconfinedTestDispatcher(testScheduler), hoy = { hoy })
        repo.cargar()
        val d = repo.datos.value!!
        assertEquals("2026-11" to "q1", d.ui.month to d.ui.mode)
        assertEquals("e1", d.ui.sel)
        assertEquals(36, d.state.festivos.size) // 18 de 2026 + 18 de 2027
        assertEquals(0, almacen.guardados.size)  // como el escritorio: cargar no escribe
    }
}

