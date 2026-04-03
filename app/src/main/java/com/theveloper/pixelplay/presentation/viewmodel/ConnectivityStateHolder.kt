package com.theveloper.pixelplay.presentation.viewmodel

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

data class BluetoothAudioDeviceState(
    val name: String,
    val address: String? = null,
    val isConnected: Boolean = false,
    val batteryPercent: Int? = null
)

/**
 * Manages WiFi and Bluetooth connectivity state.
 * Extracted from PlayerViewModel to improve modularity.
 *
 * Responsibilities:
 * - WiFi state tracking (enabled, radio state, network name)
 * - Bluetooth state tracking (enabled, active device name, discovered/connected audio devices)
 * - System callback registration and lifecycle management
 */
@Singleton
class ConnectivityStateHolder @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // WiFi State
    private val _isWifiEnabled = MutableStateFlow(false)
    val isWifiEnabled: StateFlow<Boolean> = _isWifiEnabled.asStateFlow()

    private val _isWifiRadioOn = MutableStateFlow(false)
    val isWifiRadioOn: StateFlow<Boolean> = _isWifiRadioOn.asStateFlow()

    private val _wifiName = MutableStateFlow<String?>(null)
    val wifiName: StateFlow<String?> = _wifiName.asStateFlow()

    private val _isOnline = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    // Bluetooth State
    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private val _bluetoothName = MutableStateFlow<String?>(null)
    val bluetoothName: StateFlow<String?> = _bluetoothName.asStateFlow()

    private val _bluetoothAudioDeviceStates = MutableStateFlow<List<BluetoothAudioDeviceState>>(emptyList())
    val bluetoothAudioDeviceStates: StateFlow<List<BluetoothAudioDeviceState>> = _bluetoothAudioDeviceStates.asStateFlow()

    private val _bluetoothAudioDevices = MutableStateFlow<List<String>>(emptyList())
    val bluetoothAudioDevices: StateFlow<List<String>> = _bluetoothAudioDevices.asStateFlow()
    
    // Offline Barrier Event
    // Event to signal that playback was blocked due to offline status
    // Using extraBufferCapacity to ensure the event isn't lost if no collectors are immediately suspended
    private val _offlinePlaybackBlocked = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
    val offlinePlaybackBlocked: SharedFlow<Unit> = _offlinePlaybackBlocked.asSharedFlow()
    
    fun triggerOfflineBlockedEvent() {
        _offlinePlaybackBlocked.tryEmit(Unit)
    }

    /**
     * Manually refresh local connection info (e.g. WiFi SSID).
     */
    fun refreshLocalConnectionInfo(refreshBluetoothDevices: Boolean = false) {
        updateWifiInfo()
        if (refreshBluetoothDevices) {
            refreshBluetoothAudioDevices()
        } else {
            updateAudioDevices()
        }
    }

    // System services
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager: WifiManager? =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager: BluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val audioManager: android.media.AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

    // Callbacks and receivers
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var wifiStateReceiver: BroadcastReceiver? = null
    private var bluetoothStateReceiver: BroadcastReceiver? = null
    private var audioDeviceCallback: android.media.AudioDeviceCallback? = null

    private var isInitialized = false
    private val discoveredBluetoothAudioDevices = linkedMapOf<String, BluetoothAudioDeviceState>()

    /**
     * Initialize connectivity monitoring. Should be called once from ViewModel.
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true

        // Initial state check
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        updateWifiRadioState()
        _isWifiEnabled.value = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        if (_isWifiEnabled.value) {
            updateWifiInfo()
        }
        
        _isOnline.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        updateBluetoothEnabledState()

        // Register WiFi network callback
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            // Track all valid networks to handle rapid switching
            private val availableNetworks = mutableSetOf<Network>()

            override fun onAvailable(network: Network) {
                // Network is available, but waiting for capability check
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                
                if (hasInternet && isValidated) {
                    availableNetworks.add(network)
                } else {
                    availableNetworks.remove(network)
                }
                
                checkConnectivity()
                
                if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    _isWifiEnabled.value = true
                    updateWifiInfo()
                }
            }

            override fun onLost(network: Network) {
                availableNetworks.remove(network)
                checkConnectivity()
                
                val currentNetwork = connectivityManager.activeNetwork
                val caps = connectivityManager.getNetworkCapabilities(currentNetwork)
                _isWifiEnabled.value = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                if (!_isWifiEnabled.value) _wifiName.value = null
            }
            
            private fun checkConnectivity() {
                _isOnline.value = availableNetworks.isNotEmpty()
            }
        }
        
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback!!)

        // Register receivers
        wifiStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == WifiManager.WIFI_STATE_CHANGED_ACTION) {
                     updateWifiRadioState()
                }
            }
        }
        context.registerReceiver(wifiStateReceiver, IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION))

        bluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        updateBluetoothEnabledState()
                        if (_isBluetoothEnabled.value) {
                            updateAudioDevices()
                        } else {
                            discoveredBluetoothAudioDevices.clear()
                            _bluetoothAudioDeviceStates.value = emptyList()
                            _bluetoothAudioDevices.value = emptyList()
                            updateBluetoothName(emptyList())
                        }
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED,
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> updateAudioDevices()
                    BluetoothDevice.ACTION_FOUND -> {
                        extractBluetoothDevice(intent)
                            ?.takeIf { it.isAudioOutputCandidate() }
                            ?.toBluetoothAudioDeviceState(isConnected = false)
                            ?.let { deviceState ->
                                discoveredBluetoothAudioDevices[deviceState.uniqueKey()] = deviceState
                                updateAudioDevices()
                            }
                    }
                }
            }
        }
        context.registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
                addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothDevice.ACTION_FOUND)
            }
        )

        // Audio Device Callback
        audioDeviceCallback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateAudioDevices()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out android.media.AudioDeviceInfo>?) {
                updateAudioDevices()
            }
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        updateAudioDevices()
    }

    private fun updateWifiRadioState() {
        _isWifiRadioOn.value = wifiManager?.isWifiEnabled == true
    }

    private fun updateWifiInfo() {
        _wifiName.value = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R && hasFineLocationPermission()) {
            readConnectedWifiSsid()
        } else {
            // On Android 12+ we intentionally avoid SSID reads so the cast flow does not look like location usage.
            "WiFi Connected"
        }
    }

    private fun updateBluetoothName(connectedAudioDevices: List<String>) {
        if (!_isBluetoothEnabled.value) {
            _bluetoothName.value = null
            return
        }

        _bluetoothName.value = _bluetoothName.value
            ?.takeIf { it in connectedAudioDevices }
            ?: connectedAudioDevices.firstOrNull()
    }

    private fun updateAudioDevices() {
        updateBluetoothEnabledState()
        if (!_isBluetoothEnabled.value) {
            discoveredBluetoothAudioDevices.clear()
            _bluetoothAudioDeviceStates.value = emptyList()
            _bluetoothAudioDevices.value = emptyList()
            updateBluetoothName(emptyList())
            return
        }

        val localDeviceNames = resolveLocalBluetoothDeviceNames()
        val connectedDevices = collectConnectedBluetoothDevices()
        val availableDevices = discoveredBluetoothAudioDevices.values
            .map { sanitizeBluetoothAudioDeviceState(it, localDeviceNames) }
            .filterNotNull()
        val connectedDeviceKeys = connectedDevices.mapTo(mutableSetOf()) { it.uniqueKey() }

        val mergedDevices = linkedMapOf<String, BluetoothAudioDeviceState>()
        connectedDevices.forEach { mergedDevices[it.uniqueKey()] = it }
        availableDevices.forEach { deviceState ->
            val key = deviceState.uniqueKey()
            val existing = mergedDevices[key]
            if (existing == null) {
                mergedDevices[key] = deviceState
            } else {
                mergedDevices[key] = existing.mergeWith(deviceState)
            }
        }

        val bluetoothDevices = mergedDevices.values
            .sortedWith(
                compareByDescending<BluetoothAudioDeviceState> { it.uniqueKey() in connectedDeviceKeys }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
        _bluetoothAudioDeviceStates.value = bluetoothDevices
        _bluetoothAudioDevices.value = bluetoothDevices.map(BluetoothAudioDeviceState::name)
        updateBluetoothName(connectedDevices.map(BluetoothAudioDeviceState::name))
    }

    @SuppressLint("MissingPermission")
    private fun safeGetConnectedDevices(profile: Int): List<BluetoothDevice> {
        if (!hasBluetoothConnectPermission()) return emptyList()
        return safeBluetoothCall(emptyList()) { bluetoothManager.getConnectedDevices(profile) }
    }

    private fun collectConnectedBluetoothDevices(): List<BluetoothAudioDeviceState> {
        val connectedDevices = linkedMapOf<String, BluetoothAudioDeviceState>()
        val localDeviceNames = resolveLocalBluetoothDeviceNames()
        val audioDevices = audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)

        for (device in audioDevices) {
            if (
                device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                device.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            ) {
                val name = device.productName?.toString()?.trim().orEmpty()
                if (name.isNotEmpty() && !isOwnBluetoothDeviceName(name, localDeviceNames)) {
                    val address = device.address?.trim().orEmpty().takeIf { it.isNotEmpty() }
                    val key = bluetoothDeviceKey(address, name)
                    connectedDevices[key] = BluetoothAudioDeviceState(
                        name = name,
                        address = address,
                        isConnected = true
                    )
                }
            }
        }

        if (hasBluetoothConnectPermission()) {
            safeGetConnectedDevices(BluetoothProfile.A2DP)
                .mapNotNull { it.toBluetoothAudioDeviceState(isConnected = true) }
                .forEach { deviceState ->
                    val key = deviceState.uniqueKey()
                    connectedDevices[key] = connectedDevices[key]?.mergeWith(deviceState) ?: deviceState
                }

            safeGetConnectedDevices(BluetoothProfile.HEADSET)
                .mapNotNull { it.toBluetoothAudioDeviceState(isConnected = true) }
                .forEach { deviceState ->
                    val key = deviceState.uniqueKey()
                    connectedDevices[key] = connectedDevices[key]?.mergeWith(deviceState) ?: deviceState
                }
        }

        return connectedDevices.values.toList()
    }

    private fun refreshBluetoothAudioDevices() {
        discoveredBluetoothAudioDevices.clear()
        updateAudioDevices()

        val adapter = bluetoothAdapter ?: return
        if (!canStartBluetoothDiscovery()) return

        if (isBluetoothDiscoveryActive(adapter)) {
            cancelBluetoothDiscovery(adapter)
        }
        startBluetoothDiscovery(adapter)
    }

    private fun sanitizeBluetoothAudioDeviceState(
        deviceState: BluetoothAudioDeviceState,
        localDeviceNames: Set<String> = resolveLocalBluetoothDeviceNames()
    ): BluetoothAudioDeviceState? {
        val normalizedName = deviceState.name.trim()
        if (normalizedName.isEmpty() || isOwnBluetoothDeviceName(normalizedName, localDeviceNames)) return null

        return deviceState.copy(
            name = normalizedName,
            address = deviceState.address?.trim()?.takeIf { it.isNotEmpty() },
            batteryPercent = deviceState.batteryPercent?.takeIf { it in 0..100 }
        )
    }

    private fun isOwnBluetoothDeviceName(
        name: String,
        localDeviceNames: Set<String> = resolveLocalBluetoothDeviceNames()
    ): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return true

        return localDeviceNames.any { it.equals(normalizedName, ignoreCase = true) }
    }

    private fun updateBluetoothEnabledState() {
        _isBluetoothEnabled.value = if (!hasBluetoothConnectPermission()) {
            false
        } else {
            readBluetoothEnabledState()
        }
    }

    private fun resolveLocalBluetoothAdapterName(): String? {
        if (!hasBluetoothConnectPermission()) return null
        return readLocalBluetoothAdapterName()
    }

    private fun resolveLocalBluetoothDeviceNames(): Set<String> {
        return buildSet {
            resolveLocalBluetoothAdapterName()?.let { add(it) }
            Build.MODEL.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothScanPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBluetoothDiscoveryLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ||
            hasFineLocationPermission()
    }

    private fun canStartBluetoothDiscovery(): Boolean {
        return _isBluetoothEnabled.value &&
            hasBluetoothScanPermission() &&
            hasBluetoothDiscoveryLocationPermission()
    }

    private fun BluetoothAudioDeviceState.uniqueKey(): String {
        return bluetoothDeviceKey(address, name)
    }

    private fun BluetoothAudioDeviceState.mergeWith(other: BluetoothAudioDeviceState): BluetoothAudioDeviceState {
        return copy(
            name = if (name.isNotBlank()) name else other.name,
            address = address ?: other.address,
            isConnected = isConnected || other.isConnected,
            batteryPercent = batteryPercent ?: other.batteryPercent
        )
    }

    private fun bluetoothDeviceKey(address: String?, name: String): String {
        return address?.takeIf { it.isNotBlank() } ?: "name:${name.lowercase()}"
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.toBluetoothAudioDeviceState(isConnected: Boolean): BluetoothAudioDeviceState? {
        if (!hasBluetoothConnectPermission()) return null

        val deviceName = safeBluetoothCall("") { name?.trim().orEmpty() }
        val localDeviceNames = resolveLocalBluetoothDeviceNames()
        if (deviceName.isEmpty() || isOwnBluetoothDeviceName(deviceName, localDeviceNames)) return null

        return BluetoothAudioDeviceState(
            name = deviceName,
            address = safeBluetoothCall("") { address?.trim().orEmpty() }
                .takeIf { it.isNotEmpty() },
            isConnected = isConnected,
            batteryPercent = resolveBatteryPercent(this)
        )
    }

    private fun resolveBatteryPercent(device: BluetoothDevice): Int? {
        if (!hasBluetoothConnectPermission()) return null

        return runCatching {
            val batteryMethod = device.javaClass.methods.firstOrNull { method ->
                method.name == "getBatteryLevel" && method.parameterCount == 0
            } ?: device.javaClass.declaredMethods.firstOrNull { method ->
                method.name == "getBatteryLevel" && method.parameterCount == 0
            }

            batteryMethod
                ?.apply { isAccessible = true }
                ?.invoke(device) as? Int
        }.getOrNull()?.takeIf { it in 0..100 }
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.isAudioOutputCandidate(): Boolean {
        if (!hasBluetoothConnectPermission()) return false

        val deviceClass = safeBluetoothCall<BluetoothClass?>(null) { bluetoothClass } ?: return false
        return deviceClass.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            deviceClass.hasService(BluetoothClass.Service.RENDER)
    }

    @SuppressLint("MissingPermission")
    private fun readConnectedWifiSsid(): String? {
        if (!hasFineLocationPermission()) return null

        val info = wifiManager?.connectionInfo ?: return null
        if (info.supplicantState != android.net.wifi.SupplicantState.COMPLETED) return null

        var ssid = info.ssid
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        return ssid
    }

    @SuppressLint("MissingPermission")
    private fun readBluetoothEnabledState(): Boolean {
        if (!hasBluetoothConnectPermission()) return false

        return safeBluetoothCall(false) { bluetoothAdapter?.isEnabled ?: false }
    }

    @SuppressLint("MissingPermission")
    private fun readLocalBluetoothAdapterName(): String? {
        if (!hasBluetoothConnectPermission()) return null

        return safeBluetoothCall("") { bluetoothAdapter?.name?.trim().orEmpty() }
            .takeIf { it.isNotEmpty() }
    }

    @SuppressLint("MissingPermission")
    private fun isBluetoothDiscoveryActive(adapter: BluetoothAdapter): Boolean {
        if (!hasBluetoothScanPermission()) return false

        return runCatching { adapter.isDiscovering }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun cancelBluetoothDiscovery(adapter: BluetoothAdapter) {
        if (!hasBluetoothScanPermission()) return

        runCatching { adapter.cancelDiscovery() }
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothDiscovery(adapter: BluetoothAdapter) {
        if (!hasBluetoothScanPermission()) return

        runCatching { adapter.startDiscovery() }
    }

    private inline fun <T> safeBluetoothCall(defaultValue: T, block: () -> T): T {
        return runCatching(block).getOrDefault(defaultValue)
    }

    @Suppress("DEPRECATION")
    private fun extractBluetoothDevice(intent: Intent): BluetoothDevice? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
    }

    /**
     * Cleanup resources. Should be called from ViewModel's onCleared.
     */
    fun onCleared() {
        networkCallback?.let { 
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
        }
        wifiStateReceiver?.let { 
            runCatching { context.unregisterReceiver(it) }
        }
        bluetoothStateReceiver?.let { 
            runCatching { context.unregisterReceiver(it) }
        }
        if (hasBluetoothScanPermission()) {
            bluetoothAdapter?.takeIf { isBluetoothDiscoveryActive(it) }?.let {
                cancelBluetoothDiscovery(it)
            }
        }
        discoveredBluetoothAudioDevices.clear()
        _bluetoothAudioDeviceStates.value = emptyList()
        _bluetoothAudioDevices.value = emptyList()
        isInitialized = false
    }
}
