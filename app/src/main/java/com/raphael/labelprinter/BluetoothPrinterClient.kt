package com.raphael.labelprinter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class PrinterNotFoundException : IOException("XP-420B belum dipasangkan")

data class ConnectedPrinter(
    val name: String,
    val address: String,
)

class BluetoothPrinterClient(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val adapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    @Volatile
    private var socket: BluetoothSocket? = null
    private val stateLock = Any()
    private var closeGeneration = 0L
    private val connectionAttempt = CancellableConnectionAttempt<BluetoothSocket> { candidate ->
        candidate.close()
    }

    val savedAddress: String?
        get() = preferences.getString(KEY_ADDRESS, null)

    val savedName: String?
        get() = preferences.getString(KEY_NAME, null)

    fun isConnected(): Boolean = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun bestPairedDevice(): BluetoothDevice? {
        val bluetoothAdapter = adapter ?: return null
        val devices = bluetoothAdapter.bondedDevices.orEmpty()
        val candidates = devices.map { PrinterCandidate(it.name, it.address) }
        val selected = PrinterCandidateSelector.select(candidates, savedAddress) ?: return null
        return devices.firstOrNull { it.address.equals(selected.address, ignoreCase = true) }
    }

    @SuppressLint("MissingPermission")
    fun connectBest(): ConnectedPrinter {
        val device = bestPairedDevice() ?: throw PrinterNotFoundException()
        return connect(device)
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice): ConnectedPrinter {
        synchronized(stateLock) {
            socket
        }?.let { current ->
            if (current.isConnected && current.remoteDevice.address == device.address) {
                return ConnectedPrinter(device.name ?: "XP-420B", device.address)
            }
        }

        val (generation, previousSocket) = synchronized(stateLock) {
            val previous = socket
            socket = null
            closeGeneration to previous
        }
        runCatching { previousSocket?.close() }

        val connectedSocket = connectSocket(device)
        val accepted = synchronized(stateLock) {
            if (closeGeneration == generation) {
                socket = connectedSocket
                true
            } else {
                false
            }
        }
        if (!accepted) {
            runCatching { connectedSocket.close() }
            throw ConnectionCancelledException()
        }
        val name = device.name ?: "XP-420B"
        preferences.edit()
            .putString(KEY_ADDRESS, device.address)
            .putString(KEY_NAME, name)
            .apply()
        return ConnectedPrinter(name, device.address)
    }

    fun outputStream(): OutputStream =
        socket?.takeIf { it.isConnected }?.outputStream
            ?: throw IOException("Printer belum terhubung")

    @SuppressLint("MissingPermission")
    private fun connectSocket(device: BluetoothDevice): BluetoothSocket {
        return connectionAttempt.connect(
            create = { device.createRfcommSocketToServiceRecord(SPP_UUID) },
            connect = { candidate ->
                adapter?.cancelDiscovery()
                candidate.connect()
            },
        )
    }

    override fun close() {
        val current = synchronized(stateLock) {
            closeGeneration += 1
            socket.also { socket = null }
        }
        connectionAttempt.cancel()
        runCatching { current?.close() }
    }

    companion object {
        private const val PREFERENCES_NAME = "printer_preferences"
        private const val KEY_ADDRESS = "printer_address"
        private const val KEY_NAME = "printer_name"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
