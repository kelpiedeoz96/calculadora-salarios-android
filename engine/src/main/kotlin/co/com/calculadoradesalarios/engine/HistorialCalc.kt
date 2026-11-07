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

    const val MaxEntradas = 25
    const val VentanaFusionMs = 120_000L

    /**
     * MainViewModel.Snapshot del escritorio: si la última entrada tiene el mismo motivo y es de
     * hace menos de 2 minutos se reemplaza (fusión); se inserta al inicio y se conservan 25.
     */
    fun Snapshot(history: MutableList<HistoryEntry>, reason: String, nowMs: Long, payload: String) {
        val last = history.firstOrNull()
        if (last != null && last.reason == reason && nowMs - last.ts < VentanaFusionMs) history.removeAt(0)
        history.add(0, HistoryEntry(ts = nowMs, reason = reason, payload = payload))
        while (history.size > MaxEntradas) history.removeAt(history.size - 1)
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
