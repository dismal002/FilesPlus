/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 * 
 * Based on EtchDroid's EtchDroidUsbMassStorageDevice implementation
 */

package com.dismal.files.diskimage

import android.content.Context
import android.hardware.usb.*
import android.util.Log
import me.jahnen.libaums.core.driver.BlockDeviceDriver
import me.jahnen.libaums.core.driver.BlockDeviceDriverFactory
import me.jahnen.libaums.core.driver.scsi.commands.sense.MediaNotInserted
import me.jahnen.libaums.core.usb.UsbCommunication
import me.jahnen.libaums.core.usb.UsbCommunicationFactory
import java.io.IOException

class EtchDroidUsbMassStorageDevice(
    private val usbManager: UsbManager,
    val usbDevice: UsbDevice,
    private val usbInterface: UsbInterface,
    private val inEndpoint: UsbEndpoint,
    private val outEndpoint: UsbEndpoint,
) {
    private lateinit var usbCommunication: UsbCommunication
    lateinit var blockDevices: Map<Int, BlockDeviceDriver>

    private var inited = false

    @Throws(IOException::class)
    fun init() {
        if (inited) {
            throw IllegalStateException("Mass storage device already initialized")
        }

        if (usbManager.hasPermission(usbDevice)) {
            setupDevice()
        } else {
            throw SecurityException("No USB permission for device")
        }

        inited = true
    }

    @Throws(IOException::class)
    private fun setupDevice() {
        try {
            usbCommunication = UsbCommunicationFactory
                .createUsbCommunication(
                    usbManager, usbDevice, usbInterface, outEndpoint, inEndpoint
                )
        } catch (cause: Exception) {
            throw IOException("Initialization failed", cause)
        }

        val maxLun = ByteArray(1)
        usbCommunication.controlTransfer(161, 254, 0, usbInterface.id, maxLun, 1)

        Log.d(TAG, "Max LUN " + maxLun[0].toInt())

        val mutableBlockDevices = mutableMapOf<Int, BlockDeviceDriver>()

        for (lun in 0..maxLun[0]) {
            val blockDevice =
                BlockDeviceDriverFactory.createBlockDevice(usbCommunication, lun = lun.toByte())
            try {
                blockDevice.init()
                mutableBlockDevices[lun] = blockDevice
            } catch (e: MediaNotInserted) {
                // This LUN does not have media inserted. Ignore it.
                continue
            }
        }

        blockDevices = mutableBlockDevices.toMap()
    }

    fun close() {
        if (::usbCommunication.isInitialized) {
            usbCommunication.close()
        }
        inited = false
    }

    companion object {
        private const val TAG = "EtchDroidUsbMassStorageDevice"

        /**
         * subclass 6 means that the usb mass storage device implements the SCSI
         * transparent command set
         */
        private const val INTERFACE_SUBCLASS = 6

        /**
         * protocol 80 means the communication happens only via bulk transfers
         */
        private const val INTERFACE_PROTOCOL = 80

        val UsbDevice.isMassStorageDevice: Boolean
            get() = massStorageDevices.isNotEmpty()

        val UsbDevice.massStorageDevices: List<UsbMassStorageDeviceDescriptor>
            get() = (0 until interfaceCount)
                .map { i ->
                    val usbInterface = getInterface(i)
                    usbInterface
                }
                .filter { usbInterface ->
                    // we currently only support SCSI transparent command set with
                    // bulk transfers only!
                    !(usbInterface.interfaceClass != UsbConstants.USB_CLASS_MASS_STORAGE
                            || usbInterface.interfaceSubclass != INTERFACE_SUBCLASS
                            || usbInterface.interfaceProtocol != INTERFACE_PROTOCOL)
                }
                .map { usbInterface ->
                    // Every mass storage device has exactly two endpoints
                    // One IN and one OUT endpoint
                    val endpointCount = usbInterface.endpointCount
                    if (endpointCount != 2) {
                        Log.w(TAG, "interface endpoint count != 2")
                    }

                    var outEndpoint: UsbEndpoint? = null
                    var inEndpoint: UsbEndpoint? = null

                    for (j in 0 until endpointCount) {
                        val endpoint = usbInterface.getEndpoint(j)
                        if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (endpoint.direction == UsbConstants.USB_DIR_OUT) {
                                outEndpoint = endpoint
                            } else {
                                inEndpoint = endpoint
                            }
                        }
                    }

                    if (outEndpoint == null || inEndpoint == null) {
                        Log.e(TAG, "Not all needed endpoints found!")
                        return@map null
                    }

                    return@map UsbMassStorageDeviceDescriptor(
                        this, usbInterface, inEndpoint, outEndpoint
                    )
                }
                .filterNotNull()

        fun UsbDevice.getMassStorageDevices(usbManager: UsbManager) =
            massStorageDevices.map { it.buildDevice(usbManager) }

        fun getMassStorageDevices(context: Context): Array<EtchDroidUsbMassStorageDevice> {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

            return usbManager.deviceList
                .map {
                    val device = it.value
                    device.getMassStorageDevices(usbManager)
                }
                .flatten()
                .toTypedArray()
        }
    }
}

data class UsbMassStorageDeviceDescriptor(
    private val usbDevice: UsbDevice,
    val usbInterface: UsbInterface,
    val inEndpoint: UsbEndpoint,
    val outEndpoint: UsbEndpoint,
) {
    fun buildDevice(usbManager: UsbManager): EtchDroidUsbMassStorageDevice {
        return EtchDroidUsbMassStorageDevice(
            usbManager,
            usbDevice,
            usbInterface,
            inEndpoint,
            outEndpoint
        )
    }
}