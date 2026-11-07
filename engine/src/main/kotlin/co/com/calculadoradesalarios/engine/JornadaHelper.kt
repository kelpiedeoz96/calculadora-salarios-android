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

    fun CalculateWeekGauge(workedMin: Int, extraMin: Int, capMin: Int): WeekGaugeData {
        val ord = Math.max(0, workedMin - extraMin)
        val scale = Math.max(Math.max(capMin.toDouble(), workedMin.toDouble()), 1.0)
        val fo = ord / scale
        val fe = extraMin / scale
        val fc = Math.min(1.0, capMin / scale)
        return WeekGaugeData(ord, extraMin, fo, fe, fc)
    }
}
