package co.com.calculadoradesalarios.engine

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.LocalDate

@Serializable
data class TotCatDto(
    val min: Int = 0,
    val amount: Long = 0L
)

@Serializable
data class TarifaExpectedDto(
    val hv: Double = 0.0,
    @kotlinx.serialization.SerialName("ODiu") val oDiu: Double = 0.0,
    @kotlinx.serialization.SerialName("ONoc") val oNoc: Double = 0.0,
    @kotlinx.serialization.SerialName("ODom") val oDom: Double = 0.0,
    @kotlinx.serialization.SerialName("ONocDom") val oNocDom: Double = 0.0,
    @kotlinx.serialization.SerialName("HED") val hed: Double = 0.0,
    @kotlinx.serialization.SerialName("HEN") val hen: Double = 0.0,
    @kotlinx.serialization.SerialName("HEDDom") val hedDom: Double = 0.0,
    @kotlinx.serialization.SerialName("HENDom") val henDom: Double = 0.0
)

@Serializable
data class PayrollExpectedDto(
    val sal: Double = 0.0,
    val auxOn: Boolean = false,
    val basicoQ: Long = 0L,
    val auxQ: Long = 0L,
    val recargos: Long = 0L,
    val devengado: Long = 0L,
    val base: Double = 0.0,
    val salud: Long = 0L,
    val pension: Long = 0L,
    val descuentos: Long = 0L,
    val neto: Long = 0L
)

@Serializable
data class WeekExpectedDto(
    val start: String = "",
    val end: String = "",
    val workedMin: Int = 0,
    val extraMin: Int = 0,
    val straddle: Boolean = false
)

@Serializable
data class DayExpectedDto(
    val fecha: String = "",
    val dow: Int = 0,
    val descanso: Boolean = false,
    val festivo: Boolean = false,
    val dominical: Boolean = false,
    val enPeriodo: Boolean = false,
    val minPorCategoria: Map<String, Int> = emptyMap(),
    val dayMin: Int = 0,
    val extraMin: Int = 0,
    val valorPorCategoria: Map<String, Long> = emptyMap(),
    val dayAmount: Long = 0L
)

@Serializable
data class EmpleadoExpectedDto(
    val nombre: String = "",
    val tarifas: TarifaExpectedDto = TarifaExpectedDto(),
    val totales: Map<String, TotCatDto> = emptyMap(),
    val totalMin: Int = 0,
    val totalRecargos: Long = 0L,
    val nomina: PayrollExpectedDto = PayrollExpectedDto(),
    val semanas: List<WeekExpectedDto> = emptyList(),
    val alertas: List<String> = emptyList(),
    val dias: List<DayExpectedDto> = emptyList()
)

@Serializable
data class ScenarioDto(
    val escenario: String = "",
    val periodo: List<String> = emptyList(),
    val incluirSueldo: Boolean = false,
    val cfgAplicada: Config = Config(),
    val empleados: List<EmpleadoExpectedDto> = emptyList()
)

@Serializable
data class OracleRootDto(
    val descripcion: String = "",
    val escenarios: List<ScenarioDto> = emptyList()
)

class RegressionOracleTest {
    private fun loadResourceText(fileName: String): String {
        val stream = javaClass.classLoader.getResourceAsStream("TestData/$fileName")
            ?: java.io.File("engine/src/test/resources/TestData/$fileName").inputStream()
        return stream.bufferedReader().use { it.readText() }
    }

    private fun loadOracle(): OracleRootDto {
        val json = loadResourceText("salida_esperada.json")
        return JsonConfig.json.decodeFromString(OracleRootDto.serializer(), json)
    }

    private fun loadBackupState(): AppState {
        val json = loadResourceText("respaldo_sintetico.json")
        return JsonConfig.json.decodeFromString(AppState.serializer(), json)
    }

