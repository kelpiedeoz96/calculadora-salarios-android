package co.com.calculadoradesalarios.engine.exportar

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/** Lector mínimo de .xlsx para comparar libros en las pruebas (sirve para el nuestro y el de ClosedXML). */
class LectorXlsx(bytes: ByteArray) {
    val partes: Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            generateSequence { z.nextEntry }.forEach { e -> put(e.name, z.readBytes()) }
        }
    }

    private fun xml(ruta: String): Element =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder()
            .parse(ByteArrayInputStream(partes[ruta] ?: error("Falta $ruta"))).documentElement

    private fun Element.hijos(nombre: String): List<Element> {
        val l = getElementsByTagNameNS("*", nombre)
        return (0 until l.length).map { l.item(it) as Element }
    }

    private val compartidas: List<String> by lazy {
        if ("xl/sharedStrings.xml" !in partes) emptyList()
        else xml("xl/sharedStrings.xml").hijos("si").map { si -> si.hijos("t").joinToString("") { it.textContent } }
    }

    /** Hojas en orden: nombre -> ruta de la parte. */
    val hojas: List<Pair<String, String>> by lazy {
        val rels = xml("xl/_rels/workbook.xml.rels").hijos("Relationship").associate { it.getAttribute("Id") to it.getAttribute("Target") }
        xml("xl/workbook.xml").hijos("sheet").map { s ->
            val destino = rels.getValue(s.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id"))
            s.getAttribute("name") to ("xl/" + destino.removePrefix("/xl/").removePrefix("xl/"))
        }
    }

    /** Valor de cada celda: String para texto, Double para número, Boolean para booleano. */
    fun celdas(hoja: String): Map<String, Any> {
        val ruta = hojas.first { it.first == hoja }.second
        return xml(ruta).hijos("c").associate { c ->
            val v = c.hijos("v").firstOrNull()?.textContent
            val valor: Any = when (c.getAttribute("t")) {
                "s" -> compartidas[v!!.toInt()]
                "inlineStr" -> c.hijos("t").joinToString("") { it.textContent }
                "str" -> v ?: ""
                "b" -> v == "1"
                else -> v?.toDouble() ?: ""
            }
            c.getAttribute("r") to valor
        }
    }

    fun anchos(hoja: String): Map<Int, Double> {
        val ruta = hojas.first { it.first == hoja }.second
        return xml(ruta).hijos("col").flatMap { col ->
            (col.getAttribute("min").toInt()..col.getAttribute("max").toInt()).map { it to col.getAttribute("width").toDouble() }
        }.toMap()
    }

    /** ¿La celda usa una fuente en negrita? (sigue s -> cellXfs -> fonts). */
    fun negrita(hoja: String, ref: String): Boolean {
        val ruta = hojas.first { it.first == hoja }.second
        val c = xml(ruta).hijos("c").first { it.getAttribute("r") == ref }
        val s = c.getAttribute("s").ifEmpty { "0" }.toInt()
        val estilos = xml("xl/styles.xml")
        val xf = estilos.hijos("cellXfs").first().hijos("xf")[s]
        val fuente = estilos.hijos("fonts").first().hijos("font")[xf.getAttribute("fontId").ifEmpty { "0" }.toInt()]
        return fuente.hijos("b").isNotEmpty()
    }
}
