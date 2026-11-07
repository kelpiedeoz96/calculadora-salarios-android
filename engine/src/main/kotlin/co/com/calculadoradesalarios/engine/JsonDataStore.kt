package co.com.calculadoradesalarios.engine

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.UUID

interface IDataStore {
    val dataDirectory: String
    val dataPath: String
    fun load(): Triple<AppState, UiState, MutableList<HistoryEntry>>
    fun save(state: AppState, ui: UiState, history: List<HistoryEntry>)
    fun exportJson(state: AppState): String
    fun importBackup(filePath: String): AppState
    fun copyLogoToDataDirectory(sourceFilePath: String): String
    fun loadLogoBytes(customLogoRelPath: String?): ByteArray?
    fun saveEmployeePhoto(employeeId: String, sourceFilePath: String): String
    fun loadEmployeePhotoBytes(photoRelPath: String?): ByteArray?
}

class JsonDataStore(customDataDirectory: String? = null) : IDataStore {
    override val dataDirectory: String = customDataDirectory ?: File(
        System.getProperty("user.home"),
        ".config/NominaApp"
    ).path

    override val dataPath: String
        get() = File(dataDirectory, "data.json").path

    override fun load(): Triple<AppState, UiState, MutableList<HistoryEntry>> {
        val file = File(dataPath)
        if (!file.exists()) {
            return Triple(createFreshState(), UiState(), mutableListOf())
        }
        return try {
            val text = file.readText()
            val root = JsonConfig.json.parseToJsonElement(text).jsonObject

            val state: AppState
            val ui: UiState
            val history: MutableList<HistoryEntry>

            // Igual que el C#: en el formato anidado el estado va en "state"; en el formato
            // plano (respaldo antiguo) el estado es la raíz. En ambos, "ui" e "history" se leen
            // de la raíz si existen.
            state = if (root.containsKey("state")) {
                JsonConfig.json.decodeFromJsonElement(AppState.serializer(), root["state"]!!)
            } else {
                JsonConfig.json.decodeFromString(AppState.serializer(), text)
            }

            ui = if (root.containsKey("ui")) {
                JsonConfig.json.decodeFromJsonElement(UiState.serializer(), root["ui"]!!)
            } else {
                UiState()
            }

            history = if (root.containsKey("history")) {
                JsonConfig.json.decodeFromJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(HistoryEntry.serializer()),
                    root["history"]!!
                ).toMutableList()
            } else {
                mutableListOf()
            }

            if (ui.dark != null) {
                ui.themeMode = if (ui.dark == true) "dark" else "light"
                ui.dark = null
            } else if (ui.themeMode.isBlank()) {
                ui.themeMode = "system"
            }

            Triple(state, ui, history)
        } catch (_: Exception) {
            try {
                val corruptedFile = File(dataPath + ".corrupted." + Instant.now().epochSecond)
                file.copyTo(corruptedFile, overwrite = true)
            } catch (_: Exception) {
            }
            Triple(createFreshState(), UiState(), mutableListOf())
        }
    }

    // Hook for testing failure midway through saving
    internal var writeInterceptor: ((File) -> Unit)? = null

    override fun save(state: AppState, ui: UiState, history: List<HistoryEntry>) {
        val dir = File(dataDirectory)
        dir.mkdirs()
        val container = PersistContainer(
            state = state,
            ui = ui,
            history = history.toMutableList()
        )
        val json = JsonConfig.json.encodeToString(PersistContainer.serializer(), container)
        val tempFile = File(dir, UUID.randomUUID().toString().replace("-", "") + ".tmp")
        try {
            tempFile.writeText(json)
            writeInterceptor?.invoke(tempFile)
            moverAtomico(tempFile, File(dataPath))
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    override fun exportJson(state: AppState): String =
        JsonConfig.exportJson.encodeToString(AppState.serializer(), state)

    override fun importBackup(filePath: String): AppState {
        val text = File(filePath).readText()
        val root = JsonConfig.json.parseToJsonElement(text).jsonObject
        return if (root.containsKey("state")) {
            JsonConfig.json.decodeFromJsonElement(AppState.serializer(), root["state"]!!)
        } else {
            JsonConfig.json.decodeFromString(AppState.serializer(), text)
        }
    }

    override fun copyLogoToDataDirectory(sourceFilePath: String): String {
        val dir = File(dataDirectory)
        dir.mkdirs()
        val source = File(sourceFilePath)
        val ext = source.extension.let { if (it.isEmpty()) "" else ".$it" }
        val targetFileName = "empresa_logo$ext"
        val targetFile = File(dir, targetFileName)
        source.copyTo(targetFile, overwrite = true)
        return targetFileName
    }

    override fun loadLogoBytes(customLogoRelPath: String?): ByteArray? {
        if (!customLogoRelPath.isNullOrBlank()) {
            val file = File(dataDirectory, customLogoRelPath)
            if (file.exists()) {
                try {
                    return file.readBytes()
                } catch (_: Exception) {
                }
            }
        }
        return null
    }

    private val PhotoExtensions = setOf("png", "jpg", "jpeg")

    override fun saveEmployeePhoto(employeeId: String, sourceFilePath: String): String {
        val source = File(sourceFilePath)
        val ext = source.extension.lowercase()
        if (ext !in PhotoExtensions) {
            throw IllegalStateException("Formato de imagen no permitido (usa PNG o JPG).")
        }
        val safeId = employeeId.filter { it.isLetterOrDigit() }.ifEmpty { "emp" }
        val dir = File(dataDirectory)
        dir.mkdirs()

        var stamp = System.currentTimeMillis()
        var targetFileName = "foto_${safeId}_$stamp.$ext"
        while (File(dir, targetFileName).exists()) {
            targetFileName = "foto_${safeId}_${++stamp}.$ext"
        }
        source.copyTo(File(dir, targetFileName), overwrite = false)
        return targetFileName
    }

    override fun loadEmployeePhotoBytes(photoRelPath: String?): ByteArray? {
        if (photoRelPath.isNullOrBlank()) return null
        val file = File(photoRelPath)
        if (file.name != photoRelPath) return null
        val full = File(dataDirectory, photoRelPath)
        if (!full.exists()) return null
        return try {
            full.readBytes()
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        /**
         * Reemplaza [destino] por [origen] con un rename atómico (mismo directorio).
         * Nunca cae a copiar byte a byte: una copia a medias dejaría [destino] truncado,
         * que es justo lo que la escritura atómica debe evitar. Si el sistema de archivos
         * no admite ATOMIC_MOVE se usa un rename normal (rename(2) en POSIX también
         * reemplaza de forma atómica); si eso falla, se lanza la excepción y [destino]
         * queda como estaba.
         */
        fun moverAtomico(origen: File, destino: File) {
            try {
                java.nio.file.Files.move(
                    origen.toPath(), destino.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                java.nio.file.Files.move(
                    origen.toPath(), destino.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
                )
            }
        }

        fun createFreshState(): AppState {
            val st = AppState()
            st.emps.add(Employee(name = "Empleado 1", salario = st.cfg.smmlv))
            return st
        }
    }
}
