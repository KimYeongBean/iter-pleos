package io.github.kimyeongbean.iter.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener
import android.Manifest
import android.annotation.SuppressLint
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import io.github.kimyeongbean.iter.model.GeoPoint
import io.github.kimyeongbean.iter.model.SAMPLE_INTERVAL_MS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

private const val TAG = "PleosVehicleSignals"

/** AAOS runtime permission for vehicle speed (CarPropertyManager). */
internal const val CAR_SPEED_PERMISSION = "android.car.permission.CAR_SPEED"

/**
 * The real vehicle signal flow: turn signal, wiper, low beam, steering angle, brake pedal and gear
 * from the Pleos Vehicle SDK, vehicle speed from CarPropertyManager, location from LocationManager
 * (GPS). A signal that cannot be read (no permission, not supported, failed) stays null, and speed 0.
 * Listeners are registered on collection and released when collection stops.
 */
internal fun pleosVehicleSignals(context: Context): Flow<VehicleSignals> = flow {
    val latest = LatestVehicleSignals()
    val stops = mutableListOf<() -> Unit>()
    try {
        stops += listenPleos(context, latest)
        stops += listenSpeed(context, latest)
        stops += listenLocation(context, latest)
        emitAll(latest.ticks(System::currentTimeMillis))
    } finally {
        stops.asReversed().forEach { stop -> runCatching(stop).onFailure { Log.w(TAG, "stop failed", it) } }
    }
}

/** Registers a listener, logging (not throwing) when the SDK refuses. Returns how to unregister it. */
private fun listen(
    name: String,
    register: () -> Unit,
    unregister: () -> Unit,
): () -> Unit {
    val error = runCatching(register).exceptionOrNull() ?: return unregister
    Log.w(TAG, "$name: register failed", error)
    return {}
}

private fun failed(
    name: String,
    error: Exception,
) {
    Log.w(TAG, "$name: signal failed", error)
}

// Permissions are declared in the :app manifest (docs/interfaces.md 3), which library lint cannot see.
@SuppressLint("MissingPermission")
private fun listenPleos(
    context: Context,
    latest: LatestVehicleSignals,
): () -> Unit {
    val vehicle = runCatching { Vehicle(context).apply { initialize() } }.getOrElse {
        Log.w(TAG, "Pleos Vehicle SDK: initialize failed", it)
        return {}
    }
    val frontWindshield = WindowArea(WindowValue.FRONT_WINDSHIELD)

    val turnSignal = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) {
            latest.turnSignal = value?.toTurnSignal()
        }

        override fun onFailed(error: Exception) {
            failed("turn signal", error)
        }
    }
    val wiper = object : WiperStateListener {
        override fun onWiperStateUpdated(value: WiperState?) {
            latest.wiperOn = value?.toWiperOn()
        }

        override fun onFailed(error: Exception) {
            failed("wiper", error)
        }
    }
    val lowBeam = object : LightStateListener {
        override fun onLightStateUpdated(value: LightState?) {
            latest.lowBeamOn = value?.toLowBeamOn()
        }

        override fun onFailed(error: Exception) {
            failed("low beam", error)
        }
    }
    val steering = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(value: Float?) {
            latest.steeringAngleDeg = value?.toDouble()
        }

        override fun onFailed(error: Exception) {
            failed("steering angle", error)
        }
    }
    val brake = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(value: Boolean?) {
            latest.brakePedalPressed = value
        }

        override fun onFailed(error: Exception) {
            failed("brake pedal", error)
        }
    }
    val gear = object : GearSelectionListener {
        override fun onSelected(value: VehicleGear?) {
            latest.gear = value?.toGear()
        }

        override fun onFailed(error: Exception) {
            failed("gear", error)
        }
    }

    val unregisters = listOf(
        listen("turn signal", { vehicle.getTurnSignal().registerTurnSignalState(turnSignal) }, { vehicle.getTurnSignal().unregisterTurnSignalState(turnSignal) }),
        listen(
            "wiper",
            { vehicle.getWiper().registerWiperState(frontWindshield, wiper) },
            { vehicle.getWiper().unregisterWiperState(frontWindshield, wiper) },
        ),
        listen("low beam", { vehicle.getLight().registerLowBeamLightState(lowBeam) }, { vehicle.getLight().unregisterLowBeamLightState(lowBeam) }),
        listen(
            "steering angle",
            { vehicle.getSteeringWheel().registerSteeringWheelAngle(steering) },
            { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steering) },
        ),
        listen("brake pedal", { vehicle.getBrake().registerBrakePedalStatus(brake) }, { vehicle.getBrake().unregisterBrakePedalStatus(brake) }),
        listen("gear", { vehicle.getGear().registerGearSelection(gear) }, { vehicle.getGear().unregisterGearSelection(gear) }),
    )
    return {
        unregisters.forEach { it() }
        vehicle.release()
    }
}

private fun listenSpeed(
    context: Context,
    latest: LatestVehicleSignals,
): () -> Unit {
    if (context.checkSelfPermission(CAR_SPEED_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "speed: no $CAR_SPEED_PERMISSION")
        return {}
    }
    val car = runCatching { Car.createCar(context) }.getOrElse {
        Log.w(TAG, "speed: no car service", it)
        return {}
    }
    val properties = car.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
    val speed = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            (value.value as? Float)?.let { latest.speedKmh = speedKmhFromMetersPerSecond(it) }
        }

        override fun onErrorEvent(
            propertyId: Int,
            areaId: Int,
        ) {
            Log.w(TAG, "speed: error event $propertyId")
        }
    }
    val unregister = listen(
        "speed",
        { properties.registerCallback(speed, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL) },
        { properties.unregisterCallback(speed) },
    )
    return {
        unregister()
        car.disconnect()
    }
}

// Permissions are declared in the :app manifest (docs/interfaces.md 3), which library lint cannot see.
@SuppressLint("MissingPermission")
private fun listenLocation(
    context: Context,
    latest: LatestVehicleSignals,
): () -> Unit {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        Log.w(TAG, "location: no ${Manifest.permission.ACCESS_FINE_LOCATION}")
        return {}
    }
    val locationManager = context.getSystemService(LocationManager::class.java) ?: return {}
    val location = LocationListener { value: Location -> latest.location = GeoPoint(value.latitude, value.longitude) }
    return listen(
        "location",
        {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                SAMPLE_INTERVAL_MS,
                0f,
                location,
                Looper.getMainLooper(),
            )
        },
        { locationManager.removeUpdates(location) },
    )
}
