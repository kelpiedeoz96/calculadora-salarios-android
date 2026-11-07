package co.com.calculadoradesalarios.engine

import java.util.Locale

data class PanelResultados(
    val empleadosCount: Int,
    val totalMin: Int,
    val totalAmount: Long,
    val extraMin: Int,
    val nightMin: Int,
    val domMin: Int,
    val conExtra: Int,
    val minutosPorCategoria: IntArray,
    val maxCategoria: Int,
    val alertas: List<String>,
    val top5: List<EmpSummary>,
    val promedio: Long,
    val pctExtraTexto: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PanelResultados
        return empleadosCount == other.empleadosCount &&
                totalMin == other.totalMin &&
                totalAmount == other.totalAmount &&
                extraMin == other.extraMin &&
                nightMin == other.nightMin &&
                domMin == other.domMin &&
                conExtra == other.conExtra &&
                minutosPorCategoria.contentEquals(other.minutosPorCategoria) &&
                maxCategoria == other.maxCategoria &&
                alertas == other.alertas &&
                top5 == other.top5 &&
                promedio == other.promedio &&
                pctExtraTexto == other.pctExtraTexto
    }

    override fun hashCode(): Int {
        var result = empleadosCount
        result = 31 * result + totalMin
        result = 31 * result + totalAmount.hashCode()
        result = 31 * result + extraMin
        result = 31 * result + nightMin
        result = 31 * result + domMin
        result = 31 * result + conExtra
        result = 31 * result + minutosPorCategoria.contentHashCode()
        result = 31 * result + maxCategoria
        result = 31 * result + alertas.hashCode()
        result = 31 * result + top5.hashCode()
        result = 31 * result + promedio.hashCode()
        result = 31 * result + pctExtraTexto.hashCode()
        return result
    }
}

object PanelCalc {
    fun Calcular(summaries: Iterable<EmpSummary>): PanelResultados {
        val list = summaries.toList()
        val empCount = list.size
        var totalMin = 0
        var totalAmount = 0L
        var totalExtra = 0
        var totalNight = 0
        var totalDom = 0
        var withExtra = 0
        val catTotals = IntArray(8)
        val alerts = mutableListOf<String>()

        for (s in list) {
            totalMin += s.r.totalMin
            totalAmount += s.r.totalAmount
            val empExtra = s.r.totMin[Cat.HED.ordinal] +
                    s.r.totMin[Cat.HEN.ordinal] +
                    s.r.totMin[Cat.HEDDom.ordinal] +
                    s.r.totMin[Cat.HENDom.ordinal]
            totalExtra += empExtra
            if (empExtra > 0) withExtra++
            totalNight += s.r.totMin[Cat.ONoc.ordinal] + s.r.totMin[Cat.ONocDom.ordinal]
            totalDom += s.r.totMin[Cat.ODom.ordinal] + s.r.totMin[Cat.ONocDom.ordinal]

            for (i in 0 until 8) {
                catTotals[i] += s.r.totMin[i]
            }

            val name = if (s.emp.name.isBlank()) "Sin nombre" else s.emp.name
            for (a in s.r.alerts) {
                alerts.add("$name: $a")
            }
        }

        val maxCat = Math.max(1, catTotals.maxOrNull() ?: 1)
        val promedio = if (empCount > 0) Engine.JsRound(totalAmount.toDouble() / empCount).toLong() else 0L
        val pctExtra = if (totalMin > 0) {
            "%.1f%% del total".format(Locale.US, totalExtra * 100.0 / totalMin)
        } else {
            "—"
        }

        val top5 = list.sortedByDescending { it.r.totalAmount }.take(5)

        return PanelResultados(
            empleadosCount = empCount,
            totalMin = totalMin,
            totalAmount = totalAmount,
            extraMin = totalExtra,
            nightMin = totalNight,
            domMin = totalDom,
            conExtra = withExtra,
            minutosPorCategoria = catTotals,
            maxCategoria = maxCat,
            alertas = alerts,
            top5 = top5,
            promedio = promedio,
            pctExtraTexto = pctExtra
        )
    }
}
