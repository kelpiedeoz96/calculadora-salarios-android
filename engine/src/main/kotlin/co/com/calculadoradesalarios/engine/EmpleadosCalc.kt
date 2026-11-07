package co.com.calculadoradesalarios.engine

data class EmpleadosTotales(
    val totalMin: Int,
    val nocMin: Int,
    val domMin: Int,
    val extraMin: Int,
    val recargosAmount: Long,
    val basicoQ: Long,
    val auxQ: Long,
    val descuentos: Long,
    val neto: Long
)

object EmpleadosCalc {
    fun Matches(emp: Employee, query: String?): Boolean {
        if (query.isNullOrBlank()) return true
        val q = query.trim().lowercase()
        val haystack = "${emp.name} ${emp.cedula} ${emp.cargo}".lowercase()
        return haystack.contains(q)
    }

    fun SumCats(a: Analysis, vararg cats: Cat): Int {
        var sum = 0
        for (c in cats) {
            sum += a.totMin[c.ordinal]
        }
        return sum
    }

    fun CalcularTotales(summaries: Iterable<EmpSummary>, inclSueldo: Boolean): EmpleadosTotales {
        var totalMin = 0
        var nocMin = 0
        var domMin = 0
        var extraMin = 0
        var recargos = 0L
        var basico = 0L
        var aux = 0L
        var desc = 0L
        var neto = 0L

        for (s in summaries) {
            totalMin += s.r.totalMin
            nocMin += SumCats(s.r, Cat.ONoc)
            domMin += SumCats(s.r, Cat.ODom, Cat.ONocDom)
            extraMin += SumCats(s.r, Cat.HED, Cat.HEN, Cat.HEDDom, Cat.HENDom)
            recargos += s.r.totalAmount
            if (inclSueldo) {
                basico += s.p.basicoQ
                aux += s.p.auxQ
                desc += s.p.descuentos
                neto += s.p.neto
            }
        }

        return EmpleadosTotales(
            totalMin = totalMin,
            nocMin = nocMin,
            domMin = domMin,
            extraMin = extraMin,
            recargosAmount = recargos,
            basicoQ = basico,
            auxQ = aux,
            descuentos = desc,
            neto = neto
        )
    }
}
