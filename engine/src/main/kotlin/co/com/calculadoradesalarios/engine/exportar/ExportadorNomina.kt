package co.com.calculadoradesalarios.engine.exportar

import co.com.calculadoradesalarios.engine.AppState
import co.com.calculadoradesalarios.engine.Employee
import co.com.calculadoradesalarios.engine.Engine
import java.time.LocalDate

/** Archivo listo para compartir o guardar. */
class ArchivoExportado(val nombre: String, val mime: String, val bytes: ByteArray)

/** Las cuatro exportaciones del escritorio (PDF/Excel de un empleado y "de todos"), con sus nombres de archivo. */
class ExportadorNomina(private val comprobante: ComprobantePdf) {
    companion object {
        const val MimePdf = "application/pdf"
        const val MimeXlsx = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

        /** ExportPdf del escritorio: comprobante_{nombre con _}_{desde}_{hasta}.pdf */
        fun nombrePdfEmpleado(e: Employee, desde: LocalDate, hasta: LocalDate) =
            "comprobante_${e.name.trim().replace(Regex("\\s+"), "_")}_${Engine.Key(desde)}_${Engine.Key(hasta)}.pdf"

        fun nombrePdfTodos(desde: LocalDate, hasta: LocalDate) = "comprobantes_todos_${Engine.Key(desde)}_${Engine.Key(hasta)}.pdf"

        fun nombreExcel(desde: LocalDate, hasta: LocalDate) = "nomina_recargos_${Engine.Key(desde)}_${Engine.Key(hasta)}.xlsx"
    }

    fun pdf(st: AppState, incl: Boolean, emps: List<Employee>, desde: LocalDate, hasta: LocalDate, hoy: LocalDate, logo: ByteArray?, nombre: String): ArchivoExportado {
        require(emps.isNotEmpty()) { "No hay empleados para exportar." }
        return ArchivoExportado(nombre, MimePdf, comprobante.generar(st, incl, emps, desde, hasta, hoy, logo).first)
    }

    fun excel(st: AppState, incl: Boolean, emps: List<Employee>, desde: LocalDate, hasta: LocalDate): ArchivoExportado {
        require(emps.isNotEmpty()) { "No hay empleados para exportar." }
        return ArchivoExportado(nombreExcel(desde, hasta), MimeXlsx, ExcelNomina.xlsx(st, incl, emps, desde, hasta))
    }
}
