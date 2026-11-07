package co.com.calculadoradesalarios.exportar

import co.com.calculadoradesalarios.engine.exportar.DecodificadorImagen
import co.com.calculadoradesalarios.engine.exportar.ImagenRgba
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/** Decodificador de JVM para las pruebas sin Robolectric (en el teléfono se usa DecodificadorAndroid). */
object DecodificadorJvm : DecodificadorImagen {
    override fun decodificar(bytes: ByteArray): ImagenRgba? {
        val img = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
        return ImagenRgba.desdeArgb(img.width, img.height, img.getRGB(0, 0, img.width, img.height, null, 0, img.width))
    }

    fun pngTransparente(): ByteArray {
        val img = BufferedImage(60, 40, BufferedImage.TYPE_INT_ARGB)
        img.createGraphics().apply { color = Color(14, 90, 97); fillOval(10, 0, 40, 40); dispose() }
        return ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }
}
