package com.catblock.app.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catblock.app.R
import com.catblock.app.data.AppRule
import com.catblock.app.data.InstalledApp
import com.catblock.app.ui.CatBlockViewModel

@Composable
fun AppListScreen(contentPadding: PaddingValues) {
    val vm: CatBlockViewModel = viewModel()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val rulesByPkg = remember(rules) { rules.associateBy { it.packageName } }

    LazyColumn(
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
            start = 16.dp,
            end = 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Header() }

        val suggestions = apps.filter { it.isSocialSuggestion }
        val rest = apps.filter { !it.isSocialSuggestion }

        if (suggestions.isNotEmpty()) {
            item {
                SectionTitle(androidx.compose.ui.res.stringResource(R.string.section_social))
            }
            items(suggestions, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    rule = rulesByPkg[app.packageName],
                    onToggle = { enabled ->
                        if (enabled) vm.upsert(AppRule(app.packageName, 15, 2))
                        else vm.remove(app.packageName)
                    },
                    onChangeTrigger = { delta ->
                        rulesByPkg[app.packageName]?.let {
                            vm.upsert(it.copy(triggerMinutes = (it.triggerMinutes + delta).coerceIn(1, 240)))
                        }
                    },
                    onChangeBreak = { delta ->
                        rulesByPkg[app.packageName]?.let {
                            vm.upsert(it.copy(breakMinutes = (it.breakMinutes + delta).coerceIn(1, 60)))
                        }
                    }
                )
            }
            item {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }

        item { SectionTitle(androidx.compose.ui.res.stringResource(R.string.section_all)) }

        items(rest, key = { it.packageName }) { app ->
            AppRow(
                app = app,
                rule = rulesByPkg[app.packageName],
                onToggle = { enabled ->
                    if (enabled) vm.upsert(AppRule(app.packageName, 15, 2))
                    else vm.remove(app.packageName)
                },
                onChangeTrigger = { delta ->
                    rulesByPkg[app.packageName]?.let {
                        vm.upsert(it.copy(triggerMinutes = (it.triggerMinutes + delta).coerceIn(1, 240)))
                    }
                },
                onChangeBreak = { delta ->
                    rulesByPkg[app.packageName]?.let {
                        vm.upsert(it.copy(breakMinutes = (it.breakMinutes + delta).coerceIn(1, 60)))
                    }
                }
            )
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(
            painter = painterResource(R.drawable.cat_07),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                androidx.compose.ui.res.stringResource(R.string.pick_apps_title),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                androidx.compose.ui.res.stringResource(R.string.pick_apps_subtitle),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun AppRow(
    app: InstalledApp,
    rule: AppRule?,
    onToggle: (Boolean) -> Unit,
    onChangeTrigger: (Int) -> Unit,
    onChangeBreak: (Int) -> Unit
) {
    val iconPainter: Painter = remember(app.packageName) {
        app.icon?.toComposePainter() ?: ColorBlockPainter
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    Image(
                        painter = iconPainter,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).padding(4.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        app.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        app.packageName,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
                Switch(
                    checked = rule != null,
                    onCheckedChange = onToggle
                )
            }

            if (rule != null) {
                Spacer(Modifier.height(10.dp))
                Stepper(
                    label = androidx.compose.ui.res.stringResource(R.string.cat_appears_after),
                    value = androidx.compose.ui.res.stringResource(R.string.minutes_short, rule.triggerMinutes),
                    onMinus = { onChangeTrigger(-1) },
                    onPlus = { onChangeTrigger(+1) }
                )
                Spacer(Modifier.height(6.dp))
                Stepper(
                    label = androidx.compose.ui.res.stringResource(R.string.break_lasts),
                    value = androidx.compose.ui.res.stringResource(R.string.minutes_short, rule.breakMinutes),
                    onMinus = { onChangeBreak(-1) },
                    onPlus = { onChangeBreak(+1) }
                )
            }
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMinus) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease")
        }
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(64.dp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        IconButton(onClick = onPlus) {
            Icon(Icons.Default.Add, contentDescription = "Increase")
        }
    }
}

private fun Drawable.toComposePainter(): Painter {
    val bmp = this.toBitmap(72, 72)
    return BitmapPainter(bmp.asImageBitmap())
}

private val ColorBlockPainter: Painter = run {
    val bmp = android.graphics.Bitmap.createBitmap(40, 40, android.graphics.Bitmap.Config.ARGB_8888)
    bmp.eraseColor(android.graphics.Color.parseColor("#CCCCCC"))
    BitmapPainter(bmp.asImageBitmap())
}
