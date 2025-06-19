package com.example.remote_control_app

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject

class RemoteControlViewModel : ViewModel() {
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private var nsdManager: NsdManager? = null
    private var discoveredServer: NsdServiceInfo? = null
    
    private val SERVICE_TYPE = "_remote-control._tcp."
    private val SERVICE_NAME = "remote-tv-server"
    
    companion object {
        private const val TAG = "RemoteControlViewModel"
    }

    fun initializeDiscovery(context: Context) {
        Log.d(TAG, "Initializing mDNS discovery...")
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        discoverServices()
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
        
        val request = Request.Builder()
            .url(url)
            .build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connection opened successfully!")
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received message: $text")
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket connection failed: ${t.message}")
                Log.e(TAG, "Response: ${response?.message}")
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket connection closed: $code - $reason")
            }
        })
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
} 