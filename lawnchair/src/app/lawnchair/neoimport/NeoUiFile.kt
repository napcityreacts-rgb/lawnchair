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

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

/** A loaded .neoui.zip: the parsed spec plus any asset bytes keyed by file name. */
data class NeoUiFile(
    val spec: UiSpec,
    val assets: Map<String, ByteArray>,
) {
    /** Wallpaper bytes, or null when the spec names no wallpaper or the file is absent. */
    val wallpaperBytes: ByteArray?
        get() {
            val name = spec.theme.wallpaperFile
            if (name.isEmpty()) return null
            return assets[name]
        }

    companion object {
        const val SPEC_ENTRY = "ui.json"

        /**
         * Reads a .neoui.zip from a content [Uri] (e.g. from ACTION_OPEN_DOCUMENT).
         * Throws IllegalArgumentException when ui.json is missing or invalid.
         */
        fun read(context: Context, uri: Uri): NeoUiFile {
            val assets = mutableMapOf<String, ByteArray>()
            context.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        if (entry.isDirectory) continue
                        val out = ByteArrayOutputStream()
                        zip.copyTo(out)
                        assets[entry.name] = out.toByteArray()
                    }
                }
            } ?: throw IllegalArgumentException("Could not open file")
            val specBytes = assets[SPEC_ENTRY]
                ?: throw IllegalArgumentException("Missing $SPEC_ENTRY — not a .neoui.zip")
            val spec = UiSpec.parse(String(specBytes, Charsets.UTF_8))
            return NeoUiFile(spec, assets)
        }
    }
}
