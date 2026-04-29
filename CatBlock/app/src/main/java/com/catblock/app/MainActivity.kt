package com.catblock.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catblock.app.permissions.PermissionUtils
import com.catblock.app.service.UsageMonitorService
import com.catblock.app.ui.CatBlockViewModel
import com.catblock.app.ui.screens.AppListScreen
import com.catblock.app.ui.screens.OnboardingScreen
import com.catblock.app.ui.screens.SettingsScreen
import com.catblock.app.ui.theme.CatBlockTheme

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CatBlockTheme {
                val ctx = this
                val vm: CatBlockViewModel = viewModel()
                val onboardingDone by vm.onboardingDone.collectAsState()

                // Permissions can be revoked outside the app, so always re-check.
                val permissionsOk = remember(onboardingDone) {
                    PermissionUtils.hasOverlayPermission(ctx) &&
                        PermissionUtils.hasUsageStatsPermission(ctx)
                }

                // If the user has finished onboarding AND has permissions, show main UI.
                // Otherwise show onboarding (guide -> permissions).
                var forceShowGuide by remember { mutableStateOf(false) }
                val showOnboarding = forceShowGuide || !onboardingDone || !permissionsOk

                if (showOnboarding) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
                    ) { padding ->
                        OnboardingScreen(
                            contentPadding = padding,
                            onFinished = {
                                vm.setOnboardingDone(true)
                                forceShowGuide = false
                            }
                        )
                    }
                } else {
                    UsageMonitorService.start(ctx)
                    MainScaffold(
                        onShowGuide = { forceShowGuide = true }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionUtils.hasOverlayPermission(this) &&
            PermissionUtils.hasUsageStatsPermission(this)
        ) {
            UsageMonitorService.start(this)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.compose.runtime.Composable
private fun MainScaffold(onShowGuide: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = {
                Text(
                    if (tab == 0) stringResource(R.string.pick_apps_title)
                    else stringResource(R.string.settings_title)
                )
            })
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.section_all)) }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings_title)) }
                )
            }
        }
    ) { padding ->
        when (tab) {
            0 -> AppListScreen(contentPadding = padding)
            else -> SettingsScreen(contentPadding = padding, onShowGuide = onShowGuide)
        }
    }
}
