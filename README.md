# Tomas Control - Android Client

A modern Android app that transforms your phone into a powerful remote control for your computer. Built with Jetpack Compose, the app features a beautiful Material Design 3 interface and automatically discovers and connects to Tomas Control servers on your local network.

## 🚀 Features

### **Remote Control Capabilities**
- **Touchpad Control**: Drag to move mouse with precision
- **Tap-to-Click**: Tap anywhere on touchpad for left click
- **Mouse Buttons**: Right click button for context menus
- **Keyboard Input**: Full text typing with special key support
- **Special Keys**: Backspace, Enter, Tab, Escape, Function keys
- **Real-time Response**: Immediate command execution

### **Smart Connection Management**
- **Automatic Discovery**: Finds servers using mDNS (no manual IP needed)
- **Connection Resilience**: Automatic reconnection with exponential backoff
- **Visual Status**: Real-time connection status with color-coded indicators
- **Manual Reconnect**: One-tap reconnection when disconnected

### **Modern UI/UX**
- **Material Design 3**: Beautiful, modern interface
- **Dark/Light Theme**: Adapts to system theme
- **Responsive Design**: Works on phones and tablets
- **Intuitive Controls**: Easy-to-use touchpad and keyboard interfaces

### **Network Features**
- **Zero Configuration**: No manual IP setup required
- **Local Network**: Secure communication within your network
- **Cross-Platform**: Works with Windows, macOS, and Linux servers

## 📱 Screenshots

### **Mouse Control Screen**
- **Touchpad Area**: Large, responsive touchpad with gradient background
- **Tap-to-Click**: Tap anywhere on touchpad for left click
- **Drag Movement**: Drag to move mouse cursor with precision
- **Right Click Button**: Full-width button for context menus
- **Connection Status**: Real-time status indicator with reconnect button
- **Visual Feedback**: Icons and colors for clear interaction

### **Keyboard Screen**
- **Text Input**: Multi-line text field for typing
- **Backspace Button**: Prominent backspace button for easy deletion
- **Quick Actions**: Enter, Tab, and Escape buttons
- **Send Button**: Large send button with icon

## 🏗️ Architecture

```
┌─────────────────┐    WebSocket    ┌─────────────────┐
│   Tomas Control │ ◄─────────────► │  Tomas Control  │
│   Android App   │                 │    Server       │
└─────────────────┘                 └─────────────────┘
         │                                    │
         ▼                                    ▼
┌─────────────────┐                 ┌─────────────────┐
│   mDNS Client   │                 │   mDNS Service  │
│   (Discovery)   │                 │  (Registration) │
└─────────────────┘                 └─────────────────┘
```

## 📦 Installation

### **Prerequisites**
- Android Studio Arctic Fox or later
- Android SDK 21+
- Kotlin 1.5+
- Java 11+

### **Build Requirements**
```gradle
android {
    compileSdk 34
    minSdk 21
    targetSdk 34
}
```

### **Dependencies**
```gradle
dependencies {
    // Jetpack Compose
    implementation "androidx.compose.ui:ui:1.5.0"
    implementation "androidx.compose.material3:material3:1.1.0"
    implementation "androidx.compose.ui:ui-tooling-preview:1.5.0"
    
    // WebSocket
    implementation "com.squareup.okhttp3:okhttp:4.11.0"
    
    // ViewModel
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2"
    
    // Coroutines
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1"
}
```

## 🚀 Quick Start

### **1. Build the App**
```bash
cd client
./gradlew assembleDebug
```

### **2. Install on Device**
```bash
# Via ADB
adb install app/build/outputs/apk/debug/app-debug.apk

# Or transfer APK manually to device
```

### **3. Start Server**
```bash
cd ../server
python run.py
```

### **4. Connect and Control**
- Open "Tomas Control" app on your phone
- App automatically discovers the server
- Tap to connect
- Start controlling your PC!

## ⚙️ Configuration

### **Network Security**
The app includes network security configuration for local development:

```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">127.0.0.1</domain>
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">192.168.0.0/24</domain>
    </domain-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">local</domain>
    </domain-config>
</network-security-config>
```

### **Connection Settings**
```kotlin
// RemoteControlViewModel.kt
private val SERVICE_TYPE = "_remote-control._tcp."
private val SERVICE_NAME = "remote-control"
private val maxReconnectAttempts = 5
private val reconnectDelayMs = 2000L
```

