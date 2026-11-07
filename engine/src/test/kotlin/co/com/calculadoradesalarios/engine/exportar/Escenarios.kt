package co.com.calculadoradesalarios.engine.exportar

import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Config
import co.com.calculadoradesalarios.engine.JsonConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate

/** Los 5 escenarios del oráculo, listos para exportar (mismos datos que usó el escritorio). */
object Escenarios {
    /** Fecha con la que se generaron las referencias del escritorio ("Lugar y fecha"). */
    val Hoy: LocalDate = LocalDate.of(2026, 11, 7)

    fun recurso(n: String): ByteArray = javaClass.classLoader.getResourceAsStream(n)!!.readBytes()

    val oraculo: List<JsonObject> by lazy {
        JsonConfig.json.parseToJsonElement(recurso("TestData/salida_esperada.json").decodeToString()).jsonObject["escenarios"]!!.jsonArray.map { it.jsonObject }
    }

    data class Escenario(val nombre: String, val st: AppState, val incl: Boolean, val desde: LocalDate, val hasta: LocalDate, val json: JsonObject)

    val todos: List<Escenario> by lazy {
        oraculo.map { sc ->
            val st = JsonConfig.json.decodeFromString(AppState.serializer(), recurso("TestData/respaldo_sintetico.json").decodeToString())
            st.cfg = JsonConfig.json.decodeFromJsonElement(Config.serializer(), sc["cfgAplicada"]!!)
            val p = sc["periodo"]!!.jsonArray.map { LocalDate.parse(it.jsonPrimitive.content) }
            Escenario(sc["escenario"]!!.jsonPrimitive.content, st, sc["incluirSueldo"]!!.jsonPrimitive.boolean, p[0], p[1], sc)
        }
    }

    fun comprobante() = ComprobantePdf(
        FuenteTtf(recurso("fuentes/Lato-Regular.ttf")),
        FuenteTtf(recurso("fuentes/Lato-Bold.ttf")),
        DecodificadorImageIO,
    )
}
