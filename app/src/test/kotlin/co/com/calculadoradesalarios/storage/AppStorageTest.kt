package co.com.calculadoradesalarios.storage

import android.content.Context
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.engine.HistoryEntry
import co.com.calculadoradesalarios.engine.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayInputStream
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
class AppStorageTest {
    private lateinit var context: Context
    private lateinit var appStorage: AppStorage

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        File(context.filesDir.path).deleteRecursively()
        File(context.cacheDir.path).deleteRecursively()
        appStorage = AppStorage(context)
    }

    @Test
    fun load_InitialState_CreatesFreshState() {
        val (state, ui, history) = appStorage.load()
        assertNotNull(state)
        assertEquals(1, state.emps.size)
        assertEquals("Empleado 1", state.emps[0].name)
        assertTrue(history.isEmpty())
    }

    @Test
    fun saveAndLoad_RoundTrip_PreservesDataInFilesDir() {
        val state = AppState().apply {
            emps.clear()
            emps.add(Employee(name = "Carlos Mario", cargo = "Operario", salario = 2000000.0))
        }
        val ui = UiState(tab = "emp", month = "2026-10")
        val history = listOf(HistoryEntry(reason = "Prueba Android"))

        appStorage.save(state, ui, history)

        assertTrue(File(context.filesDir, "data.json").exists())

        val (loadedState, loadedUi, loadedHistory) = appStorage.load()
        assertEquals(1, loadedState.emps.size)
        assertEquals("Carlos Mario", loadedState.emps[0].name)
        assertEquals("emp", loadedUi.tab)
        assertEquals(1, loadedHistory.size)
        assertEquals("Prueba Android", loadedHistory[0].reason)
    }

    @Test
    fun backupAndRestore_Lifecycle() {
        val state = AppState().apply {
            emps.clear()
            emps.add(Employee(name = "Ana Gómez", salario = 1800000.0))
        }
        appStorage.save(state, UiState(), emptyList())

        // Create backup
        val backupResult = appStorage.createManualBackup()
        assertTrue(File(backupResult.ruta).exists())
        assertEquals(1, appStorage.listBackups().size)

        // Modify state
        state.emps[0].name = "Ana Modificada"
        appStorage.save(state, UiState(), emptyList())
        assertEquals("Ana Modificada", appStorage.load().first.emps[0].name)

        // Restore backup from stream
        val backupBytes = File(backupResult.ruta).readBytes()
        appStorage.restoreBackupFromStream(ByteArrayInputStream(backupBytes))

        // State is restored to previous
        val (restoredState, _, _) = appStorage.load()
        assertEquals("Ana Gómez", restoredState.emps[0].name)
    }

    @Test
    fun autoBackup_ConRelojInyectado_RespetaReglaDeUnaHoraSinEsperar() {
        var ahora = LocalDateTime.of(2026, 5, 4, 8, 0, 0)
        fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val storage = AppStorage(context) { ahora }
        val dataJson = File(context.filesDir, "data.json")

        storage.save(AppState(), UiState(), emptyList())
        dataJson.setLastModified(ms(ahora.minusMinutes(1)))
        assertNotNull(storage.checkAndRunAutoBackup())     // primera vez: sí
        assertNull(storage.checkAndRunAutoBackup())        // sin cambios: no

        ahora = ahora.plusMinutes(30)
        dataJson.setLastModified(ms(ahora))                // cambió, pero solo pasaron 30 min
        assertNull(storage.checkAndRunAutoBackup())

        ahora = ahora.plusMinutes(30)                      // 1 h justa desde el último respaldo
        val r = storage.checkAndRunAutoBackup()
        assertNotNull(r)
        assertTrue(r!!.ruta.endsWith("respaldo_20260504_090000.zip"))
        assertEquals(2, storage.listBackups().size)
    }

    @Test
    fun restoreBackupFromStream_ZipInvalido_NoTocaLosDatos() {
        appStorage.save(AppState().apply { emps.add(Employee(name = "Original")) }, UiState(), emptyList())
        val antes = File(context.filesDir, "data.json").readText()
        val zipMalo = java.io.ByteArrayOutputStream().also { bos ->
            java.util.zip.ZipOutputStream(bos).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("data.json")); z.write("{ esto no es json".toByteArray()); z.closeEntry()
            }
        }.toByteArray()

        assertThrows(Exception::class.java) { appStorage.restoreBackupFromStream(ByteArrayInputStream(zipMalo)) }
        assertEquals(antes, File(context.filesDir, "data.json").readText())
        assertTrue(context.cacheDir.listFiles()!!.none { it.name.endsWith(".zip") })
    }

    @Test
    fun employeePhotoAndLogo_StreamHandling() {
        val pngBytes = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0)
        val photoFileName = appStorage.saveEmployeePhotoFromStream("emp101", ByteArrayInputStream(pngBytes), "png")
        assertTrue(photoFileName.startsWith("foto_emp101_"))
        val loadedBytes = appStorage.store.loadEmployeePhotoBytes(photoFileName)
        assertNotNull(loadedBytes)
        assertEquals(pngBytes.size, loadedBytes!!.size)

        val logoFileName = appStorage.saveLogoFromStream(ByteArrayInputStream(pngBytes), "png")
        assertEquals("empresa_logo.png", logoFileName)
        val loadedLogo = appStorage.store.loadLogoBytes(logoFileName)
        assertNotNull(loadedLogo)
        assertEquals(pngBytes.size, loadedLogo!!.size)
    }
}
