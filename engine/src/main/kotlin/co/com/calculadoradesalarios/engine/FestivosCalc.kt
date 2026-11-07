package co.com.calculadoradesalarios.engine

import java.time.LocalDate

object FestivosCalc {
    fun DomingoPascua(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(year, month, day)
    }

    private fun LunesSiguiente(d: LocalDate): LocalDate {
        val dow = d.dayOfWeek.value % 7
        val dias = (1 - dow + 7) % 7
        return d.plusDays(dias.toLong())
    }

    private fun Clave(d: LocalDate): String = "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)

    fun ParaAnio(year: Int): List<String> {
        val pascua = DomingoPascua(year)
        val set = mutableSetOf(
            LocalDate.of(year, 1, 1),
            LocalDate.of(year, 5, 1),
            LocalDate.of(year, 7, 20),
            LocalDate.of(year, 8, 7),
            LocalDate.of(year, 12, 8),
            LocalDate.of(year, 12, 25),
            pascua.minusDays(3),
            pascua.minusDays(2)
        )

        val traslados = listOf(
            1 to 6,
            3 to 19,
            6 to 29,
            8 to 15,
            10 to 12,
            11 to 1,
            11 to 11
        )
        for ((mes, dia) in traslados) {
            set.add(LunesSiguiente(LocalDate.of(year, mes, dia)))
        }

        for (offset in listOf(39L, 60L, 68L)) {
            set.add(LunesSiguiente(pascua.plusDays(offset)))
        }

        return set.map { Clave(it) }.sorted()
    }

    fun AgregarAnio(festivos: MutableList<String>, year: Int): Int {
        val existentes = festivos.toMutableSet()
        var agregados = 0
        for (f in ParaAnio(year)) {
            if (existentes.add(f)) {
                festivos.add(f)
                agregados++
            }
        }
        if (agregados > 0) {
            festivos.sort()
        }
        return agregados
    }

    fun CompletarAnios(festivos: MutableList<String>, anios: Iterable<Int>): Int {
        var total = 0
        for (year in anios.distinct()) {
            val prefijo = "%04d-".format(year)
            if (festivos.any { it.startsWith(prefijo) }) continue
            total += AgregarAnio(festivos, year)
        }
        return total
    }
}
