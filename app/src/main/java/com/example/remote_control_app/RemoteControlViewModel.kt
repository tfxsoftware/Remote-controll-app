package com.example.remote_control_app

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.cancel
import kotlin.coroutines.resume
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

class RemoteControlViewModel(application: Application) : AndroidViewModel(application) {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
    private var nsdManager: NsdManager? = null
    private var discoveredServer: NsdServiceInfo? = null
    private lateinit var connectionPreferences: ConnectionPreferences
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var currentConnectionId = 0 // To track connection attempts
    
    // Connection state management
    private val _connectionState = mutableStateOf(ConnectionState.DISCONNECTED)
    val connectionState: State<ConnectionState> = _connectionState
    
    // Connection healing
    private var reconnectJob: Job? = null
    private var isReconnecting = false
    private val maxReconnectAttempts = 5
    private var reconnectAttempts = 0
    private val reconnectDelayMs = 2000L // Start with 2 seconds
    private var currentConnectionStrategy = ConnectionStrategy.MDNS
    private val connectionScope = CoroutineScope(Job() + Dispatchers.IO)
    
    private val SERVICE_TYPE = "_remote-control._tcp."
    private val SERVICE_NAME = "remote-control"
    private val DEFAULT_PORTS = listOf(8765, 8766, 8767) // Alternative ports to try
    
    private var lastMouseMoveTime = 0L
    private val minMovementInterval = 16L // ~60fps
    private var accumulatedX = 0
    private var accumulatedY = 0
    private var mouseMovementJob: Job? = null
    
    // Scroll handling
    private var lastScrollTime = 0L
    private val minScrollInterval = 50L // Minimum 50ms between scroll events
    private var accumulatedScroll = 0
    private var scrollJob: Job? = null
    
    companion object {
        private const val TAG = "RemoteControlViewModel"
    }

    private enum class ConnectionStrategy {
        MDNS,
        CACHED_IP,
        DOMAIN_NAME
    }

    fun clearConnectionCache() {
        Log.d(TAG, "Clearing connection cache due to network change")
        connectionPreferences.clearConnectionCache()
    }

