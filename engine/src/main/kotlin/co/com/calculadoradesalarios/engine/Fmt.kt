package co.com.calculadoradesalarios.engine

import java.time.LocalDate

object Fmt {
    private val Thousands = Regex("""\B(?=(\d{3})+(?!\d))""")

    fun Money(n: Long): String {
        val neg = n < 0
        val s = Thousands.replace(Math.abs(n).toString(), ".")
        return (if (neg) "-" else "") + "$ " + s
    }

    fun Dmy(d: LocalDate): String = "%02d/%02d".format(d.dayOfMonth, d.monthValue)

    fun DmyFull(d: LocalDate): String = "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)

    fun Initials(name: String?): String {
        val parts = (name ?: "").split(Regex("\\s+")).filter { it.isNotBlank() }
        val s = parts.take(2).map { it[0] }.joinToString("")
        return if (s.isEmpty()) "·" else s.uppercase()
    }

    fun HueOf(name: String?): Int {
        var h = 0
        for (c in (name ?: "")) {
            h = (h * 31 + c.code) % 360
        }
        return h
    }
}
