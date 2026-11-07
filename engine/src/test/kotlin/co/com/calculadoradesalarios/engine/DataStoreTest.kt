package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.util.UUID

class DataStoreTest {
    private lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        val tmpRoot = System.getProperty("java.io.tmpdir")
        tempDir = File(tmpRoot, "test_nomina_" + UUID.randomUUID().toString().replace("-", ""))
        tempDir.mkdirs()
    }

    @AfterEach
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun loadResourceText(fileName: String): String {
        val stream = javaClass.classLoader.getResourceAsStream("TestData/$fileName")
            ?: File("engine/src/test/resources/TestData/$fileName").inputStream()
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun load_DebeLeerFormatoAnidadoActual_YMigrarDarkAThemeMode() {
        val json = loadResourceText("data_formato_actual_v1.json")
        File(tempDir, "data.json").writeText(json)

        val store = JsonDataStore(tempDir.path)
        val (state, ui, history) = store.load()

        assertEquals(6, state.emps.size)
        assertTrue(state.festivos.size >= 18)
        assertEquals("liq", ui.tab)
        assertTrue(ui.incl)
        assertEquals("dark", ui.themeMode)
        assertNull(ui.dark)
        assertEquals(1, history.size)
        assertEquals("Respaldo inicial", history[0].reason)
    }

    @Test
    fun save_SeguidoDeLoad_DebeMantenerIdaYVueltaExacta() {
        val json = loadResourceText("data_formato_actual_v1.json")
        File(tempDir, "data.json").writeText(json)

        val store = JsonDataStore(tempDir.path)
        val (state1, ui1, hist1) = store.load()
        state1.emps[0].cargo = "Jefe de Cocina"
        ui1.month = "2026-11"
        store.save(state1, ui1, hist1)

        val (state2, ui2, hist2) = store.load()
        assertEquals(6, state2.emps.size)
        assertEquals("Jefe de Cocina", state2.emps[0].cargo)
        assertTrue(state2.emps[0].days.containsKey("2026-10-01"))
        assertEquals(60.0, state2.emps[0].days["2026-10-01"]?.brk)
        assertEquals("2026-11", ui2.month)
        assertEquals(1, hist2.size)
    }

    @Test
    fun archivoCorrupto_DebeCrearCopiaCorrupted_YNoLanzarExcepcion() {
        val dataFile = File(tempDir, "data.json")
        dataFile.writeText("{ json_invalido_totalmente_danado: [")

        val store = JsonDataStore(tempDir.path)
        val (state, _, history) = store.load()

        assertNotNull(state)
        assertEquals(1, state.emps.size)
        assertTrue(history.isEmpty())
        val corruptedFiles = tempDir.listFiles { _, name -> name.startsWith("data.json.corrupted.") } ?: emptyArray()
        assertEquals(1, corruptedFiles.size)
    }

    @Test
    fun escrituraAtomica_FalloAMitad_ConservaArchivoAnteriorIntacto() {
        val initialJson = loadResourceText("data_formato_actual_v1.json")
        val dataFile = File(tempDir, "data.json")
        dataFile.writeText(initialJson)

        val store = JsonDataStore(tempDir.path)
        val (stateOriginal, uiOriginal, histOriginal) = store.load()

        // Modify state in memory (deep copy employees so stateOriginal isn't mutated in-place)
        val stateModified = stateOriginal.copy(
            emps = stateOriginal.emps.map { it.copy() }.toMutableList()
        )
        stateModified.emps[0].cargo = "Cargo Modificado Que No Debe Guardarse"

        // Inject simulated failure during write to temp file before atomic rename
        store.writeInterceptor = { tempFile ->
            assertTrue(tempFile.exists(), "El archivo temporal debe existir antes de renombrar")
            throw java.io.IOException("Fallo de I/O o caída de energía simulada a mitad de escritura")
        }

        assertThrows(java.io.IOException::class.java) {
            store.save(stateModified, uiOriginal, histOriginal)
        }

        // Assert that data.json remains completely untouched and has the initial content
        assertEquals(initialJson, dataFile.readText(), "El archivo data.json original debe quedar intacto")
        val (stateReloaded, _, _) = store.load()
        assertEquals(stateOriginal.emps[0].cargo, stateReloaded.emps[0].cargo)

        // Assert no leftover .tmp files
        val tmpFiles = tempDir.listFiles { _, name -> name.endsWith(".tmp") } ?: emptyArray()
        assertEquals(0, tmpFiles.size, "No deben quedar archivos temporales tras un fallo")

        // Removing the failure interceptor allows atomic save to succeed
        store.writeInterceptor = null
        store.save(stateModified, uiOriginal, histOriginal)
        val (stateAfterSuccess, _, _) = store.load()
        assertEquals("Cargo Modificado Que No Debe Guardarse", stateAfterSuccess.emps[0].cargo)
    }

    @Test
    fun importarRespaldoSinteticoYDataFormatoActualV1_LeeYEscribeIdentico() {
        val store = JsonDataStore(tempDir.path)

        // 1. data_formato_actual_v1.json: read, write back, re-read and assert total equality
        val v1Json = loadResourceText("data_formato_actual_v1.json")
        File(tempDir, "data.json").writeText(v1Json)

        val (stateV1, uiV1, histV1) = store.load()
        assertEquals(6, stateV1.emps.size)
        assertEquals(42.0, stateV1.cfg.weeklyCap)
        assertEquals(1750905.0, stateV1.cfg.smmlv)
        assertEquals(249095.0, stateV1.cfg.auxilio)

        // Save back
        store.save(stateV1, uiV1, histV1)

        // Re-read after save
        val (stateV1After, uiV1After, histV1After) = store.load()
        assertEquals(stateV1, stateV1After, "State debe ser exactamente idéntico tras guardar y recargar")
        assertEquals(uiV1, uiV1After, "UiState debe ser idéntico tras guardar y recargar")
        assertEquals(histV1, histV1After, "Historial debe ser idéntico tras guardar y recargar")

        // 2. respaldo_sintetico.json: import, export, re-import and assert total equality
        val syntheticJson = loadResourceText("respaldo_sintetico.json")
        val syntheticFile = File(tempDir, "respaldo_sintetico.json")
        syntheticFile.writeText(syntheticJson)

        val stateSynthetic = store.importBackup(syntheticFile.path)
        assertEquals(6, stateSynthetic.emps.size)
        assertEquals(2, stateSynthetic.v)
        assertEquals(42.0, stateSynthetic.cfg.weeklyCap)

        // Export and re-decode
        val exportedSynthetic = store.exportJson(stateSynthetic)
        val reimportedSynthetic = JsonConfig.json.decodeFromString(AppState.serializer(), exportedSynthetic)
        assertEquals(stateSynthetic, reimportedSynthetic, "Respaldo sintético debe exportarse e importarse idéntico")

        // Also test loading respaldo_sintetico.json as direct data.json
        val syntheticStoreDir = File(tempDir, "synthetic_store")
        syntheticStoreDir.mkdirs()
        File(syntheticStoreDir, "data.json").writeText(syntheticJson)
        val syntheticStore = JsonDataStore(syntheticStoreDir.path)
        val (stateDirect, uiDirect, histDirect) = syntheticStore.load()
        assertEquals(stateSynthetic, stateDirect)

        syntheticStore.save(stateDirect, uiDirect, histDirect)
        val (stateDirectAfter, _, _) = syntheticStore.load()
        assertEquals(stateSynthetic, stateDirectAfter, "Respaldo sintético guardado y recargado debe ser idéntico")
    }
}
