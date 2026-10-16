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

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.lawnchair.ui.theme.LawnchairTheme
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import UI — picks a .neoui.zip (a ZIP holding ui.json + assets), shows a
 * preview of the spec, and applies it via [UiImporter].
 *
 * Reached from Settings → Backup & restore → Import UI.
 * The Activity is registered in lawnchair/AndroidManifest.xml (exported=false:
 * it is only launched from inside the launcher).
 */
class ImportUiActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LawnchairTheme {
                ImportUiScreen()
            }
        }
    }
}

private sealed interface ImportState {
    data object Idle : ImportState
    data object Picking : ImportState
    data class Preview(val file: NeoUiFile) : ImportState
    data object Applying : ImportState
    data class Done(val result: UiImporter.ImportResult) : ImportState
    data class Error(val message: String) : ImportState
}

@Composable
private fun ImportUiScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ImportState>(ImportState.Idle) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            state = ImportState.Idle
            return@rememberLauncherForActivityResult
        }
        val uri: Uri = result.data?.data ?: run {
            state = ImportState.Idle
            return@rememberLauncherForActivityResult
        }
        state = ImportState.Picking
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { NeoUiFile.read(context, uri) }
            }
            state = loaded.fold(
                onSuccess = { ImportState.Preview(it) },
                onFailure = { ImportState.Error(it.message ?: context.getString(R.string.import_ui_error)) },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.import_ui),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.import_ui_description),
            style = MaterialTheme.typography.bodyMedium,
        )

        when (val s = state) {
            is ImportState.Idle -> {
                Button(onClick = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/zip")
                        .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/zip", "application/octet-stream"))
                    picker.launch(intent)
                }) {
                    Text(stringResource(R.string.import_ui_pick))
                }
            }
            is ImportState.Picking -> {
                CircularProgressIndicator()
            }
            is ImportState.Preview -> {
                val spec = s.file.spec
                Text(stringResource(R.string.import_ui_preview_name, spec.name))
                Text(stringResource(R.string.import_ui_preview_author, spec.author))
                Text(stringResource(R.string.import_ui_preview_theme, spec.theme.accent, spec.theme.background))
                Text(stringResource(R.string.import_ui_preview_counts, spec.homeScreen.size, spec.controlCenter.tiles.size, spec.appWheel.apps.size))
                Button(onClick = {
                    state = ImportState.Applying
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { UiImporter.apply(context, s.file) }
                        }
                        result.fold(
                            onSuccess = {
                                state = ImportState.Done(it)
                                Toast.makeText(context, R.string.import_ui_success, Toast.LENGTH_LONG).show()
                            },
                            onFailure = {
                                state = ImportState.Error(it.message ?: context.getString(R.string.import_ui_error))
                            },
                        )
                    }
                }) {
                    Text(stringResource(R.string.import_ui_apply))
                }
                OutlinedButton(onClick = { state = ImportState.Idle }) {
                    Text(stringResource(R.string.import_ui_pick_different))
                }
            }
            is ImportState.Applying -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.import_ui_applying))
            }
            is ImportState.Done -> {
                Text(stringResource(R.string.import_ui_success), style = MaterialTheme.typography.titleMedium)
                if (s.result.applied.isNotEmpty()) {
                    Text(stringResource(R.string.import_ui_applied))
                    s.result.applied.forEach { Text("✓ $it", style = MaterialTheme.typography.bodySmall) }
                }
                if (s.result.pending.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.import_ui_pending))
                    s.result.pending.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
                OutlinedButton(onClick = { state = ImportState.Idle }) {
                    Text(stringResource(R.string.import_ui_pick_different))
                }
            }
            is ImportState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { state = ImportState.Idle }) {
                    Text(stringResource(R.string.import_ui_try_again))
                }
            }
        }
    }
}
