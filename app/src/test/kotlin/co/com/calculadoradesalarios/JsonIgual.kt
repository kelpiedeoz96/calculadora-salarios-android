package co.com.calculadoradesalarios

import co.com.calculadoradesalarios.engine.JsonConfig
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.fail
import java.math.BigDecimal

/** Mismo contenido JSON (42 == 42.0, sin importar sangría ni escapes); falla con la ruta exacta. */
fun assertMismoJson(esperado: String, obtenido: String) =
    comparar(JsonConfig.json.parseToJsonElement(esperado), JsonConfig.json.parseToJsonElement(obtenido), "$")

private fun comparar(e: JsonElement, o: JsonElement, ruta: String) {
    when {
        e is JsonObject && o is JsonObject -> {
            if (e.keys != o.keys) fail("$ruta: faltan ${e.keys - o.keys}, sobran ${o.keys - e.keys}")
            for (k in e.keys) comparar(e[k]!!, o[k]!!, "$ruta.$k")
        }
        e is JsonArray && o is JsonArray -> {
            if (e.size != o.size) fail("$ruta: longitud ${e.size} esperada, ${o.size} obtenida")
            for (i in e.indices) comparar(e[i], o[i], "$ruta[$i]")
        }
        e is JsonNull || o is JsonNull -> if (e != o) fail("$ruta: esperado $e, obtenido $o")
        e is JsonPrimitive && o is JsonPrimitive -> {
            val iguales = when {
                e.isString != o.isString -> false
                !e.isString && e.content !in setOf("true", "false") -> BigDecimal(e.content).compareTo(BigDecimal(o.content)) == 0
                else -> e.content == o.content
            }
            if (!iguales) fail("$ruta: esperado $e, obtenido $o")
        }
        else -> fail("$ruta: tipos distintos")
    }
}
