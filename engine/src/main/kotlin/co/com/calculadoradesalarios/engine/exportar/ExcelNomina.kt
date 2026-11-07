package co.com.calculadoradesalarios.engine.exportar

import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.exportar.ExportComun.DayType
import co.com.calculadoradesalarios.engine.exportar.ExportComun.Dec
import co.com.calculadoradesalarios.engine.exportar.ExportComun.DetailRows
import co.com.calculadoradesalarios.engine.exportar.ExportComun.Lbl
import co.com.calculadoradesalarios.engine.exportar.ExportComun.Payc
import co.com.calculadoradesalarios.engine.exportar.ExportComun.ShiftText
import co.com.calculadoradesalarios.engine.exportar.ExportComun.numNet
import java.time.LocalDate

/** Hojas "Resumen", "Detalle diario" y "Parámetros", como Export.Excel del escritorio. */
object ExcelNomina {
    private fun t(s: String) = CeldaXlsx.Texto(s)
    private fun n(v: Double) = CeldaXlsx.Numero(v)
    private fun n(v: Long) = CeldaXlsx.Numero(v.toDouble())

    fun hojas(st: AppState, incl: Boolean, emps: List<Employee>, desde: LocalDate, hasta: LocalDate): List<HojaXlsx> {
        val cfg = st.cfg
        val lista = ExportComun.resumenes(st, emps, desde, hasta, incl)

        val head = listOf("Empleado", "Cédula", "Cargo", "Salario mensual", "Valor hora", "Horas totales").map(::t) +
            Engine.Cats.map { t("Horas: " + Lbl(it)) } +
            Payc.map { t("$ " + Engine.CatLabel.getValue(it)) } +
            t("Recargos + extras ($)") +
            (if (incl) listOf("Básico quincenal", "Auxilio", "Total devengado", "Salud", "Pensión", "Neto a pagar").map(::t) else emptyList())
        val resumen = listOf(head) + lista.map { s ->
            listOf(t(s.emp.name), t(s.emp.cedula), t(s.emp.cargo), n(s.p.sal), n(s.r.rates.hv), n(Dec(s.r.totalMin))) +
                Engine.Cats.map { n(Dec(s.r.totMin[it.ordinal])) } +
                Payc.map { n(s.r.totAmount[it.ordinal]) } +
                n(s.r.totalAmount) +
                (if (incl) listOf(n(s.p.basicoQ), n(s.p.auxQ), n(s.p.devengado), n(-s.p.salud), n(-s.p.pension), n(s.p.neto)) else emptyList())
        }

        val dh = listOf("Empleado", "Semana", "Fecha", "Día", "Tipo", "Jornada", "Descanso (min)", "Horas").map(::t) +
            Engine.Cats.map { t("H: " + Lbl(it)) } +
            Payc.map { t("$ " + Engine.CatLabel.getValue(it)) } +
            t("Total del día ($)")
        val detalle = listOf(dh) + lista.flatMap { s ->
            DetailRows(s.r).map { r ->
                listOf(t(s.emp.name), t(Engine.Key(Engine.MondayOf(r.date))), t(Engine.Key(r.date)), t(Engine.DowFull[r.dow]),
                    t(DayType(r)), t(ShiftText(r.rec)), n(r.rec?.brk ?: 0.0), n(Dec(r.dayMin))) +
                    Engine.Cats.map { n(Dec(r.cats[it.ordinal])) } +
                    Payc.map { n(r.amounts[it.ordinal]) } +
                    n(r.dayAmount)
            }
        }

        val a = Engine.EffectiveDom(cfg, desde)
        val b = Engine.EffectiveDom(cfg, hasta)
        val dominical: CeldaXlsx = if (a == b) n(a) else
            t("${numNet(a)} hasta ${Engine.Key(Engine.DominicalCienDesde.minusDays(1))}, ${numNet(b)} desde ${Engine.Key(Engine.DominicalCienDesde)}")
        val parametros = listOf(
            listOf(t("Parámetro"), t("Valor")),
            listOf(t("Periodo"), t(Engine.Key(desde) + " a " + Engine.Key(hasta))),
            listOf(t("Empresa"), t(cfg.razonSocial)),
            listOf(t("NIT"), t(if (cfg.nit.isBlank()) "—" else cfg.nit)),
            listOf(t("Tope semanal (h)"), n(cfg.weeklyCap)),
            listOf(t("Jornada diaria (h)"), n(cfg.dailyRef)),
            listOf(t("Tope diario como extra"), t(if (cfg.dailyCapOn) "Sí" else "No")),
            listOf(t("Nocturna"), t(cfg.nightStart + " a " + cfg.nightEnd)),
            listOf(t("Regla de minutos"), t(if (cfg.minuteRuleOn) "Salida <" + numNet(cfg.minValid) + "min → trunca a la hora" else "No")),
            listOf(t("Recargo nocturno %"), n(cfg.pct.noc)),
            listOf(t("Extra diurna %"), n(cfg.pct.hed)),
            listOf(t("Extra nocturna %"), n(cfg.pct.hen)),
            listOf(t("Dominical %"), dominical),
            listOf(t("Divisor valor hora"), n(cfg.divisor)),
            listOf(t("Salario mínimo"), n(cfg.smmlv)),
            listOf(t("Auxilio"), n(cfg.auxilio)),
            listOf(t("Salud %"), n(cfg.saludPct)),
            listOf(t("Pensión %"), n(cfg.pensionPct)),
            listOf(t("Descuentos sobre"), t(if (cfg.deductBase == "basico") "Básico" else "Básico + recargos")),
            listOf(t("Festivos"), t(st.festivos.joinToString(", "))),
        )
        return listOf(
            HojaXlsx("Resumen", resumen, listOf(26.0, 14.0, 20.0, 14.0, 11.0, 12.0)),
            HojaXlsx("Detalle diario", detalle, listOf(26.0, 13.0, 12.0, 11.0, 16.0, 24.0, 12.0, 12.0)),
            HojaXlsx("Parámetros", parametros, listOf(36.0, 60.0)),
        )
    }

    fun xlsx(st: AppState, incl: Boolean, emps: List<Employee>, desde: LocalDate, hasta: LocalDate): ByteArray =
        XlsxEscritor.escribir(hojas(st, incl, emps, desde, hasta))
}
