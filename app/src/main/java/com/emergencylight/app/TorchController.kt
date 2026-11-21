package com.emergencylight.app

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Controls the phone's flashlight through the CameraManager torch API.
 * The camera itself is never opened, so the CAMERA permission is not needed.
 */
class TorchController private constructor(
    private val cameraManager: CameraManager?,
    private val cameraId: String?,
    /** Highest strength level the phone reports (1 = on/off only). */
    val maxLevel: Int,
) {
    enum class Result { OK, BUSY, NO_FLASH }

    interface Listener {
        fun onTorchChanged(enabled: Boolean)
        fun onTorchUnavailable()
    }

    var listener: Listener? = null

    val hasFlash: Boolean get() = cameraManager != null && cameraId != null

    /** Several brightness levels only on Android 13+ and only if the phone reports more than 1 level. */
    val strengthSupported: Boolean get() = Build.VERSION.SDK_INT >= 33 && maxLevel > 1

    @Volatile
    private var lastSelfChange = 0L

    /** True right after this app changed the torch itself (to ignore our own callbacks). */
    fun recentlySelfChanged(): Boolean = SystemClock.elapsedRealtime() - lastSelfChange < SELF_CHANGE_GRACE_MS

    /** Turns the torch on. [deviceLevel] = real device level (1..maxLevel) or null for plain on. */
    fun turnOn(deviceLevel: Int?): Result {
        val cm = cameraManager ?: return Result.NO_FLASH
        val id = cameraId ?: return Result.NO_FLASH
        lastSelfChange = SystemClock.elapsedRealtime()
        return try {
            if (deviceLevel != null && strengthSupported && Build.VERSION.SDK_INT >= 33) {
                cm.turnOnTorchWithStrengthLevel(id, deviceLevel.coerceIn(1, maxLevel))
            } else {
                cm.setTorchMode(id, true)
            }
            Result.OK
        } catch (e: Exception) {
            // CameraAccessException (camera in use), IllegalArgumentException, etc.
            Result.BUSY
        }
    }

    fun turnOff() {
        val cm = cameraManager ?: return
        val id = cameraId ?: return
        lastSelfChange = SystemClock.elapsedRealtime()
        try {
            cm.setTorchMode(id, false)
        } catch (e: Exception) {
            // Ignore: nothing else we can do.
        }
    }

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) listener?.onTorchChanged(enabled)
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == cameraId) listener?.onTorchUnavailable()
        }
    }

    fun startListening() {
        try {
            cameraManager?.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            // Ignore.
        }
    }

    companion object {
        private const val SELF_CHANGE_GRACE_MS = 400L

        fun create(context: Context): TorchController {
            return try {
                val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                    ?: return TorchController(null, null, 1)
                var chosen: String? = null
                var chosenMax = 1
                // Prefer a back camera with a flash, otherwise any camera with a flash.
                val withFlash = cm.cameraIdList.filter { id ->
                    try {
                        cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                    } catch (e: Exception) {
                        false
                    }
                }
                val back = withFlash.firstOrNull { id ->
                    try {
                        cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                            CameraCharacteristics.LENS_FACING_BACK
                    } catch (e: Exception) {
                        false
                    }
                }
                chosen = back ?: withFlash.firstOrNull()
                if (chosen != null && Build.VERSION.SDK_INT >= 33) {
                    chosenMax = try {
                        cm.getCameraCharacteristics(chosen)
                            .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                    } catch (e: Exception) {
                        1
                    }
                }
                TorchController(if (chosen != null) cm else null, chosen, chosenMax.coerceAtLeast(1))
            } catch (e: Exception) {
                TorchController(null, null, 1)
            }
        }
    }
}