    private fun <T> checkEqual(expected: T, actual: T, scenario: String, employee: String, detail: String) {
        assertEquals(
            expected,
            actual,
            "[Fallo Escenario: $scenario] [Empleado: $employee] [$detail] -> Esperado: $expected, Obtenido: $actual"
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3, 4])
    fun escenario_DebeReproducirExactamenteElOraculo(scenarioIndex: Int) {
        val oracle = loadOracle()
        assertTrue(scenarioIndex in 0 until oracle.escenarios.size)
        val sc = oracle.escenarios[scenarioIndex]
        val backupState = loadBackupState()
        val festSet = backupState.festivos.toSet()
        val from = LocalDate.parse(sc.periodo[0])
        val to = LocalDate.parse(sc.periodo[1])
        val cfg = sc.cfgAplicada

        for (expectedEmp in sc.empleados) {
            val actualEmp = backupState.emps.firstOrNull { it.name == expectedEmp.nombre }
            assertNotNull(actualEmp, "[Escenario: ${sc.escenario}] Empleado '${expectedEmp.nombre}' no encontrado.")
            val analysis = Engine.Analyze(actualEmp!!, cfg, festSet, from, to)
            val payroll = Engine.GetPayroll(actualEmp, cfg, analysis.totalAmount)

            checkEqual(expectedEmp.totalMin, analysis.totalMin, sc.escenario, expectedEmp.nombre, "TotalMin")
            checkEqual(expectedEmp.totalRecargos, analysis.totalAmount, sc.escenario, expectedEmp.nombre, "TotalRecargos ($)")

            checkEqual(expectedEmp.tarifas.hv, analysis.rates.hv, sc.escenario, expectedEmp.nombre, "Tarifa Hv")
            checkEqual(expectedEmp.tarifas.oDiu, analysis.rates[Cat.ODiu], sc.escenario, expectedEmp.nombre, "Tarifa ODiu")
            checkEqual(expectedEmp.tarifas.oNoc, analysis.rates[Cat.ONoc], sc.escenario, expectedEmp.nombre, "Tarifa ONoc")
            checkEqual(expectedEmp.tarifas.oDom, analysis.rates[Cat.ODom], sc.escenario, expectedEmp.nombre, "Tarifa ODom")
            checkEqual(expectedEmp.tarifas.oNocDom, analysis.rates[Cat.ONocDom], sc.escenario, expectedEmp.nombre, "Tarifa ONocDom")
            checkEqual(expectedEmp.tarifas.hed, analysis.rates[Cat.HED], sc.escenario, expectedEmp.nombre, "Tarifa HED")
            checkEqual(expectedEmp.tarifas.hen, analysis.rates[Cat.HEN], sc.escenario, expectedEmp.nombre, "Tarifa HEN")
            checkEqual(expectedEmp.tarifas.hedDom, analysis.rates[Cat.HEDDom], sc.escenario, expectedEmp.nombre, "Tarifa HEDDom")
            checkEqual(expectedEmp.tarifas.henDom, analysis.rates[Cat.HENDom], sc.escenario, expectedEmp.nombre, "Tarifa HENDom")

            for (c in Engine.Cats) {
                val catName = c.name
                val expCat = expectedEmp.totales[catName]!!
                checkEqual(expCat.min, analysis.totMin[c.ordinal], sc.escenario, expectedEmp.nombre, "TotMin $catName")
                checkEqual(expCat.amount, analysis.totAmount[c.ordinal], sc.escenario, expectedEmp.nombre, "TotAmount $catName")
            }

            checkEqual(expectedEmp.nomina.sal, payroll.sal, sc.escenario, expectedEmp.nombre, "Nomina Salario")
            checkEqual(expectedEmp.nomina.auxOn, payroll.auxOn, sc.escenario, expectedEmp.nombre, "Nomina AuxOn")
            checkEqual(expectedEmp.nomina.basicoQ, payroll.basicoQ, sc.escenario, expectedEmp.nombre, "Nomina BasicoQ")
            checkEqual(expectedEmp.nomina.auxQ, payroll.auxQ, sc.escenario, expectedEmp.nombre, "Nomina AuxQ")
            checkEqual(expectedEmp.nomina.recargos, payroll.recargos, sc.escenario, expectedEmp.nombre, "Nomina Recargos")
            checkEqual(expectedEmp.nomina.devengado, payroll.devengado, sc.escenario, expectedEmp.nombre, "Nomina Devengado")
            checkEqual(expectedEmp.nomina.base, payroll.base, sc.escenario, expectedEmp.nombre, "Nomina Base")
            checkEqual(expectedEmp.nomina.salud, payroll.salud, sc.escenario, expectedEmp.nombre, "Nomina Salud")
            checkEqual(expectedEmp.nomina.pension, payroll.pension, sc.escenario, expectedEmp.nombre, "Nomina Pension")
            checkEqual(expectedEmp.nomina.descuentos, payroll.descuentos, sc.escenario, expectedEmp.nombre, "Nomina Descuentos")
            checkEqual(expectedEmp.nomina.neto, payroll.neto, sc.escenario, expectedEmp.nombre, "Nomina Neto")

            checkEqual(expectedEmp.semanas.size, analysis.weeks.size, sc.escenario, expectedEmp.nombre, "Cant Semanas")
            for (w in expectedEmp.semanas.indices) {
                val expW = expectedEmp.semanas[w]
                val actW = analysis.weeks[w]
                checkEqual(expW.start, Engine.Key(actW.start), sc.escenario, expectedEmp.nombre, "Semana $w Start")
                checkEqual(expW.end, Engine.Key(actW.end), sc.escenario, expectedEmp.nombre, "Semana $w End")
                checkEqual(expW.workedMin, actW.workedMin, sc.escenario, expectedEmp.nombre, "Semana $w WorkedMin")
                checkEqual(expW.extraMin, actW.extraMin, sc.escenario, expectedEmp.nombre, "Semana $w ExtraMin")
                checkEqual(expW.straddle, actW.straddle, sc.escenario, expectedEmp.nombre, "Semana $w Straddle")
            }

            checkEqual(expectedEmp.alertas.size, analysis.alerts.size, sc.escenario, expectedEmp.nombre, "Cant Alertas")
            for (a in expectedEmp.alertas.indices) {
                checkEqual(expectedEmp.alertas[a], analysis.alerts[a], sc.escenario, expectedEmp.nombre, "Alerta $a")
            }

            checkEqual(expectedEmp.dias.size, analysis.rows.size, sc.escenario, expectedEmp.nombre, "Cant Dias")
            for (d in expectedEmp.dias.indices) {
                val expD = expectedEmp.dias[d]
                val actD = analysis.rows[d]
                val f = expD.fecha
                checkEqual(expD.fecha, Engine.Key(actD.date), sc.escenario, expectedEmp.nombre, "Dia $f Fecha")
                checkEqual(expD.dow, actD.dow, sc.escenario, expectedEmp.nombre, "Dia $f Dow")
                checkEqual(expD.descanso, actD.rest, sc.escenario, expectedEmp.nombre, "Dia $f Rest")
                checkEqual(expD.festivo, actD.isFest, sc.escenario, expectedEmp.nombre, "Dia $f Fest")
                checkEqual(expD.dominical, actD.dom, sc.escenario, expectedEmp.nombre, "Dia $f Dom")
                checkEqual(expD.enPeriodo, actD.inPeriod, sc.escenario, expectedEmp.nombre, "Dia $f InPeriod")
                checkEqual(expD.dayMin, actD.dayMin, sc.escenario, expectedEmp.nombre, "Dia $f DayMin")
                checkEqual(expD.extraMin, actD.extraMin, sc.escenario, expectedEmp.nombre, "Dia $f ExtraMin")
                checkEqual(expD.dayAmount, actD.dayAmount, sc.escenario, expectedEmp.nombre, "Dia $f DayAmount")
                for (c in Engine.Cats) {
                    val catName = c.name
                    checkEqual(expD.minPorCategoria[catName] ?: 0, actD.cats[c.ordinal], sc.escenario, expectedEmp.nombre, "Dia $f Min $catName")
                    checkEqual(expD.valorPorCategoria[catName] ?: 0L, actD.amounts[c.ordinal], sc.escenario, expectedEmp.nombre, "Dia $f Amount $catName")
                }
            }
        }
    }
}
