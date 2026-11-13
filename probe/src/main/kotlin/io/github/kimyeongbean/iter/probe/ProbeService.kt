package io.github.kimyeongbean.iter.probe

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service (type location) that listens to the same kinds of signals the real app
 * records and writes, every [TICK_MS], what arrived since the last line and whether the process
 * is in the foreground. A long gap between ticks means the device slept or the process froze.
 */
class ProbeService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val stops = mutableListOf<() -> Unit>()

    private val gearEvents = AtomicInteger()
    private val turnEvents = AtomicInteger()
    private val lowBeamEvents = AtomicInteger()
    private val speedEvents = AtomicInteger()
    private val locationEvents = AtomicInteger()

    @Volatile private var gear: String? = null

    @Volatile private var turn: String? = null

    @Volatile private var lowBeam: String? = null

    @Volatile private var speedKmh: Float? = null

    @Volatile private var lastLocationMs: Long? = null
    private var lastTickMs = 0L
    private var lastSleepS = 0L

    private val tick =
        object : Runnable {
            override fun run() {
                writeTick()
                handler.postDelayed(this, TICK_MS)
            }
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ProbeLog.write(this, "SERVICE_CREATE")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val from = intent?.getStringExtra(EXTRA_FROM) ?: "restart(null intent)"
        ProbeLog.write(this, "SERVICE_START from=$from")
        if (!goForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (stops.isEmpty()) {
            stops += listenPleos()
            stops += listenSpeed()
            stops += listenLocation()
            lastTickMs = System.currentTimeMillis()
            lastSleepS = ProbeLog.deepSleepSeconds()
            handler.postDelayed(tick, TICK_MS)
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        ProbeLog.write(this, "TASK_REMOVED (swiped from recents) service keeps running")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ProbeLog.write(this, "SERVICE_DESTROY")
        handler.removeCallbacks(tick)
        stops.asReversed().forEach { runCatching(it) }
        stops.clear()
        super.onDestroy()
    }

    private fun goForeground(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "실차 확인", NotificationManager.IMPORTANCE_LOW))
        val notification =
            Notification
                .Builder(this, CHANNEL)
                .setContentTitle("Iter 실차 확인 중")
                .setContentText("차량 신호·위치 수신을 기록하고 있습니다")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setOngoing(true)
                .build()
        return runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onSuccess {
            ProbeLog.write(this, "FOREGROUND_OK type=location")
        }.onFailure {
            ProbeLog.write(this, "FOREGROUND_FAILED ${it.javaClass.simpleName}: ${it.message}")
        }.isSuccess
    }

    private fun writeTick() {
        val now = System.currentTimeMillis()
        val sleep = ProbeLog.deepSleepSeconds()
        val gapS = (now - lastTickMs) / 1000
        if (gapS > GAP_WARN_S) {
            ProbeLog.write(this, "GAP ${gapS}s since last tick (slept ${sleep - lastSleepS}s of it)")
        }
        lastTickMs = now
        lastSleepS = sleep
        val locationAge = lastLocationMs?.let { "${(now - it) / 1000}s ago" } ?: "never"
        ProbeLog.write(
            this,
            "TICK ${importance()} " +
                "gear=$gear(+${gearEvents.getAndSet(0)}) turn=$turn(+${turnEvents.getAndSet(0)}) " +
                "lowBeam=$lowBeam(+${lowBeamEvents.getAndSet(0)}) speed=$speedKmh(+${speedEvents.getAndSet(0)}) " +
                "location=$locationAge(+${locationEvents.getAndSet(0)}) bgLocation=${granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)}",
        )
    }

    private fun importance(): String {
        val info = ActivityManager.RunningAppProcessInfo().also { ActivityManager.getMyMemoryState(it) }
        return when (info.importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "app=FOREGROUND"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "app=BACKGROUND(fg-service)"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "app=VISIBLE"
            else -> "app=BACKGROUND(${info.importance})"
        }
    }

    private fun granted(permission: String) = checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun listenPleos(): () -> Unit {
        val vehicle =
            runCatching { Vehicle(this).apply { initialize() } }.getOrElse {
                ProbeLog.write(this, "PLEOS_INIT_FAILED ${it.javaClass.simpleName}: ${it.message}")
                return {}
            }
        val gearListener =
            object : GearSelectionListener {
                override fun onSelected(value: VehicleGear?) {
                    gear = value?.toString()
                    gearEvents.incrementAndGet()
                }

                override fun onFailed(error: Exception) {
                    ProbeLog.write(this@ProbeService, "PLEOS_GEAR_FAILED ${error.message}")
                }
            }
        val turnListener =
            object : TurnSignalStateListener {
                override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
                    turn = value?.toString()
                    turnEvents.incrementAndGet()
                }

                override fun onFailed(error: Exception) {
                    ProbeLog.write(this@ProbeService, "PLEOS_TURN_FAILED ${error.message}")
                }
            }
        val lowBeamListener =
            object : LightStateListener {
                override fun onLightStateUpdated(value: LightState?) {
                    lowBeam = value?.toString()
                    lowBeamEvents.incrementAndGet()
                }

                override fun onFailed(error: Exception) {
                    ProbeLog.write(this@ProbeService, "PLEOS_LOWBEAM_FAILED ${error.message}")
                }
            }
        val registered =
            listOf(
                register("gear", { vehicle.getGear().registerGearSelection(gearListener) }) {
                    vehicle.getGear().unregisterGearSelection(gearListener)
                },
                register("turn", { vehicle.getTurnSignal().registerTurnSignalState(turnListener) }) {
                    vehicle.getTurnSignal().unregisterTurnSignalState(turnListener)
                },
                register("lowBeam", { vehicle.getLight().registerLowBeamLightState(lowBeamListener) }) {
                    vehicle.getLight().unregisterLowBeamLightState(lowBeamListener)
                },
            )
        return {
            registered.forEach { it() }
            vehicle.release()
        }
    }

    private fun listenSpeed(): () -> Unit {
        if (!granted("android.car.permission.CAR_SPEED")) {
            ProbeLog.write(this, "SPEED_NO_PERMISSION")
            return {}
        }
        val car =
            runCatching { Car.createCar(this) }.getOrElse {
                ProbeLog.write(this, "SPEED_NO_CAR ${it.message}")
                return {}
            }
        val properties = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
        val callback =
            object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    (value.value as? Float)?.let { speedKmh = it * 3.6f }
                    speedEvents.incrementAndGet()
                }

                override fun onErrorEvent(
                    propertyId: Int,
                    areaId: Int,
                ) {
                    ProbeLog.write(this@ProbeService, "SPEED_ERROR $propertyId")
                }
            }
        val unregister =
            register("speed", {
                properties.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            }) { properties.unregisterCallback(callback) }
        return {
            unregister()
            car.disconnect()
        }
    }

    @SuppressLint("MissingPermission")
    private fun listenLocation(): () -> Unit {
        if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            ProbeLog.write(this, "LOCATION_NO_PERMISSION")
            return {}
        }
        val manager = getSystemService(LocationManager::class.java) ?: return {}
        val listener =
            LocationListener {
                lastLocationMs = System.currentTimeMillis()
                locationEvents.incrementAndGet()
            }
        return register("location", {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
        }) { manager.removeUpdates(listener) }
    }

    private fun register(
        name: String,
        register: () -> Unit,
        unregister: () -> Unit,
    ): () -> Unit {
        val error = runCatching(register).exceptionOrNull()
        if (error == null) {
            ProbeLog.write(this, "REGISTER_OK $name")
            return unregister
        }
        ProbeLog.write(this, "REGISTER_FAILED $name ${error.javaClass.simpleName}: ${error.message}")
        return {}
    }

    companion object {
        private const val TICK_MS = 10_000L
        private const val GAP_WARN_S = 30L
        private const val CHANNEL = "probe"
        private const val NOTIFICATION_ID = 124
        private const val EXTRA_FROM = "from"

        fun start(
            context: Context,
            from: String,
        ) {
            val intent = Intent(context, ProbeService::class.java).putExtra(EXTRA_FROM, from)
            runCatching { context.startForegroundService(intent) }
                .onFailure { ProbeLog.write(context, "START_SERVICE_FAILED from=$from ${it.javaClass.simpleName}: ${it.message}") }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProbeService::class.java))
        }
    }
}
