package io.github.picturehouse.cartoon.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.picturehouse.cartoon.CartoonController
import io.github.picturehouse.cartoon.SourceMode
import io.github.picturehouse.cartoon.model.Gear
import io.github.picturehouse.cartoon.model.Turn
import io.github.picturehouse.cartoon.model.VehicleSignal
import io.github.picturehouse.cartoon.model.WiperLevel

/**
 * 시연·테스트용 차량 시뮬레이터. Pleos 차량 서비스가 없는 환경에서도 동작을 확인할 수 있고,
 * Pleos 에뮬레이터에서는 Virtual Console과 함께 쓸 수 있다.
 * (실차 배포 빌드에서는 숨길 개발자 도구)
 */
@Composable
fun SimulatorPanel(controller: CartoonController, modifier: Modifier = Modifier) {
    val v by controller.vehicle.collectAsStateWithLifecycle()
    val log by controller.log.collectAsStateWithLifecycle()
    val mode by controller.mode.collectAsStateWithLifecycle()
    fun send(s: VehicleSignal) = controller.simulate(s)

    Column(
        modifier
            .padding(end = 20.dp, bottom = 20.dp)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("🧪 차량 시뮬레이터", style = MaterialTheme.typography.titleLarge)
        Text(
            if (mode == SourceMode.PLEOS) "Virtual Console(127.0.0.1:8088)로 실제 차량 신호를 바꾸거나, 여기서 바로 신호를 넣을 수 있어요."
            else "차량 서비스가 없어 시뮬레이터로 차량 신호를 입력합니다.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Label("기어")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Gear.PARK, Gear.REVERSE, Gear.NEUTRAL, Gear.DRIVE).forEach { g ->
                SimButton(g.label, v.gear == g, Modifier.weight(1f)) { send(VehicleSignal.GearChanged(g)) }
            }
        }

        Label("방향지시등")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimButton("◀ 왼쪽", v.turn == Turn.LEFT, Modifier.weight(1f)) {
                send(VehicleSignal.TurnSignal(if (v.turn == Turn.LEFT) Turn.OFF else Turn.LEFT))
            }
            SimButton("끄기", v.turn == Turn.OFF, Modifier.weight(1f)) { send(VehicleSignal.TurnSignal(Turn.OFF)) }
            SimButton("오른쪽 ▶", v.turn == Turn.RIGHT, Modifier.weight(1f)) {
                send(VehicleSignal.TurnSignal(if (v.turn == Turn.RIGHT) Turn.OFF else Turn.RIGHT))
            }
        }

        Label("와이퍼")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(WiperLevel.OFF to "끄기", WiperLevel.INTERMITTENT to "간헐", WiperLevel.SLOW to "저속", WiperLevel.FAST to "고속").forEach { (lv, label) ->
                SimButton(label, v.wiper == lv, Modifier.weight(1f)) { send(VehicleSignal.Wiper(lv)) }
            }
        }

        Label("조명 · 창문")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SimButton("💡 전조등", v.headlight, Modifier.weight(1f)) { send(VehicleSignal.Headlight(!v.headlight)) }
            val driverOpen = "운전석" in v.openWindows
            SimButton("🪟 창문 열림", driverOpen, Modifier.weight(1f)) { send(VehicleSignal.Window("운전석", !driverOpen)) }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Label("이벤트 로그")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
            items(log, key = { it.time.toString() + it.text }) { e ->
                Text(
                    "${controller.formatTime(e.time)}  ${e.text}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (e.played) CartoonColors.Yellow else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SimButton(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) CartoonColors.Yellow else CartoonColors.SurfaceHigh,
            contentColor = if (active) Color(0xFF1A1500) else MaterialTheme.colorScheme.onSurface,
        ),
    ) { Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1) }
}
