package co.com.calculadoradesalarios.engine

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files

/**
 * Intercambio de data.json entre el escritorio (C#) y el celular (Kotlin).
 *
 * Los archivos `*.guardado_por_escritorio.json` NO los escribió Kotlin: se generaron con el
 * JsonDataStore real del proyecto C# (CalculadoraDeSalarios.Core) haciendo Load() + Save()
 * sobre cada archivo de entrada. Son la referencia de lo que el escritorio produce.
 *
 * Se compara el contenido JSON, no los bytes: el C# escribe `42` y Kotlin `42.0` para un
 * double, y la sangría difiere. Ambos programas leen los dos estilos sin problema; lo que no
 * puede cambiar es ningún campo, valor, orden de lista ni presencia/ausencia de una clave.
 */
class InteropEscritorioTest {
    private lateinit var dir: File

    @BeforeEach
    fun setUp() {
        dir = Files.createTempDirectory("nomina_interop_").toFile()
    }

    @AfterEach
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun recurso(nombre: String): String =
        javaClass.classLoader.getResourceAsStream("TestData/$nombre")!!.bufferedReader().use { it.readText() }

    /** Carga [json] como data.json con el JsonDataStore de Kotlin y lo vuelve a guardar. */
    private fun cargarYGuardarConKotlin(json: String, sub: String): String {
        val d = File(dir, sub).apply { mkdirs() }
        File(d, "data.json").writeText(json)
        val store = JsonDataStore(d.path)
        val (st, ui, hist) = store.load()
        assertEquals(0, d.listFiles { _, n -> n.contains(".corrupted.") }!!.size, "Kotlin no pudo leer el archivo")
        store.save(st, ui, hist)
        return File(d, "data.json").readText()
    }

    @ParameterizedTest
    @ValueSource(strings = ["data_formato_actual_v1", "respaldo_sintetico"])
    fun kotlin_GuardaElMismoJsonQueElEscritorio(nombre: String) {
        val entrada = recurso("$nombre.json")
        val esperado = recurso("$nombre.guardado_por_escritorio.json")

        // 1. Mismo punto de partida que el escritorio -> mismo resultado que el escritorio.
        val deKotlin = cargarYGuardarConKotlin(entrada, "a")
        assertMismoJson(esperado, deKotlin)

        // 2. Abrir en el celular un data.json escrito por el escritorio y guardarlo no cambia nada.
        val vueltaDesdeEscritorio = cargarYGuardarConKotlin(esperado, "b")
        assertMismoJson(esperado, vueltaDesdeEscritorio)

        // 3. Guardar dos veces seguidas en Kotlin da exactamente los mismos bytes (estable).
        val otraVez = cargarYGuardarConKotlin(deKotlin, "c")
        assertEquals(deKotlin, otraVez)

        // Se deja una copia en build/ para poder abrirla con el escritorio (dirección inversa).
        File("build/interop-kotlin/$nombre").apply { mkdirs() }.resolve("data.json").writeText(deKotlin)
    }

    // ---------- comparación de árboles JSON con la ruta exacta de la primera diferencia ----------

    private fun assertMismoJson(esperado: String, obtenido: String) {
        val e = JsonConfig.json.parseToJsonElement(esperado)
        val o = JsonConfig.json.parseToJsonElement(obtenido)
        comparar(e, o, "$")
    }

    private fun comparar(e: JsonElement, o: JsonElement, ruta: String) {
        when {
            e is JsonObject && o is JsonObject -> {
                val faltan = e.keys - o.keys
                val sobran = o.keys - e.keys
                if (faltan.isNotEmpty() || sobran.isNotEmpty())
                    fail<Unit>("$ruta: claves distintas. Faltan en Kotlin: $faltan. Sobran en Kotlin: $sobran")
                for (k in e.keys) comparar(e[k]!!, o[k]!!, "$ruta.$k")
            }
            e is JsonArray && o is JsonArray -> {
                if (e.size != o.size) fail<Unit>("$ruta: longitud ${e.size} esperada, ${o.size} obtenida")
                for (i in e.indices) comparar(e[i], o[i], "$ruta[$i]")
            }
            e is JsonNull || o is JsonNull ->
                if (e != o) fail<Unit>("$ruta: esperado $e, obtenido $o")
            e is JsonPrimitive && o is JsonPrimitive -> {
                if (e.isString != o.isString) fail<Unit>("$ruta: tipo distinto ($e vs $o)")
                val iguales = if (!e.isString && e.content != "true" && e.content != "false") {
                    BigDecimal(e.content).compareTo(BigDecimal(o.content)) == 0
                } else {
                    e.content == o.content
                }
                if (!iguales) fail<Unit>("$ruta: esperado $e, obtenido $o")
            }
            else -> fail<Unit>("$ruta: tipo distinto (${e::class.simpleName} vs ${o::class.simpleName})")
        }
    }
}
