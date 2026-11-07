package co.com.calculadoradesalarios.engine.exportar

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.Deflater

/** Color RGB 0-255. */
data class ColorPdf(val r: Int, val g: Int, val b: Int) {
    companion object {
        fun hex(h: String): ColorPdf = h.removePrefix("#").toInt(16).let { ColorPdf(it shr 16 and 0xFF, it shr 8 and 0xFF, it and 0xFF) }
        val Negro = ColorPdf(0, 0, 0)
        val Blanco = ColorPdf(255, 255, 255)
    }
}

/**
 * Escritor de PDF 1.7 en Kotlin puro (sin librerías). Coordenadas en puntos con el origen arriba a la
 * izquierda, como en el diseño; se convierten al sistema del PDF al escribir.
 *
 * Las fuentes TrueType se incrustan como Type0 / CIDFontType2 con codificación Identity-H, anchos /W
 * reales de cada glifo, subconjunto de glifos y un CMap ToUnicode (así pdftotext extrae bien tildes,
 * ñ y $). Las imágenes van como XObject con /SMask si tienen transparencia.
 */
class DocumentoPdf(private val fuentes: Map<String, FuenteTtf>, private val titulo: String = "") {
    private val usados = fuentes.keys.associateWith { HashMap<Int, Int>() } // glifo -> punto de código
    private val paginas = mutableListOf<PaginaPdf>()
    private val imagenes = mutableListOf<ImagenRgba>()

    fun fuente(nombre: String): FuenteTtf = fuentes.getValue(nombre)

    fun nuevaPagina(ancho: Float, alto: Float): PaginaPdf = PaginaPdf(this, ancho, alto).also { paginas += it }

    val numPaginas: Int get() = paginas.size

    /** Páginas en orden, para comprobar en las pruebas qué se dibujó y dónde. */
    val todasLasPaginas: List<PaginaPdf> get() = paginas

    /** Registra una imagen y devuelve su nombre de recurso (/Im1, /Im2…). */
    fun agregarImagen(img: ImagenRgba): String {
        imagenes += img
        return "Im${imagenes.size}"
    }

    /** /F1, /F2… en el orden en que se registraron las fuentes (el mismo de los recursos de página). */
    internal fun recursoFuente(nombre: String): String {
        val i = fuentes.keys.indexOf(nombre)
        require(i >= 0) { "Fuente no registrada: $nombre" }
        return "F${i + 1}"
    }

    internal fun codificar(fuente: String, texto: String): String {
        val f = fuente(fuente)
        val mapa = usados.getValue(fuente)
        val sb = StringBuilder("<")
        texto.codePoints().forEach { cp ->
            val g = f.glifo(cp)
            mapa.putIfAbsent(g, cp)
            sb.append(String.format(Locale.ROOT, "%04X", g))
        }
        return sb.append('>').toString()
    }

    fun escribir(): ByteArray {
        val w = Escritor()
        val catalogo = w.reservar()
        val raizPaginas = w.reservar()
        val recursosFuente = StringBuilder()
        fuentes.keys.forEachIndexed { i, nombre ->
            val ref = escribirFuente(w, nombre, prefijoSubconjunto(i))
            recursosFuente.append("/F${i + 1} $ref 0 R ")
        }
        val recursosImg = StringBuilder()
        imagenes.forEachIndexed { i, img ->
            val smask = img.alfa?.let { a ->
                w.flujo("<< /Type /XObject /Subtype /Image /Width ${img.ancho} /Height ${img.alto} /ColorSpace /DeviceGray /BitsPerComponent 8", a)
            }
            val ref = w.flujo(
                "<< /Type /XObject /Subtype /Image /Width ${img.ancho} /Height ${img.alto} /ColorSpace /DeviceRGB /BitsPerComponent 8" +
                    (smask?.let { " /SMask $it 0 R" } ?: ""),
                img.rgb,
            )
            recursosImg.append("/Im${i + 1} $ref 0 R ")
        }
        val recursos = "<< /Font << $recursosFuente>> /XObject << $recursosImg>> >>"
        val refsPaginas = paginas.map { p ->
            val contenido = w.flujo("<<", p.contenido().toByteArray(Charsets.ISO_8859_1))
            w.objeto(
                "<< /Type /Page /Parent $raizPaginas 0 R /MediaBox [0 0 ${n(p.ancho)} ${n(p.alto)}] /Resources $recursos /Contents $contenido 0 R >>",
            )
        }
        w.objeto("<< /Type /Pages /Kids [${refsPaginas.joinToString(" ") { "$it 0 R" }}] /Count ${refsPaginas.size} >>", raizPaginas)
        w.objeto("<< /Type /Catalog /Pages $raizPaginas 0 R >>", catalogo)
        val info = w.objeto("<< /Title ${textoPdf(titulo)} /Producer (Calculadora de Salarios Colombia) >>")
        return w.cerrar(catalogo, info)
    }

