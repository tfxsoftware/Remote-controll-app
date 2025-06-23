package com.example.remote_control_app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MouseScreen(viewModel: RemoteControlViewModel) {
    var text by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
            .padding(16.dp)
    ) {
        // Connection status indicator
        ConnectionStatusIndicator(viewModel.connectionState.value, viewModel::manualReconnect)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Touchpad area
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFFE3F2FD),
                                Color(0xFFBBDEFB)
                            )
                        )
                    )
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            val (dx, dy) = dragAmount
                            viewModel.sendMouseMove(dx.toInt(), dy.toInt(), relative = true)
                            change.consume()
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // Send left click on tap
                                viewModel.sendMouseClick("left")
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Touchpad",
                        modifier = Modifier.size(48.dp),
                        tint = Color(0xFF1976D2)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Touchpad Area",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1976D2)
                    )
                    Text(
                        text = "Drag to move mouse • Tap to click",
                        fontSize = 14.sp,
                        color = Color(0xFF757575)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Scroll wheel area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Scroll up button
            ElevatedButton(
                onClick = { viewModel.sendMouseScroll(-120) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = Color(0xFF2196F3)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Scroll Up",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            
            // Scroll down button
            ElevatedButton(
                onClick = { viewModel.sendMouseScroll(120) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = Color(0xFF2196F3)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Scroll Down",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Right click button (directly under touchpad)
        ElevatedButton(
            onClick = { viewModel.sendMouseClick("right") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.elevatedButtonColors(
                containerColor = Color(0xFFFF4081)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                "Right Click",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Keyboard input area with backspace button
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Text input field
                OutlinedTextField(
                    value = text,
                    onValueChange = { newText ->
                        // Send each new character individually and clear the field
                        if (newText.length > text.length) {
                            val newChar = newText.last()
                            viewModel.sendKeyType(newChar.toString())
                            // Clear the text immediately to simulate real keyboard behavior
                            text = ""
                        } else {
                            text = newText
                        }
                    },
                    label = { 
                        Text(
                            "Type here - keystrokes sent directly to PC",
                            color = Color(0xFF757575)
                        ) 
                    },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF2196F3),
                        unfocusedBorderColor = Color(0xFFE0E0E0),
                        focusedLabelColor = Color(0xFF2196F3)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 1,
                    maxLines = 2
                )
                
                // Backspace button
                ElevatedButton(
                    onClick = { viewModel.sendSpecialKey("backspace") },
                    modifier = Modifier.height(56.dp),
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = Color(0xFFFF5722)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "⌫",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionStatusIndicator(
    connectionState: ConnectionState,
    onManualReconnect: () -> Unit
) {
    val backgroundColor = when (connectionState) {
        ConnectionState.CONNECTED -> Color(0xFF4CAF50)
        ConnectionState.CONNECTING -> Color(0xFFFFC107)
        ConnectionState.RECONNECTING -> Color(0xFFFF9800)
        ConnectionState.DISCONNECTED -> Color(0xFFF44336)
    }
    
    val text = when (connectionState) {
        ConnectionState.CONNECTED -> "Connected"
        ConnectionState.CONNECTING -> "Connecting..."
        ConnectionState.RECONNECTING -> "Reconnecting..."
        ConnectionState.DISCONNECTED -> "Disconnected"
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(backgroundColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = text,
                    color = backgroundColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp
                )
            }
            
            if (connectionState == ConnectionState.DISCONNECTED) {
                TextButton(
                    onClick = onManualReconnect,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = backgroundColor
                    )
                ) {
                    Text("Reconnect", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
} 