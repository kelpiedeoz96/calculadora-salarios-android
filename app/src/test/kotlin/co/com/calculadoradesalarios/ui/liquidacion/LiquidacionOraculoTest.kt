package co.com.calculadoradesalarios.ui.liquidacion

import androidx.lifecycle.SavedStateHandle
import co.com.calculadoradesalarios.ReglaMain
import co.com.calculadoradesalarios.datos.AlmacenFalso
import co.com.calculadoradesalarios.datos.DatosNomina
import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.engine.Config
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.Fmt
import co.com.calculadoradesalarios.engine.JsonConfig
import co.com.calculadoradesalarios.engine.UiState
import co.com.calculadoradesalarios.ui.HoyPrueba
import co.com.calculadoradesalarios.ui.repoFalso
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Lo que muestra la pantalla de Liquidación, comparado con salida_esperada.json (salida del motor
 * original) para los 5 escenarios y los 6 empleados del oráculo.
 */
@RunWith(Parameterized::class)
class LiquidacionOraculoTest(private val escenario: Int) {
    @get:Rule val main = ReglaMain()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "escenario {0}")
        fun escenarios() = (0..4).toList()
    }

    private fun recurso(n: String) = javaClass.classLoader!!.getResource("TestData/$n")!!.readText()

    @Test
    fun cifrasDeLaPantalla_CoincidenConElOraculo() = runTest {
        val oraculo = JsonConfig.json.parseToJsonElement(recurso("salida_esperada.json")).jsonObject
        val sc = oraculo["escenarios"]!!.jsonArray[escenario].jsonObject
        val nombreSc = sc["escenario"]!!.jsonPrimitive.content
        val (desde, hasta) = sc["periodo"]!!.jsonArray.map { it.jsonPrimitive.content }
        val st = JsonConfig.json.decodeFromString(AppState.serializer(), recurso("respaldo_sintetico.json"))
        st.cfg = JsonConfig.json.decodeFromJsonElement(Config.serializer(), sc["cfgAplicada"]!!)
        val incl = sc["incluirSueldo"]!!.jsonPrimitive.boolean
        val almacen = AlmacenFalso(DatosNomina(st, UiState(month = desde.take(7), mode = "custom", from = desde, to = hasta, incl = incl), emptyList()))
        val repo = repoFalso(almacen)
        val vm = LiquidacionViewModel(repo, SavedStateHandle(), { HoyPrueba }, UnconfinedTestDispatcher(testScheduler))

        val empleados = sc["empleados"]!!.jsonArray.map { it.jsonObject }
        assertEquals(6, empleados.size)
        for (esp in empleados) {
            val nombre = esp["nombre"]!!.jsonPrimitive.content
            vm.seleccionar(st.emps.first { it.name == nombre }.id)
            val e = vm.estado.value
            val ctx = "[$nombreSc · $nombre]"
            assertEquals(ctx, nombre, e.empleadoNombre)
            assertEquals("$ctx incl", incl, e.inclSueldo)

            assertEquals("$ctx horas", Engine.FmtHM(esp.int("totalMin")), e.totalHoras)
            assertEquals("$ctx recargos", Fmt.Money(esp.long("totalRecargos")), e.totalRecargos)
            val hv = esp["tarifas"]!!.jsonObject["hv"]!!.jsonPrimitive.double
            assertEquals("$ctx valor hora", "Valor hora base: ${Fmt.Money(Engine.JsRound(hv).toLong())} · salario ÷ ${LiquidacionViewModel.num(st.cfg.divisor)}", e.valorHora)

            val n = esp["nomina"]!!.jsonObject
            assertEquals("$ctx básico", Fmt.Money(n.long("basicoQ")), e.nomina.basico)
            assertEquals("$ctx auxilio", Fmt.Money(n.long("auxQ")), e.nomina.auxilio)
            assertEquals("$ctx recargos nómina", Fmt.Money(n.long("recargos")), e.nomina.recargos)
            assertEquals("$ctx devengado", Fmt.Money(n.long("devengado")), e.nomina.devengado)
            assertEquals("$ctx salud", "- " + Fmt.Money(n.long("salud")), e.nomina.salud)
            assertEquals("$ctx pensión", "- " + Fmt.Money(n.long("pension")), e.nomina.pension)
            assertEquals("$ctx neto", Fmt.Money(n.long("neto")), e.nomina.neto)

            // Auditoría: cada categoría con minutos (y siempre Ord y Noc).
            val totales = esp["totales"]!!.jsonObject
            val auditadas = e.auditoria.associateBy { it.cat }
            for (c in Cat.entries) {
                val t = totales[c.name]!!.jsonObject
                val min = t.int("min")
                if (min == 0 && c != Cat.ODiu && c != Cat.ONoc) { assertEquals("$ctx $c no debe aparecer", null, auditadas[c]); continue }
                assertEquals("$ctx auditoría $c horas", Engine.FmtHM(min), auditadas.getValue(c).horas)
                assertEquals("$ctx auditoría $c total", Fmt.Money(t.long("amount")), auditadas.getValue(c).total)
            }

            // Semanas (texto y medidor).
            val semanas = esp["semanas"]!!.jsonArray.map { it.jsonObject }
            assertEquals("$ctx semanas", semanas.size, e.semanas.size)
            val cap = Engine.FmtHM(Engine.JsRound(st.cfg.weeklyCap * 60).toInt())
            semanas.forEachIndexed { i, w ->
                val extra = w.int("extraMin")
                assertEquals("$ctx semana $i", "${Engine.FmtHM(w.int("workedMin"))} / $cap" + (if (extra > 0) " · extra ${Engine.FmtHM(extra)}" else ""), e.semanas[i].horas)
                assertEquals("$ctx medidor $i extra", extra, e.semanas[i].medidor.extraMin)
            }

            // Avisos de la sección 22, en el mismo orden.
            assertEquals("$ctx avisos", esp["alertas"]!!.jsonArray.map { it.jsonPrimitive.content }, e.alertas)

            // Cada día: horas, monto, categorías y estado de descanso.
            val dias = esp["dias"]!!.jsonArray.map { it.jsonObject }
            assertEquals("$ctx días", dias.size, e.dias.size)
            dias.forEachIndexed { i, d ->
                val f = e.dias[i]
                val dc = "$ctx ${d["fecha"]!!.jsonPrimitive.content}"
                assertEquals(dc, d["fecha"]!!.jsonPrimitive.content, f.clave)
                val dayMin = d.int("dayMin")
                assertEquals("$dc horas", if (dayMin > 0) Engine.FmtHM(dayMin) else "", f.horas)
                val monto = d.long("dayAmount")
                assertEquals("$dc monto", if (monto > 0) Fmt.Money(monto) else "", f.monto)
                val minCat = d["minPorCategoria"]!!.jsonObject
                val esperadas = Cat.entries.filter { (minCat[it.name]?.jsonPrimitive?.int ?: 0) > 0 }.map { it to Engine.FmtHM(minCat[it.name]!!.jsonPrimitive.int) }
                assertEquals("$dc categorías", esperadas, f.categorias)
                val desc = d["descanso"]!!.jsonPrimitive.boolean
                assertEquals("$dc descanso", desc, f.descanso)
                assertEquals("$dc festivo", d["festivo"]!!.jsonPrimitive.boolean, f.festivo)
                assertEquals("$dc en periodo", d["enPeriodo"]!!.jsonPrimitive.boolean, f.enPeriodo)
                assertEquals("$dc estado", if (desc && dayMin == 0) "Descansó" else if (desc) "Trabajó descanso" else "", f.estado)
            }
        }
    }

    private fun JsonObject.int(k: String) = this[k]!!.jsonPrimitive.int
    private fun JsonObject.long(k: String) = this[k]!!.jsonPrimitive.long
}