    /** Prefijo de 6 letras obligatorio para fuentes en subconjunto (ABCDEA+, ABCDEB+…). */
    private fun prefijoSubconjunto(i: Int) = "CDSAN" + ('A' + i) + "+"

    private fun escribirFuente(w: Escritor, nombre: String, prefijo: String): Int {
        val f = fuente(nombre)
        val mapa = usados.getValue(nombre)
        val glifos = (mapa.keys + 0).sorted()
        val esc = 1000f / f.unidadesPorEm
        val base = prefijo + f.nombrePostScript
        val datos = f.subconjunto(glifos.toSet())
        val archivo = w.flujo("<< /Length1 ${datos.size}", datos)
        val descriptor = w.objeto(
            "<< /Type /FontDescriptor /FontName /$base /Flags 32 /FontBBox [${n(f.cajaX0 * esc)} ${n(f.cajaY0 * esc)} ${n(f.cajaX1 * esc)} ${n(f.cajaY1 * esc)}]" +
                " /ItalicAngle 0 /Ascent ${n(f.ascendente * esc)} /Descent ${n(f.descendente * esc)} /CapHeight ${n(f.altoMayusculas * esc)} /StemV 80 /FontFile2 $archivo 0 R >>",
        )
        val anchos = glifos.joinToString(" ") { g -> "$g [${n(f.avance(g) * esc)}]" }
        val cid = w.objeto(
            "<< /Type /Font /Subtype /CIDFontType2 /BaseFont /$base /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >>" +
                " /FontDescriptor $descriptor 0 R /CIDToGIDMap /Identity /DW 1000 /W [$anchos] >>",
        )
        val tu = w.flujo("<<", toUnicode(mapa).toByteArray(Charsets.US_ASCII))
        return w.objeto("<< /Type /Font /Subtype /Type0 /BaseFont /$base /Encoding /Identity-H /DescendantFonts [$cid 0 R] /ToUnicode $tu 0 R >>")
    }

    private fun toUnicode(mapa: Map<Int, Int>): String {
        val sb = StringBuilder()
        sb.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
        sb.append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n/CMapName /Adobe-Identity-UCS def\n/CMapType 2 def\n")
        sb.append("1 begincodespacerange\n<0000> <FFFF>\nendcodespacerange\n")
        mapa.entries.filter { it.key != 0 }.sortedBy { it.key }.chunked(100).forEach { bloque ->
            sb.append("${bloque.size} beginbfchar\n")
            bloque.forEach { (g, cp) ->
                val u = String(Character.toChars(cp)).toByteArray(Charsets.UTF_16BE).joinToString("") { String.format(Locale.ROOT, "%02X", it) }
                sb.append(String.format(Locale.ROOT, "<%04X> <%s>\n", g, u))
            }
            sb.append("endbfchar\n")
        }
        sb.append("endcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n")
        return sb.toString()
    }

    private class Escritor {
        private val salida = ByteArrayOutputStream()
        private val offsets = HashMap<Int, Int>()
        private var siguiente = 1

