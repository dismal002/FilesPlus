/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 */

package com.dismal.files.diskimage

import android.hardware.usb.UsbDevice
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dismal.files.R
import com.google.android.material.card.MaterialCardView

class UsbDeviceAdapter(
    private val onDeviceSelected: (UsbDevice) -> Unit
) : ListAdapter<UsbDevice, UsbDeviceAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var selectedDevice: UsbDevice? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.usb_device_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device, device == selectedDevice) { selectedDevice ->
            this.selectedDevice = selectedDevice
            onDeviceSelected(selectedDevice)
            notifyDataSetChanged() // Update selection state
        }
    }

    fun setSelectedDevice(device: UsbDevice?) {
        selectedDevice = device
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val deviceName: TextView = itemView.findViewById(R.id.device_name)
        private val deviceInfo: TextView = itemView.findViewById(R.id.device_info)

        fun bind(device: UsbDevice, isSelected: Boolean, onSelected: (UsbDevice) -> Unit) {
            // Set device name (use product name or fallback to vendor/product ID)
            deviceName.text = device.productName ?: "USB Device (${device.vendorId}:${device.productId})"
            
            // Set device info (manufacturer and basic info)
            val manufacturer = device.manufacturerName ?: "Unknown"
            deviceInfo.text = "$manufacturer • USB Device"
            
            // Set selection state
            cardView.isChecked = isSelected
            
            // Set click listener
            cardView.setOnClickListener {
                onSelected(device)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<UsbDevice>() {
            override fun areItemsTheSame(oldItem: UsbDevice, newItem: UsbDevice): Boolean {
                return oldItem.deviceId == newItem.deviceId
            }

            override fun areContentsTheSame(oldItem: UsbDevice, newItem: UsbDevice): Boolean {
                return oldItem.deviceId == newItem.deviceId &&
                        oldItem.productName == newItem.productName &&
                        oldItem.manufacturerName == newItem.manufacturerName
            }
        }
    }
}