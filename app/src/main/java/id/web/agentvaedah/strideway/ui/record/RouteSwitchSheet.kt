package id.web.agentvaedah.strideway.ui.record

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.web.agentvaedah.strideway.core.format.Format
import id.web.agentvaedah.strideway.data.db.RouteEntity
import id.web.agentvaedah.strideway.ui.theme.Palette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteSwitchSheet(
    routes: List<RouteEntity>,
    activeId: Long?,
    onSelect: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding()) {
            Text("Pilih rute", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            LazyColumn {
                item {
                    RouteRow("Tanpa rute", "Rekam bebas", activeId == null) { onSelect(null) }
                    HorizontalDivider(color = Palette.Outline)
                }
                items(routes, key = { it.id }) { r ->
                    val detail = "${Format.km(r.distanceM)} km · naik ${Format.integer(r.elevationGainM)} m"
                    RouteRow(r.name, detail, r.id == activeId) { onSelect(r.id) }
                }
                if (routes.isEmpty()) {
                    item {
                        Text(
                            "Belum ada rute tersimpan. Impor GPX atau buat AI Route di tab Peta.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextMuted,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteRow(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(detail, color = Palette.TextMuted) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
