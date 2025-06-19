package com.example.remote_control_app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
teating
@Composable
fun MouseScreen(viewModel: RemoteControlViewModel) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.LightGray)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        val (dx, dy) = dragAmount
                        viewModel.sendMouseMove(dx.toInt(), dy.toInt(), relative = true)
                        change.consume()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text("Touchpad Area (Drag to move mouse)")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { viewModel.sendMouseClick("left") }) { Text("Left Click") }
            Button(onClick = { viewModel.sendMouseClick("right") }) { Text("Right Click") }
        }
    }
} 