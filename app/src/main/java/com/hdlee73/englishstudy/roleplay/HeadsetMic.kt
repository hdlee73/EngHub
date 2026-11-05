package com.hdlee73.englishstudy.roleplay

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hdlee73.englishstudy.speaking.speech.BluetoothInputPolicy

/**
 * Makes the Bluetooth headset (hands-free/SCO or LE headset, never a watch) the microphone whenever one is connected and the
 * Speaking setting "블루투스 헤드셋 마이크 항상 사용" is on. The route is opened right before the microphone and closed again
 * afterwards, so the partner's voice is still read over the normal high-quality Bluetooth output.
 */
class HeadsetMic(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val prefs = app.getSharedPreferences("learning", Context.MODE_PRIVATE)
    private var active: AudioDeviceInfo? = null

    fun enabled(): Boolean = prefs.getBoolean("headset_mic_always", true)

    private fun isHeadsetType(t: Int) = t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || t == AudioDeviceInfo.TYPE_BLE_HEADSET

    private fun isWatch(d: AudioDeviceInfo): Boolean {
        val cls = runCatching {
            app.getSystemService(BluetoothManager::class.java)?.adapter?.bondedDevices?.firstOrNull { it.address == d.address }?.bluetoothClass?.deviceClass
        }.getOrNull()
        return BluetoothInputPolicy.isWatch(d.productName.toString(), cls)
    }

    private fun permitted() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(app, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun pick(list: List<AudioDeviceInfo>): AudioDeviceInfo? {
        val heads = list.filter { isHeadsetType(it.type) && it.address.isNotBlank() && !isWatch(it) }
        val chosen = prefs.getString("bluetooth_input", "").orEmpty()
        if (chosen.isNotBlank()) heads.firstOrNull { it.address == chosen }?.let { return it }
        return heads.maxByOrNull { if (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) 2 else 1 }
    }

    /** Opens the headset microphone route; returns how many milliseconds to wait before recording (0 = nothing to wait for or no headset). */
    fun acquire(): Long {
        if (audio == null || !enabled() || !permitted()) return 0L
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dev = pick(audio.availableCommunicationDevices) ?: return 0L
                if (active?.id == dev.id) return 0L
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                if (audio.setCommunicationDevice(dev)) { active = dev; 800L } else { audio.mode = AudioManager.MODE_NORMAL; 0L }
            } else {
                val dev = pick(audio.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()) ?: return 0L
                if (active != null) return 0L
                audio.mode = AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION") audio.startBluetoothSco()
                @Suppress("DEPRECATION") audio.isBluetoothScoOn = true
                active = dev
                1_000L
            }
        }.getOrDefault(0L)
    }

    /** The headset's input device to hand to AudioRecord, or null when the headset route is not open. */
    fun inputDevice(): AudioDeviceInfo? {
        val a = active ?: return null
        return audio?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.firstOrNull { it.address == a.address && isHeadsetType(it.type) }
    }

    fun release() {
        if (active == null) return
        active = null
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio?.clearCommunicationDevice()
            else {
                @Suppress("DEPRECATION") audio?.stopBluetoothSco()
                @Suppress("DEPRECATION") audio?.isBluetoothScoOn = false
            }
            audio?.mode = AudioManager.MODE_NORMAL
        }
    }
}
