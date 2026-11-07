package co.com.calculadoradesalarios.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object HistorialCalc {
    private val DateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

    fun ParsePayload(payload: String?): AppState? {
        if (payload.isNullOrBlank()) return null
        return try {
            JsonConfig.json.decodeFromString(AppState.serializer(), payload)
        } catch (_: Exception) {
            null
        }
    }

    fun EstadoInicial(): AppState {
        val st = AppState()
        for (i in 1..6) {
            st.emps.add(Employee(name = "Empleado $i"))
        }
        st.emps[3].restDay = 3
        return st
    }

    fun FormatearFecha(tsMs: Long, zoneId: ZoneId = ZoneOffset.UTC): String {
        val zdt = Instant.ofEpochMilli(tsMs).atZone(zoneId)
        return zdt.format(DateFormatter)
    }

    fun Normalizar(s: AppState, ui: UiState, today: LocalDate) {
        if (ui.month.isEmpty()) {
            ui.month = "%04d-%02d".format(today.year, today.monthValue)
            ui.mode = if (today.dayOfMonth <= 15) "q1" else "q2"
        }

        if (s.emps.none { it.id == ui.sel }) {
            ui.sel = if (s.emps.isNotEmpty()) s.emps[0].id else ""
        }
    }
}
