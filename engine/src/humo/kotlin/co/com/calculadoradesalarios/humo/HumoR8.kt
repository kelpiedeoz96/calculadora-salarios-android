package co.com.calculadoradesalarios.humo

import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Config
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.JsonConfig
import co.com.calculadoradesalarios.engine.JsonDataStore
import co.com.calculadoradesalarios.engine.exportar.ComprobantePdf
import co.com.calculadoradesalarios.engine.exportar.DecodificadorImagen
import co.com.calculadoradesalarios.engine.exportar.ExcelNomina
import co.com.calculadoradesalarios.engine.exportar.FuenteTtf
import co.com.calculadoradesalarios.engine.exportar.ImagenRgba
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import java.nio.file.Files
import java.time.LocalDate

/**
 * Prueba de humo de R8: se ejecuta igual antes y después de pasar por R8 (con las reglas del release)
 * y escribe en [args][0] los PDF, Excel, data.json y las cifras del oráculo. Si R8 rompiera algo
 * (serialización, fuentes, recursos), los archivos saldrían distintos o el programa fallaría.
 */
object HumoR8 {
    private fun recurso(n: String): ByteArray =
        HumoR8::class.java.classLoader.getResourceAsStream(n)?.use { it.readBytes() } ?: error("Falta el recurso $n")

    /** Logo de 4x2 con transparencia, sin depender de la plataforma. */
    private val logo = DecodificadorImagen { _ ->
        ImagenRgba.desdeArgb(4, 2, intArrayOf(0x000E5A61, 0xFF0E5A61.toInt(), 0x800E5A61.toInt(), 0xFFF2994A.toInt(), 0xFF0E5A61.toInt(), 0, 0x400E5A61, -1))
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val salida = File(args[0]).apply { deleteRecursively(); mkdirs() }
        val hoy = LocalDate.of(2026, 11, 7)
        val comprobante = ComprobantePdf(FuenteTtf(recurso("fuentes/Lato-Regular.ttf")), FuenteTtf(recurso("fuentes/Lato-Bold.ttf")), logo)
        val oraculo = JsonConfig.json.parseToJsonElement(recurso("TestData/salida_esperada.json").decodeToString()).jsonObject["escenarios"]!!.jsonArray
        val cifras = StringBuilder()
        var fallas = 0
        oraculo.forEachIndexed { i, el ->
            val sc = el.jsonObject
            val nombre = sc["escenario"]!!.jsonPrimitive.content
            val st = JsonConfig.json.decodeFromString(AppState.serializer(), recurso("TestData/respaldo_sintetico.json").decodeToString())
            st.cfg = JsonConfig.json.decodeFromJsonElement(Config.serializer(), sc["cfgAplicada"]!!)
            val (desde, hasta) = sc["periodo"]!!.jsonArray.map { LocalDate.parse(it.jsonPrimitive.content) }
            val incl = sc["incluirSueldo"]!!.jsonPrimitive.boolean
            File(salida, "$nombre.pdf").writeBytes(comprobante.generar(st, incl, st.emps, desde, hasta, hoy, if (i == 0) byteArrayOf(1) else null).first)
            File(salida, "$nombre.xlsx").writeBytes(ExcelNomina.xlsx(st, incl, st.emps, desde, hasta))
            val fest = st.festivos.toHashSet()
            for (emp in sc["empleados"]!!.jsonArray.map { it.jsonObject }) {
                val e = st.emps.first { it.name == emp["nombre"]!!.jsonPrimitive.content }
                val a = Engine.Analyze(e, st.cfg, fest, desde, hasta)
                val neto = Engine.GetPayroll(e, st.cfg, a.totalAmount).neto
                val esperadoRecargos = emp["totalRecargos"]!!.jsonPrimitive.long
                val esperadoNeto = emp["nomina"]!!.jsonObject["neto"]!!.jsonPrimitive.long
                val ok = a.totalAmount == esperadoRecargos && neto == esperadoNeto
                if (!ok) fallas++
                cifras.append("$nombre ${e.name} recargos=${a.totalAmount} neto=$neto ${if (ok) "OK" else "DISTINTO"}\n")
            }
        }
        File(salida, "cifras.txt").writeText(cifras.toString())

        // data.json: leer el de los campos del escritorio y volver a guardarlo.
        val dir = Files.createTempDirectory("humo").toFile()
        File(dir, "data.json").writeBytes(recurso("TestData/data_campos_escritorio.json"))
        val store = JsonDataStore(dir.path)
        val (st, ui, hist) = store.load()
        store.save(st, ui, hist)
        File(dir, "data.json").copyTo(File(salida, "data.json"))
        dir.deleteRecursively()

        println("HumoR8: ${oraculo.size} escenarios, ${salida.listFiles()!!.size} archivos, fallas del oráculo: $fallas")
        if (fallas > 0) throw IllegalStateException("Cifras distintas del oráculo")
    }
}
