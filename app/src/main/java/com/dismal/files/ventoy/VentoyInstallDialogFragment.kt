/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 */

package com.dismal.files.ventoy

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
import com.dismal.files.databinding.VentoyInstallDialogBinding
import com.dismal.files.diskimage.SimpleUsbDeviceAdapter
import com.dismal.files.diskimage.UsbDeviceInfo
import com.dismal.files.diskimage.UsbMassStorageHelper
import com.dismal.files.file.FileItem
import com.dismal.files.filelist.name
import com.dismal.files.provider.common.newInputStream
import com.dismal.files.util.ParcelableArgs
import com.dismal.files.util.args
import com.dismal.files.util.putArgs
import com.dismal.files.util.show
import com.dismal.files.util.showToast

class VentoyInstallDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()
    private var _binding: VentoyInstallDialogBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var usbManager: UsbManager
    private lateinit var deviceAdapter: SimpleUsbDeviceAdapter
    private var selectedDevice: UsbDevice? = null
    private var isInstalling = false
    
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED,
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    refreshUsbDevices()
                }
                ACTION_USB_PERMISSION -> {
                    selectedDevice?.let { device ->
                        if (usbManager.hasPermission(device)) {
                            startActualInstallation(device)
                        } else {
                            showToast(R.string.storage_permission_permanently_denied_message)
                            resetToDeviceSelection()
                        }
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = VentoyInstallDialogBinding.inflate(LayoutInflater.from(requireContext()))
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
        binding.fileInfo.text = args.file.path.name
        
        binding.cancelButton.setOnClickListener {
            if (isInstalling) {
                // TODO: Cancel installation
            } else {
                dismiss()
            }
        }
        
        binding.installButton.setOnClickListener {
            selectedDevice?.let { device ->
                requestUsbPermissionAndInstall(device)
            }
        }
    }
    
    private fun setupRecyclerView() {
        deviceAdapter = SimpleUsbDeviceAdapter { deviceInfo ->
            selectedDevice = usbManager.deviceList.values.find { 
                it.deviceId.toString() == deviceInfo.deviceId 
            }
            binding.installButton.isEnabled = selectedDevice != null
        }
        
        binding.usbDevicesList.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }
    
    private fun refreshUsbDevices() {
        val devices = getUsbMassStorageDevices()
        if (devices.isEmpty()) {
            binding.deviceSelectionLayout.visibility = View.VISIBLE
            binding.noDevicesText.visibility = View.VISIBLE
            binding.usbDevicesList.visibility = View.GONE
            binding.installButton.isEnabled = false
        } else {
            binding.deviceSelectionLayout.visibility = View.VISIBLE
            binding.noDevicesText.visibility = View.GONE
            binding.usbDevicesList.visibility = View.VISIBLE
            deviceAdapter.submitList(devices)
        }
    }
    
    private fun getUsbMassStorageDevices(): List<UsbDeviceInfo> {
        return usbManager.deviceList.values
            .filter { UsbMassStorageHelper.isMassStorageDevice(it) }
            .map { device ->
                UsbDeviceInfo(
                    name = device.productName ?: "USB Device",
                    info = "${device.manufacturerName ?: "Unknown"} • ${String.format("%04X:%04X", device.vendorId, device.productId)}",
                    deviceId = device.deviceId.toString()
                )
            }
    }
    
    private fun requestUsbPermissionAndInstall(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            startActualInstallation(device)
        } else {
            val permissionIntent = PendingIntent.getBroadcast(
                requireContext(), 0, Intent(ACTION_USB_PERMISSION), 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }
    
    private fun startActualInstallation(device: UsbDevice) {
        isInstalling = true
        binding.deviceSelectionLayout.visibility = View.GONE
        binding.progressLayout.visibility = View.VISIBLE
        binding.installButton.isEnabled = false
        binding.cancelButton.text = "Cancel"
        
        lifecycleScope.launch {
            try {
                val packageHelper = VentoyPackageHelper(requireContext())
                val packageStream = args.file.path.newInputStream()
                
                packageHelper.extractImages(packageStream) { bootImg, coreImg, ventoyDiskImg, ventoyDiskImgSize ->
                    lifecycleScope.launch {
                        val result = VentoyInstallHelper.installVentoy(
                            requireContext(), device, bootImg, coreImg, ventoyDiskImg, ventoyDiskImgSize
                        ) { status, progress ->
                            val statusRes = when (status) {
                                "Preparing partition table..." -> R.string.ventoy_install_extracting // Or add new string
                                "Writing core image..." -> R.string.ventoy_install_writing_core
                                "Writing Ventoy system..." -> R.string.ventoy_install_writing_system
                                "Finalizing..." -> R.string.ventoy_install_finalizing
                                else -> R.string.ventoy_install_extracting
                            }
                            requireActivity().runOnUiThread {
                                binding.progressStatus.setText(statusRes)
                                binding.progressBar.progress = (progress * 100).toInt()
                                binding.progressPercentage.text = "${(progress * 100).toInt()}%"
                            }
                        }
                        
                        requireActivity().runOnUiThread {
                            result.fold(
                                onSuccess = {
                                    binding.progressStatus.setText(R.string.ventoy_install_complete)
                                    binding.cancelButton.setText(R.string.close)
                                    isInstalling = false
                                    showToast(R.string.ventoy_install_success)
                                },
                                onFailure = { error ->
                                    showToast(getString(R.string.ventoy_install_failed, error.message))
                                    resetToDeviceSelection()
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    showToast("Error: ${e.message}")
                    resetToDeviceSelection()
                }
            }
        }
    }
    
    private fun resetToDeviceSelection() {
        isInstalling = false
        binding.deviceSelectionLayout.visibility = View.VISIBLE
        binding.progressLayout.visibility = View.GONE
        binding.installButton.isEnabled = selectedDevice != null
        binding.cancelButton.text = "Cancel"
    }
    
    private fun registerUsbReceiver() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_USB_PERMISSION)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            requireContext().registerReceiver(usbReceiver, filter)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try { requireContext().unregisterReceiver(usbReceiver) } catch (e: Exception) {}
        _binding = null
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.dismal.files.VENTOY_USB_PERMISSION"
        fun show(file: FileItem, fragment: Fragment) {
            VentoyInstallDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }

    @Parcelize
    class Args(val file: FileItem) : ParcelableArgs

    interface Listener {
        fun openFileWith(file: FileItem)
    }
}
