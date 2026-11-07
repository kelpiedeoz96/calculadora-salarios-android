package co.com.calculadoradesalarios.ui.principal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import co.com.calculadoradesalarios.ui.comun.EstadoVacio

fun Pestana.icono(): ImageVector = when (this) {
    Pestana.Panel -> Icons.Outlined.Dashboard
    Pestana.Liquidacion -> Icons.AutoMirrored.Outlined.ReceiptLong
    Pestana.Empleados -> Icons.Outlined.Groups
    Pestana.Calendario -> Icons.Outlined.CalendarMonth
    Pestana.Historial -> Icons.Outlined.History
    Pestana.Ajustes -> Icons.Outlined.Settings
}

/** Ancho a partir del cual se usa NavigationRail (WindowWidthSizeClass.Medium de Material 3). */
val AnchoParaRail = 600.dp

@Composable
fun PrincipalContenido(
    estado: PrincipalEstado,
    onNavegar: (Pestana) -> Unit,
    onAtras: () -> Unit,
    onDesplazarPeriodo: (Int) -> Unit,
    onAbrirPeriodo: () -> Unit,
    snackbar: SnackbarHostState,
    contenido: @Composable (Pestana, Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val ancho = maxWidth >= AnchoParaRail
        Row(Modifier.fillMaxSize()) {
            if (ancho) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Pestana.Barra.forEach { p ->
                        NavigationRailItem(
                            selected = estado.pestana == p,
                            onClick = { onNavegar(p) },
                            icon = { Icon(p.icono(), contentDescription = null) },
                            label = { Text(p.titulo) },
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = { BarraSuperior(estado, onNavegar, onAtras, onDesplazarPeriodo, onAbrirPeriodo) },
                bottomBar = {
                    if (!ancho && estado.pestana != Pestana.Ajustes) {
                        NavigationBar {
                            Pestana.Barra.forEach { p ->
                                NavigationBarItem(
                                    selected = estado.pestana == p,
                                    onClick = { onNavegar(p) },
                                    icon = { Icon(p.icono(), contentDescription = null) },
                                    label = { Text(p.titulo, maxLines = 1) },
                                )
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { relleno ->
                // En pantallas anchas el contenido no se estira más de 840 dp (ancho "Expanded" de Material 3).
                Box(Modifier.fillMaxSize().padding(relleno), contentAlignment = Alignment.TopCenter) {
                    contenido(estado.pestana, Modifier.widthIn(max = 840.dp).fillMaxSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BarraSuperior(
    estado: PrincipalEstado,
    onNavegar: (Pestana) -> Unit,
    onAtras: () -> Unit,
    onDesplazarPeriodo: (Int) -> Unit,
    onAbrirPeriodo: () -> Unit,
) {
    val p = estado.pestana
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        navigationIcon = {
            if (p == Pestana.Ajustes) {
                IconButton(onClick = onAtras) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Volver") }
            }
        },
        title = {
            Column {
                Text(p.titulo, maxLines = 1)
                if (p.usaPeriodo) {
                    Row(
                        Modifier.clickable(onClickLabel = "Cambiar periodo", role = Role.Button, onClick = onAbrirPeriodo),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(estado.etiquetaPeriodo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    }
                } else if (p == Pestana.Ajustes) {
                    Text("Preferencias de cálculo y datos", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        actions = {
            if (p.usaPeriodo) {
                IconButton(onClick = { onDesplazarPeriodo(-1) }, enabled = estado.puedeDesplazar) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "Quincena anterior")
                }
                IconButton(onClick = { onDesplazarPeriodo(1) }, enabled = estado.puedeDesplazar) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "Quincena siguiente")
                }
            }
            if (p != Pestana.Ajustes) {
                IconButton(onClick = { onNavegar(Pestana.Ajustes) }) { Icon(Icons.Outlined.Settings, contentDescription = "Ajustes") }
            }
        },
    )
}

/** Pantallas que llegan en las entregas 3b y 3c. */
@Composable
fun EnConstruccion(p: Pestana, modifier: Modifier = Modifier) {
    val entrega = if (p == Pestana.Liquidacion) "3b" else "3c"
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EstadoVacio(p.icono(), p.titulo, "Esta pantalla llega en la entrega $entrega.")
    }
}
