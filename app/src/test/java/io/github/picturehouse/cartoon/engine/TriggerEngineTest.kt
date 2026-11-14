package io.github.picturehouse.cartoon.engine

import io.github.picturehouse.cartoon.model.CarAction
import io.github.picturehouse.cartoon.model.Gear
import io.github.picturehouse.cartoon.model.Turn
import io.github.picturehouse.cartoon.model.VehicleSignal
import io.github.picturehouse.cartoon.model.WiperLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TriggerEngineTest {
    private lateinit var engine: TriggerEngine

    @Before
    fun setUp() {
        engine = TriggerEngine()
        engine.seed(VehicleSignal.GearChanged(Gear.PARK))
    }

    private fun send(s: VehicleSignal) = engine.onSignal(s)
    private val tick = TriggerEngine.TURN_TICK_MS

    @Test
    fun `깜빡이 - 켜져 있는 동안 깜빡임마다 반복, 끄면 멈춤`() {
        assertEquals(Trigger.Start(SoundChannel.TURN, CarAction.LEFT_TURN, tick), send(VehicleSignal.TurnSignal(Turn.LEFT)))
        assertNull(send(VehicleSignal.TurnSignal(Turn.LEFT)))
        assertEquals(Trigger.Stop(SoundChannel.TURN), send(VehicleSignal.TurnSignal(Turn.OFF)))
        assertEquals(Trigger.Start(SoundChannel.TURN, CarAction.RIGHT_TURN, tick), send(VehicleSignal.TurnSignal(Turn.RIGHT)))
    }

    @Test
    fun `깜빡이 - 방향을 바로 바꾸면 새 방향으로 다시 시작`() {
        send(VehicleSignal.TurnSignal(Turn.LEFT))
        assertEquals(Trigger.Start(SoundChannel.TURN, CarAction.RIGHT_TURN, tick), send(VehicleSignal.TurnSignal(Turn.RIGHT)))
    }

    @Test
    fun `깜빡이 - 차량이 깜빡임마다 켜짐·꺼짐을 보내면 켜짐마다 1번`() {
        repeat(3) {
            assertEquals(Trigger.Start(SoundChannel.TURN, CarAction.LEFT_TURN, tick), send(VehicleSignal.TurnSignal(Turn.LEFT)))
            assertEquals(Trigger.Stop(SoundChannel.TURN), send(VehicleSignal.TurnSignal(Turn.OFF)))
        }
    }

    @Test
    fun `와이퍼 - 단계에 맞는 왕복 주기로 반복, 끄면 멈춤`() {
        assertEquals(
            Trigger.Start(SoundChannel.WIPER, CarAction.WIPER_SLOW, WiperLevel.SLOW.periodMs),
            send(VehicleSignal.Wiper(WiperLevel.SLOW)),
        )
        assertNull(send(VehicleSignal.Wiper(WiperLevel.SLOW)))
        assertEquals(
            Trigger.Start(SoundChannel.WIPER, CarAction.WIPER_FAST, WiperLevel.FAST.periodMs),
            send(VehicleSignal.Wiper(WiperLevel.FAST)),
        )
        assertEquals(Trigger.Stop(SoundChannel.WIPER), send(VehicleSignal.Wiper(WiperLevel.OFF)))
    }

    @Test
    fun `와이퍼 - 간헐 단계가 바뀌면 새 주기로, 1회 닦기는 한 번만`() {
        assertEquals(
            Trigger.Start(SoundChannel.WIPER, CarAction.WIPER_SLOW, 6_000),
            send(VehicleSignal.Wiper(WiperLevel.INTERMITTENT, 6_000)),
        )
        assertEquals(
            Trigger.Start(SoundChannel.WIPER, CarAction.WIPER_SLOW, 3_000),
            send(VehicleSignal.Wiper(WiperLevel.INTERMITTENT, 3_000)),
        )
        assertEquals(Trigger.Start(SoundChannel.WIPER, CarAction.WIPER_SLOW, 0), send(VehicleSignal.Wiper(WiperLevel.MIST)))
    }

    @Test
    fun `기어 P→D 출발, D→P 정지 - 한 번씩`() {
        assertEquals(Trigger.Once(CarAction.DEPART), send(VehicleSignal.GearChanged(Gear.DRIVE)))
        assertNull(send(VehicleSignal.GearChanged(Gear.REVERSE)))
        assertNull(send(VehicleSignal.GearChanged(Gear.DRIVE)))
        assertEquals(Trigger.Once(CarAction.ARRIVE), send(VehicleSignal.GearChanged(Gear.PARK)))
    }

    @Test
    fun `전조등 켜질 때마다 1번, 창문은 한 짝이 열리고 닫힐 때마다 1번`() {
        assertEquals(Trigger.Once(CarAction.HEADLIGHT), send(VehicleSignal.Headlight(true)))
        assertNull(send(VehicleSignal.Headlight(true)))
        assertNull(send(VehicleSignal.Headlight(false)))
        assertEquals(Trigger.Once(CarAction.HEADLIGHT), send(VehicleSignal.Headlight(true)))

        assertEquals(Trigger.Once(CarAction.WINDOW_OPEN), send(VehicleSignal.Window("FL", true)))
        assertNull(send(VehicleSignal.Window("FL", true)))
        assertEquals(Trigger.Once(CarAction.WINDOW_OPEN), send(VehicleSignal.Window("FR", true)))
        assertEquals(Trigger.Once(CarAction.WINDOW_CLOSE), send(VehicleSignal.Window("FL", false)))
        assertEquals(Trigger.Once(CarAction.WINDOW_OPEN), send(VehicleSignal.Window("FL", true)))
    }

    @Test
    fun `창문 닫기 - 열려 있던 한 짝이 닫힐 때마다 1번, 원래 닫혀 있던 창문은 소리 없음`() {
        assertNull(send(VehicleSignal.Window("FL", false)))
        engine.seed(VehicleSignal.Window("FR", true)) // 앱 시작 때 이미 열려 있던 창문
        assertEquals(Trigger.Once(CarAction.WINDOW_CLOSE), send(VehicleSignal.Window("FR", false)))
        assertNull(send(VehicleSignal.Window("FR", false)))
        send(VehicleSignal.Window("FL", true))
        send(VehicleSignal.Window("FR", true))
        assertEquals(Trigger.Once(CarAction.WINDOW_CLOSE), send(VehicleSignal.Window("FL", false)))
        assertEquals(Trigger.Once(CarAction.WINDOW_CLOSE), send(VehicleSignal.Window("FR", false)))
    }

    @Test
    fun `앱 시작 때 - 켜는 소리는 없고, 동작 중인 깜빡이·와이퍼는 다음 주기부터`() {
        assertNull(engine.seed(VehicleSignal.GearChanged(Gear.DRIVE)))
        assertNull(send(VehicleSignal.GearChanged(Gear.DRIVE)))
        assertEquals(
            Trigger.Start(SoundChannel.WIPER, CarAction.WIPER_SLOW, WiperLevel.SLOW.periodMs, immediate = false),
            engine.seed(VehicleSignal.Wiper(WiperLevel.SLOW)),
        )
        assertNull(send(VehicleSignal.Wiper(WiperLevel.SLOW)))
        assertEquals(
            Trigger.Start(SoundChannel.TURN, CarAction.LEFT_TURN, tick, immediate = false),
            engine.seed(VehicleSignal.TurnSignal(Turn.LEFT)),
        )
        assertNull(engine.seed(VehicleSignal.TurnSignal(Turn.OFF)))
        assertNull(engine.seed(VehicleSignal.Headlight(true)))
    }
}
