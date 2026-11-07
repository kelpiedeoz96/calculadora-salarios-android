package co.com.calculadoradesalarios.engine.exportar

import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.Fmt
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.zip.InflaterInputStream

class ComprobantePdfTest {
    /** Páginas que produce el escritorio (QuestPDF) con estos mismos datos. */
    private val paginasEscritorio = listOf(6, 6, 9, 6, 6)

    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3, 4])
    fun cifrasClave_Paginacion_YNadaFueraDeLaHoja(i: Int) {
        val e = Escenarios.todos[i]
        val (_, doc) = Escenarios.comprobante().generar(e.st, e.incl, e.st.emps, e.desde, e.hasta, Escenarios.Hoy)
        assertEquals(paginasEscritorio[i], doc.numPaginas, "${e.nombre}: páginas")

        // Nada se sale del área imprimible (márgenes de 14 mm), con 0,5 pt de tolerancia.
        val m = ComprobantePdf.Margen - 0.5f
        doc.todasLasPaginas.forEachIndexed { p, pag ->
            pag.cajas.forEach { c ->
                assertTrue(c[0] >= m && c[1] >= m && c[2] <= ComprobantePdf.AnchoHoja - m && c[3] <= ComprobantePdf.AltoHoja - m,
                    "${e.nombre} pág ${p + 1}: algo se sale de la hoja ${c.toList()}")
            }
        }

        // Cada empleado: su página empieza con el título, trae sus totales y su neto, y la firma completa.
        val textos = doc.todasLasPaginas.map { it.textos }
        val porEmpleado = mutableListOf<List<String>>()
        textos.forEach { t -> if (t.first() == "Comprobante de recargos y horas extra") porEmpleado += t else porEmpleado[porEmpleado.lastIndex] = porEmpleado.last() + t }
        val esperados = e.json["empleados"]!!.jsonArray.map { it.jsonObject }
        assertEquals(esperados.size, porEmpleado.size)
        esperados.forEachIndexed { k, emp ->
            val t = porEmpleado[k]
            val nombre = emp["nombre"]!!.jsonPrimitive.content
            assertTrue(t.any { it.startsWith("Empleado: $nombre") }, "$nombre: encabezado")
            assertTrue(Fmt.Money(emp["totalRecargos"]!!.jsonPrimitive.long) in t, "$nombre: total recargos")
            assertTrue(Engine.FmtHM(emp["totalMin"]!!.jsonPrimitive.int) in t, "$nombre: total horas")
            val neto = if (e.incl) emp["nomina"]!!.jsonObject["neto"]!!.jsonPrimitive.long else emp["totalRecargos"]!!.jsonPrimitive.long
            val iNeto = t.indexOf("Neto a pagar")
            assertEquals(Fmt.Money(neto), t[iNeto + 1], "$nombre: neto")
            emp["dias"]!!.jsonArray.map { it.jsonObject }.filter { it["dayAmount"]!!.jsonPrimitive.long > 0 && it["enPeriodo"]!!.jsonPrimitive.content == "true" }
                .forEach { d -> assertTrue(Fmt.Money(d["dayAmount"]!!.jsonPrimitive.long) in t, "$nombre: día ${d["fecha"]}") }
            assertTrue(t.last().startsWith("Lugar y fecha:") && t.last().endsWith("07 de noviembre de 2026"), "$nombre: firma al final")
        }
        // Las páginas de continuación repiten el encabezado de la tabla de días.
        textos.filter { it.first() != "Comprobante de recargos y horas extra" }.forEach { t -> assertEquals("Fecha", t.first()) }
    }

    @Test
    fun estructuraDelPdf_XrefFuentesIncrustadasYToUnicode() {
        val e = Escenarios.todos[2]
        val (pdf, _) = Escenarios.comprobante().generar(e.st, e.incl, e.st.emps, e.desde, e.hasta, Escenarios.Hoy)
        val s = String(pdf, Charsets.ISO_8859_1)
        assertTrue(s.startsWith("%PDF-1.7"))
        assertTrue(s.trimEnd().endsWith("%%EOF"))
        // startxref apunta a la tabla y cada entrada apunta a "N 0 obj".
        val xref = Regex("startxref\\s+(\\d+)").find(s)!!.groupValues[1].toInt()
        assertTrue(s.startsWith("xref", xref))
        val entradas = Regex("(\\d{10}) 00000 n ").findAll(s.substring(xref)).map { it.groupValues[1].toInt() }.toList()
        entradas.forEachIndexed { k, off -> assertTrue(s.startsWith("${k + 1} 0 obj", off), "objeto ${k + 1} en $off") }
        assertEquals(2, Regex("/Subtype /Type0").findAll(s).count())
        assertEquals(2, Regex("/Encoding /Identity-H").findAll(s).count())
        assertEquals(2, Regex("/FontFile2 \\d+ 0 R").findAll(s).count())
        assertEquals(2, Regex("/ToUnicode \\d+ 0 R").findAll(s).count())
        assertTrue(Regex("/BaseFont /[A-Z]{6}\\+Lato-Regular").containsMatchIn(s), "prefijo de subconjunto")
    }

    @Test
    fun toUnicode_MapeaTildesEnieYPesos() {
        val e = Escenarios.todos[2]
        val emp = e.st.emps[0].copy(name = "Íñigo Peña Díaz", cargo = "Técnico")
        val (pdf, _) = Escenarios.comprobante().generar(e.st, e.incl, listOf(emp), e.desde, e.hasta, Escenarios.Hoy)
        java.io.File("build/exportaciones").mkdirs()
        java.io.File("build/exportaciones/acentos.pdf").writeBytes(pdf)
        val s = String(pdf, Charsets.ISO_8859_1)
        // Descomprime los flujos y busca los CMap ToUnicode.
        val cmaps = Regex("stream\n").findAll(s).mapNotNull { m ->
            val ini = m.range.last + 1
            val fin = s.indexOf("\nendstream", ini)
            runCatching { InflaterInputStream(pdf.copyOfRange(ini, fin).inputStream()).readBytes().decodeToString() }.getOrNull()
        }.filter { it.contains("beginbfchar") }.joinToString("\n")
        for (ch in listOf('á', 'ó', 'é', 'í', 'ñ', 'Í', '$')) {
            val hex = String.format("%04X", ch.code)
            assertTrue(cmaps.contains("<$hex>"), "ToUnicode sin '$ch'")
        }
    }

    @Test
    fun subconjunto_EsTrueTypeValido_ConLosMismosGlifosYAnchos() {
        val original = FuenteTtf(Escenarios.recurso("fuentes/Lato-Regular.ttf"))
        val texto = "Pensión días ñandú $ 1.616.491"
        val usados = texto.codePoints().toArray().map { original.glifo(it) }.toSet()
        val sub = original.subconjunto(usados)
        assertTrue(sub.size < 120_000, "el subconjunto pesa ${sub.size} bytes")
        val f = FuenteTtf(sub)
        assertEquals(original.unidadesPorEm, f.unidadesPorEm)
        texto.codePoints().forEach { cp -> assertEquals(original.glifo(cp), f.glifo(cp)); assertEquals(original.avance(original.glifo(cp)), f.avance(f.glifo(cp))) }
        assertEquals(original.ancho(texto, 9f), f.ancho(texto, 9f), 0.0001f)
        assertTrue(original.glifo('ñ'.code) != 0 && original.glifo('$'.code) != 0, "Lato tiene ñ y $")
    }
}
