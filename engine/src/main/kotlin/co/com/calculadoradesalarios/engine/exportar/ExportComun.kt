package co.com.calculadoradesalarios.engine.exportar

import co.com.calculadoradesalarios.engine.Analysis
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.engine.DayRecord
import co.com.calculadoradesalarios.engine.DayRow
import co.com.calculadoradesalarios.engine.EmpSummary
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.Fmt
import java.time.LocalDate

/** Ayudas de Export.cs del escritorio, compartidas por el PDF y el Excel. */
object ExportComun {
    /** Las 7 categorías que se pagan (todas menos la ordinaria diurna). */
    val Payc = listOf(Cat.ONoc, Cat.ODom, Cat.ONocDom, Cat.HED, Cat.HEN, Cat.HEDDom, Cat.HENDom)

    fun Lbl(c: Cat): String = Engine.CatLabel.getValue(c).replace(" (incluida en el salario)", "")

    /** Horas decimales con 2 decimales: JsRound(min / 60 * 100) / 100. */
    fun Dec(min: Int): Double = Engine.JsRound(min / 60.0 * 100) / 100

    fun DayType(r: DayRow): String = when {
        r.rest && r.isFest -> "Descanso y festivo"
        r.rest -> "Descanso"
        r.isFest -> "Festivo"
        else -> "Normal"
    }

    fun ShiftText(r: DayRecord?): String {
        if (r == null) return ""
        val partes = mutableListOf<String>()
        if (!r.in1.isNullOrEmpty() && !r.out1.isNullOrEmpty()) partes += r.in1 + "-" + r.out1
        if (!r.in2.isNullOrEmpty() && !r.out2.isNullOrEmpty()) partes += r.in2 + "-" + r.out2
        return partes.joinToString(" / ")
    }

    fun DetailRows(a: Analysis): List<DayRow> = a.rows.filter { it.inPeriod && (it.dayMin > 0 || it.rest) }

    fun Money(v: Double): String = Fmt.Money(Engine.JsRound(v).toLong())

    fun resumenes(st: AppState, emps: List<Employee>, desde: LocalDate, hasta: LocalDate, incl: Boolean): List<EmpSummary> {
        val fest = st.festivos.toHashSet()
        return emps.map { e ->
            val a = Engine.Analyze(e, st.cfg, fest, desde, hasta)
            val p = Engine.GetPayroll(e, st.cfg, a.totalAmount)
            EmpSummary(e, a, p, if (incl) p.neto else a.totalAmount)
        }
    }

    private val Meses = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    /** "07 de noviembre de 2026", como fechaHoy del escritorio. */
    fun fechaLarga(d: LocalDate): String = "%02d de %s de %d".format(d.dayOfMonth, Meses[d.monthValue - 1], d.year)

    /** Como "{0:0.##}" de .NET con cultura invariante. */
    fun num2(v: Double): String = java.math.BigDecimal(v).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** Como double.ToString() de .NET para los valores habituales: 30 -> "30", 4.5 -> "4.5". */
    fun numNet(v: Double): String = if (v == Math.rint(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
}
