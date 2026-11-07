package co.com.calculadoradesalarios.engine

import java.time.LocalDate
import java.time.YearMonth

data class DiaCalendario(
    val dia: Int,
    val fecha: LocalDate,
    val clave: String,
    val tieneFila: Boolean,
    val dayMin: Int,
    val extraMin: Int,
    val dom: Boolean,
    val festivo: Boolean,
    val enPeriodo: Boolean,
    val esHoy: Boolean,
    val fraccion: Double,
    val barraExtra: Boolean,
    val turnos: String
)

data class MesCalendario(
    val pad0: Int,
    val semanas: Int,
    val dias: List<DiaCalendario>
)

object CalendarioCalc {
    fun ObtenerTextoTurnos(rec: DayRecord?): String {
        if (rec == null) return ""
        val parts = mutableListOf<String>()
        if (!rec.in1.isNullOrBlank() && !rec.out1.isNullOrBlank()) {
            parts.add("${rec.in1}-${rec.out1}")
        }
        if (!rec.in2.isNullOrBlank() && !rec.out2.isNullOrBlank()) {
            parts.add("${rec.in2}-${rec.out2}")
        }
        return parts.joinToString(" / ")
    }

    fun Construir(
        year: Int,
        month: Int,
        rows: Iterable<DayRow>,
        pFrom: LocalDate,
        pTo: LocalDate,
        today: LocalDate
    ): MesCalendario {
        val ym = YearMonth.of(year, month)
        val diasDelMes = ym.lengthOfMonth()
        val dia1 = LocalDate.of(year, month, 1)
        val pad0 = (Engine.Weekday(dia1) + 6) % 7 // la semana empieza el lunes
        val semanas = (pad0 + diasDelMes + 6) / 7
        val rowDict = rows.associateBy { it.date }
        val dias = ArrayList<DiaCalendario>(diasDelMes)

        for (d in 1..diasDelMes) {
            val fecha = LocalDate.of(year, month, d)
            val clave = Engine.Key(fecha)
            val enPeriodo = !fecha.isBefore(pFrom) && !fecha.isAfter(pTo)
            val esHoy = fecha == today
            val row = rowDict[fecha]

            if (row != null) {
                var fraccion = 0.0
                var barraExtra = false
                if (row.extraMin > 0) {
                    fraccion = row.extraMin / 480.0
                    barraExtra = true
                } else if (row.dayMin > 0) {
                    fraccion = 1.0
                }
                dias.add(
                    DiaCalendario(
                        dia = d,
                        fecha = fecha,
                        clave = clave,
                        tieneFila = true,
                        dayMin = row.dayMin,
                        extraMin = row.extraMin,
                        dom = row.dom,
                        festivo = row.isFest,
                        enPeriodo = enPeriodo,
                        esHoy = esHoy,
                        fraccion = fraccion,
                        barraExtra = barraExtra,
                        turnos = ObtenerTextoTurnos(row.rec)
                    )
                )
            } else {
                dias.add(
                    DiaCalendario(
                        dia = d,
                        fecha = fecha,
                        clave = clave,
                        tieneFila = false,
                        dayMin = 0,
                        extraMin = 0,
                        dom = false,
                        festivo = false,
                        enPeriodo = enPeriodo,
                        esHoy = esHoy,
                        fraccion = 0.0,
                        barraExtra = false,
                        turnos = ""
                    )
                )
            }
        }
        return MesCalendario(pad0, semanas, dias)
    }
}
