package co.com.calculadoradesalarios.storage

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import co.com.calculadoradesalarios.assertMismoJson
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.datos.NominaRepository
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.engine.HistoryEntry
import co.com.calculadoradesalarios.engine.JsonConfig
import co.com.calculadoradesalarios.engine.UiState
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Application simple: así NominaApp no arranca su propio repositorio durante estas pruebas. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // BitmapFactory real: la validación de imágenes se prueba de verdad
class AppStorageTest {
    private lateinit var context: Context
    private lateinit var storage: AppStorage

    private fun recurso(n: String) = javaClass.classLoader!!.getResource("TestData/$n")!!.readText()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.filesDir.deleteRecursively(); context.filesDir.mkdirs()
        context.cacheDir.deleteRecursively(); context.cacheDir.mkdirs()
        storage = AppStorage(context)
    }

    private fun png(): ByteArray = ByteArrayOutputStream().also {
        Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
    }.toByteArray()

    @Test
    fun sinDatos_EstadoNuevoConUnEmpleado() {
        val d = storage.cargar()
        assertEquals(listOf("Empleado 1"), d.state.emps.map { it.name })
        assertTrue(d.history.isEmpty())
    }

    @Test
    fun guardarYCargar_EnFilesDir() {
        val st = AppState().apply { emps.add(Employee(name = "Carlos Mario", salario = 2_000_000.0)) }
        storage.guardar(DatosNomina(st, UiState(tab = "all", month = "2026-10"), listOf(HistoryEntry(reason = "Prueba"))))
        assertTrue(File(context.filesDir, "data.json").exists())
        val d = storage.cargar()
        assertEquals("Carlos Mario", d.state.emps[0].name)
        assertEquals("all", d.ui.tab)
        assertEquals("Prueba", d.history.single().reason)
    }

    /** Complemento (1): campos del escritorio con valores no por defecto, guardados desde Android. */
    @Test
    fun camposDelEscritorio_ImportadosYGuardadosDesdeAndroid_QuedanIdenticos() = runTest {
        File(context.filesDir, "data.json").writeText(recurso("data_campos_escritorio.json"))
        val repo = NominaRepository(AppStorage(context, CarpetaFalsa()), backgroundScope, UnconfinedTestDispatcher(testScheduler), hoy = { LocalDate.of(2026, 11, 7) })
        repo.cargar()
        repo.editarUi { it.tab = "all" }   // un cambio cualquiera para que Android escriba data.json
        repo.esperarGuardado()

        val escrito = File(context.filesDir, "data.json").readText()
        val ref = JsonConfig.json.parseToJsonElement(recurso("data_campos_escritorio.guardado_por_escritorio.json")) as JsonObject
        val ui = ref["ui"] as JsonObject
        val esperado = JsonObject(ref + ("ui" to JsonObject(ui + ("tab" to JsonPrimitive("all")))))
        assertMismoJson(esperado.toString(), escrito)
        assertTrue(context.filesDir.list()!!.none { it.contains(".corrupted.") })
    }

    @Test
    fun respaldoAutomatico_ConRelojInyectado_RespetaReglaDeUnaHora() {
        var ahora = LocalDateTime.of(2026, 5, 4, 8, 0, 0)
        fun ms(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val s = AppStorage(context) { ahora }
        val dataJson = File(context.filesDir, "data.json")

        s.guardar(s.cargar())
        dataJson.setLastModified(ms(ahora.minusMinutes(1)))
        assertNotNull(s.respaldarSiCorresponde())
        assertNull(s.respaldarSiCorresponde())

        ahora = ahora.plusMinutes(30)
        dataJson.setLastModified(ms(ahora))
        assertNull(s.respaldarSiCorresponde())

        ahora = ahora.plusMinutes(30)
        assertTrue(s.respaldarSiCorresponde()!!.ruta.endsWith("respaldo_20260504_090000.zip"))
        assertEquals(2, s.infoRespaldos().cantidad)
    }

    @Test
    fun respaldo_CopiaALaCarpetaExtra_YUnErrorAhiNoRompeElRespaldo() {
        val extra = CarpetaFalsa().apply { nombre = "Drive" }
        val s = AppStorage(context, extra)
        s.guardar(s.cargar())
        assertEquals("copiado:respaldo_", s.respaldarAhora().rutaExtra!!.take(17))
        extra.fallar = true
        val r = s.respaldarAhora()
        assertTrue(File(r.ruta).exists())
        assertEquals("sin espacio", r.errorExtra)
    }

    @Test
    fun respaldarYRestaurar() {
        storage.guardar(DatosNomina(AppState().apply { emps.add(Employee(name = "Ana Gómez")) }, UiState(), emptyList()))
        val r = storage.respaldarAhora()
        storage.guardar(DatosNomina(AppState().apply { emps.add(Employee(name = "Ana Modificada")) }, UiState(), emptyList()))
        storage.restaurar { File(r.ruta).inputStream() }
        assertEquals("Ana Gómez", storage.cargar().state.emps[0].name)
        assertTrue(context.cacheDir.list()!!.none { it.endsWith(".zip") })
    }

    @Test
    fun restaurarZipInvalido_NoTocaLosDatos() {
        storage.guardar(DatosNomina(AppState().apply { emps.add(Employee(name = "Original")) }, UiState(), emptyList()))
        val antes = File(context.filesDir, "data.json").readText()
        val zipMalo = ByteArrayOutputStream().also { bos ->
            java.util.zip.ZipOutputStream(bos).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("data.json")); z.write("{ esto no es json".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        assertThrows(Exception::class.java) { storage.restaurar { ByteArrayInputStream(zipMalo) } }
        assertEquals(antes, File(context.filesDir, "data.json").readText())
        assertTrue(context.cacheDir.list()!!.none { it.endsWith(".zip") })
    }

    @Test
    fun fotoYLogo_SeValidanYGuardan() {
        val foto = storage.guardarFotoEmpleado("emp101", { ByteArrayInputStream(png()) }, "png")
        assertTrue(foto.startsWith("foto_emp101_"))
        assertEquals(png().size, storage.leerImagen(foto)!!.size)
        assertEquals("empresa_logo.png", storage.guardarLogo({ ByteArrayInputStream(png()) }, ".PNG"))
        assertNull("no sale de filesDir", storage.leerImagen("../data.json"))
    }

    @Test
    fun imagenInvalida_OFormatoNoPermitido_SeRechaza() {
        val e = assertThrows(IllegalArgumentException::class.java) {
            storage.guardarFotoEmpleado("e1", { ByteArrayInputStream("no es imagen".toByteArray()) }, "png")
        }
        assertEquals("El archivo no es una imagen válida.", e.message)
        assertThrows(IllegalStateException::class.java) { storage.guardarFotoEmpleado("e1", { ByteArrayInputStream(png()) }, "gif") }
        assertTrue(context.cacheDir.list()!!.isEmpty())
    }

    @Test
    fun podarCarpetaExtra_MismasReglasQueElEscritorio() {
        val nombres = (1..23).map { "x$it" to "respaldo_202601%02d_000000.zip".format(it) }
        val borrar = CarpetaExtraSaf.ParaBorrar(nombres.shuffled(), 20)
        assertEquals(listOf("respaldo_20260103_000000.zip", "respaldo_20260102_000000.zip", "respaldo_20260101_000000.zip"), borrar.map { it.second })
    }
}

private class CarpetaFalsa : CarpetaExtra {
    var nombre: String? = null
    var fallar = false
    override fun nombre() = nombre
    override fun fijar(uri: String?) { nombre = uri }
    override fun copiar(zip: File, conservar: Int): String {
        if (fallar) throw java.io.IOException("sin espacio")
        return "copiado:${zip.name}"
    }
}
