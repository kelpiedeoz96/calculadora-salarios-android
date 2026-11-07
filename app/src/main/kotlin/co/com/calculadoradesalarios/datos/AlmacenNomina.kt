package co.com.calculadoradesalarios.datos

import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.HistoryEntry
import co.com.calculadoradesalarios.engine.ResultadoRespaldo
import co.com.calculadoradesalarios.engine.UiState
import java.io.InputStream
import java.time.LocalDateTime

/**
 * Todo lo que guarda en disco el contenido de data.json. Los objetos que circulan aquí no se
 * modifican después de publicarse; quien quiera cambiar algo trabaja sobre una copia.
 */
data class DatosNomina(
    val state: AppState,
    val ui: UiState,
    val history: List<HistoryEntry>,
)

data class InfoRespaldos(val cantidad: Int = 0, val ultimo: LocalDateTime? = null)

/**
 * Almacenamiento en disco. Todas las funciones bloquean (E/S): el repositorio las llama
 * siempre fuera del hilo principal. En las pruebas se usa una implementación en memoria.
 */
interface AlmacenNomina {
    fun cargar(): DatosNomina
    fun guardar(datos: DatosNomina)

    fun respaldarSiCorresponde(): ResultadoRespaldo?
    fun respaldarAhora(): ResultadoRespaldo
    fun infoRespaldos(): InfoRespaldos
    fun restaurar(abrir: () -> InputStream)

    fun guardarFotoEmpleado(empId: String, abrir: () -> InputStream, extension: String): String
    fun guardarLogo(abrir: () -> InputStream, extension: String): String
    fun leerImagen(relPath: String?): ByteArray?

    /** Nombre visible de la carpeta extra de respaldos, o null si no hay. Solo vive en Android. */
    fun carpetaExtra(): String?
    fun fijarCarpetaExtra(uri: String?)
}
