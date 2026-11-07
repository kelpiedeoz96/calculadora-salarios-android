package co.com.calculadoradesalarios.engine.exportar

/** Imagen ya decodificada: RGB de 8 bits por canal y, si tiene transparencia, el canal alfa aparte. */
class ImagenRgba(val ancho: Int, val alto: Int, val rgb: ByteArray, val alfa: ByteArray?) {
    init {
        require(ancho > 0 && alto > 0) { "Imagen vacía" }
        require(rgb.size == ancho * alto * 3) { "RGB de tamaño incorrecto" }
        require(alfa == null || alfa.size == ancho * alto) { "Alfa de tamaño incorrecto" }
    }

    companion object {
        /** Desde píxeles ARGB no premultiplicados (como Bitmap.getPixels o BufferedImage.getRGB). */
        fun desdeArgb(ancho: Int, alto: Int, argb: IntArray): ImagenRgba {
            val rgb = ByteArray(ancho * alto * 3)
            val alfa = ByteArray(ancho * alto)
            var opaca = true
            for (i in argb.indices) {
                val p = argb[i]
                val a = p ushr 24
                if (a != 0xFF) opaca = false
                alfa[i] = a.toByte()
                rgb[i * 3] = (p shr 16).toByte(); rgb[i * 3 + 1] = (p shr 8).toByte(); rgb[i * 3 + 2] = p.toByte()
            }
            return ImagenRgba(ancho, alto, rgb, if (opaca) null else alfa)
        }
    }
}

/**
 * Decodificar PNG o JPG depende de la plataforma: en Android se usa BitmapFactory y en las pruebas de
 * JVM, javax.imageio. Devuelve null si los bytes no son una imagen válida.
 */
fun interface DecodificadorImagen {
    fun decodificar(bytes: ByteArray): ImagenRgba?
}
