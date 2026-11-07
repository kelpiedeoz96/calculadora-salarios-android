package co.com.calculadoradesalarios.engine.exportar

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

sealed interface CeldaXlsx {
    data class Texto(val v: String) : CeldaXlsx
    data class Numero(val v: Double) : CeldaXlsx
    data class Booleano(val v: Boolean) : CeldaXlsx
}

/** Una hoja: filas de celdas, anchos de columna y la fila 1 como encabezado (negrita y fondo). */
class HojaXlsx(val nombre: String, val filas: List<List<CeldaXlsx>>, val anchos: List<Double>)

/**
 * Generador mínimo de .xlsx (SpreadsheetML) con java.util.zip: [Content_Types].xml, relaciones,
 * workbook, styles, sharedStrings, propiedades del documento y una hoja por [HojaXlsx].
 */
object XlsxEscritor {
    private val NombreProhibido = Regex("""[\[\]:*?/\\]""")

    fun escribir(hojas: List<HojaXlsx>): ByteArray {
        require(hojas.isNotEmpty()) { "Un libro necesita al menos una hoja" }
        hojas.forEach { require(it.nombre.length in 1..31 && !NombreProhibido.containsMatchIn(it.nombre)) { "Nombre de hoja inválido: ${it.nombre}" } }
        val compartidas = LinkedHashMap<String, Int>()
        val xmlHojas = hojas.map { hoja(it, compartidas) }

        val salida = ByteArrayOutputStream()
        ZipOutputStream(salida).use { z ->
            fun poner(ruta: String, xml: String) {
                z.putNextEntry(ZipEntry(ruta)); z.write(xml.toByteArray(Charsets.UTF_8)); z.closeEntry()
            }
            poner("[Content_Types].xml", contentTypes(hojas.size))
            poner("_rels/.rels", Cab + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
                """<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>""" +
                """<Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>""" +
                "</Relationships>")
            poner("docProps/core.xml", Cab + """<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:dcterms="http://purl.org/dc/terms/" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">""" +
                "<dc:creator>Calculadora de Salarios Colombia</dc:creator></cp:coreProperties>")
            poner("docProps/app.xml", Cab + """<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties"><Application>Calculadora de Salarios Colombia</Application></Properties>""")
            poner("xl/workbook.xml", Cab + """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets>""" +
                hojas.mapIndexed { i, h -> """<sheet name="${esc(h.nombre)}" sheetId="${i + 1}" r:id="rId${i + 1}"/>""" }.joinToString("") + "</sheets></workbook>")
            poner("xl/_rels/workbook.xml.rels", Cab + """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
                hojas.indices.joinToString("") { i -> """<Relationship Id="rId${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${i + 1}.xml"/>""" } +
                """<Relationship Id="rId${hojas.size + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""" +
                """<Relationship Id="rId${hojas.size + 2}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings" Target="sharedStrings.xml"/>""" +
                "</Relationships>")
            poner("xl/styles.xml", Estilos)
            xmlHojas.forEachIndexed { i, x -> poner("xl/worksheets/sheet${i + 1}.xml", x) }
            poner("xl/sharedStrings.xml", Cab + """<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="${compartidas.size}" uniqueCount="${compartidas.size}">""" +
                compartidas.keys.joinToString("") { s -> "<si><t xml:space=\"preserve\">${esc(s)}</t></si>" } + "</sst>")
        }
        return salida.toByteArray()
    }

    const val RellenoColumna = 0.710625

    private const val Cab = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" + "\n"

    /** Estilo 0: normal. Estilo 1: negrita con fondo #E2EFEE (encabezado, como el escritorio). */
    private val Estilos = Cab + """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
        """<fonts count="2"><font><sz val="11"/><name val="Calibri"/><family val="2"/></font><font><b/><sz val="11"/><name val="Calibri"/><family val="2"/></font></fonts>""" +
        """<fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill>""" +
        """<fill><patternFill patternType="solid"><fgColor rgb="FFE2EFEE"/><bgColor indexed="64"/></patternFill></fill></fills>""" +
        """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
        """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
        """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>""" +
        """<xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/></cellXfs>""" +
        """<cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>"""

    private fun contentTypes(n: Int) = Cab + """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
        """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/>""" +
        """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
        (1..n).joinToString("") { """<Override PartName="/xl/worksheets/sheet$it.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" } +
        """<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""" +
        """<Override PartName="/xl/sharedStrings.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml"/>""" +
        """<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""" +
        """<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>""" +
        "</Types>"

    private fun hoja(h: HojaXlsx, compartidas: MutableMap<String, Int>): String {
        val cols = (h.filas.firstOrNull()?.size ?: 0).coerceAtLeast(1)
        val sb = StringBuilder(Cab)
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        val ultima = h.filas.size.coerceAtLeast(1)
        sb.append("""<dimension ref="A1:${columna(cols - 1)}$ultima"/><sheetFormatPr defaultRowHeight="15"/><cols>""")
        for (i in 0 until cols) {
            // Como ClosedXML: al ancho pedido se suma el relleno de celda de Calibri 11 (0,710625 caracteres).
            val w = h.anchos.getOrElse(i) { 16.0 } + RellenoColumna
            sb.append("""<col min="${i + 1}" max="${i + 1}" width="${num(w)}" customWidth="1"/>""")
        }
        sb.append("</cols><sheetData>")
        h.filas.forEachIndexed { r, fila ->
            val encabezado = r == 0
            sb.append(if (encabezado) """<row r="1" s="1" customFormat="1">""" else """<row r="${r + 1}">""")
            fila.forEachIndexed { c, celda ->
                val ref = columna(c) + (r + 1)
                val s = if (encabezado) """ s="1"""" else ""
                when (celda) {
                    is CeldaXlsx.Texto -> {
                        val i = compartidas.getOrPut(celda.v) { compartidas.size }
                        sb.append("""<c r="$ref"$s t="s"><v>$i</v></c>""")
                    }
                    is CeldaXlsx.Numero -> sb.append("""<c r="$ref"$s><v>${num(celda.v)}</v></c>""")
                    is CeldaXlsx.Booleano -> sb.append("""<c r="$ref"$s t="b"><v>${if (celda.v) 1 else 0}</v></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    /** 0 -> A, 25 -> Z, 26 -> AA … */
    fun columna(i: Int): String {
        var n = i + 1
        val sb = StringBuilder()
        while (n > 0) { val r = (n - 1) % 26; sb.insert(0, 'A' + r); n = (n - 1) / 26 }
        return sb.toString()
    }

    private fun num(v: Double): String {
        require(!v.isNaN() && !v.isInfinite()) { "Número no válido para Excel: $v" }
        return if (v == Math.rint(v) && Math.abs(v) < 1e15) v.toLong().toString() else v.toString()
    }

    /** Escapa XML y quita caracteres de control que XML 1.0 no permite. */
    fun esc(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) when {
            ch == '&' -> sb.append("&amp;")
            ch == '<' -> sb.append("&lt;")
            ch == '>' -> sb.append("&gt;")
            ch == '"' -> sb.append("&quot;")
            ch == '\'' -> sb.append("&apos;")
            ch < ' ' && ch != '\t' && ch != '\n' && ch != '\r' -> {}
            ch == '￾' || ch == '￿' -> {}
            else -> sb.append(ch)
        }
        return sb.toString()
    }
}
