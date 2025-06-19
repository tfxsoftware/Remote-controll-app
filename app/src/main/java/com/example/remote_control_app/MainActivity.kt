package com.example.remote_control_app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.remote_control_app.ui.theme.Remote_control_appTheme
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPagerApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Remote_control_appTheme {
                val viewModel: RemoteControlViewModel = viewModel()
                // Initialize mDNS discovery
                LaunchedEffect(Unit) {
                    viewModel.initializeDiscovery(this@MainActivity)
                }
                RemoteControlApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalPagerApi::class)
@Composable
fun RemoteControlApp(viewModel: RemoteControlViewModel) {
    val pagerState = rememberPagerState()
    val coroutineScope = rememberCoroutineScope()
    val tabs = listOf("Mouse", "Keyboard", "System")

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = pagerState.currentPage == index,
                        onClick = { 
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(count = tabs.size, state = pagerState, modifier = Modifier.padding(innerPadding)) { page ->
            when (page) {
                0 -> MouseScreen(viewModel)
                1 -> KeyboardScreen(viewModel)
                2 -> SystemScreen(viewModel)
            }
        }
    }
}