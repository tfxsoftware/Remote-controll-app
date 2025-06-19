package com.example.remote_control_app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

class RemoteControlViewModel : ViewModel() {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private var nsdManager: NsdManager? = null
    private var discoveredServer: NsdServiceInfo? = null
    
    // Connection state management
    private val _connectionState = mutableStateOf(ConnectionState.DISCONNECTED)
    val connectionState: State<ConnectionState> = _connectionState
    
    // Connection healing
    private var reconnectJob: kotlinx.coroutines.Job? = null
    private var isReconnecting = false
    private val maxReconnectAttempts = 5
    private var reconnectAttempts = 0
    private val reconnectDelayMs = 2000L // Start with 2 seconds
    
    private val SERVICE_TYPE = "_remote-control._tcp."
    private val SERVICE_NAME = "remote-control"
    
    companion object {
        private const val TAG = "RemoteControlViewModel"
    }

    fun initializeDiscovery(context: Context) {
        Log.d(TAG, "Initializing mDNS discovery...")
        _connectionState.value = ConnectionState.CONNECTING
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        discoverServices()
        
        // Try domain name connection as fallback after a delay
        viewModelScope.launch {
            delay(3000) // Wait 3 seconds for mDNS discovery
            if (_connectionState.value != ConnectionState.CONNECTED) {
                Log.d(TAG, "mDNS discovery failed, trying domain name...")
                connectToDomainName()
            }
        }
    }

    private fun discoverServices() {
        Log.d(TAG, "Starting service discovery for type: $SERVICE_TYPE")
        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting service discovery: ${e.message}")
        }
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

    private fun connectWebSocket(host: String, port: Int) {
        val url = "ws://$host:$port"
        Log.d(TAG, "Attempting WebSocket connection to: $url")
        
        _connectionState.value = ConnectionState.CONNECTING
        
        val request = Request.Builder()
            .url(url)
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened successfully!")
                _connectionState.value = ConnectionState.CONNECTED
                reconnectAttempts = 0 // Reset reconnect attempts on successful connection
                isReconnecting = false
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: $text")
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket connection failed: ${t.message}")
                Log.e(TAG, "Response: ${response?.message}")
                _connectionState.value = ConnectionState.DISCONNECTED
                
                // Start reconnection if not already reconnecting
                if (!isReconnecting) {
                    startReconnection()
                }
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket connection closed: $code - $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                
                // Start reconnection if not already reconnecting
                if (!isReconnecting) {
                    startReconnection()
                }
            }
        })
    }

    private fun connectToDomainName() {
        Log.d(TAG, "Attempting connection to domain name...")
        _connectionState.value = ConnectionState.CONNECTING
        // Try the domain name first
        connectWebSocket("remote-control.local", 8765)
        
        // If domain fails, fall back to IP after a delay
        viewModelScope.launch {
            delay(2000) // Wait 2 seconds for domain connection
            if (_connectionState.value != ConnectionState.CONNECTED) {
                Log.d(TAG, "Domain connection failed, trying IP fallback...")
                connectWebSocket("192.168.0.8", 8765)
            }
        }
    }

    fun sendMouseMove(x: Int, y: Int, relative: Boolean) {
        val json = JSONObject()
        json.put("type", "mouse_move")
        json.put("x", x)
        json.put("y", y)
        json.put("relative", relative)
        Log.d(TAG, "Sending mouse move: $json")
        send(json)
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

    private fun send(json: JSONObject) {
        viewModelScope.launch(Dispatchers.IO) {
            val message = json.toString()
            Log.d(TAG, "Sending message: $message")
            webSocket?.send(message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel being cleared, closing connections")
        webSocket?.close(1000, null)
        nsdManager?.stopServiceDiscovery(discoveryListener)
    }

    private fun startReconnection() {
        if (isReconnecting || reconnectAttempts >= maxReconnectAttempts) {
            Log.d(TAG, "Reconnection stopped: attempts=$reconnectAttempts, max=$maxReconnectAttempts")
            return
        }
        
        isReconnecting = true
        reconnectAttempts++
        
        viewModelScope.launch {
            _connectionState.value = ConnectionState.RECONNECTING
            
            // Exponential backoff: 2s, 4s, 8s, 16s, 32s
            val delayMs = reconnectDelayMs * (1 shl (reconnectAttempts - 1))
            Log.d(TAG, "Attempting reconnection #$reconnectAttempts in ${delayMs}ms")
            
            delay(delayMs)
            
            // Try different connection strategies
            when (reconnectAttempts) {
                1, 2 -> {
                    // First attempts: try discovered server or domain
                    if (discoveredServer != null) {
                        val host = discoveredServer!!.host.hostAddress
                        val port = discoveredServer!!.port
                        Log.d(TAG, "Reconnecting to discovered server: $host:$port")
                        connectWebSocket(host, port)
                    } else {
                        Log.d(TAG, "Reconnecting to domain name")
                        connectWebSocket("remote-control.local", 8765)
                    }
                }
                3, 4 -> {
                    // Later attempts: try IP fallback
                    Log.d(TAG, "Reconnecting to IP fallback")
                    connectWebSocket("192.168.0.8", 8765)
                }
                5 -> {
                    // Last attempt: restart discovery
                    Log.d(TAG, "Last reconnection attempt: restarting discovery")
                    discoverServices()
                }
            }
            
            // Check if reconnection was successful after a delay
            delay(5000)
            if (_connectionState.value != ConnectionState.CONNECTED) {
                isReconnecting = false
                if (reconnectAttempts < maxReconnectAttempts) {
                    startReconnection() // Try again
                } else {
                    Log.d(TAG, "Max reconnection attempts reached")
                    _connectionState.value = ConnectionState.DISCONNECTED
                }
            }
        }
    }
    
    fun manualReconnect() {
        Log.d(TAG, "Manual reconnection requested")
        reconnectAttempts = 0
        isReconnecting = false
        startReconnection()
    }
} 