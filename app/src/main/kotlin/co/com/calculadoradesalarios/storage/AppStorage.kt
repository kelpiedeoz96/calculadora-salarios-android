package co.com.calculadoradesalarios.storage

import android.content.Context
import android.graphics.BitmapFactory
import co.com.calculadoradesalarios.datos.AlmacenNomina
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.datos.InfoRespaldos
import co.com.calculadoradesalarios.engine.JsonDataStore
import co.com.calculadoradesalarios.engine.RespaldoAutomatico
import co.com.calculadoradesalarios.engine.ResultadoRespaldo
import java.io.File
import java.io.InputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/**
 * Copia los respaldos a la carpeta extra que eligió el usuario. En Android es una carpeta del
 * selector del sistema (content://), no una ruta, por eso vive fuera de data.json.
 */
interface CarpetaExtra {
    fun nombre(): String?
    fun fijar(uri: String?)
    /** Copia [zip] y deja solo los [conservar] más nuevos. Devuelve la ubicación de la copia. */
    fun copiar(zip: File, conservar: Int): String
}

/**
 * data.json, fotos, logo y respaldos en el almacenamiento privado de la app (filesDir):
 * no hacen falta permisos de almacenamiento y ninguna otra app puede leerlos.
 */
class AppStorage(
    private val context: Context,
    private val carpetaExtra: CarpetaExtra? = null,
    /** Reloj inyectable: en pruebas se pasa uno virtual para probar la regla de 1 h sin esperar. */
    private val reloj: () -> LocalDateTime = { LocalDateTime.now() },
) : AlmacenNomina {
    val dataDir: String get() = context.filesDir.path
    val store = JsonDataStore(dataDir)

    override fun cargar(): DatosNomina {
        val (st, ui, hist) = store.load()
        return DatosNomina(st, ui, hist)
    }

    override fun guardar(datos: DatosNomina) = store.save(datos.state, datos.ui, datos.history)

    override fun respaldarSiCorresponde(): ResultadoRespaldo? {
        val ahora = reloj()
        return if (RespaldoAutomatico.DebeRespaldar(dataDir, ahora)) crearRespaldo(ahora) else null
    }

    override fun respaldarAhora(): ResultadoRespaldo = crearRespaldo(reloj())

    private fun crearRespaldo(ahora: LocalDateTime): ResultadoRespaldo {
        val r = RespaldoAutomatico.Crear(dataDir, ahora, null)
        val extra = carpetaExtra ?: return r
        if (extra.nombre() == null) return r
        return try {
            r.copy(rutaExtra = extra.copiar(File(r.ruta), RespaldoAutomatico.MaxRespaldos))
        } catch (e: Exception) {
            r.copy(errorExtra = e.message ?: e.javaClass.simpleName)
        }
    }

    fun listarRespaldos(): List<File> = RespaldoAutomatico.Listar(RespaldoAutomatico.CarpetaDe(dataDir))

    override fun infoRespaldos(): InfoRespaldos {
        val lista = listarRespaldos()
        val ultimo = lista.firstOrNull()?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it.lastModified()), ZoneId.systemDefault())
        }
        return InfoRespaldos(lista.size, ultimo)
    }

    override fun restaurar(abrir: () -> InputStream) {
        val tempZip = File(context.cacheDir, "restore_${UUID.randomUUID()}.zip")
        try {
            abrir().use { input -> tempZip.outputStream().use { input.copyTo(it) } }
            RespaldoAutomatico.Restaurar(tempZip.path, dataDir)
        } finally {
            tempZip.delete()
        }
    }

    override fun guardarFotoEmpleado(empId: String, abrir: () -> InputStream, extension: String): String =
        conImagenTemporal("foto", abrir, extension) { store.saveEmployeePhoto(empId, it.path) }

    override fun guardarLogo(abrir: () -> InputStream, extension: String): String =
        conImagenTemporal("logo", abrir, extension) { store.copyLogoToDataDirectory(it.path) }

    override fun leerImagen(relPath: String?): ByteArray? {
        if (relPath.isNullOrBlank() || File(relPath).name != relPath) return null
        val f = File(dataDir, relPath)
        return if (f.isFile) runCatching { f.readBytes() }.getOrNull() else null
    }

    override fun carpetaExtra(): String? = carpetaExtra?.nombre()

    override fun fijarCarpetaExtra(uri: String?) {
        carpetaExtra?.fijar(uri)
    }

    /** Copia la imagen a un temporal, la valida como el escritorio (≤ 25 MB y decodificable) y la guarda. */
    private fun conImagenTemporal(prefijo: String, abrir: () -> InputStream, extension: String, guardar: (File) -> String): String {
        val ext = extension.removePrefix(".").lowercase()
        val temp = File(context.cacheDir, "temp_${prefijo}_${UUID.randomUUID()}.$ext")
        try {
            abrir().use { input -> temp.outputStream().use { input.copyTo(it) } }
            if (temp.length() > MaxImagenBytes) throw IllegalArgumentException("La imagen es muy pesada (máximo 25 MB).")
            val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.path, opciones)
            if (opciones.outWidth <= 0 || opciones.outHeight <= 0) throw IllegalArgumentException("El archivo no es una imagen válida.")
            return guardar(temp)
        } finally {
            temp.delete()
        }
    }

    companion object {
        const val MaxImagenBytes = 25L * 1024 * 1024
    }
}
