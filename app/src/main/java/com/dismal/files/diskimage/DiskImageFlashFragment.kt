/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 */

package com.dismal.files.diskimage

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java8.nio.file.Path
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import com.dismal.files.R
import com.dismal.files.file.MimeType
import com.dismal.files.util.ParcelableArgs
import com.dismal.files.util.ParcelableParceler
import com.dismal.files.util.args
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

class DiskImageFlashFragment : Fragment() {
    private val args by args<Args>()
    
    private val viewModel by viewModels<DiskImageFlashViewModel>()
    
    private lateinit var usbManager: UsbManager
    private lateinit var usbDeviceAdapter: UsbDeviceAdapter
    
    // UI components
    private lateinit var diskImageName: TextView
    private lateinit var diskImageSize: TextView
    private lateinit var usbDevicesRecyclerView: RecyclerView
    private lateinit var noUsbDevicesText: TextView
    private lateinit var progressCard: MaterialCardView
    private lateinit var progressStatus: TextView
    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var progressPercentage: TextView
    private lateinit var flashButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        
        // Initialize ViewModel with the disk image path
        viewModel.setDiskImagePath(args.path)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.disk_image_flash_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeViews(view)
        setupRecyclerView()
        observeViewModel()
        setupClickListeners()
        
        setupUsbDeviceDetection()
        scanForUsbDevices()
    }
    
    private fun initializeViews(view: View) {
        diskImageName = view.findViewById(R.id.disk_image_name)
        diskImageSize = view.findViewById(R.id.disk_image_size)
        usbDevicesRecyclerView = view.findViewById(R.id.usb_devices_recycler_view)
        noUsbDevicesText = view.findViewById(R.id.no_usb_devices_text)
        progressCard = view.findViewById(R.id.progress_card)
        progressStatus = view.findViewById(R.id.progress_status)
        progressBar = view.findViewById(R.id.progress_bar)
        progressPercentage = view.findViewById(R.id.progress_percentage)
        flashButton = view.findViewById(R.id.flash_button)
        
        // Set disk image info
        diskImageName.text = args.path.fileName.toString()
        diskImageSize.text = "Calculating size..." // TODO: Get actual file size
    }
    
    private fun setupRecyclerView() {
        usbDeviceAdapter = UsbDeviceAdapter { device ->
            viewModel.selectUsbDevice(device)
        }
        
        usbDevicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = usbDeviceAdapter
        }
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.state.collect { state ->
                updateUI(state)
            }
        }
    }
    
    private fun updateUI(state: DiskImageFlashState) {
        // Update USB devices list
        usbDeviceAdapter.submitList(state.availableUsbDevices)
        usbDeviceAdapter.setSelectedDevice(state.selectedDevice)
        
        // Show/hide no devices message
        noUsbDevicesText.isVisible = state.availableUsbDevices.isEmpty()
        usbDevicesRecyclerView.isVisible = state.availableUsbDevices.isNotEmpty()
        
        // Update flash button state
        flashButton.isEnabled = state.selectedDevice != null && !state.isFlashing
        
        // Update progress
        progressCard.isVisible = state.isFlashing || state.flashProgress > 0
        if (state.isFlashing || state.flashProgress > 0) {
            progressStatus.text = state.flashStatus
            progressBar.progress = (state.flashProgress * 100).toInt()
            progressPercentage.text = "${(state.flashProgress * 100).toInt()}%"
        }
        
        // Show error if any
        state.error?.let { error ->
            // TODO: Show error dialog or snackbar
            Log.e("DiskImageFlash", "Error: $error")
        }
    }
    
    private fun setupClickListeners() {
        flashButton.setOnClickListener {
            if (checkPermissions()) {
                viewModel.startFlashing()
            } else {
                // TODO: Request permissions
                Log.w("DiskImageFlash", "Missing permissions for USB access")
            }
        }
    }

    private fun setupUsbDeviceDetection() {
        // TODO: Register USB device attach/detach receivers
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        
        // Register receiver for USB events
        // requireContext().registerReceiver(usbReceiver, filter)
    }

    private fun scanForUsbDevices() {
        lifecycleScope.launch {
            try {
                val devices = usbManager.deviceList.values.toList()
                Log.d("DiskImageFlash", "Found ${devices.size} USB devices")
                
                // TODO: Filter for mass storage devices only
                // For now, show all USB devices
                viewModel.setAvailableUsbDevices(devices)
                
            } catch (e: Exception) {
                Log.e("DiskImageFlash", "Error scanning USB devices", e)
            }
        }
    }

    private fun checkPermissions(): Boolean {
        // Check if we have necessary permissions
        val hasStoragePermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        
        // TODO: Check USB permissions
        
        return hasStoragePermission
    }

    override fun onDestroy() {
        super.onDestroy()
        // TODO: Unregister USB receiver
    }

    @Parcelize
    class Args(
        val path: @WriteWith<ParcelableParceler> Path,
        val mimeType: MimeType
    ) : ParcelableArgs
}