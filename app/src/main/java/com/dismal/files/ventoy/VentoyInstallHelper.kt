/*
 * Copyright (c) 2024 Dismal <dismal@dismal.dev>
 * All Rights Reserved.
 */

package com.dismal.files.ventoy

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.dismal.files.diskimage.EtchDroidUsbMassStorageDevice
import com.dismal.files.diskimage.UsbMassStorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.ByteBuffer
import java.io.IOException

class VentoyInstallHelper {
    companion object {
        private const val TAG = "VentoyInstallHelper"
        
        // Ventoy constants from ventoy_lib.sh
        private const val VENTOY_SECTOR_SIZE = 512
        private const val VENTOY_SECTOR_NUM = 65536 // 32MB
        
        suspend fun installVentoy(
            context: Context,
            device: UsbDevice,
            bootImg: ByteArray,
            coreImg: ByteArray,
            ventoyDiskImg: InputStream,
            ventoyDiskImgSize: Long,
            progressCallback: (status: String, progress: Float) -> Unit
        ): Result<Unit> = withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starting Ventoy installation on ${device.productName}")
                
                val massStorageDevices = EtchDroidUsbMassStorageDevice.getMassStorageDevices(context)
                val targetDevice = massStorageDevices.find { it.usbDevice.deviceId == device.deviceId }
                    ?: return@withContext Result.failure(IOException("Could not find mass storage device"))
                
                targetDevice.init()
                val blockDevice = targetDevice.blockDevices[0]
                    ?: return@withContext Result.failure(IOException("No block device found"))
                
                val diskSectorNum = blockDevice.blocks
                if (diskSectorNum <= VENTOY_SECTOR_NUM + 2048) {
                    return@withContext Result.failure(IOException("Disk too small"))
                }
                
                // 1. Prepare Partition Table (MBR)
                progressCallback("Preparing partition table...", 0.1f)
                val mbr = ByteBuffer.allocate(512)
                
                // Copy bootImg (first 446 bytes)
                mbr.put(bootImg, 0, 446)
                
                // Partition 1 (ExFAT)
                // Offset 446: status=0x80 (bootable), type=0x07 (Install script says 0x07 for MBR)
                // Start sector: 2048
                // End sector: diskSectorNum - VENTOY_SECTOR_NUM - 1
                val part1Start = 2048L
                val part1End = diskSectorNum - VENTOY_SECTOR_NUM - 1
                val part1Size = part1End - part1Start + 1
                
                writePartitionEntry(mbr, 446, 0x80.toByte(), 0x07.toByte(), part1Start, part1Size)
                
                // Partition 2 (VTOYEFI)
                // Offset 462: status=0x00, type=0xEF (EFI)
                // Start sector: part1End + 1
                // Size: VENTOY_SECTOR_NUM
                val part2Start = part1End + 1
                writePartitionEntry(mbr, 462, 0x00.toByte(), 0xEF.toByte(), part2Start, VENTOY_SECTOR_NUM.toLong())
                
                // Signature
                mbr.put(510, 0x55.toByte())
                mbr.put(511, 0xAA.toByte())
                
                mbr.rewind()
                blockDevice.write(0, mbr)
                
                // 2. Write Core Image
                progressCallback("Writing core image...", 0.2f)
                val coreBuffer = ByteBuffer.allocate(((coreImg.size + 511) / 512) * 512)
                coreBuffer.put(coreImg)
                coreBuffer.rewind()
                blockDevice.write(1, coreBuffer)
                
                // 3. Write Ventoy Disk Image to Partition 2
                progressCallback("Writing Ventoy system...", 0.3f)
                writeInputStreamToDevice(blockDevice, ventoyDiskImg, ventoyDiskImgSize, part2Start) { p ->
                    progressCallback("Writing Ventoy system...", 0.3f + (p * 0.6f))
                }
                
                // 4. Sync
                progressCallback("Finalizing...", 0.95f)
                
                targetDevice.close()
                Log.d(TAG, "Ventoy installation completed successfully")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Ventoy installation failed", e)
                Result.failure(e)
            }
        }
        
        private fun writePartitionEntry(buffer: ByteBuffer, offset: Int, status: Byte, type: Byte, start: Long, size: Long) {
            buffer.position(offset)
            buffer.put(status)
            buffer.put(0x00.toByte()) // CHS Start
            buffer.put(0x00.toByte())
            buffer.put(0x00.toByte())
            buffer.put(type)
            buffer.put(0x00.toByte()) // CHS End
            buffer.put(0x00.toByte())
            buffer.put(0x00.toByte())
            
            // LBA Start
            buffer.putInt(Integer.reverseBytes(start.toInt()))
            // LBA Size
            buffer.putInt(Integer.reverseBytes(size.toInt()))
        }
        
        private suspend fun writeInputStreamToDevice(
            blockDevice: me.jahnen.libaums.core.driver.BlockDeviceDriver,
            inputStream: InputStream,
            size: Long,
            startSector: Long,
            progressCallback: (progress: Float) -> Unit
        ) = withContext(Dispatchers.IO) {
            val buffer = ByteArray(1024 * 1024) // 1MB buffer
            var totalWritten = 0L
            var currentSector = startSector
            
            inputStream.use { input ->
                while (totalWritten < size) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    
                    val blocksToWrite = (read + 511) / 512
                    val writeBuffer = ByteBuffer.allocate(blocksToWrite * 512)
                    writeBuffer.put(buffer, 0, read)
                    while (writeBuffer.hasRemaining()) writeBuffer.put(0.toByte())
                    writeBuffer.rewind()
                    
                    blockDevice.write(currentSector, writeBuffer)
                    
                    totalWritten += read
                    currentSector += blocksToWrite
                    progressCallback(totalWritten.toFloat() / size)
                }
            }
        }
    }
}
