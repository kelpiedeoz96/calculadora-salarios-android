package co.com.calculadoradesalarios.engine.exportar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

/** El logo: PNG con transparencia, PNG opaco y JPG, por la interfaz DecodificadorImagen. */
class LogoPdfTest {
    companion object {
        /** Círculo turquesa sobre fondo transparente, con borde semitransparente. */
        fun pngTransparente(): ByteArray {
            val img = BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB)
            val g = img.createGraphics()
            g.color = Color(14, 90, 97, 255); g.fillOval(30, 10, 60, 60)
            g.color = Color(230, 120, 30, 128); g.fillRect(0, 70, 120, 10)
            g.dispose()
            return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
        }

        fun jpg(): ByteArray {
            val img = BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics(); g.color = Color.WHITE; g.fillRect(0, 0, 100, 50); g.color = Color(14, 90, 97); g.fillRect(10, 10, 80, 30); g.dispose()
            return ByteArrayOutputStream().also { ImageIO.write(img, "jpg", it) }.toByteArray()
        }
    }

    @Test
    fun decodificador_ConservaLaTransparencia() {
        val img = DecodificadorImageIO.decodificar(pngTransparente())!!
        assertEquals(120 to 80, img.ancho to img.alto)
        val alfa = img.alfa!!
        assertEquals(0, alfa[0].toInt() and 0xFF, "esquina transparente")
        assertEquals(255, alfa[40 * 120 + 60].toInt() and 0xFF, "centro opaco")
        assertEquals(128, alfa[75 * 120 + 5].toInt() and 0xFF, "franja semitransparente")
        assertNull(DecodificadorImageIO.decodificar(jpg())!!.alfa, "un JPG no tiene canal alfa")
        assertNull(DecodificadorImageIO.decodificar("no es imagen".toByteArray()))
    }

    @Test
    fun pdfConLogoTransparente_LlevaSMask_YElTextoSeCorre() {
        val e = Escenarios.todos[0]
        val (pdf, doc) = Escenarios.comprobante().generar(e.st, e.incl, e.st.emps.take(1), e.desde, e.hasta, Escenarios.Hoy, pngTransparente())
        val texto = String(pdf, Charsets.ISO_8859_1)
        assertTrue(texto.contains("/SMask"), "la transparencia va como /SMask")
        assertTrue(texto.contains("/Subtype /Image"))
        // El logo mide 34 mm de ancho y el título empieza 8 pt a su derecha, como en el escritorio.
        val titulo = doc.todasLasPaginas[0].cajas.first()
        assertEquals(ComprobantePdf.Margen, titulo[0], 0.01f) // la primera caja es el logo
        File("build/exportaciones").mkdirs()
        File("build/exportaciones/logo_transparente.pdf").writeBytes(pdf)
        val (pdfJpg, _) = Escenarios.comprobante().generar(e.st, e.incl, e.st.emps.take(1), e.desde, e.hasta, Escenarios.Hoy, jpg())
        File("build/exportaciones/logo_jpg.pdf").writeBytes(pdfJpg)
        assertTrue(!String(pdfJpg, Charsets.ISO_8859_1).contains("/SMask"), "un JPG opaco no necesita /SMask")
    }

    @Test
    fun logoInvalido_SeOmite_SinRomperElPdf() {
        val e = Escenarios.todos[0]
        val (pdf, doc) = Escenarios.comprobante().generar(e.st, e.incl, e.st.emps.take(1), e.desde, e.hasta, Escenarios.Hoy, "basura".toByteArray())
        assertNotNull(pdf)
        assertEquals("Comprobante de recargos y horas extra", doc.todasLasPaginas[0].textos.first())
    }
}
