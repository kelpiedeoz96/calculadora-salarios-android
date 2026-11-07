package co.com.calculadoradesalarios.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import co.com.calculadoradesalarios.engine.RespaldoAutomatico
import java.io.File

/**
 * Carpeta extra elegida con ACTION_OPEN_DOCUMENT_TREE (Drive, tarjeta SD, USB…). Se guarda la
 * URI con permiso persistente en las preferencias de Android, no en data.json: en el escritorio
 * backupExtraDir es una ruta del disco y una URI content:// no le serviría.
 */
class CarpetaExtraSaf(private val context: Context) : CarpetaExtra {
    private val prefs = context.getSharedPreferences("respaldos", Context.MODE_PRIVATE)

    private fun uri(): Uri? = prefs.getString(Clave, null)?.let(Uri::parse)

    override fun nombre(): String? {
        val tree = uri() ?: return null
        return try {
            val doc = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            context.contentResolver.query(doc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: tree.lastPathSegment
        } catch (_: Exception) {
            tree.lastPathSegment
        }
    }

    override fun fijar(uri: String?) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        uri()?.let { anterior -> runCatching { context.contentResolver.releasePersistableUriPermission(anterior, flags) } }
        if (uri == null) {
            prefs.edit().remove(Clave).apply()
        } else {
            context.contentResolver.takePersistableUriPermission(Uri.parse(uri), flags)
            prefs.edit().putString(Clave, uri).apply()
        }
    }

    override fun copiar(zip: File, conservar: Int): String {
        val tree = uri() ?: throw IllegalStateException("No hay carpeta extra.")
        val resolver = context.contentResolver
        val padre = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val destino = DocumentsContract.createDocument(resolver, padre, "application/zip", zip.name)
            ?: throw IllegalStateException("No se pudo crear el archivo en la carpeta extra.")
        resolver.openOutputStream(destino)!!.use { out -> zip.inputStream().use { it.copyTo(out) } }
        podar(tree, conservar)
        return destino.toString()
    }

    /** Mismas reglas que RespaldoAutomatico.Podar: se borran los más viejos por nombre. */
    private fun podar(tree: Uri, conservar: Int) {
        val resolver = context.contentResolver
        val hijos = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val respaldos = mutableListOf<Pair<String, String>>()
        resolver.query(
            hijos,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val nombre = c.getString(1) ?: continue
                if (nombre.startsWith(RespaldoAutomatico.Prefijo) && nombre.endsWith(".zip")) respaldos += c.getString(0) to nombre
            }
        }
        for ((id, _) in ParaBorrar(respaldos, conservar)) {
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
    }

    companion object {
        private const val Clave = "carpeta_extra_uri"

        /** (id, nombre) que sobran: orden descendente por nombre y se conservan al menos 1. */
        fun <T> ParaBorrar(respaldos: List<Pair<T, String>>, conservar: Int): List<Pair<T, String>> =
            respaldos.sortedByDescending { it.second }.drop(maxOf(1, conservar))
    }
}
