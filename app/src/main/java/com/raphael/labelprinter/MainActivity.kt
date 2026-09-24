package com.raphael.labelprinter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var printerStatus: TextView
    private lateinit var pdfStatus: TextView
    private lateinit var activityStatus: TextView
    private lateinit var selectPdfButton: Button
    private lateinit var connectButton: Button
    private lateinit var printButton: Button
    private lateinit var progressBar: ProgressBar

    private lateinit var session: RetainedPrinterSession
    private val printerClient: BluetoothPrinterClient
        get() = session.printerClient
    private val ioExecutor
        get() = session.ioExecutor
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var selectedPdf: Uri?
        get() = session.selectedPdf
        set(value) { session.selectedPdf = value }
    private val busy: Boolean
        get() = session.busy
    private var receiverRegistered = false
    private val discoveredDevices: MutableMap<String, BluetoothDevice>
        get() = session.discoveredDevices
    private var pendingPermissionPrintAfter: Boolean?
        get() = session.pendingPermissionPrintAfter
        set(value) { session.pendingPermissionPrintAfter = value }
    private var pendingEnablePrint: Boolean
        get() = session.pendingEnablePrint
        set(value) { session.pendingEnablePrint = value }
    private var pendingPrintAfterConnection: Boolean
        get() = session.pendingPrintAfterConnection
        set(value) { session.pendingPrintAfterConnection = value }
    private var pendingBondAddress: String?
        get() = session.pendingBondAddress
        set(value) { session.pendingBondAddress = value }
    private var printerDiscoveryInProgress: Boolean
        get() = session.printerDiscoveryInProgress
        set(value) { session.printerDiscoveryInProgress = value }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.bluetoothDeviceExtra()
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    if (device != null) discoveredDevices[device.address] = device
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (printerDiscoveryInProgress) finishPrinterDiscovery()
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    if (device == null || device.address != pendingBondAddress) return
                    when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                        BluetoothDevice.BOND_BONDED -> {
                            pendingBondAddress = null
                            connectSpecificDevice(device, pendingPrintAfterConnection)
                        }
                        BluetoothDevice.BOND_NONE -> {
                            val previous = intent.getIntExtra(
                                BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE,
                                BluetoothDevice.ERROR,
                            )
                            if (previous == BluetoothDevice.BOND_BONDING) {
                                pendingBondAddress = null
                                showFailure(R.string.connection_failed)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = (lastNonConfigurationInstance as? RetainedPrinterSession)
            ?: RetainedPrinterSession(applicationContext)
        setContentView(R.layout.activity_main)

        printerStatus = findViewById(R.id.printerStatus)
        pdfStatus = findViewById(R.id.pdfStatus)
        activityStatus = findViewById(R.id.activityStatus)
        selectPdfButton = findViewById(R.id.selectPdfButton)
        connectButton = findViewById(R.id.connectButton)
        printButton = findViewById(R.id.printButton)
        progressBar = findViewById(R.id.progressBar)

        bluetoothAdapter =
            (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        registerBluetoothReceiver()

        selectPdfButton.setOnClickListener { choosePdf() }
        connectButton.setOnClickListener { requestConnection(printAfter = false) }
        printButton.setOnClickListener {
            if (selectedPdf == null) {
                Toast.makeText(this, R.string.select_pdf_first, Toast.LENGTH_SHORT).show()
                session.printContinuation.waitForPdf()
                choosePdf()
            } else {
                requestConnection(printAfter = true)
            }
        }

        session.attach(this)
        if (selectedPdf == null) restoreSelectedPdf() else renderPdfStatus()
        consumeIncomingPdf(intent)
        updatePrinterStatus()
        setBusy(busy)
        resumePendingBluetoothOperation()
        if (!busy) maybeAutoConnect()
    }

    override fun onRetainNonConfigurationInstance(): Any = session

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIncomingPdf(intent)
    }

    @Deprecated("Uses the platform picker for broad Android compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_PICK_PDF -> {
                val uri = data?.data
                if (resultCode == RESULT_OK && uri != null) {
                    rememberPdf(uri, data.flags)
                } else {
                    session.printContinuation.cancel()
                }
            }
            REQUEST_ENABLE_BLUETOOTH -> {
                val printAfter = pendingEnablePrint
                pendingEnablePrint = false
                if (resultCode == RESULT_OK || bluetoothAdapter?.isEnabled == true) {
                    requestConnection(printAfter)
                } else {
                    showFailure(R.string.bluetooth_disabled)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_BLUETOOTH_PERMISSIONS) return
        val printAfter = pendingPermissionPrintAfter
        pendingPermissionPrintAfter = null
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            if (printAfter != null) requestConnection(printAfter)
        } else {
            showFailure(R.string.permission_denied)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        if (receiverRegistered) unregisterReceiver(bluetoothReceiver)
        session.detach(this)
        if (!isChangingConfigurations) {
            printerDiscoveryInProgress = false
            bluetoothAdapter?.cancelDiscovery()
            session.close()
        }
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun choosePdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/pdf"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK_PDF)
    }

    private fun consumeIncomingPdf(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.streamUriExtra() ?: intent.clipData?.getItemAt(0)?.uri
            else -> null
        }
        if (uri != null && uri != selectedPdf) rememberPdf(uri, intent.flags)
    }

    private fun rememberPdf(uri: Uri, grantFlags: Int) {
        val previousPdf = selectedPdf
        selectedPdf = uri
        inspectPdf(uri, grantFlags, previousPdf, restoring = false)
        setBusy(busy)
    }

    private fun restoreSelectedPdf() {
        val stored = storedPdfUri() ?: return
        val uri = Uri.parse(stored)
        if (!hasPersistedReadGrant(uri)) {
            storePdfUri(null)
            return
        }
        selectedPdf = uri
        inspectPdf(uri, grantFlags = null, previousPdf = null, restoring = true)
    }

    private fun inspectPdf(
        uri: Uri,
        grantFlags: Int?,
        previousPdf: Uri?,
        restoring: Boolean,
    ) {
        activityStatus.setText(R.string.status_reading_pdf)
        ioExecutor.execute {
            runCatching {
                resolveDisplayName(uri) to PdfLabelRenderer.pageCount(this, uri)
            }.onSuccess { (name, pages) ->
                session.postUi { activity ->
                    activity.completePdfInspection(
                        uri = uri,
                        name = name,
                        pages = pages,
                        grantFlags = grantFlags,
                        restoring = restoring,
                    )
                }
            }.onFailure {
                session.postUi { activity ->
                    activity.failPdfInspection(uri, previousPdf, restoring)
                }
            }
        }
    }

    private fun completePdfInspection(
        uri: Uri,
        name: String,
        pages: Int,
        grantFlags: Int?,
        restoring: Boolean,
    ) {
        if (selectedPdf != uri) return
        if (!restoring) {
            val persisted = hasPersistedReadGrant(uri) ||
                (grantFlags != null && takePersistedReadGrant(uri, grantFlags))
            applyPdfGrantUpdate(
                PdfGrantPolicy.acceptSelection(
                    previousPersistedUri = storedPdfUri(),
                    newUri = uri.toString(),
                    newUriIsPersisted = persisted,
                ),
            )
        }
        session.pdfDisplayName = name
        session.pdfPageCount = pages
        renderPdfStatus()
        activityStatus.setText(R.string.status_ready)
        setBusy(busy)
        if (!restoring && session.printContinuation.consumeAfterPdfAccepted()) {
            requestConnection(printAfter = true)
        }
    }

    private fun failPdfInspection(uri: Uri, previousPdf: Uri?, restoring: Boolean) {
        if (selectedPdf != uri) return
        if (restoring) {
            releasePersistedReadGrant(uri)
            storePdfUri(null)
            selectedPdf = null
            session.pdfDisplayName = null
            session.pdfPageCount = null
        } else {
            selectedPdf = previousPdf
            if (previousPdf == null) {
                session.pdfDisplayName = null
                session.pdfPageCount = null
            }
            session.printContinuation.cancel()
        }
        renderPdfStatus()
        showFailure(R.string.pdf_read_failed)
    }

    private fun takePersistedReadGrant(uri: Uri, grantFlags: Int): Boolean {
        if (!PdfGrantPolicy.canPersistReadGrant(grantFlags)) return false
        return runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
    }

    private fun hasPersistedReadGrant(uri: Uri): Boolean = runCatching {
        contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }.getOrDefault(false)

    private fun applyPdfGrantUpdate(update: PdfGrantUpdate) {
        update.releaseUris.forEach { releasePersistedReadGrant(Uri.parse(it)) }
        storePdfUri(update.persistedUri)
    }

    private fun releasePersistedReadGrant(uri: Uri) {
        runCatching {
            contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun storedPdfUri(): String? =
        getSharedPreferences(DOCUMENT_PREFERENCES, MODE_PRIVATE)
            .getString(KEY_PDF_URI, null)

    private fun storePdfUri(uri: String?) {
        getSharedPreferences(DOCUMENT_PREFERENCES, MODE_PRIVATE)
            .edit()
            .apply {
                if (uri == null) remove(KEY_PDF_URI) else putString(KEY_PDF_URI, uri)
            }
            .apply()
    }

    private fun renderPdfStatus() {
        val name = session.pdfDisplayName
        val pages = session.pdfPageCount
        if (selectedPdf != null && name != null && pages != null) {
            pdfStatus.text = getString(R.string.pdf_pages, name, pages)
        } else {
            pdfStatus.setText(R.string.no_pdf)
        }
    }

    private fun resolveDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return it.getString(index)
            }
        }
        return uri.lastPathSegment ?: "label.pdf"
    }

    private fun requestConnection(printAfter: Boolean) {
        if (!hasBluetoothPermissions()) {
            pendingPermissionPrintAfter = printAfter
            requestPermissions(requiredBluetoothPermissions(), REQUEST_BLUETOOTH_PERMISSIONS)
            return
        }
        val adapter = bluetoothAdapter
        if (adapter == null) {
            showFailure(R.string.bluetooth_not_supported)
            return
        }
        @SuppressLint("MissingPermission")
        if (!adapter.isEnabled) {
            pendingEnablePrint = printAfter
            @Suppress("DEPRECATION")
            startActivityForResult(
                Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),
                REQUEST_ENABLE_BLUETOOTH,
            )
            return
        }
        connectBestDevice(printAfter)
    }

    @SuppressLint("MissingPermission")
    private fun connectBestDevice(printAfter: Boolean) {
        if (busy) {
            Toast.makeText(this, R.string.busy, Toast.LENGTH_SHORT).show()
            return
        }
        setBusy(true)
        val displayName = printerClient.savedName ?: "XP-420B"
        activityStatus.text = getString(R.string.status_connecting, displayName)
        ioExecutor.execute {
            val attemptedAddress = runCatching {
                printerClient.bestPairedDevice()?.address
            }.getOrNull()
            try {
                val connected = printerClient.connectBest()
                session.failedAutomaticAddress = null
                session.postUi { activity ->
                    activity.showConnected(connected)
                    if (!printAfter) {
                        activity.setBusy(false)
                        activity.activityStatus.text =
                            activity.getString(R.string.status_connected, connected.name)
                    }
                }
                if (printAfter) printSelectedPdf()
            } catch (_: Exception) {
                printerClient.close()
                session.failedAutomaticAddress = attemptedAddress
                session.postUi { activity ->
                    activity.setBusy(false)
                    activity.startPrinterDiscovery(printAfter)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startPrinterDiscovery(printAfter: Boolean) {
        val adapter = bluetoothAdapter ?: run {
            showFailure(R.string.bluetooth_not_supported)
            return
        }
        pendingPrintAfterConnection = printAfter
        discoveredDevices.clear()
        adapter.bondedDevices.orEmpty().forEach { discoveredDevices[it.address] = it }
        printerDiscoveryInProgress = false
        if (adapter.isDiscovering) adapter.cancelDiscovery()
        setBusy(true)
        activityStatus.setText(R.string.status_scanning)
        printerDiscoveryInProgress = adapter.startDiscovery()
        if (!printerDiscoveryInProgress) {
            setBusy(false)
            showFailure(R.string.no_printer_found)
        }
    }

    @SuppressLint("MissingPermission")
    private fun finishPrinterDiscovery() {
        if (!printerDiscoveryInProgress) return
        printerDiscoveryInProgress = false
        setBusy(false)
        val devices = discoveredDevices.values.toList()
        val candidates = devices.map { PrinterCandidate(it.name, it.address) }
        val preferred = PrinterCandidateSelector.select(
            candidates = candidates,
            savedAddress = printerClient.savedAddress,
            excludedAddress = session.failedAutomaticAddress,
        )
        if (preferred != null) {
            val device = devices.first { it.address == preferred.address }
            prepareDevice(device, pendingPrintAfterConnection)
            return
        }
        if (devices.isEmpty()) {
            showFailure(R.string.no_printer_found)
            return
        }

        val labels = devices.map { device ->
            val name = device.name?.takeIf { it.isNotBlank() } ?: device.address
            "$name\n${device.address}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_printer)
            .setItems(labels) { _, which -> prepareDevice(devices[which], pendingPrintAfterConnection) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun prepareDevice(device: BluetoothDevice, printAfter: Boolean) {
        session.failedAutomaticAddress = null
        pendingPrintAfterConnection = printAfter
        if (device.bondState == BluetoothDevice.BOND_BONDED) {
            connectSpecificDevice(device, printAfter)
            return
        }

        pendingBondAddress = device.address
        setBusy(true)
        activityStatus.text = getString(R.string.status_pairing, device.name ?: "XP-420B")
        if (!device.createBond()) {
            pendingBondAddress = null
            showFailure(R.string.connection_failed)
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectSpecificDevice(device: BluetoothDevice, printAfter: Boolean) {
        setBusy(true)
        activityStatus.text = getString(R.string.status_connecting, device.name ?: "XP-420B")
        ioExecutor.execute {
            try {
                val connected = printerClient.connect(device)
                session.postUi { activity ->
                    activity.showConnected(connected)
                    if (!printAfter) {
                        activity.setBusy(false)
                        activity.activityStatus.text =
                            activity.getString(R.string.status_connected, connected.name)
                    }
                }
                if (printAfter) printSelectedPdf()
            } catch (_: Exception) {
                session.postUi { activity ->
                    activity.showFailure(R.string.connection_failed)
                }
            }
        }
    }

    private fun printSelectedPdf() {
        val uri = selectedPdf
        if (uri == null) {
            session.postUi { activity ->
                activity.showFailure(R.string.select_pdf_first)
            }
            return
        }
        try {
            val total = PdfLabelRenderer.printAll(
                context = applicationContext,
                uri = uri,
                output = printerClient.outputStream(),
            ) { page, pageTotal ->
                session.postUi { activity ->
                    activity.activityStatus.text =
                        activity.getString(R.string.status_printing, page, pageTotal)
                }
            }
            session.postUi { activity ->
                activity.setBusy(false)
                activity.activityStatus.text =
                    activity.getString(R.string.status_print_complete, total)
                Toast.makeText(
                    activity,
                    activity.getString(R.string.status_print_complete, total),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (_: Exception) {
            printerClient.close()
            session.postUi { activity ->
                activity.updatePrinterStatus()
                activity.showFailure(R.string.print_failed)
            }
        }
    }

    private fun showConnected(connected: ConnectedPrinter) {
        printerStatus.text = getString(R.string.printer_connected, connected.name)
        printerStatus.setTextColor(getColor(R.color.status_ready))
    }

    private fun updatePrinterStatus() {
        if (printerClient.isConnected()) {
            printerStatus.text = getString(
                R.string.printer_connected,
                printerClient.savedName ?: "XP-420B",
            )
            printerStatus.setTextColor(getColor(R.color.status_ready))
        } else {
            printerStatus.setText(R.string.printer_not_connected)
            printerStatus.setTextColor(getColor(R.color.status_idle))
        }
    }

    private fun showFailure(messageResource: Int) {
        setBusy(false)
        val message = getString(messageResource)
        activityStatus.text = getString(R.string.status_error, message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun setBusy(value: Boolean) {
        session.busy = value
        progressBar.visibility = if (value) View.VISIBLE else View.GONE
        progressBar.contentDescription = if (value) getString(R.string.accessibility_busy) else null
        selectPdfButton.isEnabled = !value
        connectButton.isEnabled = !value
        printButton.isEnabled = !value
    }

    private fun maybeAutoConnect() {
        if (printerClient.savedAddress == null || !hasBluetoothPermissions()) return
        val adapter = bluetoothAdapter ?: return
        @SuppressLint("MissingPermission")
        if (adapter.isEnabled) connectBestDevice(printAfter = false)
    }

    @SuppressLint("MissingPermission")
    private fun resumePendingBluetoothOperation() {
        if (!hasBluetoothPermissions()) return
        val adapter = bluetoothAdapter ?: return
        val bondAddress = pendingBondAddress
        if (bondAddress != null) {
            val device = runCatching { adapter.getRemoteDevice(bondAddress) }.getOrNull()
            when (device?.bondState) {
                BluetoothDevice.BOND_BONDED -> {
                    pendingBondAddress = null
                    connectSpecificDevice(device, pendingPrintAfterConnection)
                }
                BluetoothDevice.BOND_NONE -> {
                    pendingBondAddress = null
                    setBusy(false)
                    startPrinterDiscovery(pendingPrintAfterConnection)
                }
            }
            return
        }
        if (printerDiscoveryInProgress && !adapter.isDiscovering) {
            finishPrinterDiscovery()
        }
    }

    private fun hasBluetoothPermissions(): Boolean =
        requiredBluetoothPermissions().all {
            checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requiredBluetoothPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun registerBluetoothReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(bluetoothReceiver, filter)
        }
        receiverRegistered = true
    }

    @Suppress("DEPRECATION")
    private fun Intent.bluetoothDeviceExtra(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    @Suppress("DEPRECATION")
    private fun Intent.streamUriExtra(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM)
        }

    companion object {
        private const val REQUEST_PICK_PDF = 100
        private const val REQUEST_BLUETOOTH_PERMISSIONS = 101
        private const val REQUEST_ENABLE_BLUETOOTH = 102
        private const val DOCUMENT_PREFERENCES = "document_preferences"
        private const val KEY_PDF_URI = "selected_pdf_uri"
    }
}
