package co.com.calculadoradesalarios.engine

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object AjustesCalc {
    private val HoraRegex = Regex("""^([01]?\d|2[0-3]):?([0-5]\d)$""")
    private val TurnoRegex = Regex("""^(\d{2}):(\d{2})-(\d{2}):(\d{2})$""")
    private val FestivoRegex = Regex("""\d{4}-\d{2}-\d{2}""")
    private val DateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun TryParseNumero(texto: String?): Pair<Boolean, Double> {
        if (texto.isNullOrBlank()) return Pair(true, 0.0)
        val limpio = texto.trim().replace(',', '.')
        val parsed = limpio.toDoubleOrNull() ?: return Pair(false, 0.0)
        if (!parsed.isFinite()) return Pair(false, 0.0)
        return Pair(true, parsed)
    }

    fun FormatearNumero(v: Double): String {
        return if (v == Math.floor(v) && v.isFinite()) {
            v.toLong().toString()
        } else {
            v.toString()
        }
    }

    fun TryParseHora(texto: String?): Pair<Boolean, String> {
        if (texto.isNullOrBlank()) return Pair(false, "")
        val m = HoraRegex.matchEntire(texto.trim()) ?: return Pair(false, "")
        val h = m.groupValues[1].toInt()
        val min = m.groupValues[2].toInt()
        return Pair(true, "%02d:%02d".format(h, min))
    }

    fun ParseTurnos(texto: String?): List<String> {
        val resultado = mutableListOf<String>()
        if (texto.isNullOrBlank()) return resultado

        for (linea in texto.split(Regex("""\r\n|\r|\n"""))) {
            val l = linea.trim()
            val m = TurnoRegex.matchEntire(l) ?: continue
            val h1 = m.groupValues[1].toInt()
            val m1 = m.groupValues[2].toInt()
            val h2 = m.groupValues[3].toInt()
            val m2 = m.groupValues[4].toInt()
            if (h1 <= 23 && m1 <= 59 && h2 <= 23 && m2 <= 59) {
                resultado.add(l)
            }
        }
        return resultado
    }

    fun ParseFestivos(texto: String?): List<String> {
        if (texto.isNullOrBlank()) return emptyList()

        val encontrados = mutableSetOf<String>()
        for (m in FestivoRegex.findAll(texto)) {
            val candidate = m.value
            try {
                val d = LocalDate.parse(candidate, DateFormat)
                val formatted = "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)
                if (candidate == formatted) {
                    encontrados.add(candidate)
                }
            } catch (_: Exception) {
            }
        }
        return encontrados.sorted()
    }

    fun UnirLineas(lineas: Iterable<String>?): String =
        lineas?.joinToString("\n") ?: ""
}
