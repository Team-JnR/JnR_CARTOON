package io.github.picturehouse.cartoon.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.picturehouse.cartoon.CartoonController
import io.github.picturehouse.cartoon.SourceMode
import io.github.picturehouse.cartoon.model.SoundPack

/**
 * 메인 화면: 위쪽은 소리 켜기/끄기와 상태(마지막으로 울린 소리), 아래쪽은 사운드 팩 고르기.
 * 팩의 동작별 소리는 카드의 ⚙ 버튼으로 여는 설정 화면에 있다 (+ 시연용 시뮬레이터).
 * 운전자는 화면을 조작할 필요 없이 원래 하던 대로 차량을 조작하면 된다.
 */
@Composable
fun CartoonRoot(controller: CartoonController) {
    val vehicle by controller.vehicle.collectAsStateWithLifecycle()
    val setupRestricted by controller.restrictions.setupRestricted.collectAsStateWithLifecycle()
    val running by controller.running.collectAsStateWithLifecycle()
    val soundOn by controller.packStore.soundOn.collectAsStateWithLifecycle()
    val mode by controller.mode.collectAsStateWithLifecycle()
    val parked = controller.canChangeSettings(vehicle.gear, setupRestricted, running)
    var showSimulator by rememberSaveable { mutableStateOf(true) }
    var settingsPack by rememberSaveable { mutableStateOf<SoundPack?>(null) }

    // 설정 화면은 주차 중에만: 주행을 시작하면 닫는다
    LaunchedEffect(parked) { if (!parked) settingsPack = null }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("CAR", style = MaterialTheme.typography.headlineLarge, color = CartoonColors.Yellow, fontWeight = FontWeight.Black)
                Text("TOON", style = MaterialTheme.typography.headlineLarge, color = CartoonColors.Pink, fontWeight = FontWeight.Black)
                Spacer(Modifier.weight(1f))
                if (soundOn) Pill(if (mode == SourceMode.PLEOS) "🚘 ${mode.label}" else "🧪 ${mode.label}")
                OutlinedButton(onClick = { showSimulator = !showSimulator }) {
                    Text(if (showSimulator) "시뮬레이터 닫기" else "시뮬레이터 열기")
                }
            }
            Row(Modifier.fillMaxSize()) {
                val content = Modifier.weight(1f).fillMaxHeight()
                when (val p = settingsPack) {
                    null -> MainPanel(controller, parked, onOpenSettings = { settingsPack = it }, modifier = content)
                    else -> PackSettingsScreen(controller, p, onBack = { settingsPack = null }, modifier = content)
                }
                if (showSimulator) SimulatorPanel(controller, Modifier.width(500.dp).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun MainPanel(controller: CartoonController, parked: Boolean, onOpenSettings: (SoundPack) -> Unit, modifier: Modifier) {
    val pack by controller.packStore.pack.collectAsStateWithLifecycle()

    Column(modifier.padding(start = 28.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SoundStatus(controller, Modifier.weight(1f).fillMaxWidth())

        // 사운드 팩 고르기 (주차 중에만)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("사운드 팩", style = MaterialTheme.typography.titleLarge)
            Text(
                if (parked) "눌러서 바꾸고, ⚙에서 동작별 소리를 들어 볼 수 있어요" else "주행 중에는 바꿀 수 없어요",
                style = MaterialTheme.typography.bodyMedium,
                color = if (parked) MaterialTheme.colorScheme.onSurfaceVariant else CartoonColors.Pink,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth().height(320.dp)) {
            SoundPack.entries.forEach { p ->
                PackCard(
                    p,
                    selected = p == pack,
                    enabled = parked,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onSettings = { onOpenSettings(p) },
                    onClick = { controller.selectPack(p) },
                )
            }
        }
    }
}

/** 위쪽: 소리 켜기/끄기 + 상태 (가운데 정렬, 정적 표시·애니메이션 없음) */
@Composable
private fun SoundStatus(controller: CartoonController, modifier: Modifier) {
    val soundOn by controller.packStore.soundOn.collectAsStateWithLifecycle()
    val running by controller.running.collectAsStateWithLifecycle()
    val last by controller.last.collectAsStateWithLifecycle()
    val ready by controller.soundBank.ready.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
    ) {
        Text(if (!running) "🔇" else last?.action?.emoji ?: "🔊", fontSize = 88.sp)
        Text(
            if (!running) "소리가 꺼져 있어요" else last?.let { it.pack.caption(it.action) } ?: "깜빡이를 켜 보세요!",
            style = MaterialTheme.typography.displayMedium,
            color = CartoonColors.Yellow,
            textAlign = TextAlign.Center,
        )
        Text(
            if (!running) "아래 버튼으로 켜면 차량 동작에 맞춰 소리가 나요."
            else last?.let { "방금: ${it.action.label} · ${it.pack.title}" }
                ?: "차량 동작에 맞춰 짧은 소리가 나요. 화면을 볼 필요는 없어요.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        if (running && !ready) Text("소리 불러오는 중…", style = MaterialTheme.typography.bodyMedium, color = CartoonColors.Mint)
        Spacer(Modifier.height(6.dp))
        // 소리 켜기/끄기: 켠 동안만 백그라운드 서비스가 동작한다. 끄는 쪽은 주행 중에도 허용(주의 분산을 줄이는 방향).
        Button(
            onClick = { controller.setSoundOn(context, !soundOn) },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (soundOn) CartoonColors.Mint else CartoonColors.SurfaceHigh,
                contentColor = if (soundOn) CartoonColors.Bg else MaterialTheme.colorScheme.onSurface,
            ),
            contentPadding = PaddingValues(horizontal = 36.dp, vertical = 18.dp),
        ) {
            Text(if (soundOn) "🔊 소리 켜짐" else "🔇 소리 꺼짐", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun PackCard(
    pack: SoundPack,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onSettings: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier
            .alpha(if (enabled || selected) 1f else 0.45f)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(BorderStroke(5.dp, if (selected) CartoonColors.Yellow else Color.Transparent), shape)
            .clickable(enabled = enabled && !selected, onClick = onClick),
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(pack.emoji, fontSize = 88.sp)
            Text(
                pack.title,
                style = MaterialTheme.typography.headlineMedium,
                color = if (selected) CartoonColors.Yellow else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                pack.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            if (selected) Pill("✔ 사용 중", CartoonColors.Mint.copy(alpha = 0.3f))
        }
        // 오른쪽 위 설정 버튼: 이 팩의 동작별 소리 (설정 화면이므로 주차 중에만)
        Surface(
            onClick = onSettings,
            enabled = enabled,
            shape = CircleShape,
            color = CartoonColors.SurfaceHigh,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text("⚙️", fontSize = 36.sp) }
        }
    }
}

@Composable
fun Pill(text: String, color: Color = CartoonColors.SurfaceHigh) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 8.dp),
    )
}
