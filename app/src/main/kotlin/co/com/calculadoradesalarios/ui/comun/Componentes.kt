package co.com.calculadoradesalarios.ui.comun

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Lee una imagen guardada en filesDir (foto o logo). Se provee desde MainActivity. */
val LocalCargadorImagen = staticCompositionLocalOf<suspend (String?) -> ByteArray?> { { null } }

/**
 * Campo que se confirma al pulsar "Listo" o al salir de él, como CampoAjusteControl del escritorio
 * (que confirmaba con Enter/Tab o al perder el foco). Si el valor no es válido, se vuelve a mostrar
 * el anterior y el error queda debajo.
 */
@Composable
fun CampoConfirmable(
    etiqueta: String,
    valor: String,
    onConfirmar: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    teclado: KeyboardType = KeyboardType.Text,
    marcador: String? = null,
    ayuda: String? = null,
    unaLinea: Boolean = true,
    iconoFinal: (@Composable () -> Unit)? = null,
) {
    var texto by rememberSaveable(valor) { mutableStateOf(valor) }
    var tuvoFoco by remember { mutableStateOf(false) }
    val foco = LocalFocusManager.current
    fun confirmar() {
        if (texto != valor) onConfirmar(texto)
        texto = valor
    }
    OutlinedTextField(
        value = texto,
        onValueChange = { texto = it },
        label = { Text(etiqueta) },
        placeholder = marcador?.let { { Text(it) } },
        isError = error != null,
        trailingIcon = iconoFinal,
        supportingText = (error ?: ayuda)?.let { { Text(it) } },
        singleLine = unaLinea,
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { confirmar(); foco.clearFocus() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (it.isFocused) tuvoFoco = true
                else if (tuvoFoco) { tuvoFoco = false; confirmar() }
            },
    )
}

@Composable
fun Seccion(titulo: String, modifier: Modifier = Modifier, contenido: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            contenido()
        }
    }
}

/** Fila completa tocable (≥ 48 dp) con interruptor, como las casillas del escritorio. */
@Composable
fun FilaInterruptor(etiqueta: String, marcado: Boolean, onCambio: (Boolean) -> Unit, ayuda: String? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = marcado, role = Role.Switch, onValueChange = onCambio),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(etiqueta, style = MaterialTheme.typography.bodyLarge)
            if (ayuda != null) Text(ayuda, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = marcado, onCheckedChange = null)
    }
}

/** Color del avatar con la fórmula de EmpleadoRowViewModel del escritorio. */
fun colorAvatar(tono: Int): Color = Color(80 + (tono % 100), 100 + ((tono * 3) % 80), 120 + ((tono * 7) % 80))

@Composable
fun imagenGuardada(relPath: String?): ImageBitmap? {
    val cargar = LocalCargadorImagen.current
    val img by produceState<ImageBitmap?>(null, relPath) {
        value = cargar(relPath)?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }
    return img
}

@Composable
fun AvatarEmpleado(iniciales: String, tono: Int, foto: String?, tamano: Dp = 44.dp, descripcion: String? = null) {
    val img = imagenGuardada(foto)
    Box(
        Modifier.size(tamano).clip(CircleShape).background(colorAvatar(tono)),
        contentAlignment = Alignment.Center,
    ) {
        if (img != null) {
            Image(img, contentDescription = descripcion, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                iniciales,
                color = Color.White,
                style = if (tamano >= 64.dp) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
fun EstadoVacio(icono: ImageVector, titulo: String, texto: String, modifier: Modifier = Modifier, boton: String? = null, onBoton: () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icono, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text(titulo, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (boton != null) {
            Spacer(Modifier.width(0.dp))
            Button(onClick = onBoton) { Text(boton) }
        }
    }
}
