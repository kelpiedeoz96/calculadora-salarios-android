package co.com.calculadoradesalarios.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate

class DominicalPorFechaTest {
    private val Domingo1 = LocalDate.of(2027, 6, 27)
    private val Domingo2 = LocalDate.of(2027, 7, 4)

    private fun EmpleadoConDomingos(): Employee {
        val emp = Employee(name = "Empleado prueba", salario = 0.0, restDay = 0)
        for (d in listOf(Domingo1, Domingo2)) {
            emp.days[Engine.Key(d)] = DayRecord(in1 = "08:00", out1 = "12:00")
        }
        return emp
    }

    private fun OrdDomingo(a: Analysis, d: LocalDate): Long =
        a.rows.single { it.date == d }.amounts[Cat.ODom.ordinal]

    private fun Analizar(cfg: Config, from: LocalDate, to: LocalDate): Analysis =
        Engine.Analyze(EmpleadoConDomingos(), cfg, emptySet(), from, to)

    @Test
    fun los_dias_de_prueba_son_domingo() {
        assertEquals(DayOfWeek.SUNDAY, Domingo1.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, Domingo2.dayOfWeek)
    }

    @Test
    fun effectiveDom_cambia_justo_el_1_de_julio_de_2027() {
        val cfg = Config()
        assertEquals(90.0, Engine.EffectiveDom(cfg, LocalDate.of(2027, 6, 30)))
        assertEquals(100.0, Engine.EffectiveDom(cfg, LocalDate.of(2027, 7, 1)))
        assertEquals(90.0, Engine.EffectiveDom(cfg, LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun antes_y_despues_del_cambio_se_paga_90_y_100() {
        val a = Analizar(Config(), LocalDate.of(2027, 6, 21), LocalDate.of(2027, 7, 11))
        assertEquals(30016L, OrdDomingo(a, Domingo1))
        assertEquals(33352L, OrdDomingo(a, Domingo2))
    }

    @Test
    fun con_el_cambio_automatico_apagado_sigue_en_90() {
        val cfg = Config(domAutoOn = false)
        val a = Analizar(cfg, LocalDate.of(2027, 6, 21), LocalDate.of(2027, 7, 11))
        assertEquals(30016L, OrdDomingo(a, Domingo1))
        assertEquals(30016L, OrdDomingo(a, Domingo2))
    }

    @Test
    fun un_porcentaje_mayor_a_100_configurado_se_respeta() {
        val cfg = Config()
        cfg.pct.dom = 110.0
        val a = Analizar(cfg, LocalDate.of(2027, 6, 21), LocalDate.of(2027, 7, 11))
        assertEquals(36688L, OrdDomingo(a, Domingo1))
        assertEquals(36688L, OrdDomingo(a, Domingo2))
    }

    @Test
    fun las_tarifas_mostradas_son_las_vigentes_al_final_del_periodo() {
        val antes = Analizar(Config(), LocalDate.of(2027, 6, 21), LocalDate.of(2027, 6, 30))
        assertEquals(7504.0, antes.rates[Cat.ODom])

        val cruza = Analizar(Config(), LocalDate.of(2027, 6, 21), LocalDate.of(2027, 7, 11))
        assertEquals(8338.0, cruza.rates[Cat.ODom])
    }

    @Test
    fun getRates_sin_fecha_no_cambia_y_con_fecha_antigua_es_identico() {
        val emp = Employee()
        val cfg = Config()
        val sinFecha = Engine.GetRates(emp, cfg)
        val conFecha = Engine.GetRates(emp, cfg, LocalDate.of(2026, 10, 15))
        for (i in 0 until 8) {
            assertEquals(sinFecha.byCat[i], conFecha.byCat[i])
        }
        assertEquals(sinFecha.hv, conFecha.hv)
    }

    @Test
    fun una_semana_normal_de_2026_no_cambia_con_la_regla_nueva() {
        val emp = EmpleadoConDomingos()
        emp.days["2026-10-04"] = DayRecord(in1 = "08:00", out1 = "12:00")
        val a = Engine.Analyze(emp, Config(), emptySet(), LocalDate.of(2026, 9, 28), LocalDate.of(2026, 10, 11))
        assertEquals(30016L, a.rows.single { it.date == LocalDate.of(2026, 10, 4) }.amounts[Cat.ODom.ordinal])
    }
}
