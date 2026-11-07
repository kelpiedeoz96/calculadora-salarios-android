package co.com.calculadoradesalarios.engine

import java.time.LocalDate

data class WeekGaugeData(
    val ordMin: Int,
    val extraMin: Int,
    val ordRatio: Double,
    val extraRatio: Double,
    val capRatio: Double
)

object JornadaHelper {
    private val TimeRx = Regex("""^(\d{1,2})(?::?(\d{2}))?$""")

    fun NormTime(s: String?): String? {
        val trimmed = (s ?: "").trim()
        if (trimmed.isEmpty()) return ""
        val m = TimeRx.matchEntire(trimmed) ?: return null
        val h = m.groupValues[1].toInt()
        val mi = if (m.groupValues[2].isNotEmpty()) m.groupValues[2].toInt() else 0
        if (h > 23 || mi > 59) return null
        return "%02d:%02d".format(h, mi)
    }

    fun EditDay(emp: Employee, date: LocalDate, mut: (DayRecord) -> Unit) {
        val key = Engine.Key(date)
        val rec = emp.days.getOrPut(key) { DayRecord() }
        val defRest = Engine.IsRest(emp, date, null)
        if (rec.desc == null) {
            rec.desc = defRest
        }
        mut(rec)
        val empty = rec.in1.isNullOrEmpty() &&
                rec.out1.isNullOrEmpty() &&
                rec.in2.isNullOrEmpty() &&
                rec.out2.isNullOrEmpty() &&
                rec.brk == 0.0 &&
                rec.desc == defRest
        if (empty) {
            emp.days.remove(key)
        }
    }

    /** OnPresetApplied del escritorio: "HH:MM-HH:MM" (o "HH:MM a HH:MM") en el turno 1 y borra el turno 2. */
    fun AplicarTurno(emp: Employee, date: LocalDate, turno: String): Boolean {
        val partes = turno.split(" a ", "-").map { it.trim() }.filter { it.isNotEmpty() }
        if (partes.size != 2) return false
        EditDay(emp, date) { rec ->
            rec.in1 = partes[0]
            rec.out1 = partes[1]
            rec.in2 = ""
            rec.out2 = ""
        }
        return true
    }

    /** Minutos de descanso como OnCellCommitted del escritorio: solo los dígitos; vacío = 0. */
    fun ParseDescanso(texto: String?): Double {
        val digitos = (texto ?: "").filter { it in '0'..'9' }
        return if (digitos.isEmpty()) 0.0 else digitos.toDouble()
    }

    /**
     * Cambia una celda del registro (in1, out1, in2, out2 ya normalizadas con NormTime, o brk) con
     * EditDay. Devuelve true si el valor cambió.
     */
    fun EditarCelda(emp: Employee, date: LocalDate, campo: String, valor: String): Boolean {
        var cambio = false
        EditDay(emp, date) { rec ->
            when (campo) {
                "in1" -> if (rec.in1 != valor) { rec.in1 = valor; cambio = true }
                "out1" -> if (rec.out1 != valor) { rec.out1 = valor; cambio = true }
                "in2" -> if (rec.in2 != valor) { rec.in2 = valor; cambio = true }
                "out2" -> if (rec.out2 != valor) { rec.out2 = valor; cambio = true }
                "brk" -> { val b = ParseDescanso(valor); if (rec.brk != b) { rec.brk = b; cambio = true } }
                else -> throw IllegalArgumentException("Campo desconocido: $campo")
            }
        }
        return cambio
    }

    /** Deja el día como si no tuviera registro (EditDay lo elimina al quedar vacío). */
    fun LimpiarDia(emp: Employee, date: LocalDate): Boolean {
        if (!emp.days.containsKey(Engine.Key(date))) return false
        EditDay(emp, date) { rec ->
            rec.in1 = ""; rec.out1 = ""; rec.in2 = ""; rec.out2 = ""; rec.brk = 0.0
            rec.desc = Engine.IsRest(emp, date, null)
        }
        return true
    }

    /** ClearPeriodHours del escritorio: borra los registros entre from y to (inclusive). Devuelve cuántos. */
    fun LimpiarPeriodo(emp: Employee, from: LocalDate, to: LocalDate): Int {
        val kf = Engine.Key(from)
        val kt = Engine.Key(to)
        val borrar = emp.days.keys.filter { it >= kf && it <= kt }
        borrar.forEach { emp.days.remove(it) }
        return borrar.size
    }

    fun CalculateWeekGauge(workedMin: Int, extraMin: Int, capMin: Int): WeekGaugeData {
        val ord = Math.max(0, workedMin - extraMin)
        val scale = Math.max(Math.max(capMin.toDouble(), workedMin.toDouble()), 1.0)
        val fo = ord / scale
        val fe = extraMin / scale
        val fc = Math.min(1.0, capMin / scale)
        return WeekGaugeData(ord, extraMin, fo, fe, fc)
    }
}
