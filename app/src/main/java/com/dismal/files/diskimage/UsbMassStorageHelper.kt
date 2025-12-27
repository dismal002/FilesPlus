/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 * 
 * Based on EtchDroid's USB mass storage implementation
 */

package com.dismal.files.diskimage

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import com.dismal.files.diskimage.EtchDroidUsbMassStorageDevice.Companion.isMassStorageDevice
import me.jahnen.libaums.core.usb.UsbCommunicationFactory
import me.jahnen.libaums.libusbcommunication.LibusbCommunicationCreator
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UsbMassStorageHelper {
    
    companion object {
        private const val TAG = "UsbMassStorageHelper"
        private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer
        
        init {
            // Set up libusb communication like EtchDroid does
            UsbCommunicationFactory.registerCommunication(LibusbCommunicationCreator())
            UsbCommunicationFactory.underlyingUsbCommunication = 
                UsbCommunicationFactory.UnderlyingUsbCommunication.OTHER
        }
        
        fun isMassStorageDevice(device: UsbDevice): Boolean {
            return device.isMassStorageDevice
        }
        
        suspend fun flashImage(
            context: Context,
            device: UsbDevice,
            imageInputStream: InputStream,
            imageSize: Long,
            progressCallback: (bytesWritten: Long, totalBytes: Long, speed: Float) -> Unit
        ): Result<Unit> = withContext(Dispatchers.IO) {
            try {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
                
                if (!usbManager.hasPermission(device)) {
                    return@withContext Result.failure(SecurityException("No USB permission for device"))
                }
                
                Log.d(TAG, "Starting flash process for device: ${device.productName}")
                
                // Use EtchDroid's approach to get mass storage devices
                val massStorageDevices = EtchDroidUsbMassStorageDevice.getMassStorageDevices(context)
                val targetDevice = massStorageDevices.find { it.usbDevice.deviceId == device.deviceId }
                    ?: return@withContext Result.failure(IOException("Could not find mass storage device"))
                
                targetDevice.init()
                
                // Get the first block device like EtchDroid does
                val blockDevice = targetDevice.blockDevices[0]
                    ?: return@withContext Result.failure(IOException("No block device found"))
                    
                Log.d(TAG, "Block device info: ${blockDevice.blocks} blocks, ${blockDevice.blockSize} bytes per block")
                
                val deviceSize = blockDevice.blocks * blockDevice.blockSize
                if (deviceSize < imageSize) {
                    return@withContext Result.failure(IOException("Device too small: $deviceSize < $imageSize"))
                }
                
                // Write the image
                writeImageToDevice(blockDevice, imageInputStream, imageSize, progressCallback)
                
                targetDevice.close()
                Log.d(TAG, "Flash process completed successfully")
                
                Result.success(Unit)
                
            } catch (e: Exception) {
                Log.e(TAG, "Flash process failed", e)
                Result.failure(e)
            }
        }
        
        private suspend fun writeImageToDevice(
            blockDevice: me.jahnen.libaums.core.driver.BlockDeviceDriver,
            imageInputStream: InputStream,
            imageSize: Long,
            progressCallback: (bytesWritten: Long, totalBytes: Long, speed: Float) -> Unit
        ) = withContext(Dispatchers.IO) {
            val buffer = ByteArray(BUFFER_SIZE)
            val bufferedInput = BufferedInputStream(imageInputStream, BUFFER_SIZE * 4)
            
            var totalBytesWritten = 0L
            var lastProgressTime = System.currentTimeMillis()
            var lastBytesWritten = 0L
            
            try {
                while (totalBytesWritten < imageSize) {
                    val bytesToRead = minOf(BUFFER_SIZE.toLong(), imageSize - totalBytesWritten).toInt()
                    val bytesRead = bufferedInput.read(buffer, 0, bytesToRead)
                    
                    if (bytesRead == -1) break
                    
                    // Calculate which blocks to write
                    val currentBlock = (totalBytesWritten / blockDevice.blockSize).toLong()
                    val blocksToWrite = (bytesRead + blockDevice.blockSize - 1) / blockDevice.blockSize
                    val bytesToWrite = blocksToWrite * blockDevice.blockSize
                    
                    // Pad the buffer to block boundary if necessary
                    val writeBuffer = ByteBuffer.allocate(bytesToWrite)
                    writeBuffer.put(buffer, 0, bytesRead)
                    
                    // Pad with zeros if needed
                    while (writeBuffer.hasRemaining()) {
                        writeBuffer.put(0.toByte())
                    }
                    
                    writeBuffer.rewind()
                    
                    // Write to USB device
                    blockDevice.write(currentBlock, writeBuffer)
                    
                    totalBytesWritten += bytesRead
                    
                    // Update progress
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProgressTime >= 1000) { // Update every second
                        val timeDiff = (currentTime - lastProgressTime) / 1000f
                        val bytesDiff = totalBytesWritten - lastBytesWritten
                        val speed = bytesDiff / timeDiff
                        
                        progressCallback(totalBytesWritten, imageSize, speed)
                        
                        lastProgressTime = currentTime
                        lastBytesWritten = totalBytesWritten
                    }
                }
                
                // Final progress update
                progressCallback(totalBytesWritten, imageSize, 0f)
                
            } finally {
                bufferedInput.close()
            }
        }
    }
}