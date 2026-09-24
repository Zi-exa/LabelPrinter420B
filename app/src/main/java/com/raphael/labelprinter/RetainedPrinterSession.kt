package com.raphael.labelprinter

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class RetainedPrinterSession(context: Context) {
    val printerClient = BluetoothPrinterClient(context.applicationContext)
    val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val discoveredDevices = linkedMapOf<String, BluetoothDevice>()
    val printContinuation = PrintContinuation()

    var selectedPdf: Uri? = null
    var pdfDisplayName: String? = null
    var pdfPageCount: Int? = null
    var busy = false
    var pendingPermissionPrintAfter: Boolean? = null
    var pendingEnablePrint = false
    var pendingPrintAfterConnection = false
    var pendingBondAddress: String? = null
    var printerDiscoveryInProgress = false
    var failedAutomaticAddress: String? = null
    var printModeIsAll: Boolean = true
    var pageRangeText: String = ""

    private val mainHandler = Handler(Looper.getMainLooper())
    private val uiHandoff = UiHandoff<MainActivity>()

    fun attach(activity: MainActivity) {
        uiHandoff.attach(activity)
    }

    fun detach(activity: MainActivity) {
        uiHandoff.detach(activity)
    }

    fun postUi(action: (MainActivity) -> Unit) {
        mainHandler.post { uiHandoff.deliver(action) }
    }

    fun close() {
        printerClient.close()
        ioExecutor.shutdownNow()
    }
}