    fun initializeDiscovery(context: Context) {
        Log.d(TAG, "Initializing connection...")
        connectionPreferences = ConnectionPreferences(context)
        setupNetworkMonitoring(context)
        _connectionState.value = ConnectionState.CONNECTING
        
        // Start with cached IP if available
        val cachedConnection = connectionPreferences.getLastSuccessfulConnection()
        if (cachedConnection != null) {
            Log.d(TAG, "Found cached connection, trying it first")
            currentConnectionStrategy = ConnectionStrategy.CACHED_IP
            tryConnect(cachedConnection.ip, cachedConnection.port)
            
            // If cached connection fails, try mDNS after a delay
            connectionScope.launch {
                delay(2000)
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    startMDNSDiscovery(context)
                }
            }
        } else {
            startMDNSDiscovery(context)
        }
    }

    private fun setupNetworkMonitoring(context: Context) {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network became available")
                // Only attempt reconnection if we're not already connected
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    connectionScope.launch {
                        delay(1000) // Wait for network to stabilize
                        handleNetworkChange()
                    }
                }
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network connection lost")
                _connectionState.value = ConnectionState.DISCONNECTED
                cleanupConnections()
            }
            
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                Log.d(TAG, "Network capabilities changed. WiFi: $hasWifi")
                if (hasWifi && _connectionState.value != ConnectionState.CONNECTED) {
                    connectionScope.launch {
                        delay(1000) // Wait for network to stabilize
                        handleNetworkChange()
                    }
                }
            }
        }
        
        // Register network callback
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    private fun handleNetworkChange() {
        Log.d(TAG, "Handling network change")
        cleanupConnections()
        reconnectAttempts = 0
        isReconnecting = false
        
        // Start fresh connection attempt
        when (currentConnectionStrategy) {
            ConnectionStrategy.CACHED_IP -> {
                connectionPreferences.getLastSuccessfulConnection()?.let {
                    tryConnect(it.ip, it.port)
                } ?: startMDNSDiscovery()
            }
            else -> startMDNSDiscovery()
        }
    }

    private fun cleanupConnections() {
        Log.d(TAG, "Cleaning up connections")
        currentConnectionId++ // Invalidate current connection attempts
        webSocket?.close(1000, null) // Close any existing WebSocket with normal closure code
        webSocket = null
        reconnectJob?.cancel()
        reconnectJob = null
        
        try {
            nsdManager?.stopServiceDiscovery(discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping service discovery: ${e.message}")
        }
    }

    private fun tryConnect(host: String, preferredPort: Int) {
        val connectionId = ++currentConnectionId // Get new connection ID
        
        connectionScope.launch {
            // Try each port in sequence
            for (portOffset in 0..2) {
                if (connectionId != currentConnectionId) {
                    Log.d(TAG, "Connection attempt superseded by newer attempt")
                    return@launch
                }
                
                val port = preferredPort + portOffset
                if (tryConnectToPort(host, port, connectionId)) {
                    break
                }
                delay(500) // Wait before trying next port
            }
        }
    }

    private suspend fun tryConnectToPort(host: String, port: Int, connectionId: Int): Boolean {
        if (connectionId != currentConnectionId) return false
        
        Log.d(TAG, "Attempting connection to $host:$port")
        val url = "ws://$host:$port"
        
        return suspendCancellableCoroutine { continuation ->
            if (connectionId != currentConnectionId) {
                continuation.resume(false)
                return@suspendCancellableCoroutine
            }
            
            val request = Request.Builder()
                .url(url)
                .build()
            
            webSocket?.close(1000, null) // Close any existing connection
            
            webSocket = client.newWebSocket(request, object : WebSocketListener() {
                private var hasCompleted = false
                
                private fun complete(result: Boolean) {
                    if (!hasCompleted) {
                        hasCompleted = true
                        continuation.resume(result)
                    }
                }
                
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    if (connectionId != currentConnectionId) {
                        webSocket.close(1000, "Superseded by newer connection")
                        complete(false)
                        return
                    }
                    
                    Log.d(TAG, "WebSocket connection opened successfully!")
                    _connectionState.value = ConnectionState.CONNECTED
                    reconnectAttempts = 0
                    isReconnecting = false
                    
                    // Save successful connection if it's not the fallback IP
                    if (currentConnectionStrategy != ConnectionStrategy.DOMAIN_NAME) {
                        connectionPreferences.saveSuccessfulConnection(host, port)
                    }
                    
                    complete(true)
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket connection failed: ${t.message}")
                    if (connectionId != currentConnectionId) {
                        complete(false)
                        return
                    }
                    
                    _connectionState.value = ConnectionState.DISCONNECTED
                    
                    if (currentConnectionStrategy == ConnectionStrategy.CACHED_IP) {
                        connectionPreferences.clearConnectionCache()
                    }
                    
                    complete(false)
                }
            })

            continuation.invokeOnCancellation {
                Log.d(TAG, "Connection attempt cancelled")
                webSocket?.close(1000, "Cancelled")
            }
        }
    }

    private fun startMDNSDiscovery(context: Context? = null) {
        Log.d(TAG, "Starting mDNS discovery...")
        currentConnectionStrategy = ConnectionStrategy.MDNS
        
        val ctx = context ?: getApplication<Application>()
        if (nsdManager == null) {
            nsdManager = ctx.getSystemService(Context.NSD_SERVICE) as? NsdManager
        }
        
        if (nsdManager == null) {
            Log.e(TAG, "Failed to get NsdManager, falling back to domain name")
            tryDomainNameConnection()
            return
        }
        
        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            
            // Try domain name connection as fallback after a delay
            connectionScope.launch {
                delay(3000) // Wait 3 seconds for mDNS discovery
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    Log.d(TAG, "mDNS discovery failed, trying domain name...")
                    tryDomainNameConnection()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service discovery: ${e.message}")
            tryDomainNameConnection()
        }
    }

    private fun tryDomainNameConnection() {
        if (_connectionState.value == ConnectionState.CONNECTED) return
        
        Log.d(TAG, "Attempting connection to domain name...")
        currentConnectionStrategy = ConnectionStrategy.DOMAIN_NAME
        _connectionState.value = ConnectionState.CONNECTING
        connectWebSocket("remote-control.local", 8765)
    }

    private fun connectWebSocket(host: String, port: Int) {
        val url = "ws://$host:$port"
        Log.d(TAG, "Attempting WebSocket connection to: $url")
        
        _connectionState.value = ConnectionState.CONNECTING
        
        val request = Request.Builder()
            .url(url)
            .build()
        
        webSocket?.close(1000, null) // Cancel any existing connection
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened successfully!")
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0
                isReconnecting = false
                
                // Save successful connection if it's not the fallback IP
                if (currentConnectionStrategy != ConnectionStrategy.DOMAIN_NAME) {
                    connectionPreferences.saveSuccessfulConnection(host, port)
                }
                
                // Stop mDNS discovery if it's running
                if (currentConnectionStrategy != ConnectionStrategy.MDNS) {
                    nsdManager?.stopServiceDiscovery(discoveryListener)
                }
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: $text")
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket connection failed: ${t.message}")
                Log.e(TAG, "Response: ${response?.message}")
                _connectionState.value = ConnectionState.DISCONNECTED
                
                // Clear cache if the cached connection failed
                if (currentConnectionStrategy == ConnectionStrategy.CACHED_IP) {
                    connectionPreferences.clearConnectionCache()
                }
                
                // Start reconnection if not already reconnecting
                if (!isReconnecting) {
                    startReconnection()
                }
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket connection closed: $code - $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                
                if (!isReconnecting) {
                    startReconnection()
                }
            }
        })
    }

    private fun startReconnection() {
        if (isReconnecting || reconnectAttempts >= maxReconnectAttempts) return
        
        isReconnecting = true
        _connectionState.value = ConnectionState.RECONNECTING
        
        reconnectJob?.cancel()
        reconnectJob = connectionScope.launch {
            while (isReconnecting && reconnectAttempts < maxReconnectAttempts) {
                delay(reconnectDelayMs * (reconnectAttempts + 1))
                reconnectAttempts++
                
                when (currentConnectionStrategy) {
                    ConnectionStrategy.CACHED_IP -> {
                        val cached = connectionPreferences.getLastSuccessfulConnection()
                        if (cached != null) {
                            connectWebSocket(cached.ip, cached.port)
                        } else {
                            currentConnectionStrategy = ConnectionStrategy.MDNS
                        }
                    }
                    ConnectionStrategy.MDNS -> {
                        // Try to restart mDNS discovery
                        nsdManager?.let { manager ->
                            try {
                                manager.stopServiceDiscovery(discoveryListener)
                                delay(1000)
                                manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error restarting mDNS discovery: ${e.message}")
                                currentConnectionStrategy = ConnectionStrategy.DOMAIN_NAME
                            }
                        }
                    }
                    ConnectionStrategy.DOMAIN_NAME -> {
                        connectWebSocket("remote-control.local", 8765)
                    }
                }
                
                // If still not connected, try next strategy
                if (_connectionState.value != ConnectionState.CONNECTED) {
                    currentConnectionStrategy = when (currentConnectionStrategy) {
                        ConnectionStrategy.CACHED_IP -> ConnectionStrategy.MDNS
                        ConnectionStrategy.MDNS -> ConnectionStrategy.DOMAIN_NAME
                        ConnectionStrategy.DOMAIN_NAME -> ConnectionStrategy.MDNS
                    }
                }
            }
            
            if (reconnectAttempts >= maxReconnectAttempts) {
                isReconnecting = false
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanupConnections()
        networkCallback?.let { callback ->
            connectivityManager?.unregisterNetworkCallback(callback)
        }
        connectionScope.cancel()
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(TAG, "Discovery start failed for $serviceType with error code: $errorCode")
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            Log.e(TAG, "Discovery stop failed for $serviceType with error code: $errorCode")
        }

        override fun onDiscoveryStarted(serviceType: String?) {
            Log.d(TAG, "Discovery started for service type: $serviceType")
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            Log.d(TAG, "Discovery stopped for service type: $serviceType")
        }

        override fun onServiceFound(service: NsdServiceInfo?) {
            Log.d(TAG, "Service found: ${service?.serviceName}")
            service?.let {
                if (it.serviceName.contains(SERVICE_NAME)) {
                    Log.d(TAG, "Found our target server: ${it.serviceName}")
                    // Found our server, resolve it
                    nsdManager?.resolveService(it, resolveListener)
                } else {
                    Log.d(TAG, "Service found but not our target: ${it.serviceName}")
                }
            }
        }

        override fun onServiceLost(service: NsdServiceInfo?) {
            Log.d(TAG, "Service lost: ${service?.serviceName}")
        }
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.e(TAG, "Service resolve failed for ${serviceInfo?.serviceName} with error code: $errorCode")
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
            Log.d(TAG, "Service resolved: ${serviceInfo?.serviceName}")
            serviceInfo?.let {
                discoveredServer = it
                val host = it.host.hostAddress
                val port = it.port
                Log.d(TAG, "Connecting to server at $host:$port")
                connectWebSocket(host, port)
            }
        }
    }

    fun sendMouseMove(x: Int, y: Int, relative: Boolean) {
        val currentTime = System.currentTimeMillis()
        
        // Accumulate movements
        accumulatedX += x
        accumulatedY += y
        
        // Cancel any pending movement
        mouseMovementJob?.cancel()
        
        // If we haven't sent a movement recently, send immediately
        if (currentTime - lastMouseMoveTime >= minMovementInterval) {
            sendAccumulatedMovement(relative)
        } else {
            // Otherwise schedule to send after the interval
            mouseMovementJob = connectionScope.launch {
                delay(minMovementInterval - (currentTime - lastMouseMoveTime))
                sendAccumulatedMovement(relative)
            }
        }
    }
    
    private fun sendAccumulatedMovement(relative: Boolean) {
        if (accumulatedX == 0 && accumulatedY == 0) return
        
        val json = JSONObject()
        json.put("type", "mouse_move")
        json.put("x", accumulatedX)
        json.put("y", accumulatedY)
        json.put("relative", relative)
        send(json)
        
        lastMouseMoveTime = System.currentTimeMillis()
        accumulatedX = 0
        accumulatedY = 0
    }

    fun sendMouseClick(button: String, clicks: Int = 1, interval: Double = 0.0) {
        val json = JSONObject()
        json.put("type", "mouse_click")
        json.put("button", button)
        json.put("clicks", clicks)
        json.put("interval", interval)
        Log.d(TAG, "Sending mouse click: $json")
        send(json)
    }

    fun sendMouseScroll(amount: Int) {
        val currentTime = System.currentTimeMillis()
        
        // Accumulate scroll amount
        accumulatedScroll += amount
        
        // Cancel any pending scroll
        scrollJob?.cancel()
        
        // If we haven't sent a scroll recently, send immediately
        if (currentTime - lastScrollTime >= minScrollInterval) {
            sendAccumulatedScroll()
        } else {
            // Otherwise schedule to send after the interval
            scrollJob = connectionScope.launch {
                delay(minScrollInterval - (currentTime - lastScrollTime))
                sendAccumulatedScroll()
            }
        }
    }
    
    private fun sendAccumulatedScroll() {
        if (accumulatedScroll == 0) return
        
        val json = JSONObject()
        json.put("type", "mouse_scroll")
        json.put("amount", accumulatedScroll)
        Log.d(TAG, "Sending mouse scroll: $accumulatedScroll")
        send(json)
        
        lastScrollTime = System.currentTimeMillis()
        accumulatedScroll = 0
    }

    fun sendKeyType(text: String, interval: Double = 0.01) {
        val json = JSONObject()
        json.put("type", "key_type")
        json.put("text", text)
        json.put("interval", interval)
        Log.d(TAG, "Sending key type: $json")
        send(json)
    }

    fun sendKeyPress(key: String, hold: Boolean = false, release: Boolean = false) {
        val json = JSONObject()
        json.put("type", "key_press")
        json.put("key", key)
        json.put("hold", hold)
        json.put("release", release)
        Log.d(TAG, "Sending key press: $json")
        send(json)
    }

    fun sendSpecialKey(key: String) {
        // Send common special keys
        when (key.lowercase()) {
            // Navigation keys
            "backspace" -> sendKeyPress("backspace")
            "delete" -> sendKeyPress("delete")
            "enter" -> sendKeyPress("enter")
            "tab" -> sendKeyPress("tab")
            "escape" -> sendKeyPress("escape")
            "space" -> sendKeyPress("space")
            "up" -> sendKeyPress("up")
            "down" -> sendKeyPress("down")
            "left" -> sendKeyPress("left")
            "right" -> sendKeyPress("right")
            "home" -> sendKeyPress("home")
            "end" -> sendKeyPress("end")
            "pageup" -> sendKeyPress("pageup")
            "pagedown" -> sendKeyPress("pagedown")
            
            // Function keys
            "f1" -> sendKeyPress("f1")
            "f2" -> sendKeyPress("f2")
            "f3" -> sendKeyPress("f3")
            "f4" -> sendKeyPress("f4")
            "f5" -> sendKeyPress("f5")
            "f6" -> sendKeyPress("f6")
            "f7" -> sendKeyPress("f7")
            "f8" -> sendKeyPress("f8")
            "f9" -> sendKeyPress("f9")
            "f10" -> sendKeyPress("f10")
            "f11" -> sendKeyPress("f11")
            "f12" -> sendKeyPress("f12")
            
            // Media keys
            "volumeup", "volup" -> sendKeyPress("volumeup")
            "volumedown", "voldown" -> sendKeyPress("volumedown")
            "volumemute", "mute" -> sendKeyPress("volumemute")
            "play" -> sendKeyPress("play")
            "pause" -> sendKeyPress("pause")
            "stop" -> sendKeyPress("stop")
            "nexttrack", "next" -> sendKeyPress("nexttrack")
            "prevtrack", "previous" -> sendKeyPress("prevtrack")
            
            // Browser keys
            "browserback", "back" -> sendKeyPress("browserback")
            "browserforward", "forward" -> sendKeyPress("browserforward")
            "browserrefresh", "refresh" -> sendKeyPress("browserrefresh")
            "browserstop" -> sendKeyPress("browserstop")
            "browsersearch", "search" -> sendKeyPress("browsersearch")
            "browserfavorites", "favorites" -> sendKeyPress("browserfavorites")
            "browserhome", "homepage" -> sendKeyPress("browserhome")
            
            // Default case
            else -> sendKeyPress(key)
        }
    }
    
    fun sendKeyCombination(keys: List<String>) {
        val json = JSONObject()
        json.put("type", "multiple_keys")
        json.put("keys", JSONObject().apply {
            put("keys", JSONObject().apply {
                keys.forEach { put("key", it) }
            })
        })
        Log.d(TAG, "Sending key combination: $json")
        send(json)
    }
    
    fun sendHotkey(vararg keys: String) {
        val keyString = keys.joinToString("+")
        sendKeyPress(keyString)
    }

    fun manualReconnect() {
        Log.d(TAG, "Manual reconnection requested")
        if (_connectionState.value == ConnectionState.RECONNECTING) {
            Log.d(TAG, "Already reconnecting, ignoring request")
            return
        }
        
        cleanupConnections()
        reconnectAttempts = 0
        isReconnecting = false
        _connectionState.value = ConnectionState.CONNECTING
        
        // Start fresh connection attempt
        when (currentConnectionStrategy) {
            ConnectionStrategy.CACHED_IP -> {
                connectionPreferences.getLastSuccessfulConnection()?.let {
                    tryConnect(it.ip, it.port)
                } ?: startMDNSDiscovery(null)
            }
            else -> startMDNSDiscovery(null)
        }
    }

    private fun send(json: JSONObject) {
        if (_connectionState.value != ConnectionState.CONNECTED) {
            Log.w(TAG, "Not sending message - not connected")
            return
        }
        try {
            webSocket?.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message: ${e.message}")
            _connectionState.value = ConnectionState.DISCONNECTED
            startReconnection()
        }
    }
} 