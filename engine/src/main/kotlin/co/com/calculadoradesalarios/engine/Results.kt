package co.com.calculadoradesalarios.engine

import java.time.LocalDate

data class Seg(
    var min: Int,
    var night: Boolean
)

data class Rates(
    var hv: Double = 0.0,
    val byCat: DoubleArray = DoubleArray(8)
) {
    operator fun get(c: Cat): Double = byCat[c.ordinal]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Rates
        return hv == other.hv && byCat.contentEquals(other.byCat)
    }

    override fun hashCode(): Int {
        var result = hv.hashCode()
        result = 31 * result + byCat.contentHashCode()
        return result
    }
}

data class DayRow(
    var date: LocalDate,
    var dow: Int,
    var rec: DayRecord?,
    var rest: Boolean,
    var isFest: Boolean,
    var dom: Boolean,
    var inPeriod: Boolean,
    val cats: IntArray = IntArray(8),
    var dayMin: Int = 0,
    var extraMin: Int = 0,
    val amounts: LongArray = LongArray(8),
    var dayAmount: Long = 0L
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DayRow
        return date == other.date &&
                dow == other.dow &&
                rec == other.rec &&
                rest == other.rest &&
                isFest == other.isFest &&
                dom == other.dom &&
                inPeriod == other.inPeriod &&
                cats.contentEquals(other.cats) &&
                dayMin == other.dayMin &&
                extraMin == other.extraMin &&
                amounts.contentEquals(other.amounts) &&
                dayAmount == other.dayAmount
    }

    override fun hashCode(): Int {
        var result = date.hashCode()
        result = 31 * result + dow
        result = 31 * result + (rec?.hashCode() ?: 0)
        result = 31 * result + rest.hashCode()
        result = 31 * result + isFest.hashCode()
        result = 31 * result + dom.hashCode()
        result = 31 * result + inPeriod.hashCode()
        result = 31 * result + cats.contentHashCode()
        result = 31 * result + dayMin
        result = 31 * result + extraMin
        result = 31 * result + amounts.contentHashCode()
        result = 31 * result + dayAmount.hashCode()
        return result
    }
}

data class Week(
    var start: LocalDate,
    var end: LocalDate,
    var workedMin: Int = 0,
    var extraMin: Int = 0,
    var straddle: Boolean = false
)

data class Analysis(
    val rows: MutableList<DayRow> = mutableListOf(),
    val weeks: MutableList<Week> = mutableListOf(),
    val alerts: MutableList<String> = mutableListOf(),
    val totMin: IntArray = IntArray(8),
    val totAmount: LongArray = LongArray(8),
    var totalMin: Int = 0,
    var totalAmount: Long = 0L,
    var rates: Rates = Rates()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Analysis
        return rows == other.rows &&
                weeks == other.weeks &&
                alerts == other.alerts &&
                totMin.contentEquals(other.totMin) &&
                totAmount.contentEquals(other.totAmount) &&
                totalMin == other.totalMin &&
                totalAmount == other.totalAmount &&
                rates == other.rates
    }

    override fun hashCode(): Int {
        var result = rows.hashCode()
        result = 31 * result + weeks.hashCode()
        result = 31 * result + alerts.hashCode()
        result = 31 * result + totMin.contentHashCode()
        result = 31 * result + totAmount.contentHashCode()
        result = 31 * result + totalMin
        result = 31 * result + totalAmount.hashCode()
        result = 31 * result + rates.hashCode()
        return result
    }
}

data class Payroll(
    var sal: Double = 0.0,
    var auxOn: Boolean = false,
    var basicoQ: Long = 0L,
    var auxQ: Long = 0L,
    var recargos: Long = 0L,
    var devengado: Long = 0L,
    var base: Double = 0.0,
    var salud: Long = 0L,
    var pension: Long = 0L,
    var descuentos: Long = 0L,
    var neto: Long = 0L
)

data class EmpSummary(
    val emp: Employee,
    val r: Analysis,
    val p: Payroll,
    val pay: Long
)
