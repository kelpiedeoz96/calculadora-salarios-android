package co.com.calculadoradesalarios.exportar

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import co.com.calculadoradesalarios.engine.exportar.DecodificadorImagen
import co.com.calculadoradesalarios.engine.exportar.ImagenRgba

/**
 * PNG o JPG con BitmapFactory. Se pide ARGB_8888 sin premultiplicar para conservar la transparencia
 * real del logo; las imágenes muy grandes se reducen (el logo mide 34 mm en el PDF).
 */
object DecodificadorAndroid : DecodificadorImagen {
    private const val LadoMaximo = 1200

    override fun decodificar(bytes: ByteArray): ImagenRgba? {
        val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, limites)
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null
        var muestra = 1
        while (maxOf(limites.outWidth, limites.outHeight) / muestra > LadoMaximo) muestra *= 2
        val opciones = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPremultiplied = false
            inSampleSize = muestra
        }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opciones) ?: return null
        return try {
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            ImagenRgba.desdeArgb(bmp.width, bmp.height, px)
        } finally {
            bmp.recycle()
        }
    }
}
