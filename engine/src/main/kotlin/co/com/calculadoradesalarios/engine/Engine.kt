package co.com.calculadoradesalarios.engine

import java.time.LocalDate

object Engine {
    val Cats: Array<Cat> = Cat.entries.toTypedArray()

    val CatLabel: Map<Cat, String> = mapOf(
        Cat.ODiu to "Ordinaria diurna (incluida en el salario)",
        Cat.ONoc to "Ordinaria nocturna",
        Cat.ODom to "Ordinaria dominical/festiva/descanso",
        Cat.ONocDom to "Ordinaria nocturna dominical/festiva/descanso",
        Cat.HED to "Extra diurna",
        Cat.HEN to "Extra nocturna",
        Cat.HEDDom to "Extra diurna dominical/festiva/descanso",
        Cat.HENDom to "Extra nocturna dominical/festiva/descanso"
    )

    val CatShort: Map<Cat, String> = mapOf(
        Cat.ODiu to "Ord",
        Cat.ONoc to "Noc",
        Cat.ODom to "Dom",
        Cat.ONocDom to "Noc-Dom",
        Cat.HED to "HED",
        Cat.HEN to "HEN",
        Cat.HEDDom to "HED-Dom",
        Cat.HENDom to "HEN-Dom"
    )

    val Dow = arrayOf("dom", "lun", "mar", "mié", "jue", "vie", "sáb")
    val DowFull = arrayOf("domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado")

    fun JsRound(x: Double): Double = Math.floor(x + 0.5)

    fun Key(d: LocalDate): String = "%04d-%02d-%02d".format(d.year, d.monthValue, d.dayOfMonth)

    fun Weekday(d: LocalDate): Int = d.dayOfWeek.value % 7

    fun MondayOf(d: LocalDate): LocalDate = d.minusDays(((Weekday(d) + 6) % 7).toLong())

    private fun Dm(d: LocalDate): String = "%02d/%02d".format(d.dayOfMonth, d.monthValue)

    private val HmRx = Regex("""^[0-9]{1,2}:[0-9]{2}${'$'}""")

    fun ParseHM(s: String?): Int? {
        if (s.isNullOrEmpty() || !HmRx.matches(s)) return null
        val p = s.split(':')
        return p[0].toInt() * 60 + p[1].toInt()
    }

    fun FmtHM(min: Int): String =
        "${min / 60}:%02d".format(JsRound((min % 60).toDouble()).toInt())

    private fun IsNight(t: Int, ns: Int, ne: Int): Boolean {
        val m = ((t % 1440) + 1440) % 1440
        return if (ns > ne) (m >= ns || m < ne) else (m >= ns && m < ne)
    }

    fun DaySegments(rec: DayRecord, cfg: Config): List<Seg> {
        val segs = mutableListOf<Seg>()
        val ns = ParseHM(cfg.nightStart) ?: 0
        val ne = ParseHM(cfg.nightEnd) ?: 0
        val intervals = listOf(rec.in1 to rec.out1, rec.in2 to rec.out2)

        for ((i, o) in intervals) {
            val a = ParseHM(i)
            val bn = ParseHM(o)
            if (a == null || bn == null) continue
            var b = bn
            if (b <= a) b += 1440
            if (cfg.minuteRuleOn) {
                val mm = b % 60
                if (mm < cfg.minValid) b -= mm
            }
            if (b <= a) continue

            for (t in a until b) {
                val n = IsNight(t, ns, ne)
                val last = segs.lastOrNull()
                if (last != null && last.night == n) {
                    last.min++
                } else {
                    segs.add(Seg(min = 1, night = n))
                }
            }
        }

        var brk = Math.max(0, rec.brk.toInt())
        for (pass in listOf(false, true)) {
            for (s in segs) {
                if (s.night == pass && brk > 0) {
                    val d = Math.min(s.min, brk)
                    s.min -= d
                    brk -= d
                }
            }
        }
        return segs.filter { it.min > 0 }
    }