## 📡 API Integration

### **WebSocket Connection**
```kotlin
// Connect to server
val url = "ws://$host:$port"
webSocket = client.newWebSocket(request, object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        // Connection established
    }
    
    override fun onMessage(webSocket: WebSocket, text: String) {
        // Handle server messages
    }
    
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        // Handle connection failures
    }
})
```

### **Command Sending**
```kotlin
// Send mouse movement
viewModel.sendMouseMove(x, y, relative = true)

// Send mouse click
viewModel.sendMouseClick("left")

// Send text
viewModel.sendKeyType("Hello World!")

// Send special key
viewModel.sendSpecialKey("backspace")
```

## 🔧 Development

### **Project Structure**
```
client/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/remote_control_app/
│   │   │   ├── MainActivity.kt              # Main activity
│   │   │   ├── RemoteControlViewModel.kt    # ViewModel with business logic
│   │   │   ├── MouseScreen.kt              # Mouse control UI
│   │   │   └── KeyboardScreen.kt           # Keyboard control UI
│   │   ├── res/
│   │   │   ├── values/
│   │   │   │   ├── strings.xml             # App strings
│   │   │   │   └── themes.xml              # App theme
│   │   │   └── xml/
│   │   │       └── network_security_config.xml  # Network security
│   │   └── AndroidManifest.xml             # App manifest
│   └── build.gradle.kts                    # App build config
├── build.gradle.kts                        # Project build config
└── settings.gradle.kts                     # Project settings
```

### **Key Components**

#### **RemoteControlViewModel**
- **Connection Management**: WebSocket connection handling
- **mDNS Discovery**: Automatic server discovery
- **Command Sending**: Mouse and keyboard commands
- **State Management**: Connection status and UI state

#### **MouseScreen**
- **Touchpad**: Drag gesture handling for mouse movement
- **Tap-to-Click**: Tap gesture handling for left click
- **Right Click Button**: Full-width button for right click
- **Connection Status**: Visual connection indicator

#### **KeyboardScreen**
- **Text Input**: Multi-line text field
- **Backspace Button**: Prominent backspace button for easy deletion
- **Quick Actions**: Enter, Tab, and Escape buttons
- **Send Functionality**: Text sending to server

### **Connection States**
```kotlin
enum class ConnectionState {
    DISCONNECTED,    // Not connected
    CONNECTING,      // Attempting to connect
    CONNECTED,       // Successfully connected
    RECONNECTING     // Attempting to reconnect
}
```

## 🎨 UI/UX Design

### **Color Scheme**
- **Primary**: `#2196F3` (Blue)
- **Success**: `#4CAF50` (Green)
- **Warning**: `#FF9800` (Orange)
- **Error**: `#F44336` (Red)
- **Background**: `#FAFAFA` (Light Gray)

### **Material Design 3**
- **Elevated Cards**: For touchpad and input areas
- **Rounded Corners**: 12dp radius for modern look
- **Elevated Buttons**: With shadows and hover effects
- **Icons**: Material Design icons throughout

### **Responsive Layout**
- **Adaptive**: Works on different screen sizes
- **Orientation**: Supports portrait and landscape
- **Tablet**: Optimized for larger screens

## 🔒 Security

### **Network Security**
- **Local Network Only**: Designed for trusted networks
- **Cleartext Traffic**: Allowed for local development
- **mDNS Discovery**: Automatic service discovery

### **Permissions**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
```

## 🐛 Troubleshooting

### **Common Issues**

#### **App Can't Find Server**
- Ensure both devices are on same WiFi network
- Check if server is running (`python run.py`)
- Verify mDNS is enabled on network
- Try manual IP connection as fallback

#### **Connection Fails**
- Check network security configuration
- Verify firewall settings
- Ensure server port (8765) is accessible
- Check server logs for errors

#### **Commands Not Working**
- Verify WebSocket connection is established
- Check server logs for command errors
- Ensure proper command format
- Test with server's test client

### **Debug Mode**
```kotlin
// Enable debug logging
Log.d(TAG, "Debug message")
```

## 📄 License

This project is part of Tomas Control - a remote control solution for local networks.

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Test on different devices
5. Submit a pull request

## 📱 Download

The latest APK can be found in:
```
client/app/build/outputs/apk/debug/app-debug.apk
```

---

**Tomas Control Android Client** - Transform your phone into a powerful remote control! 📱✨ 