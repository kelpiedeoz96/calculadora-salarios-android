package co.com.calculadoradesalarios.exportar

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import co.com.calculadoradesalarios.engine.exportar.ArchivoExportado
import co.com.calculadoradesalarios.engine.exportar.ComprobantePdf
import co.com.calculadoradesalarios.engine.exportar.DecodificadorImagen
import co.com.calculadoradesalarios.engine.exportar.ExportadorNomina
import co.com.calculadoradesalarios.engine.exportar.FuenteTtf
import java.io.File
import java.io.OutputStream

/** Fuentes Lato desde los recursos del motor (las mismas en el teléfono y en las pruebas). */
object FuentesLato {
    private fun cargar(n: String) = FuenteTtf(
        FuentesLato::class.java.classLoader!!.getResourceAsStream("fuentes/$n")?.use { it.readBytes() }
            ?: error("Falta la fuente $n en los recursos del motor"),
    )

    val regular: FuenteTtf by lazy { cargar("Lato-Regular.ttf") }
    val negrita: FuenteTtf by lazy { cargar("Lato-Bold.ttf") }

    fun exportador(decodificador: DecodificadorImagen = DecodificadorAndroid) =
        ExportadorNomina(ComprobantePdf(regular, negrita, decodificador))
}

/**
 * Escribe la exportación en cacheDir/exportaciones (lo único que el FileProvider comparte) y arma los
 * intents para compartirla (ACTION_SEND) o la copia a donde el usuario eligió (CreateDocument).
 */
class ServicioExportacion(private val context: Context) {
    val autoridad: String get() = context.packageName + ".archivos"
    private val carpeta: File get() = File(context.cacheDir, "exportaciones")

    /** Guarda el archivo para compartirlo; borra las exportaciones anteriores (no se acumulan datos). */
    fun preparar(a: ArchivoExportado): File {
        carpeta.mkdirs()
        carpeta.listFiles()?.forEach { it.delete() }
        return File(carpeta, a.nombre).apply { writeBytes(a.bytes) }
    }

    fun uri(f: File): Uri = FileProvider.getUriForFile(context, autoridad, f)

    fun intentCompartir(f: File, mime: String): Intent {
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri(f))
            putExtra(Intent.EXTRA_SUBJECT, f.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(envio, "Compartir ${f.name}").apply { addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }

    fun copiar(a: ArchivoExportado, destino: OutputStream) = destino.use { it.write(a.bytes) }
}
