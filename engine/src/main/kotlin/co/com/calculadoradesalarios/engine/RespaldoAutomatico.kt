package co.com.calculadoradesalarios.engine

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ResultadoRespaldo(
    val ruta: String,
    val rutaExtra: String?,
    val errorExtra: String?
)

object RespaldoAutomatico {
    const val CarpetaNombre = "respaldos"
    const val Prefijo = "respaldo_"
    const val MaxRespaldos = 20
    val IntervaloMinimo: Duration = Duration.ofHours(1)

    private val StampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

    fun CarpetaDe(dataDir: String): String = File(dataDir, CarpetaNombre).path

    fun Listar(carpeta: String): List<File> {
        val dir = File(carpeta)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles { _, name ->
            name.startsWith(Prefijo) && name.endsWith(".zip")
        } ?: return emptyList()
        return files.sortedByDescending { it.name }
    }

    fun DebeRespaldar(dataDir: String, ahora: LocalDateTime = LocalDateTime.now()): Boolean {
        val dataJson = File(dataDir, "data.json")
        if (!dataJson.exists()) return false

        val ultimo = Listar(CarpetaDe(dataDir)).firstOrNull() ?: return true

        if (dataJson.lastModified() <= ultimo.lastModified()) return false

        val ultimoTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(ultimo.lastModified()), ZoneId.systemDefault())
        return Duration.between(ultimoTime, ahora) >= IntervaloMinimo
    }

    fun Crear(dataDir: String, ahora: LocalDateTime = LocalDateTime.now(), carpetaExtra: String? = null): ResultadoRespaldo {
        val dataJson = File(dataDir, "data.json")
        if (!dataJson.exists()) {
            throw IllegalStateException("Todavía no hay datos guardados para respaldar.")
        }

        val carpeta = File(CarpetaDe(dataDir))
        carpeta.mkdirs()

        val baseName = Prefijo + ahora.format(StampFormatter)
        var destino = File(carpeta, "$baseName.zip")
        var n = 1
        while (destino.exists()) {
            destino = File(carpeta, "${baseName}_${n++}.zip")
        }

        val temporal = File(carpeta, destino.name + ".tmp")
        try {
            ZipOutputStream(FileOutputStream(temporal)).use { zipOut ->
                val files = File(dataDir).listFiles() ?: emptyArray()
                for (archivo in files) {
                    if (archivo.isDirectory) continue
                    val nombre = archivo.name
                    if (nombre.endsWith(".tmp", ignoreCase = true)) continue
                    if (nombre.contains(".corrupted.")) continue

                    val entry = ZipEntry(nombre)
                    entry.time = archivo.lastModified()
                    zipOut.putNextEntry(entry)
                    FileInputStream(archivo).use { fis ->
                        fis.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }
            // La fecha del .zip es la que usa DebeRespaldar como "último respaldo". Se fija a
            // [ahora] para que el reloj inyectado y los archivos hablen del mismo tiempo (en
            // producción ahora == momento de escritura, igual que en el C#).
            temporal.setLastModified(ahora.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            JsonDataStore.moverAtomico(temporal, destino)
        } finally {
            if (temporal.exists()) {
                temporal.delete()
            }
        }

        Podar(carpeta.path, MaxRespaldos)

        var rutaExtra: String? = null
        var errorExtra: String? = null
        if (!carpetaExtra.isNullOrBlank()) {
            try {
                val dirExtra = File(carpetaExtra)
                dirExtra.mkdirs()
                val destExtra = File(dirExtra, destino.name)
                destino.copyTo(destExtra, overwrite = true)
                Podar(carpetaExtra, MaxRespaldos)
                rutaExtra = destExtra.path
            } catch (ex: Exception) {
                rutaExtra = null
                errorExtra = ex.message
            }
        }

        return ResultadoRespaldo(destino.path, rutaExtra, errorExtra)
    }

    fun Podar(carpeta: String, conservar: Int): Int {
        var borrados = 0
        val lista = Listar(carpeta)
        val paraBorrar = lista.drop(Math.max(1, conservar))
        for (viejo in paraBorrar) {
            try {
                if (viejo.delete()) borrados++
            } catch (_: Exception) {
            }
        }
        return borrados
    }

    fun Restaurar(zipPath: String, dataDir: String) {
        val zipFile = File(zipPath)
        if (!zipFile.exists()) {
            throw IOException("No se encontró el respaldo: $zipPath")
        }

        val tmp = File(System.getProperty("java.io.tmpdir"), "nomina_restore_" + UUID.randomUUID().toString().replace("-", ""))
        tmp.mkdirs()

        try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val name = e.name
                    if (name.isEmpty() || name.contains("..") || name.contains("/") || name.contains("\\")) continue
                    val target = File(tmp, name)
                    zip.getInputStream(e).use { input ->
                        FileOutputStream(target).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }

            val dataTmp = File(tmp, "data.json")
            if (!dataTmp.exists()) {
                throw IllegalStateException("Ese .zip no es un respaldo válido (no trae data.json).")
            }

            // Validar exhaustivamente que el JSON es válido y contiene un estado deserializable
            val jsonText = dataTmp.readText()
            val parsed = JsonConfig.json.parseToJsonElement(jsonText)
            if (parsed !is kotlinx.serialization.json.JsonObject) {
                throw IllegalStateException("data.json dentro del respaldo no contiene un objeto JSON válido.")
            }
            if (parsed.containsKey("state")) {
                JsonConfig.json.decodeFromJsonElement(AppState.serializer(), parsed["state"]!!)
            } else {
                JsonConfig.json.decodeFromString(AppState.serializer(), jsonText)
            }

            // Sólo después de validar exitosamente se toca dataDir
            val dirDest = File(dataDir)
            dirDest.mkdirs()
            val actual = File(dirDest, "data.json")
            if (actual.exists()) {
                actual.copyTo(File(dirDest, "data.json.antes_de_restaurar"), overwrite = true)
            }

            // Primero las fotos y demás archivos; data.json va de último y por rename atómico,
            // para que una restauración interrumpida nunca deje un data.json a medias.
            val extractedFiles = tmp.listFiles() ?: emptyArray()
            for (f in extractedFiles) {
                if (f.name == "data.json") continue
                f.copyTo(File(dirDest, f.name), overwrite = true)
            }
            val dataStaging = File(dirDest, "data.json." + UUID.randomUUID().toString().replace("-", "") + ".tmp")
            try {
                dataTmp.copyTo(dataStaging, overwrite = true)
                JsonDataStore.moverAtomico(dataStaging, actual)
            } finally {
                if (dataStaging.exists()) dataStaging.delete()
            }
        } finally {
            try {
                tmp.deleteRecursively()
            } catch (_: Exception) {
            }
        }
    }
}
