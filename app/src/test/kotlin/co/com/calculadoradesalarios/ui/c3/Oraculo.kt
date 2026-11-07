package co.com.calculadoradesalarios.ui.c3

import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Config
import co.com.calculadoradesalarios.engine.JsonConfig
import co.com.calculadoradesalarios.engine.UiState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Acceso a salida_esperada.json y respaldo_sintetico.json para las pruebas de la 3c. */
object Oraculo {
    private fun recurso(n: String) = javaClass.classLoader!!.getResource("TestData/$n")!!.readText()

    val escenarios: List<JsonObject> by lazy {
        JsonConfig.json.parseToJsonElement(recurso("salida_esperada.json")).jsonObject["escenarios"]!!.jsonArray.map { it.jsonObject }
    }

    fun estadoSintetico(): AppState = JsonConfig.json.decodeFromString(AppState.serializer(), recurso("respaldo_sintetico.json"))

    fun textoSintetico(): String = recurso("respaldo_sintetico.json")

    fun recursoTexto(n: String) = recurso(n)

    /** Almacén con los datos sintéticos, la configuración y el periodo del escenario. */
    fun almacen(sc: JsonObject, selNombre: String? = null): AlmacenFalso {
        val st = estadoSintetico()
        st.cfg = JsonConfig.json.decodeFromJsonElement(Config.serializer(), sc["cfgAplicada"]!!)
        val (desde, hasta) = periodo(sc)
        val sel = st.emps.firstOrNull { it.name == selNombre }?.id ?: st.emps[0].id
        val incl = sc["incluirSueldo"]!!.jsonPrimitive.boolean
        return AlmacenFalso(DatosNomina(st, UiState(month = desde.take(7), mode = "custom", from = desde, to = hasta, sel = sel, incl = incl), emptyList()))
    }

    fun periodo(sc: JsonObject): Pair<String, String> = sc["periodo"]!!.jsonArray.map { it.jsonPrimitive.content }.let { it[0] to it[1] }
    fun empleados(sc: JsonObject): List<JsonObject> = sc["empleados"]!!.jsonArray.map { it.jsonObject }
    fun JsonObject.int(k: String) = this[k]!!.jsonPrimitive.int
    fun JsonObject.long(k: String) = this[k]!!.jsonPrimitive.long
    fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
    fun JsonObject.bool(k: String) = this[k]!!.jsonPrimitive.boolean
}
