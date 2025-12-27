/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 */

package com.dismal.files.diskimage

import android.hardware.usb.UsbDevice
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java8.nio.file.Path
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiskImageFlashState(
    val diskImagePath: Path? = null,
    val diskImageSize: Long = 0L,
    val availableUsbDevices: List<UsbDevice> = emptyList(),
    val selectedDevice: UsbDevice? = null,
    val isFlashing: Boolean = false,
    val flashProgress: Float = 0f,
    val flashStatus: String = "",
    val error: String? = null
)

class DiskImageFlashViewModel : ViewModel() {
    private val _state = MutableStateFlow(DiskImageFlashState())
    val state: StateFlow<DiskImageFlashState> = _state.asStateFlow()

    fun setDiskImagePath(path: Path) {
        viewModelScope.launch {
            try {
                // TODO: Get file size and validate the image
                val size = 0L // path.fileSize()
                
                _state.value = _state.value.copy(
                    diskImagePath = path,
                    diskImageSize = size
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to load disk image: ${e.message}"
                )
            }
        }
    }

    fun setAvailableUsbDevices(devices: List<UsbDevice>) {
        _state.value = _state.value.copy(
            availableUsbDevices = devices
        )
    }

    fun selectUsbDevice(device: UsbDevice) {
        _state.value = _state.value.copy(
            selectedDevice = device
        )
    }

    fun startFlashing() {
        val currentState = _state.value
        if (currentState.diskImagePath == null || currentState.selectedDevice == null) {
            _state.value = currentState.copy(
                error = "Please select both a disk image and USB device"
            )
            return
        }

        _state.value = currentState.copy(
            isFlashing = true,
            flashProgress = 0f,
            flashStatus = "Starting flash operation...",
            error = null
        )

        viewModelScope.launch {
            try {
                // TODO: Implement actual flashing logic
                // This would integrate with EtchDroid's WorkerService
                
                // Simulate progress for now
                for (i in 1..100) {
                    kotlinx.coroutines.delay(50)
                    _state.value = _state.value.copy(
                        flashProgress = i / 100f,
                        flashStatus = "Flashing... $i%"
                    )
                }
                
                _state.value = _state.value.copy(
                    isFlashing = false,
                    flashStatus = "Flash completed successfully!",
                    flashProgress = 1f
                )
                
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isFlashing = false,
                    error = "Flash failed: ${e.message}",
                    flashStatus = "Flash failed"
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}