    fun IsRest(emp: Employee, date: LocalDate, rec: DayRecord?): Boolean =
        rec?.desc ?: (emp.restDay != null && Weekday(date) == emp.restDay)

    val DominicalCienDesde: LocalDate = LocalDate.of(2027, 7, 1)

    fun EffectiveDom(cfg: Config, date: LocalDate): Double {
        var dom = cfg.pct.dom
        if (cfg.domAutoOn && !date.isBefore(DominicalCienDesde)) {
            dom = Math.max(dom, 100.0)
        }
        return dom
    }

    fun GetRates(emp: Employee, cfg: Config): Rates = BuildRates(emp, cfg, cfg.pct.dom)

    fun GetRates(emp: Employee, cfg: Config, date: LocalDate): Rates =
        BuildRates(emp, cfg, EffectiveDom(cfg, date))

    private fun BuildRates(emp: Employee, cfg: Config, domPct: Double): Rates {
        val sal = if (emp.salario > 0) emp.salario else cfg.smmlv
        var hv = sal / cfg.divisor
        if (cfg.rateRound) hv = JsRound(hv)
        val nPct = cfg.pct.noc / 100.0
        val dPct = domPct / 100.0
        val aPct = cfg.pct.hed / 100.0
        val bPct = cfg.pct.hen / 100.0

        val r = Rates(hv = hv)
        r.byCat[Cat.ODiu.ordinal] = 0.0
        r.byCat[Cat.ONoc.ordinal] = hv * nPct
        r.byCat[Cat.ODom.ordinal] = hv * dPct
        r.byCat[Cat.ONocDom.ordinal] = hv * (dPct + nPct)
        r.byCat[Cat.HED.ordinal] = hv * (1.0 + aPct)
        r.byCat[Cat.HEN.ordinal] = hv * (1.0 + bPct)
        r.byCat[Cat.HEDDom.ordinal] = hv * (1.0 + dPct + aPct)
        r.byCat[Cat.HENDom.ordinal] = hv * (1.0 + dPct + bPct)

        if (cfg.rateRound) {
            for (i in 0 until 8) {
                r.byCat[i] = JsRound(r.byCat[i])
            }
        }
        return r
    }

