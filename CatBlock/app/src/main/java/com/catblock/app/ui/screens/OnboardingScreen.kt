package com.catblock.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catblock.app.R
import com.catblock.app.permissions.PermissionUtils
import com.catblock.app.service.UsageMonitorService

/**
 * Two-phase onboarding:
 *  Phase 1 — 5-step swipeable visual guide
 *  Phase 2 — permission grants
 */
@Composable
fun OnboardingScreen(
    contentPadding: PaddingValues,
    onFinished: () -> Unit
) {
    var phase by remember { mutableIntStateOf(0) } // 0 = guide, 1 = perms

    when (phase) {
        0 -> GuidePages(
            contentPadding = contentPadding,
            onDone = { phase = 1 }
        )
        else -> PermissionPage(
            contentPadding = contentPadding,
            onFinished = onFinished
        )
    }
}

private data class GuidePage(val titleRes: Int, val textRes: Int, val catIndex: Int)

@Composable
private fun GuidePages(
    contentPadding: PaddingValues,
    onDone: () -> Unit
) {
    val pages = remember {
        listOf(
            GuidePage(R.string.how_step1_title, R.string.how_step1_text, catIndex = 7),
            GuidePage(R.string.how_step2_title, R.string.how_step2_text, catIndex = 14),
            GuidePage(R.string.how_step3_title, R.string.how_step3_text, catIndex = 21),
            GuidePage(R.string.how_step4_title, R.string.how_step4_text, catIndex = 33),
            GuidePage(R.string.how_step5_title, R.string.how_step5_text, catIndex = 42)
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val catResId = remember(index) {
        context.resources.getIdentifier(
            "cat_%02d".format(pages[index].catIndex),
            "drawable",
            context.packageName
        ).takeIf { it != 0 } ?: R.drawable.ic_cat
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Skip
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) {
                Text(stringResource(R.string.skip))
            }
        }

        Spacer(Modifier.height(8.dp))

        AnimatedContent(
            targetState = index,
            transitionSpec = {
                (slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(tween(300)))
                    .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut(tween(300)))
            },
            label = "guide-page",
            modifier = Modifier.weight(1f)
        ) { i ->
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(catResId),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(220.dp)
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(pages[i].titleRes),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(pages[i].textRes),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.78f),
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        // Dots
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pages.indices.forEach { i ->
                Box(
                    modifier = Modifier
                        .size(if (i == index) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            if (i == index) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f)
                        )
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(
                onClick = { if (index > 0) index-- },
                enabled = index > 0,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.previous_step))
            }

            Button(
                onClick = {
                    if (index < pages.lastIndex) index++ else onDone()
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (index < pages.lastIndex) stringResource(R.string.next_step)
                    else stringResource(R.string.continue_button)
                )
            }
        }
    }
}

@Composable
private fun PermissionPage(
    contentPadding: PaddingValues,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }
    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { refreshTick++ }

    val hasOverlay = remember(refreshTick) { PermissionUtils.hasOverlayPermission(context) }
    val hasUsage = remember(refreshTick) { PermissionUtils.hasUsageStatsPermission(context) }
    val ready = hasOverlay && hasUsage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.cat_07),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(160.dp)
        )

        Text(
            stringResource(R.string.app_name),
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            stringResource(R.string.onboarding_subtitle),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        PermissionRow(
            title = stringResource(R.string.perm_overlay_title),
            subtitle = stringResource(R.string.perm_overlay_subtitle),
            granted = hasOverlay,
            onClick = { overlayLauncher.launch(PermissionUtils.overlaySettingsIntent(context)) }
        )
        Spacer(Modifier.height(12.dp))
        PermissionRow(
            title = stringResource(R.string.perm_usage_title),
            subtitle = stringResource(R.string.perm_usage_subtitle),
            granted = hasUsage,
            onClick = { usageLauncher.launch(PermissionUtils.usageSettingsIntent()) }
        )

        Spacer(Modifier.weight(1f))

        Button(
            onClick = {
                if (ready) {
                    UsageMonitorService.start(context)
                    onFinished()
                }
            },
            enabled = ready,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Text(stringResource(R.string.continue_button), fontSize = 16.sp)
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
                Spacer(Modifier.width(10.dp))
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(
                subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 6.dp, start = 34.dp)
            )
        }
    }
}
