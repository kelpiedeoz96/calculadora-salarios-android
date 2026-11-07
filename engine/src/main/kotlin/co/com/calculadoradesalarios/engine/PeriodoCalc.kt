package co.com.calculadoradesalarios.engine

import java.time.LocalDate
import java.time.YearMonth

/**
 * Periodo de liquidación (quincena 1, quincena 2 o rango), portado de MainViewModel del escritorio:
 * Normalize, GetPeriod, ShiftPeriodInternal y ApplyFlyoutPeriod. Funciones puras: "hoy" se recibe.
 */
object PeriodoCalc {
    private val MesValido = Regex("""^\d{4}-(0[1-9]|1[0-2])$""")

    fun EsMesValido(texto: String?): Boolean = texto != null && MesValido.matches(texto)

    fun ParseFecha(texto: String?): LocalDate? {
        val t = texto ?: return null
        if (!Regex("""^\d{4}-\d{2}-\d{2}$""").matches(t)) return null
        return try {
            LocalDate.parse(t)
        } catch (_: Exception) {
            null
        }
    }

    fun Normalizar(ui: UiState, emps: List<Employee>, hoy: LocalDate) {
        if (!EsMesValido(ui.month)) {
            ui.month = "%04d-%02d".format(hoy.year, hoy.monthValue)
            ui.mode = if (hoy.dayOfMonth <= 15) "q1" else "q2"
        }
        if (ui.mode != "q1" && ui.mode != "q2" && ui.mode != "custom") ui.mode = "q1"
        if (emps.none { it.id == ui.sel }) ui.sel = emps.firstOrNull()?.id ?: ""
    }

    fun Periodo(ui: UiState, hoy: LocalDate): Pair<LocalDate, LocalDate> {
        if (ui.mode == "custom") {
            val f = ParseFecha(ui.from)
            val t = ParseFecha(ui.to)
            if (f != null && t != null) return if (f <= t) f to t else t to f
        }
        var y = hoy.year
        var m = hoy.monthValue
        val parts = ui.month.split('-')
        if (parts.size == 2) {
            val py = parts[0].toIntOrNull()
            val pm = parts[1].toIntOrNull()
            if (py != null && pm != null) {
                y = py.coerceIn(1, 9999)
                m = pm.coerceIn(1, 12)
            }
        }
        val last = YearMonth.of(y, m).lengthOfMonth()
        return if (ui.mode == "q2") LocalDate.of(y, m, 16) to LocalDate.of(y, m, last)
        else LocalDate.of(y, m, 1) to LocalDate.of(y, m, 15)
    }

    fun Etiqueta(ui: UiState, hoy: LocalDate): String {
        val (f, t) = Periodo(ui, hoy)
        return Fmt.DmyFull(f) + " – " + Fmt.DmyFull(t)
    }

    /** Quincena anterior (-1) o siguiente (+1). En rango personalizado no hace nada (devuelve false). */
    fun Desplazar(ui: UiState, dir: Int, hoy: LocalDate): Boolean {
        if (ui.mode == "custom") return false
        var y = hoy.year
        var m = hoy.monthValue
        val parts = ui.month.split('-')
        if (parts.size == 2) {
            val py = parts[0].toIntOrNull()
            val pm = parts[1].toIntOrNull()
            if (py != null && pm != null) {
                y = py; m = pm
            }
        }
        if (dir > 0) {
            if (ui.mode == "q1") ui.mode = "q2"
            else { ui.mode = "q1"; m++; if (m > 12) { m = 1; y++ } }
        } else {
            if (ui.mode == "q2") ui.mode = "q1"
            else { ui.mode = "q2"; m--; if (m < 1) { m = 12; y-- } }
        }
        ui.month = "%04d-%02d".format(y, m)
        return true
    }

    /**
     * Aplica la selección del selector de periodo. Devuelve el mensaje de error del escritorio, o
     * null si se aplicó.
     */
    fun Aplicar(ui: UiState, mes: String, modo: String, desde: String, hasta: String): String? {
        val m = mes.trim()
        if (!EsMesValido(m)) return "El mes debe ser AAAA-MM, por ejemplo 2026-10."
        val d = desde.trim()
        val h = hasta.trim()
        if (modo == "custom" && (ParseFecha(d) == null || ParseFecha(h) == null))
            return "Define un rango válido: AAAA-MM-DD en Desde y Hasta."
        ui.month = m
        ui.mode = if (modo == "q2" || modo == "custom") modo else "q1"
        ui.from = d
        ui.to = h
        return null
    }
}
