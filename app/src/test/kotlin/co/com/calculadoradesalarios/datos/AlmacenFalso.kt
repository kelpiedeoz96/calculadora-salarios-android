package co.com.calculadoradesalarios.datos

import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.engine.HistoryEntry
import co.com.calculadoradesalarios.engine.JsonConfig
import co.com.calculadoradesalarios.engine.ResultadoRespaldo
import co.com.calculadoradesalarios.engine.UiState
import java.io.InputStream
import java.time.LocalDateTime
import java.util.Collections

/** AlmacenNomina en memoria para probar repositorio y ViewModels sin disco ni emulador. */
class AlmacenFalso(
    var inicial: DatosNomina = DatosNomina(
        AppState().apply { emps.add(Employee(id = "e1", name = "Empleado 1", salario = cfg.smmlv)) },
        UiState(month = "2026-10", mode = "q1", sel = "e1"),
        emptyList(),
    ),
) : AlmacenNomina {
    /** Copia JSON de cada guardado: si alguien mutara un objeto ya publicado, se notaría aquí. */
    val guardados: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val hilosDeGuardado: MutableList<String> = Collections.synchronizedList(mutableListOf())
    val hilosDeRespaldo: MutableList<String> = Collections.synchronizedList(mutableListOf())
    var demoraGuardadoMs = 0L
    var fallarAlGuardar: Exception? = null
    var respaldos = 0
    var restaurado: String? = null
    var carpeta: String? = null
    val imagenes = mutableMapOf<String, ByteArray>()

    val ultimoGuardado: DatosNomina?
        get() = guardados.lastOrNull()?.let { j ->
            val c = JsonConfig.json.decodeFromString(co.com.calculadoradesalarios.engine.PersistContainer.serializer(), j)
            DatosNomina(c.state, c.ui, c.history)
        }

    override fun cargar() = inicial

    override fun guardar(datos: DatosNomina) {
        hilosDeGuardado += Thread.currentThread().name.substringBefore(" @")
        if (demoraGuardadoMs > 0) Thread.sleep(demoraGuardadoMs)
        fallarAlGuardar?.let { throw it }
        guardados += JsonConfig.json.encodeToString(
            co.com.calculadoradesalarios.engine.PersistContainer.serializer(),
            co.com.calculadoradesalarios.engine.PersistContainer(datos.state, datos.ui, datos.history.toMutableList<HistoryEntry>()),
        )
    }

    override fun respaldarSiCorresponde(): ResultadoRespaldo? {
        hilosDeRespaldo += Thread.currentThread().name.substringBefore(" @")
        return respaldarAhora()
    }

    override fun respaldarAhora(): ResultadoRespaldo {
        respaldos++
        return ResultadoRespaldo("respaldo_$respaldos.zip", null, null)
    }

    override fun infoRespaldos() = InfoRespaldos(respaldos, if (respaldos > 0) LocalDateTime.of(2026, 11, 7, 9, 30) else null)

    override fun restaurar(abrir: () -> InputStream) {
        restaurado = abrir().use { it.readBytes().decodeToString() }
    }

    override fun guardarFotoEmpleado(empId: String, abrir: () -> InputStream, extension: String): String {
        val nombre = "foto_${empId}_1.$extension"
        imagenes[nombre] = abrir().use { it.readBytes() }
        return nombre
    }

    override fun guardarLogo(abrir: () -> InputStream, extension: String): String {
        val nombre = "empresa_logo.$extension"
        imagenes[nombre] = abrir().use { it.readBytes() }
        return nombre
    }

    override fun leerImagen(relPath: String?) = imagenes[relPath]
    override fun carpetaExtra() = carpeta
    override fun fijarCarpetaExtra(uri: String?) {
        carpeta = uri?.substringAfterLast('/')
    }
}
