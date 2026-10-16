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

import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/**
 * The .neoui.zip spec format (version 1).
 *
 * A .neoui.zip is a plain ZIP containing:
 *   ui.json        (required) — the spec below, as JSON
 *   wallpaper.png  (optional) — wallpaper image, referenced by [UiTheme.wallpaperFile]
 *   any other asset files referenced by name (icons, etc.)
 *
 * Everything in this file is parsed with org.json — no new dependencies.
 */
data class UiSpec(
    val format: String,
    val version: Int,
    val name: String,
    val author: String,
    val theme: UiTheme,
    val grid: UiGrid,
    val homeScreen: List<HomeItem>,
    val dock: List<HomeItem>,
    val controlCenter: ControlCenter,
    val appWheel: AppWheel,
    /** The raw ui.json text, kept so the importer can persist it verbatim. */
    val rawJson: String,
) {
    companion object {
        const val CURRENT_VERSION = 1

        fun parse(rawJson: String): UiSpec {
            val root = JSONObject(rawJson)
            val format = root.optString("format", "")
            if (format != "neoui") {
                throw IllegalArgumentException("Not a neoui spec: format=$format (expected \"neoui\")")
            }
            val version = root.optInt("version", -1)
            if (version != CURRENT_VERSION) {
                throw IllegalArgumentException("Unsupported neoui version: $version (expected $CURRENT_VERSION)")
            }
            val themeJson = root.optJSONObject("theme") ?: JSONObject()
            val gridJson = root.optJSONObject("grid") ?: JSONObject()
            val ccJson = root.optJSONObject("controlCenter") ?: JSONObject()
            val wheelJson = root.optJSONObject("appWheel") ?: JSONObject()
            return UiSpec(
                format = format,
                version = version,
                name = root.optString("name", "Unnamed UI"),
                author = root.optString("author", ""),
                theme = UiTheme(
                    darkMode = themeJson.optBoolean("darkMode", true),
                    accent = themeJson.optString("accent", "#FF2A44"),
                    background = themeJson.optString("background", "#0C0A0D"),
                    surface = themeJson.optString("surface", "#19171B"),
                    hotseatBackground = themeJson.optString("hotseatBackground", "#19171B"),
                    drawerBackground = themeJson.optString("drawerBackground", "#0C0A0D"),
                    colorStyle = themeJson.optString("colorStyle", "tonal_spot"),
                    wallpaperFile = themeJson.optString("wallpaper", ""),
                ),
                grid = UiGrid(
                    columns = gridJson.optInt("columns", 4),
                    rows = gridJson.optInt("rows", 5),
                ),
                homeScreen = parseItems(root.optJSONArray("homeScreen")),
                dock = parseItems(root.optJSONArray("dock")),
                controlCenter = ControlCenter(parseTiles(ccJson.optJSONArray("tiles"))),
                appWheel = AppWheel(parseWheelApps(wheelJson.optJSONArray("apps"))),
                rawJson = rawJson,
            )
        }

        private fun parseItems(array: JSONArray?): List<HomeItem> {
            if (array == null) return emptyList()
            val out = mutableListOf<HomeItem>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                out += HomeItem(
                    type = o.optString("type", "app"),
                    packageName = o.optString("package", ""),
                    label = o.optString("label", ""),
                    className = o.optString("class", ""),
                    x = o.optInt("x", 0),
                    y = o.optInt("y", 0),
                    spanX = o.optInt("spanX", 1),
                    spanY = o.optInt("spanY", 1),
                )
            }
            return out
        }

        private fun parseTiles(array: JSONArray?): List<ControlTile> {
            if (array == null) return emptyList()
            val out = mutableListOf<ControlTile>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                out += ControlTile(
                    icon = o.optString("icon", ""),
                    label = o.optString("label", ""),
                    action = o.optString("action", ""),
                    style = o.optString("style", "plush"),
                )
            }
            return out
        }

        private fun parseWheelApps(array: JSONArray?): List<WheelApp> {
            if (array == null) return emptyList()
            val out = mutableListOf<WheelApp>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                out += WheelApp(
                    packageName = o.optString("package", ""),
                    label = o.optString("label", ""),
                    icon = o.optString("icon", ""),
                )
            }
            return out
        }
    }
}

data class UiTheme(
    val darkMode: Boolean,
    /** Hex color, e.g. "#FF2A44". */
    val accent: String,
    val background: String,
    val surface: String,
    val hotseatBackground: String,
    val drawerBackground: String,
    /** Lawnchair ColorStyle name, e.g. "tonal_spot". */
    val colorStyle: String,
    /** File name inside the .neoui.zip, e.g. "wallpaper.png". Empty = no wallpaper. */
    val wallpaperFile: String,
)

data class UiGrid(
    val columns: Int,
    val rows: Int,
)

/**
 * One home-screen / dock entry.
 * type: "app" (launch by package), "shortcut", "widget" (bind by package/class), "folder".
 * x/y/spanX/spanY are grid coordinates on the home screen page.
 */
data class HomeItem(
    val type: String,
    val packageName: String,
    val label: String,
    val className: String,
    val x: Int,
    val y: Int,
    val spanX: Int,
    val spanY: Int,
)

/** One control-center tile: icon key, label, action key (see UiImporter.ACTION_*), tile style. */
data class ControlTile(
    val icon: String,
    val label: String,
    val action: String,
    val style: String,
)

data class ControlCenter(
    val tiles: List<ControlTile>,
)

/** One app-wheel entry: app to launch from the wheel. */
data class WheelApp(
    val packageName: String,
    val label: String,
    val icon: String,
)

data class AppWheel(
    val apps: List<WheelApp>,
)

/** Parses "#RRGGBB" / "#AARRGGBB". Throws IllegalArgumentException on bad input. */
fun parseHexColor(hex: String): Int = Color.parseColor(hex)
