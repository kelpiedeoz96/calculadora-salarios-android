package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SpecificationBoundaryTest {
    @Test
    fun festivos_2026_y_2027_tienen_exactamente_18_dias_cada_uno() {
        val f2026 = FestivosCalc.ParaAnio(2026)
        val f2027 = FestivosCalc.ParaAnio(2027)
        assertEquals(18, f2026.size)
        assertEquals(18, f2027.size)
    }

    @Test
    fun cambio_dominical_1_julio_2027_vectores_de_prueba() {
        val emp = Employee(salario = 0.0, restDay = 0)
        val dAntes = LocalDate.of(2027, 6, 27) // Domingo antes del 1 de julio 2027
        val dDespues = LocalDate.of(2027, 7, 4) // Domingo después del 1 de julio 2027

        emp.days[Engine.Key(dAntes)] = DayRecord(in1 = "08:00", out1 = "12:00") // 4 horas = 240 min
        emp.days[Engine.Key(dDespues)] = DayRecord(in1 = "08:00", out1 = "12:00")

        val cfgNormal = Config()
        val aNormal = Engine.Analyze(emp, cfgNormal, emptySet(), LocalDate.of(2027, 6, 21), LocalDate.of(2027, 7, 11))
        val ordAntes = aNormal.rows.single { it.date == dAntes }.amounts[Cat.ODom.ordinal]
        val ordDespues = aNormal.rows.single { it.date == dDespues }.amounts[Cat.ODom.ordinal]

        // Vector 1: 30.016 al 90%
        assertEquals(30016L, ordAntes)
        // Vector 2: 33.352 al 100%
        assertEquals(33352L, ordDespues)

        // Vector 3: 36.688 al 110%
        val cfg110 = Config()
        cfg110.pct.dom = 110.0
        val a110 = Engine.Analyze(emp, cfg110, emptySet(), LocalDate.of(2027, 6, 21), LocalDate.of(2027, 7, 11))
        val ordAntes110 = a110.rows.single { it.date == dAntes }.amounts[Cat.ODom.ordinal]
        val ordDespues110 = a110.rows.single { it.date == dDespues }.amounts[Cat.ODom.ordinal]
        assertEquals(36688L, ordAntes110)
        assertEquals(36688L, ordDespues110)
    }

    @Test
    fun ejemplo_resuelto_semana_octubre_2026_recargos_y_neto() {
        val emp = Employee(salario = 1750905.0)
        val cfg = Config()
        val payroll = Engine.GetPayroll(emp, cfg, 181249L)

        assertEquals(875452L, payroll.basicoQ)
        assertEquals(124548L, payroll.auxQ)
        assertEquals(181249L, payroll.recargos)
        assertEquals(1181249L, payroll.devengado)
        assertEquals(875452.5, payroll.base)
        assertEquals(35018L, payroll.salud)
        assertEquals(35018L, payroll.pension)
        assertEquals(70036L, payroll.descuentos)
        assertEquals(1111213L, payroll.neto)
    }

    @Test
    fun orden_descuento_del_descanso_primero_diurno_luego_nocturno() {
        val cfg = Config(nightStart = "19:00", nightEnd = "06:00")
        // Turno de 17:00 a 21:00: 2 horas diurnas (17-19) y 2 horas nocturnas (19-21)
        // Descanso de 60 minutos: debe descontarse totalmente de las diurnas.
        val rec1 = DayRecord(in1 = "17:00", out1 = "21:00", brk = 60.0)
        val segs1 = Engine.DaySegments(rec1, cfg)
        val diurno1 = segs1.filter { !it.night }.sumOf { it.min }
        val nocturno1 = segs1.filter { it.night }.sumOf { it.min }
        assertEquals(60, diurno1) // 120 - 60 = 60 min diurnos
        assertEquals(120, nocturno1) // 120 min nocturnos intactos

        // Descanso de 150 minutos: descuenta los 120 diurnos y 30 nocturnos.
        val rec2 = DayRecord(in1 = "17:00", out1 = "21:00", brk = 150.0)
        val segs2 = Engine.DaySegments(rec2, cfg)
        val diurno2 = segs2.filter { !it.night }.sumOf { it.min }
        val nocturno2 = segs2.filter { it.night }.sumOf { it.min }
        assertEquals(0, diurno2)
        assertEquals(90, nocturno2) // 120 - 30 = 90 min nocturnos
    }

    @Test
    fun regla_de_salida_de_30_minutos() {
        val cfgOn = Config(minuteRuleOn = true, minValid = 30.0)
        val cfgOff = Config(minuteRuleOn = false)

        // 08:00 a 16:25: minutos salida = 25 < 30 -> trunca a 16:00 (480 min)
        val recMenor30 = DayRecord(in1 = "08:00", out1 = "16:25")
        val segsOn1 = Engine.DaySegments(recMenor30, cfgOn)
        assertEquals(480, segsOn1.sumOf { it.min })

        val segsOff1 = Engine.DaySegments(recMenor30, cfgOff)
        assertEquals(505, segsOff1.sumOf { it.min })

        // 08:00 a 16:30: minutos salida = 30 >= 30 -> conserva 16:30 (510 min)
        val rec30Exacto = DayRecord(in1 = "08:00", out1 = "16:30")
        val segsOn2 = Engine.DaySegments(rec30Exacto, cfgOn)
        assertEquals(510, segsOn2.sumOf { it.min })
    }

    @Test
    fun formato_de_dinero_positivo_y_negativo() {
        assertEquals("$ 1.234", Fmt.Money(1234L))
        assertEquals("-$ 1.234", Fmt.Money(-1234L))
        assertEquals("$ 0", Fmt.Money(0L))
        assertEquals("$ 1.750.905", Fmt.Money(1750905L))
        assertEquals("-$ 1.750.905", Fmt.Money(-1750905L))
    }

    @Test
    fun dailyCapOn_comportamiento() {
        val emp = Employee(salario = 0.0)
        val date = LocalDate.of(2026, 10, 5) // Lunes
        emp.days[Engine.Key(date)] = DayRecord(in1 = "08:00", out1 = "18:00") // 10 horas = 600 min

        val cfgSinCap = Config(dailyCapOn = false)
        val aSinCap = Engine.Analyze(emp, cfgSinCap, emptySet(), date, date)
        val rowSin = aSinCap.rows.single { it.date == date }
        assertEquals(0, rowSin.extraMin) // 600 min ordinarios porque tope semanal 42h no se ha alcanzado
        assertEquals(600, rowSin.cats[Cat.ODiu.ordinal])

        val cfgConCap = Config(dailyCapOn = true, dailyRef = 7.0) // 7h = 420 min
        val aConCap = Engine.Analyze(emp, cfgConCap, emptySet(), date, date)
        val rowCon = aConCap.rows.single { it.date == date }
        assertEquals(180, rowCon.extraMin) // 600 - 420 = 180 min extra
        assertEquals(420, rowCon.cats[Cat.ODiu.ordinal])
        assertEquals(180, rowCon.cats[Cat.HED.ordinal])
    }
}