        init {
            escribir("%PDF-1.7\n")
            salida.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))
        }

        private fun escribir(s: String) = salida.write(s.toByteArray(Charsets.ISO_8859_1))

        fun reservar(): Int = siguiente++

        fun objeto(cuerpo: String, num: Int = reservar()): Int {
            offsets[num] = salida.size()
            escribir("$num 0 obj\n$cuerpo\nendobj\n")
            return num
        }

        /** [dicc] es el diccionario sin cerrar ("<< /Clave valor"); se agregan /Filter y /Length. */
        fun flujo(dicc: String, datos: ByteArray): Int {
            val comprimidos = comprimir(datos)
            val num = reservar()
            offsets[num] = salida.size()
            escribir("$num 0 obj\n$dicc /Filter /FlateDecode /Length ${comprimidos.size} >>\nstream\n")
            salida.write(comprimidos)
            escribir("\nendstream\nendobj\n")
            return num
        }

        fun cerrar(catalogo: Int, info: Int): ByteArray {
            val xref = salida.size()
            val total = siguiente
            val sb = StringBuilder("xref\n0 $total\n0000000000 65535 f \n")
            for (i in 1 until total) sb.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[i] ?: error("Objeto $i sin escribir")))
            sb.append("trailer\n<< /Size $total /Root $catalogo 0 R /Info $info 0 R >>\nstartxref\n$xref\n%%EOF\n")
            escribir(sb.toString())
            return salida.toByteArray()
        }

        private fun comprimir(b: ByteArray): ByteArray {
            val d = Deflater(Deflater.BEST_COMPRESSION)
            d.setInput(b); d.finish()
            val out = ByteArrayOutputStream()
            val buf = ByteArray(8192)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            d.end()
            return out.toByteArray()
        }
    }

    companion object {
        fun n(v: Float): String = String.format(Locale.ROOT, "%.3f", v).trimEnd('0').trimEnd('.').ifEmpty { "0" }

        /** Cadena PDF en UTF-16BE con BOM (para /Title con tildes). */
        fun textoPdf(s: String): String =
            "<FEFF" + s.toByteArray(Charsets.UTF_16BE).joinToString("") { String.format(Locale.ROOT, "%02X", it) } + ">"
    }
}

enum class Alineacion { Izquierda, Derecha }

/** Una página: las operaciones se guardan y se convierten a contenido PDF al escribir. */
class PaginaPdf internal constructor(private val doc: DocumentoPdf, val ancho: Float, val alto: Float) {
    private val ops = StringBuilder()

    /** Cajas de todo lo dibujado (x0, yArriba, x1, yAbajo), para comprobar que nada se sale de la hoja. */
    val cajas = mutableListOf<FloatArray>()

    /** Textos dibujados, en orden, para las pruebas. */
    val textos = mutableListOf<String>()

    private fun n(v: Float) = DocumentoPdf.n(v)
    private fun color(c: ColorPdf, relleno: Boolean) = "${n(c.r / 255f)} ${n(c.g / 255f)} ${n(c.b / 255f)} ${if (relleno) "rg" else "RG"}\n"

    /**
     * Texto con la parte superior de la línea en [yArriba]. Con [Alineacion.Derecha], [x] es el borde
     * derecho. Devuelve el ancho dibujado.
     */
    fun texto(x: Float, yArriba: Float, s: String, fuente: String, tam: Float, color: ColorPdf = ColorPdf.Negro, alineacion: Alineacion = Alineacion.Izquierda): Float {
        if (s.isEmpty()) return 0f
        val f = doc.fuente(fuente)
        val ancho = f.ancho(s, tam)
        val x0 = if (alineacion == Alineacion.Derecha) x - ancho else x
        val base = yArriba + f.ascendente * tam / f.unidadesPorEm
        ops.append(color(color, true))
        ops.append("BT /${doc.recursoFuente(fuente)} ${n(tam)} Tf 1 0 0 1 ${n(x0)} ${n(alto - base)} Tm ${doc.codificar(fuente, s)} Tj ET\n")
        cajas += floatArrayOf(x0, yArriba, x0 + ancho, yArriba + (f.ascendente - f.descendente) * tam / f.unidadesPorEm)
        textos += s
        return ancho
    }

    fun rectangulo(x: Float, yArriba: Float, w: Float, h: Float, relleno: ColorPdf) {
        ops.append(color(relleno, true)).append("${n(x)} ${n(alto - yArriba - h)} ${n(w)} ${n(h)} re f\n")
        cajas += floatArrayOf(x, yArriba, x + w, yArriba + h)
    }

    fun linea(x0: Float, y0: Float, x1: Float, y1: Float, grosor: Float, c: ColorPdf) {
        ops.append(color(c, false)).append("${n(grosor)} w ${n(x0)} ${n(alto - y0)} m ${n(x1)} ${n(alto - y1)} l S\n")
        cajas += floatArrayOf(minOf(x0, x1), minOf(y0, y1), maxOf(x0, x1), maxOf(y0, y1))
    }

    fun imagen(nombre: String, x: Float, yArriba: Float, w: Float, h: Float) {
        ops.append("q ${n(w)} 0 0 ${n(h)} ${n(x)} ${n(alto - yArriba - h)} cm /$nombre Do Q\n")
        cajas += floatArrayOf(x, yArriba, x + w, yArriba + h)
    }

    internal fun contenido(): String = ops.toString()
}
