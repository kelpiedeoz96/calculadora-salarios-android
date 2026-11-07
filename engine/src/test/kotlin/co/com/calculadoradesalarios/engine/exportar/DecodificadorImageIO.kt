package co.com.calculadoradesalarios.engine.exportar

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO

/** Implementación de JVM (javax.imageio) para las pruebas; en Android se usa BitmapFactory. */
object DecodificadorImageIO : DecodificadorImagen {
    override fun decodificar(bytes: ByteArray): ImagenRgba? {
        val img = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return null
        val argb = img.getRGB(0, 0, img.width, img.height, null, 0, img.width)
        return ImagenRgba.desdeArgb(img.width, img.height, argb)
    }
}
