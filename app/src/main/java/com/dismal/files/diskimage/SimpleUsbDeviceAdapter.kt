/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 */

package com.dismal.files.diskimage

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dismal.files.R
import com.google.android.material.radiobutton.MaterialRadioButton

data class UsbDeviceInfo(
    val name: String,
    val info: String,
    val deviceId: String
)

class SimpleUsbDeviceAdapter(
    private val onDeviceSelected: (UsbDeviceInfo) -> Unit
) : ListAdapter<UsbDeviceInfo, SimpleUsbDeviceAdapter.ViewHolder>(DIFF_CALLBACK) {

    private var selectedDeviceId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.usb_device_simple_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val device = getItem(position)
        holder.bind(device, device.deviceId == selectedDeviceId) { selectedDevice ->
            this.selectedDeviceId = selectedDevice.deviceId
            onDeviceSelected(selectedDevice)
            notifyDataSetChanged() // Update selection state
        }
    }

    fun setSelectedDevice(deviceId: String?) {
        selectedDeviceId = deviceId
        notifyDataSetChanged()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceName: TextView = itemView.findViewById(R.id.device_name)
        private val deviceInfo: TextView = itemView.findViewById(R.id.device_info)
        private val deviceRadio: MaterialRadioButton = itemView.findViewById(R.id.device_radio)

        fun bind(device: UsbDeviceInfo, isSelected: Boolean, onSelected: (UsbDeviceInfo) -> Unit) {
            deviceName.text = device.name
            deviceInfo.text = device.info
            deviceRadio.isChecked = isSelected
            
            // Set click listener on the entire item
            itemView.setOnClickListener {
                onSelected(device)
            }
        }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<UsbDeviceInfo>() {
            override fun areItemsTheSame(oldItem: UsbDeviceInfo, newItem: UsbDeviceInfo): Boolean {
                return oldItem.deviceId == newItem.deviceId
            }

            override fun areContentsTheSame(oldItem: UsbDeviceInfo, newItem: UsbDeviceInfo): Boolean {
                return oldItem == newItem
            }
        }
    }
}