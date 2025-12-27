/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 */

package com.dismal.files.diskimage

import android.app.Dialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import com.dismal.files.R
import com.dismal.files.databinding.DiskImageFlashDialogBinding
import com.dismal.files.file.FileItem
import com.dismal.files.provider.common.newInputStream
import com.dismal.files.util.ParcelableArgs
import com.dismal.files.util.args
import com.dismal.files.util.putArgs
import com.dismal.files.util.show
import com.dismal.files.util.showToast
import java.text.DecimalFormat

class DiskImageFlashDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()
    private var _binding: DiskImageFlashDialogBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var usbManager: UsbManager
    private lateinit var deviceAdapter: SimpleUsbDeviceAdapter
    private var selectedDevice: UsbDevice? = null
    private var selectedDeviceInfo: UsbDeviceInfo? = null
    private var isFlashing = false
    
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    refreshUsbDevices()
                }
                ACTION_USB_PERMISSION -> {
                    // Like EtchDroid, we don't rely on intent extras for permission status
                    // Instead, we check directly with the USB manager
                    selectedDevice?.let { device ->
                        val granted = usbManager.hasPermission(device)
                        Log.d("DiskImageFlash", "USB permission response: granted=$granted for device: ${device.productName}")
                        
                        if (granted) {
                            startActualFlashing(device)
                        } else {
                            Log.d("DiskImageFlash", "USB permission denied for device: ${device.productName}")
                            showToast("USB permission denied. Cannot flash image.")
                            resetToDeviceSelection()
                        }
                    } ?: run {
                        Log.w("DiskImageFlash", "Received USB permission response but no device selected")
                        showToast("USB permission error. Please try again.")
                        resetToDeviceSelection()
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DiskImageFlashDialogBinding.inflate(LayoutInflater.from(requireContext()))
        
        usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        
        setupViews()
        setupRecyclerView()
        refreshUsbDevices()
        registerUsbReceiver()
        
        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .create()
    }
    
    private fun setupViews() {
        // Set file info
        val fileName = args.file.path.fileName.toString()
        val fileSize = try {
            formatFileSize(args.file.attributes.size())
        } catch (e: Exception) {
            "Unknown size"
        }
        binding.fileInfo.text = "$fileName ($fileSize)"
        
        // Setup buttons
        binding.cancelButton.setOnClickListener {
            if (isFlashing) {
                // TODO: Cancel flashing operation
                showToast("Flashing cancelled")
                resetToDeviceSelection()
            } else {
                dismiss()
            }
        }
        
        binding.flashButton.setOnClickListener {
            selectedDevice?.let { device ->
                requestUsbPermissionAndFlash(device)
            }
        }
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = SimpleUsbDeviceAdapter { deviceInfo ->
            selectedDeviceInfo = deviceInfo
            // Find the actual USB device
            selectedDevice = usbManager.deviceList.values.find { 
                it.deviceId.toString() == deviceInfo.deviceId 
            }
            binding.flashButton.isEnabled = selectedDevice != null
            Log.d("DiskImageFlash", "Selected device: ${deviceInfo.name}")
        }
        
        binding.usbDevicesList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }
    
    private fun refreshUsbDevices() {
        val devices = getUsbMassStorageDevices()
        Log.d("DiskImageFlash", "Found ${devices.size} USB devices")
        
        if (devices.isEmpty()) {
            binding.deviceSelectionLayout.visibility = View.VISIBLE
            binding.noDevicesText.visibility = View.VISIBLE
            binding.usbDevicesList.visibility = View.GONE
            binding.flashButton.isEnabled = false
        } else {
            binding.deviceSelectionLayout.visibility = View.VISIBLE
            binding.noDevicesText.visibility = View.GONE
            binding.usbDevicesList.visibility = View.VISIBLE
            deviceAdapter.submitList(devices)
        }
    }
    
    private fun getUsbMassStorageDevices(): List<UsbDeviceInfo> {
        val devices = mutableListOf<UsbDeviceInfo>()
        
        for (device in usbManager.deviceList.values) {
            if (UsbMassStorageHelper.isMassStorageDevice(device)) {
                val name = device.productName ?: "USB Device"
                val manufacturer = device.manufacturerName ?: "Unknown"
                val vidPid = String.format("%04X:%04X", device.vendorId, device.productId)
                val info = "$manufacturer • $vidPid"
                
                devices.add(UsbDeviceInfo(
                    name = name,
                    info = info,
                    deviceId = device.deviceId.toString()
                ))
            }
        }
        
        return devices
    }
    
    private fun requestUsbPermissionAndFlash(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            Log.d("DiskImageFlash", "Already have permission for device: ${device.productName}")
            startActualFlashing(device)
        } else {
            Log.d("DiskImageFlash", "Requesting permission for device: ${device.productName}")
            val permissionIntent = PendingIntent.getBroadcast(
                requireContext(), 
                0, 
                Intent(ACTION_USB_PERMISSION), 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }
    
    private fun startActualFlashing(device: UsbDevice) {
        isFlashing = true
        
        // Hide device selection and show progress
        binding.deviceSelectionLayout.visibility = View.GONE
        binding.progressLayout.visibility = View.VISIBLE
        
        // Update button states
        binding.flashButton.isEnabled = false
        binding.cancelButton.text = "Cancel"
        
        // Update progress
        binding.progressStatus.text = "Initializing USB device..."
        binding.progressBar.progress = 0
        binding.progressPercentage.text = "0%"
        binding.transferRate.text = "0 MB/s"
        
        // Start actual flashing in a coroutine
        lifecycleScope.launch {
            try {
                // Open the image file using the file path
                val imageInputStream = try {
                    args.file.path.newInputStream()
                } catch (e: Exception) {
                    Log.e("DiskImageFlash", "Could not open image file", e)
                    requireActivity().runOnUiThread {
                        showToast("Could not open image file: ${e.message}")
                        resetToDeviceSelection()
                    }
                    return@launch
                }
                
                val imageSize = args.file.attributes.size()
                
                val result = UsbMassStorageHelper.flashImage(
                    requireContext(),
                    device,
                    imageInputStream,
                    imageSize
                ) { bytesWritten, totalBytes, speed ->
                    // Update progress on main thread
                    requireActivity().runOnUiThread {
                        val progress = ((bytesWritten * 100) / totalBytes).toInt()
                        binding.progressBar.progress = progress
                        binding.progressPercentage.text = "$progress%"
                        
                        val speedMBs = speed / (1024 * 1024)
                        binding.transferRate.text = if (speed > 0) {
                            "${String.format("%.1f", speedMBs)} MB/s"
                        } else {
                            "Complete"
                        }
                        
                        when {
                            progress < 5 -> binding.progressStatus.text = "Initializing USB device..."
                            progress < 95 -> binding.progressStatus.text = "Writing image to USB drive..."
                            progress >= 95 -> binding.progressStatus.text = "Finalizing..."
                        }
                    }
                }
                
                result.fold(
                    onSuccess = {
                        requireActivity().runOnUiThread {
                            binding.progressStatus.text = "Flash completed successfully!"
                            binding.cancelButton.text = "Close"
                            isFlashing = false
                            showToast("Image flashed successfully!")
                        }
                    },
                    onFailure = { error ->
                        requireActivity().runOnUiThread {
                            Log.e("DiskImageFlash", "Flash failed", error)
                            showToast("Flash failed: ${error.message}")
                            resetToDeviceSelection()
                        }
                    }
                )
                
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    Log.e("DiskImageFlash", "Flash failed", e)
                    showToast("Flash failed: ${e.message}")
                    resetToDeviceSelection()
                }
            }
        }
    }
    
    private fun resetToDeviceSelection() {
        isFlashing = false
        binding.deviceSelectionLayout.visibility = View.VISIBLE
        binding.progressLayout.visibility = View.GONE
        binding.flashButton.isEnabled = selectedDevice != null
        binding.cancelButton.text = "Cancel"
    }
    
    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        
        // Use RECEIVER_EXPORTED for USB system broadcasts (like EtchDroid does)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            requireContext().registerReceiver(usbReceiver, filter)
        }
    }
    
    private fun unregisterUsbReceiver() {
        try {
            requireContext().unregisterReceiver(usbReceiver)
        } catch (e: Exception) {
            // Receiver might not be registered
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
        val pre = "KMGTPE"[exp - 1]
        val format = DecimalFormat("#.#")
        return "${format.format(bytes / Math.pow(1024.0, exp.toDouble()))} ${pre}B"
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        unregisterUsbReceiver()
        _binding = null
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.dismal.files.USB_PERMISSION"
        
        fun show(file: FileItem, fragment: Fragment) {
            DiskImageFlashDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }

    @Parcelize
    class Args(val file: FileItem) : ParcelableArgs

    interface Listener {
        fun openFileWith(file: FileItem)
    }
}