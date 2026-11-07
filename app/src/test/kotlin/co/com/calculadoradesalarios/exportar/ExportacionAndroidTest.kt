package co.com.calculadoradesalarios.exportar

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.engine.exportar.ArchivoExportado
import co.com.calculadoradesalarios.engine.exportar.ExportadorNomina
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ExportacionAndroidTest {
    private val context get() = RuntimeEnvironment.getApplication()

    /**
     * FileProvider guarda en una variable estática las carpetas de cada autoridad; Robolectric usa una
     * carpeta de datos distinta en cada prueba, así que se limpia antes (en el teléfono no pasa).
     */
    @org.junit.Before
    fun limpiarCacheDeFileProvider() {
        val campo = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache")
        campo.isAccessible = true
        @Suppress("UNCHECKED_CAST") (campo.get(null) as MutableMap<Any, Any>).clear()
    }

    /** Logo con transparencia hecho con la API de Android: círculo opaco y franja al 50 %. */
    private fun pngTransparente(ancho: Int = 120, alto: Int = 80): ByteArray {
        val bmp = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawCircle(60f, 40f, 30f, Paint().apply { color = Color.rgb(14, 90, 97) })
        c.drawRect(0f, 70f, 120f, 80f, Paint().apply { color = Color.argb(128, 230, 120, 30) })
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test
    fun decodificadorAndroid_ConservaTransparencia_JpgOpaco_EInvalido() {
        val img = DecodificadorAndroid.decodificar(pngTransparente())!!
        assertEquals(120 to 80, img.ancho to img.alto)
        val alfa = img.alfa!!
        assertEquals(0, alfa[0].toInt() and 0xFF)
        assertEquals(255, alfa[40 * 120 + 60].toInt() and 0xFF)
        assertTrue((alfa[75 * 120 + 5].toInt() and 0xFF) in 120..136)
        // El color de la franja no viene premultiplicado (seguiría siendo naranja, no oscuro).
        assertTrue((img.rgb[(75 * 120 + 5) * 3].toInt() and 0xFF) > 200)

        val jpg = ByteArrayOutputStream().also { Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
        assertNull(DecodificadorAndroid.decodificar(jpg)!!.alfa)
        assertNull(DecodificadorAndroid.decodificar("no es imagen".toByteArray()))
    }

    @Test
    fun decodificadorAndroid_ReduceLogosEnormes() {
        val grande = ByteArrayOutputStream().also { Bitmap.createBitmap(4000, 2000, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val img = DecodificadorAndroid.decodificar(grande)!!
        assertTrue("se redujo a ${img.ancho}x${img.alto}", img.ancho <= 1200 && img.ancho * 2 == img.alto * 4)
    }

    @Test
    fun pdfConLogoDecodificadoEnAndroid_LlevaSMask() {
        val st = AppState().apply { emps.add(Employee(name = "Ana Pérez", salario = cfg.smmlv)) }
        val a = FuentesLato.exportador(DecodificadorAndroid)
            .pdf(st, false, st.emps, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 7), pngTransparente(), "x.pdf")
        val s = String(a.bytes, Charsets.ISO_8859_1)
        assertTrue(s.startsWith("%PDF-1.7"))
        assertTrue(s.contains("/SMask"))
    }

    @Test
    fun fileProvider_SoloCompartelaCarpetaDeExportaciones_YLimpiaLaAnterior() {
        val servicio = ServicioExportacion(context)
        val f1 = servicio.preparar(ArchivoExportado("viejo.pdf", ExportadorNomina.MimePdf, byteArrayOf(1)))
        val f2 = servicio.preparar(ArchivoExportado("comprobante_Ana_2026-10-01_2026-10-15.pdf", ExportadorNomina.MimePdf, byteArrayOf(1, 2, 3)))
        assertFalse("la exportación anterior se borra", f1.exists())
        assertEquals(File(context.cacheDir, "exportaciones"), f2.parentFile)
        val uri: Uri = servicio.uri(f2)
        assertEquals("content", uri.scheme)
        assertEquals(context.packageName + ".archivos", uri.authority)
        assertEquals("/exportaciones/comprobante_Ana_2026-10-01_2026-10-15.pdf", uri.path)
    }

    @Test
    fun intentCompartir_ActionSendConPermisoDeLectura() {
        val servicio = ServicioExportacion(context)
        val f = servicio.preparar(ArchivoExportado("nomina_recargos_2026-10-01_2026-10-15.xlsx", ExportadorNomina.MimeXlsx, byteArrayOf(1)))
        val selector = servicio.intentCompartir(f, ExportadorNomina.MimeXlsx)
        assertEquals(Intent.ACTION_CHOOSER, selector.action)
        @Suppress("DEPRECATION") val envio = selector.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, envio.action)
        assertEquals(ExportadorNomina.MimeXlsx, envio.type)
        @Suppress("DEPRECATION") assertNotNull(envio.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(envio.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun guardar_CopiaLosBytesAlDestinoElegido() {
        val destino = ByteArrayOutputStream()
        ServicioExportacion(context).copiar(ArchivoExportado("a.pdf", ExportadorNomina.MimePdf, byteArrayOf(9, 8, 7)), destino)
        assertEquals(listOf<Byte>(9, 8, 7), destino.toByteArray().toList())
    }
}
