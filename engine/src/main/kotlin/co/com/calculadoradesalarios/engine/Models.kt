package co.com.calculadoradesalarios.engine

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.util.UUID

object LenientDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonNull) return 0.0
            if (element is JsonPrimitive) {
                return element.doubleOrNull
                    ?: element.content.trim().replace(',', '.').toDoubleOrNull()
                    ?: 0.0
            }
            return 0.0
        }
        return try {
            decoder.decodeDouble()
        } catch (_: Exception) {
            0.0
        }
    }

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }
}

object JsonConfig {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }

    val exportJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = false
    }
}

@Serializable
data class Pct(
    var noc: Double = 35.0,
    var hed: Double = 25.0,
    var hen: Double = 75.0,
    var dom: Double = 90.0
)

@Serializable
data class Config(
    var weeklyCap: Double = 42.0,
    var dailyRef: Double = 7.0,
    var dailyCapOn: Boolean = false,
    var nightStart: String = "19:00",
    var nightEnd: String = "06:00",
    var minuteRuleOn: Boolean = true,
    var minValid: Double = 30.0,
    var divisor: Double = 210.0,
    var rateRound: Boolean = true,
    var domOrdFull: Boolean = false,
    var domAutoOn: Boolean = true,
    var smmlv: Double = 1750905.0,
    var auxilio: Double = 249095.0,
    var saludPct: Double = 4.0,
    var pensionPct: Double = 4.0,
    var deductBase: String = "basico",
    var pct: Pct = Pct(),
    var turnos: MutableList<String> = mutableListOf(
        "08:00-12:00", "08:00-17:00", "14:00-22:00"
    ),
    var razonSocial: String = "Mi Empresa",
    var nit: String = "",
    var logoCustomRelPath: String? = null
)

@Serializable
data class DayRecord(
    var in1: String? = null,
    var out1: String? = null,
    var in2: String? = null,
    var out2: String? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    var brk: Double = 0.0,
    var desc: Boolean? = null
)

@Serializable
data class Employee(
    var id: String = UUID.randomUUID().toString().replace("-", "").take(7),
    var name: String = "Nuevo empleado",
    var cedula: String = "",
    var cargo: String = "Operario",
    @Serializable(with = LenientDoubleSerializer::class)
    var salario: Double = 0.0,
    var aux: Boolean = true,
    var restDay: Int? = null,
    var photoRelPath: String? = null,
    var days: MutableMap<String, DayRecord> = mutableMapOf()
)

@Serializable
data class AppState(
    var v: Int = 2,
    var cfg: Config = Config(),
    var festivos: MutableList<String> = mutableListOf(
        "2026-01-01", "2026-01-12", "2026-03-23", "2026-04-02", "2026-04-03", "2026-05-01",
        "2026-05-18", "2026-06-08", "2026-06-15", "2026-06-29", "2026-07-20", "2026-08-07",
        "2026-08-17", "2026-10-12", "2026-11-02", "2026-11-16", "2026-12-08", "2026-12-25"
    ),
    var emps: MutableList<Employee> = mutableListOf()
) {
    fun clone(): AppState {
        val s = JsonConfig.json.encodeToString(serializer(), this)
        return JsonConfig.json.decodeFromString(serializer(), s)
    }
}

@Serializable
data class UiState(
    var tab: String = "dashboard",
    var month: String = "",
    var mode: String = "q1",
    var from: String = "",
    var to: String = "",
    var sel: String = "",
    var incl: Boolean = false,
    var search: String = "",
    var themeMode: String = "system",
    var reduceAnimations: Boolean = false,
    var isSidebarCollapsed: Boolean = false,
    var backupExtraDir: String = "",
    // Igual que [JsonIgnore(WhenWritingNull)] en C#: no se escribe si es null.
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    var dark: Boolean? = null
) {
    fun clone(): UiState {
        val s = JsonConfig.json.encodeToString(serializer(), this)
        return JsonConfig.json.decodeFromString(serializer(), s)
    }
}

@Serializable
data class HistoryEntry(
    var ts: Long = 0L,
    var reason: String = "",
    var payload: String = ""
)

@Serializable
data class PersistContainer(
    @SerialName("state")
    var state: AppState = AppState(),
    @SerialName("ui")
    var ui: UiState = UiState(),
    @SerialName("history")
    var history: MutableList<HistoryEntry> = mutableListOf()
)
