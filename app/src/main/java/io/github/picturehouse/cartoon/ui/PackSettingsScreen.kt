package io.github.picturehouse.cartoon.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.picturehouse.cartoon.CartoonController
import io.github.picturehouse.cartoon.SourceMode
import io.github.picturehouse.cartoon.model.CarAction
import io.github.picturehouse.cartoon.model.SoundPack
import io.github.picturehouse.cartoon.vehicle.SignalDomain
import io.github.picturehouse.cartoon.vehicle.Support

/**
 * 사운드 팩 설정: 그 팩의 동작별 소리를 보고 미리 들어 본다.
 * 설정 화면이므로 주차 중에만 열 수 있고, 주행을 시작하면 [CartoonRoot]가 닫는다.
 */
@Composable
fun PackSettingsScreen(controller: CartoonController, pack: SoundPack, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val selected by controller.packStore.pack.collectAsStateWithLifecycle()
    val vehicle by controller.vehicle.collectAsStateWithLifecycle()
    val support by controller.vehicleSource.support.collectAsStateWithLifecycle()
    val mode by controller.mode.collectAsStateWithLifecycle()
    val setupRestricted by controller.restrictions.setupRestricted.collectAsStateWithLifecycle()
    val running by controller.running.collectAsStateWithLifecycle()
    val parked = controller.canChangeSettings(vehicle.gear, setupRestricted, running)

    BackHandler(onBack = onBack)

    Column(modifier.padding(start = 28.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            OutlinedButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
                Text("← 뒤로", style = MaterialTheme.typography.titleMedium)
            }
            Text(pack.emoji, fontSize = 56.sp)
            Column(Modifier.weight(1f)) {
                Text("${pack.title} 설정", style = MaterialTheme.typography.headlineMedium)
                Text(pack.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (pack == selected) {
                Pill("✔ 사용 중", CartoonColors.Mint.copy(alpha = 0.3f))
            } else {
                Button(
                    onClick = { controller.selectPack(pack) },
                    enabled = parked,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp),
                ) { Text("이 팩 사용하기", style = MaterialTheme.typography.titleMedium) }
            }
        }

        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("동작별 소리", style = MaterialTheme.typography.titleLarge)
            if (!running) {
                Text("소리를 켜면 미리 들어 볼 수 있어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(CarAction.entries) { action ->
                Row(
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(action.emoji, fontSize = 34.sp)
                    Column(Modifier.weight(1f)) {
                        Text(action.label, style = MaterialTheme.typography.titleMedium)
                        Text(action.trigger, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (mode == SourceMode.PLEOS && support[domainOf(action)] == Support.UNSUPPORTED) {
                            Text("이 차량에서는 지원되지 않음", style = MaterialTheme.typography.bodySmall, color = CartoonColors.Pink)
                        }
                    }
                    Text(pack.caption(action), style = MaterialTheme.typography.bodyLarge)
                    if (parked && running) {
                        OutlinedButton(onClick = { controller.preview(pack, action) }) { Text("▶ 듣기") }
                    }
                }
            }
            item { Spacer(Modifier.padding(4.dp)) }
        }
    }
}

private fun domainOf(a: CarAction) = when (a) {
    CarAction.LEFT_TURN, CarAction.RIGHT_TURN -> SignalDomain.TURN_SIGNAL
    CarAction.WIPER_SLOW, CarAction.WIPER_FAST -> SignalDomain.WIPER
    CarAction.DEPART, CarAction.ARRIVE -> SignalDomain.GEAR
    CarAction.HEADLIGHT -> SignalDomain.HEADLIGHT
    CarAction.WINDOW_OPEN, CarAction.WINDOW_CLOSE -> SignalDomain.WINDOW
}
