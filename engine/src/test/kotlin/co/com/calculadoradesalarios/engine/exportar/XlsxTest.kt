package co.com.calculadoradesalarios.engine.exportar

import co.com.calculadoradesalarios.engine.Engine
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class XlsxTest {
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2, 3, 4])
    fun cifrasClave_ContraElOraculo(i: Int) {
        val e = Escenarios.todos[i]
        val x = LectorXlsx(ExcelNomina.xlsx(e.st, e.incl, e.st.emps, e.desde, e.hasta))
        val resumen = x.celdas("Resumen")
        val cab = resumen.filterKeys { it.endsWith("1") && it.length <= 3 && it.drop(it.indexOfFirst(Char::isDigit)) == "1" }
        val colRecargos = cab.entries.first { it.value == "Recargos + extras ($)" }.key.dropLast(1)
        e.json["empleados"]!!.jsonArray.map { it.jsonObject }.forEachIndexed { k, emp ->
            val fila = k + 2
            assertEquals(emp["nombre"]!!.jsonPrimitive.content, resumen["A$fila"])
            assertEquals(emp["totalRecargos"]!!.jsonPrimitive.long.toDouble(), resumen["$colRecargos$fila"])
            if (e.incl) {
                val colNeto = cab.entries.first { it.value == "Neto a pagar" }.key.dropLast(1)
                assertEquals(emp["nomina"]!!.jsonObject["neto"]!!.jsonPrimitive.long.toDouble(), resumen["$colNeto$fila"])
            }
        }
        val param = x.celdas("Parámetros")
        assertEquals("${Engine.Key(e.desde)} a ${Engine.Key(e.hasta)}", param["B2"])
    }

    @Test
    fun estructura_PartesObligatorias_YRelaciones() {
        val e = Escenarios.todos[0]
        val x = LectorXlsx(ExcelNomina.xlsx(e.st, e.incl, e.st.emps, e.desde, e.hasta))
        for (p in listOf("[Content_Types].xml", "_rels/.rels", "xl/workbook.xml", "xl/_rels/workbook.xml.rels", "xl/styles.xml", "xl/sharedStrings.xml",
            "xl/worksheets/sheet1.xml", "xl/worksheets/sheet2.xml", "xl/worksheets/sheet3.xml", "docProps/core.xml", "docProps/app.xml")) {
            assertTrue(p in x.partes, "falta $p")
        }
        val ct = x.partes.getValue("[Content_Types].xml").decodeToString()
        (1..3).forEach { assertTrue(ct.contains("/xl/worksheets/sheet$it.xml")) }
        assertEquals(listOf("Resumen", "Detalle diario", "Parámetros"), x.hojas.map { it.first })
    }

    @Test
    fun escapaXml_YQuitaCaracteresDeControl() {
        val raro = "A & B <C> \"D\" 'E' \u0001fin ñandú"
        val bytes = XlsxEscritor.escribir(listOf(HojaXlsx("Prueba", listOf(listOf(CeldaXlsx.Texto("Encabezado")), listOf(CeldaXlsx.Texto(raro), CeldaXlsx.Numero(0.1), CeldaXlsx.Booleano(true))), emptyList())))
        val c = LectorXlsx(bytes).celdas("Prueba")
        assertEquals("A & B <C> \"D\" 'E' fin ñandú", c["A2"])
        assertEquals(0.1, c["B2"])
        assertEquals(true, c["C2"])
    }

    @Test
    fun columnas_YNombresDeHoja() {
        assertEquals(listOf("A", "Z", "AA", "AB", "AZ", "BA"), listOf(0, 25, 26, 27, 51, 52).map(XlsxEscritor::columna))
        assertThrows(IllegalArgumentException::class.java) { XlsxEscritor.escribir(listOf(HojaXlsx("a/b", listOf(listOf(CeldaXlsx.Texto("x"))), emptyList()))) }
        assertThrows(IllegalArgumentException::class.java) { XlsxEscritor.escribir(listOf(HojaXlsx("x".repeat(32), listOf(listOf(CeldaXlsx.Texto("x"))), emptyList()))) }
    }
}
