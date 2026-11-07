package co.com.calculadoradesalarios.ui.comun

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Deslizar hacia la izquierda para borrar. El aviso con "Deshacer" lo muestra quien llama.
 * Para lectores de pantalla queda también como acción "Eliminar".
 */
@Composable
fun FilaDeslizable(
    onBorrar: () -> Unit,
    forma: Shape,
    modifier: Modifier = Modifier,
    descripcionBorrar: String = "Eliminar",
    contenido: @Composable () -> Unit,
) {
    val estado = rememberSwipeToDismissBoxState(
        confirmValueChange = { valor ->
            if (valor == SwipeToDismissBoxValue.EndToStart) { onBorrar(); true } else false
        },
    )
    SwipeToDismissBox(
        state = estado,
        enableDismissFromStartToEnd = false,
        modifier = modifier.semantics {
            customActions = listOf(CustomAccessibilityAction(descripcionBorrar) { onBorrar(); true })
        },
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().clip(forma).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) { contenido() }
}