    fun Analyze(
        emp: Employee,
        cfg: Config,
        fest: Set<String>,
        from: LocalDate,
        to: LocalDate
    ): Analysis {
        val rtCache = mutableMapOf<Double, Rates>()
        fun ratesFor(d: LocalDate): Rates {
            val dom = EffectiveDom(cfg, d)
            return rtCache.getOrPut(dom) { BuildRates(emp, cfg, dom) }
        }

        val res = Analysis(rates = ratesFor(to))
        val capMin = JsRound(cfg.weeklyCap * 60.0).toInt()
        val refMin = JsRound(cfg.dailyRef * 60.0).toInt()
        val iHED = Cat.HED.ordinal
        val iHEN = Cat.HEN.ordinal
        val iHEDD = Cat.HEDDom.ordinal
        val iHEND = Cat.HENDom.ordinal

        var w = MondayOf(from)
        while (!w.isAfter(to)) {
            var acc = 0
            var wkWorked = 0
            var wkExtra = 0
            val missing = mutableListOf<LocalDate>()
            var straddle = false

            for (i in 0 until 7) {
                val date = w.plusDays(i.toLong())
                if (date.isAfter(to)) break
                val rec = emp.days[Key(date)]
                val rest = IsRest(emp, date, rec)
                val isFest = fest.contains(Key(date))
                val dom = rest || isFest
                val inPeriod = !date.isBefore(from) && !date.isAfter(to)
                if (!inPeriod) straddle = true

                val segs = if (rec != null) DaySegments(rec, cfg) else emptyList()
                val cats = IntArray(8)
                var dayOrd = 0

                for (s in segs) {
                    val remW = Math.max(0, capMin - acc)
                    val remD = if (cfg.dailyCapOn) Math.max(0, refMin - dayOrd) else Int.MAX_VALUE
                    val ord = Math.min(s.min, Math.min(remW, remD))
                    val ext = s.min - ord
                    acc += ord
                    dayOrd += ord

                    if (ord > 0) {
                        val cat = if (dom) {
                            if (s.night) Cat.ONocDom else Cat.ODom
                        } else {
                            if (s.night) Cat.ONoc else Cat.ODiu
                        }
                        cats[cat.ordinal] += ord
                    }
                    if (ext > 0) {
                        val cat = if (dom) {
                            if (s.night) Cat.HENDom else Cat.HEDDom
                        } else {
                            if (s.night) Cat.HEN else Cat.HED
                        }
                        cats[cat.ordinal] += ext
                    }
                }

                val dayMin = cats.sum()
                val extraMin = cats[iHED] + cats[iHEN] + cats[iHEDD] + cats[iHEND]
                wkWorked += dayMin
                wkExtra += extraMin

                val rt = ratesFor(date)
                val amounts = LongArray(8)
                var dayAmount = 0L

                for (c in 0 until 8) {
                    amounts[c] = JsRound(cats[c] / 60.0 * rt.byCat[c]).toLong()
                    dayAmount += amounts[c]
                }

                if (inPeriod) {
                    for (c in 0 until 8) {
                        res.totMin[c] += cats[c]
                        res.totAmount[c] += amounts[c]
                    }
                    res.totalMin += dayMin
                    res.totalAmount += dayAmount
                    if (extraMin > 120) {
                        res.alerts.add("Más de 2 horas extra el ${Dow[Weekday(date)]} ${Dm(date)} (${FmtHM(extraMin)}).")
                    }
                } else if (date.isBefore(from) && dayMin == 0 && !rest && rec == null) {
                    missing.add(date)
                }

                res.rows.add(
                    DayRow(
                        date = date,
                        dow = Weekday(date),
                        rec = rec,
                        rest = rest,
                        isFest = isFest,
                        dom = dom,
                        inPeriod = inPeriod,
                        cats = cats,
                        dayMin = dayMin,
                        extraMin = extraMin,
                        amounts = amounts,
                        dayAmount = dayAmount
                    )
                )
            }

            if (wkExtra > 720) {
                res.alerts.add("Más de 12 horas extra en la semana del ${Dm(w)} (${FmtHM(wkExtra)}).")
            }
            if (straddle && missing.isNotEmpty()) {
                res.alerts.add(
                    "Semana del ${Dm(w)}: sin datos en ${missing.joinToString(", ") { Dm(it) }}. El tope de 42 h se cuenta con la semana completa."
                )
            }

            val wEnd = w.plusDays(6)
            res.weeks.add(
                Week(
                    start = w,
                    end = if (wEnd.isAfter(to)) to else wEnd,
                    workedMin = wkWorked,
                    extraMin = wkExtra,
                    straddle = straddle
                )
            )

            w = w.plusDays(7)
        }

        return res
    }

    fun GetPayroll(emp: Employee, cfg: Config, recargos: Long): Payroll {
        val sal = if (emp.salario > 0) emp.salario else cfg.smmlv
        val auxOn = emp.aux && sal <= 2 * cfg.smmlv
        val auxQ = if (auxOn) JsRound(cfg.auxilio / 2.0).toLong() else 0L
        val totalQ = JsRound((sal + (if (auxOn) cfg.auxilio else 0.0)) / 2.0).toLong()
        val basicoQ = totalQ - auxQ
        val bas = sal / 2.0 + (if (cfg.deductBase == "con_recargos") recargos.toDouble() else 0.0)
        val salud = JsRound(bas * cfg.saludPct / 100.0).toLong()
        val pension = JsRound(bas * cfg.pensionPct / 100.0).toLong()
        val devengado = basicoQ + auxQ + recargos
        val descuentos = salud + pension

        return Payroll(
            sal = sal,
            auxOn = auxOn,
            basicoQ = basicoQ,
            auxQ = auxQ,
            recargos = recargos,
            devengado = devengado,
            base = bas,
            salud = salud,
            pension = pension,
            descuentos = descuentos,
            neto = devengado - descuentos
        )
    }
}
