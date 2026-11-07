package co.com.calculadoradesalarios.storage

import android.content.Context
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.HistoryEntry
import co.com.calculadoradesalarios.engine.IDataStore
import co.com.calculadoradesalarios.engine.JsonDataStore
import co.com.calculadoradesalarios.engine.RespaldoAutomatico
import co.com.calculadoradesalarios.engine.ResultadoRespaldo
import co.com.calculadoradesalarios.engine.UiState
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.util.UUID

/**
 * Gestor de almacenamiento en Android.
 * Ubica data.json, fotos y respaldos automáticos en el almacenamiento interno privado de la aplicación
 * (context.filesDir) garantizando privacidad absoluta y cero permisos de almacenamiento requeridos.
 */
class AppStorage(
    private val context: Context,
    /** Reloj inyectable: en pruebas se pasa uno virtual para probar la regla de 1 h sin esperar. */
    private val reloj: () -> LocalDateTime = { LocalDateTime.now() },
) {
    val dataDir: String get() = context.filesDir.path
    val store: IDataStore = JsonDataStore(dataDir)

    fun load(): Triple<AppState, UiState, MutableList<HistoryEntry>> = store.load()

    fun save(state: AppState, ui: UiState, history: List<HistoryEntry>) {
        store.save(state, ui, history)
    }

    fun checkAndRunAutoBackup(carpetaExtra: String? = null): ResultadoRespaldo? {
        val ahora = reloj()
        return if (RespaldoAutomatico.DebeRespaldar(dataDir, ahora)) {
            RespaldoAutomatico.Crear(dataDir, ahora, carpetaExtra)
        } else {
            null
        }
    }

    fun createManualBackup(carpetaExtra: String? = null): ResultadoRespaldo {
        return RespaldoAutomatico.Crear(dataDir, reloj(), carpetaExtra)
    }

    fun listBackups(): List<File> {
        return RespaldoAutomatico.Listar(RespaldoAutomatico.CarpetaDe(dataDir))
    }

    fun restoreBackup(zipFile: File) {
        RespaldoAutomatico.Restaurar(zipFile.path, dataDir)
    }

    fun restoreBackupFromStream(inputStream: InputStream, tempName: String = "restore_${UUID.randomUUID()}.zip") {
        val tempZip = File(context.cacheDir, tempName)
        try {
            FileOutputStream(tempZip).use { out ->
                inputStream.copyTo(out)
            }
            RespaldoAutomatico.Restaurar(tempZip.path, dataDir)
        } finally {
            if (tempZip.exists()) {
                tempZip.delete()
            }
        }
    }

    fun saveEmployeePhotoFromStream(employeeId: String, inputStream: InputStream, extension: String): String {
        val ext = extension.removePrefix(".").lowercase()
        val tempFile = File(context.cacheDir, "temp_foto_${UUID.randomUUID()}.$ext")
        return try {
            FileOutputStream(tempFile).use { out ->
                inputStream.copyTo(out)
            }
            store.saveEmployeePhoto(employeeId, tempFile.path)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun saveLogoFromStream(inputStream: InputStream, extension: String): String {
        val ext = extension.removePrefix(".").lowercase()
        val tempFile = File(context.cacheDir, "temp_logo_${UUID.randomUUID()}.$ext")
        return try {
            FileOutputStream(tempFile).use { out ->
                inputStream.copyTo(out)
            }
            store.copyLogoToDataDirectory(tempFile.path)
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    fun exportJson(state: AppState): String = store.exportJson(state)

    fun importBackup(filePath: String): AppState = store.importBackup(filePath)
}
