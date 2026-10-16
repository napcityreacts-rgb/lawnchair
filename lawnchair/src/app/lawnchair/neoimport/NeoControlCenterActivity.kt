/*
 * Copyright 2026, NeoLauncher (Lawnchair fork)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.neoimport

import android.Manifest
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.neoimport.widgets.NeoControlCenterWidget
import app.lawnchair.neoimport.widgets.NeoWidgetRenderer
import app.lawnchair.neoimport.widgets.NeoWidgetState
import app.lawnchair.neoimport.widgets.NeoWidgetUpdater
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R
import kotlinx.coroutines.delay

/**
 * NEO-TACTILE Control Center overlay.
 *
 * Tiles come from the last imported .neoui.zip spec when one exists, otherwise
 * the built-in NEO-TACTILE defaults (same source as [NeoControlCenterWidget]).
 * Every tile maps to a real action (see [NeoWidgetState.intentForAction]);
 * WiFi/Bluetooth/airplane/rotation open their system panels because Android
 * does not let apps flip them directly. The torch requests the CAMERA
 * permission when needed.
 *
 * Reached from Settings → Backup & restore → Open Control Center, and from
 * the Control Center widget header.
 *
 * TODO (marked, not faked): swipe-down gesture hook to open this overlay from
 * anywhere on the home screen. Today it only opens from Settings / the widget.
 */
class NeoControlCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LawnchairTheme {
                NeoControlCenterScreen(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun NeoControlCenterScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current

    // tile list: imported spec wins, built-in defaults otherwise
    val tiles = remember { NeoControlCenterWidget.tilesFor(context) }.flatten()
    val specName = remember {
        UiImporter.readImportedSpec(context)?.name
            ?: context.getString(R.string.neo_cc_builtin)
    }

    var toggle by remember { mutableStateOf(NeoWidgetState.toggleState(context)) }
    // re-render the header clock every 30s while the overlay is open
    var timeTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            timeTick++
        }
    }

    var pendingTorchAfterPermission by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && pendingTorchAfterPermission) {
            NeoWidgetState.toggleFlashlight(context)
            toggle = NeoWidgetState.toggleState(context)
            NeoWidgetUpdater.updateAll(context)
        } else if (!granted) {
            Toast.makeText(context, R.string.neo_cc_camera_permission_needed, Toast.LENGTH_SHORT).show()
        }
        pendingTorchAfterPermission = false
    }

    fun performAction(action: String) {
        if (action == "flashlight") {
            if (!NeoWidgetState.hasCameraPermission(context)) {
                pendingTorchAfterPermission = true
                permissionLauncher.launch(Manifest.permission.CAMERA)
                return
            }
            NeoWidgetState.toggleFlashlight(context)
            toggle = NeoWidgetState.toggleState(context)
            NeoWidgetUpdater.updateAll(context)
            return
        }
        val intent = NeoWidgetState.intentForAction(action) ?: return
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // no app handles it: do nothing rather than crash
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.neo_control_center),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = specName,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(R.string.neo_cc_gesture_todo),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onClose) {
                Text(stringResource(R.string.neo_cc_close))
            }
        }
        Spacer(Modifier.height(12.dp))

        // header clock bitmap (re-rendered on the 30s tick)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val headerW = with(density) { maxWidth.toPx() }.toInt().coerceAtLeast(100)
            val headerH = (headerW * 0.25f).toInt().coerceAtLeast(40)
            val headerBmp = remember(headerW, headerH, timeTick) {
                NeoWidgetRenderer.drawNeoHeader(headerW, headerH)
            }
            Image(
                bitmap = headerBmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(4f),
            )
        }
        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
            val gap = 8.dp
            val tileDp = (maxWidth - gap * 3) / 4
            val tilePx = with(density) { tileDp.toPx() }.toInt().coerceAtLeast(40)
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
            ) {
                items(tiles) { tile ->
                    val on = NeoWidgetState.isOnForAction(context, tile.action, toggle)
                    val bmp = remember(tile, on, tilePx) {
                        NeoWidgetRenderer.drawNeoTile(tilePx, tile.icon, tile.label, on, tile.style)
                    }
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = tile.label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clickable { performAction(tile.action) },
                    )
                }
            }
        }
    }
}
