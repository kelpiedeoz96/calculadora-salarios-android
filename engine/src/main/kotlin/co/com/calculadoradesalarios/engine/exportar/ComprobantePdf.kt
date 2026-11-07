package co.com.calculadoradesalarios.engine.exportar

import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Cat
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.engine.Engine
import co.com.calculadoradesalarios.engine.Fmt
import co.com.calculadoradesalarios.engine.exportar.ExportComun.DayType
import co.com.calculadoradesalarios.engine.exportar.ExportComun.DetailRows
import co.com.calculadoradesalarios.engine.exportar.ExportComun.Money
import co.com.calculadoradesalarios.engine.exportar.ExportComun.ShiftText
import java.time.LocalDate

/**
 * Comprobante de recargos y horas extra en PDF, con el mismo contenido y la misma distribución que
 * Export.Pdf del escritorio (QuestPDF): hoja carta horizontal, márgenes de 14 mm, una página o más por
 * empleado, encabezado de la tabla de días repetido en cada página y el bloque de firmas sin partir.
 */
class ComprobantePdf(
    private val regular: FuenteTtf,
    private val negrita: FuenteTtf,
    private val decodificador: DecodificadorImagen? = null,
) {
    companion object {
        const val AnchoHoja = 792f        // carta horizontal (PageSizes.Letter.Landscape())
        const val AltoHoja = 612f
        const val Margen = 14f * 72f / 25.4f
        val Teal = ColorPdf.hex("#0e5a61")
        val Borde = ColorPdf.hex("#d0d7d6")
        val GrisLinea = ColorPdf.hex("#5a5a5a")
        val GrisTexto = ColorPdf.hex("#3c3c3c")
        val GrisSuave = ColorPdf.hex("#6e6e6e")
        const val R = "Regular"
        const val B = "Bold"
    }

    private class Celda(val texto: String, val derecha: Boolean = false, val negrita: Boolean = false)

    private lateinit var doc: DocumentoPdf
    private lateinit var pag: PaginaPdf
    private var y = 0f
    private val ancho = AnchoHoja - 2 * Margen
    private val fondo = AltoHoja - Margen

    private fun fuente(nombre: String) = if (nombre == B) negrita else regular
    private fun alto(tam: Float) = (regular.ascendente - regular.descendente) * tam / regular.unidadesPorEm

    private fun nuevaPagina() {
        pag = doc.nuevaPagina(AnchoHoja, AltoHoja)
        y = Margen
    }

    /** Parte el texto en líneas que caben en [max] (por espacios y después de guiones). */
    private fun envolver(s: String, f: String, tam: Float, max: Float): List<String> {
        if (s.isEmpty()) return listOf("")
        val ft = fuente(f)
        if (ft.ancho(s, tam) <= max) return listOf(s)
        val piezas = Regex("""[^\s-]+-?|\s+""").findAll(s).map { it.value }.toList()
        val lineas = mutableListOf<String>()
        var actual = ""
        for (p in piezas) {
            val prueba = actual + p
            if (actual.isNotBlank() && ft.ancho(prueba.trimEnd(), tam) > max) {
                lineas += actual.trimEnd(); actual = p.trimStart()
            } else actual = prueba
        }
        if (actual.isNotBlank()) lineas += actual.trimEnd()
        return lineas
    }

    private fun altoFila(anchos: List<Float>, celdas: List<Celda>, tam: Float, padV: Float): Float {
        val lineas = celdas.mapIndexed { i, c -> envolver(c.texto, if (c.negrita) B else R, tam, anchos[i] - 6f).size }.max()
        return padV * 2 + lineas * alto(tam)
    }

    /** Fila de tabla en (x, y). Encabezado: fondo teal y texto blanco en negrita; cuerpo: línea inferior. */
    private fun fila(x: Float, anchos: List<Float>, celdas: List<Celda>, tam: Float, encabezado: Boolean): Float {
        val padV = if (encabezado) 2f else 1.5f
        val h = altoFila(anchos, celdas, tam, padV)
        if (encabezado) pag.rectangulo(x, y, anchos.sum(), h, Teal)
        var cx = x
        celdas.forEachIndexed { i, c ->
            val f = if (encabezado || c.negrita) B else R
            val color = if (encabezado) ColorPdf.Blanco else ColorPdf.Negro
            envolver(c.texto, f, tam, anchos[i] - 6f).forEachIndexed { k, l ->
                val ly = y + padV + k * alto(tam)
                if (c.derecha) pag.texto(cx + anchos[i] - 3f, ly, l, f, tam, color, Alineacion.Derecha)
                else pag.texto(cx + 3f, ly, l, f, tam, color)
            }
            cx += anchos[i]
        }
        if (!encabezado) pag.linea(x, y + h - 0.25f, x + anchos.sum(), y + h - 0.25f, 0.5f, Borde)
        return h
    }

    /** PDF con un comprobante por empleado (el escritorio exporta uno o "todos"). */
    fun generar(st: AppState, incl: Boolean, emps: List<Employee>, desde: LocalDate, hasta: LocalDate, hoy: LocalDate, logo: ByteArray? = null): Pair<ByteArray, DocumentoPdf> {
        doc = DocumentoPdf(linkedMapOf(R to regular, B to negrita), "Comprobante de recargos y horas extra")
        val imagen = logo?.let { decodificador?.decodificar(it) }
        val nombreImg = imagen?.let { doc.agregarImagen(it) }
        val cfg = st.cfg
        val fechaHoy = ExportComun.fechaLarga(hoy)
        fun hmz(m: Int) = if (m > 0) Engine.FmtHM(m) else ""

        for (s in ExportComun.resumenes(st, emps, desde, hasta, incl)) {
            val emp = s.emp
            nuevaPagina()

            // Encabezado
            val anchoLogo = 34f * 72f / 25.4f
            var x0 = Margen
            var altoLogo = 0f
            if (imagen != null && nombreImg != null) {
                altoLogo = anchoLogo * imagen.alto / imagen.ancho
                pag.imagen(nombreImg, Margen, y, anchoLogo, altoLogo)
                x0 = Margen + anchoLogo + 8f
            }
            val y0 = y
            pag.texto(x0, y, "Comprobante de recargos y horas extra", B, 15f); y += alto(15f)
            val lineas = listOf(
                cfg.razonSocial,
                "Periodo: ${Engine.Key(desde)} a ${Engine.Key(hasta)}",
                "Empleado: " + emp.name + (if (emp.cedula != "") "   C.C. " + emp.cedula else "") + (if (emp.cargo != "") "   Cargo: " + emp.cargo else ""),
                "Salario mensual: ${Money(s.p.sal)}   Valor hora: ${Money(s.r.rates.hv)}   Tope semanal: ${ExportComun.num2(cfg.weeklyCap)} h",
            )
            lineas.forEach { l -> envolver(l, R, 10f, ancho - (x0 - Margen)).forEach { pag.texto(x0, y, it, R, 10f); y += alto(10f) } }
            y = maxOf(y, y0 + altoLogo) + 8f

            // Conceptos (3/5) y liquidación (2/5), separadas 18 pt
            val izq = (ancho - 18f) * 3 / 5
            val der = (ancho - 18f) * 2 / 5
            val yTablas = y
            val ac = listOf(izq - 166f, 42f, 58f, 66f)
            y += fila(Margen, ac, listOf(Celda("Concepto"), Celda("Horas", true), Celda("Valor/hora", true), Celda("Total", true)), 8.5f, true)
            for (c in Engine.Cats) {
                val min = s.r.totMin[c.ordinal]
                if (min == 0 && c != Cat.ODiu) continue
                y += fila(Margen, ac, listOf(
                    Celda(Engine.CatLabel.getValue(c)), Celda(Engine.FmtHM(min), true),
                    Celda(if (c == Cat.ODiu) "-" else Money(s.r.rates[c]), true), Celda(Fmt.Money(s.r.totAmount[c.ordinal]), true),
                ), 8.5f, false)
            }
            y += fila(Margen, ac, listOf(Celda("Total", negrita = true), Celda(Engine.FmtHM(s.r.totalMin), true, true), Celda("", true, true), Celda(Fmt.Money(s.r.totalAmount), true, true)), 8.5f, false)
            val finIzq = y

            y = yTablas
            val xd = Margen + izq + 18f
            val al = listOf(der * 3 / 5, der * 2 / 5)
            y += fila(xd, al, listOf(Celda("Liquidación"), Celda("Valor", true)), 9f, true)
            val liq = if (incl) listOf(
                "Salario básico quincenal" to Fmt.Money(s.p.basicoQ),
                "Auxilio de transporte" to Fmt.Money(s.p.auxQ),
                "Recargos y horas extra" to Fmt.Money(s.p.recargos),
                "Total devengado" to Fmt.Money(s.p.devengado),
                "Salud (${ExportComun.num2(cfg.saludPct)}%)" to "- " + Fmt.Money(s.p.salud),
                "Pensión (${ExportComun.num2(cfg.pensionPct)}%)" to "- " + Fmt.Money(s.p.pension),
                "Neto a pagar" to Fmt.Money(s.p.neto),
            ) else listOf(
                "Recargos y horas extra" to Fmt.Money(s.r.totalAmount),
                "Neto a pagar" to Fmt.Money(s.r.totalAmount),
            )
            liq.forEachIndexed { i, (a, b) ->
                val ultimo = i == liq.lastIndex
                y += fila(xd, al, listOf(Celda(a, negrita = ultimo), Celda(b, true, ultimo)), 9f, false)
            }
            y = maxOf(finIzq, y) + 8f

            // Detalle por día, con el encabezado repetido en cada página
            val ad = listOf(58f, 86f, ancho - 58f - 86f - 9 * 36f - 60f) + List(9) { 36f } + listOf(60f)
            val hd = listOf("Fecha", "Tipo", "Jornada", "Horas", "Ord.", "Noc.", "Dom.", "Noc-Dom", "HED", "HEN", "HED-Dom", "HEN-Dom", "Recargos")
            val cab = hd.mapIndexed { i, t -> Celda(t, i >= 3) }
            val filas = DetailRows(s.r).map { rw ->
                listOf(Celda(Fmt.Dmy(rw.date) + " " + Engine.Dow[rw.dow]), Celda(DayType(rw)), Celda(ShiftText(rw.rec)), Celda(hmz(rw.dayMin), true)) +
                    Engine.Cats.map { Celda(hmz(rw.cats[it.ordinal]), true) } +
                    listOf(Celda(if (rw.dayAmount > 0) Fmt.Money(rw.dayAmount) else "", true))
            }
            val hCab = altoFila(ad, cab, 7.5f, 2f)
            val primera = filas.firstOrNull()?.let { altoFila(ad, it, 7.5f, 1.5f) } ?: 0f
            if (y + hCab + primera > fondo) nuevaPagina()
            y += fila(Margen, ad, cab, 7.5f, true)
            for (f in filas) {
                val h = altoFila(ad, f, 7.5f, 1.5f)
                if (y + h > fondo) {
                    nuevaPagina()
                    y += fila(Margen, ad, cab, 7.5f, true)
                }
                y += fila(Margen, ad, f, 7.5f, false)
            }

            // Firmas (ShowEntire: si no caben completas, van a la página siguiente)
            val altoFirmas = 20f + 26f + 3f + alto(8f) + 2 * alto(7.5f) + 14f + alto(7.5f)
            if (y + 8f + altoFirmas > fondo) nuevaPagina() else y += 8f
            y += 20f
            val firmas = listOf(
                Triple("Firma del empleado", "Nombre: " + emp.name, "C.C.: " + (if (emp.cedula != "") emp.cedula else "____________________")),
                Triple("Firma del empleador", cfg.razonSocial, "NIT: " + (if (cfg.nit.isBlank()) "____________________" else cfg.nit)),
            )
            firmas.forEachIndexed { i, (t1, t2, t3) ->
                val fx = Margen + i * (190f + 40f)
                var fy = y + 26f
                pag.linea(fx, fy - 0.35f, fx + 190f, fy - 0.35f, 0.7f, GrisLinea)
                fy += 3f
                pag.texto(fx, fy, t1, R, 8f, GrisTexto); fy += alto(8f)
                pag.texto(fx, fy, t2, R, 7.5f, GrisSuave); fy += alto(7.5f)
                pag.texto(fx, fy, t3, R, 7.5f, GrisSuave)
            }
            y += 26f + 3f + alto(8f) + 2 * alto(7.5f) + 14f
            pag.texto(Margen, y, "Lugar y fecha: ____________________,  $fechaHoy", R, 7.5f, GrisSuave)
            y += alto(7.5f)
        }
        return doc.escribir() to doc
    }
}
