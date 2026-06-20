package online.fujinet.go.coco

import android.content.Context
import android.content.res.AssetManager
import java.io.File

/**
 * Stages the bundled runtime trees from APK assets into writable directories:
 *
 *   assets/fujinet/{fnconfig.ini, data, SD}  (tools/fujinet/build-fujinet.sh)
 *     -> filesDir/fujinet   (the FujiNet runtime can chdir into and mutate this)
 *   assets/xroar/roms       (tools/xroar/build-xroar-core.sh)
 *     -> filesDir/xroar/roms (XRoar's -rompath: CoCo system + HDB-DOS Becker ROMs)
 */
class RuntimeInstaller(private val context: Context) {

    data class Paths(
        val runtimeRoot: String,
        val configPath: String,
        val sdPath: String,
        val dataPath: String,
        val romPath: String,
    )

    fun install(force: Boolean = false): Paths {
        val fujinetRoot = File(context.filesDir, "fujinet")
        if (force || !File(fujinetRoot, "fnconfig.ini").exists()) {
            copyAssetDir("fujinet", fujinetRoot)
        }

        val xroarRoot = File(context.filesDir, "xroar")
        val romDir = File(xroarRoot, "roms")
        if (force || !romDir.exists() || (romDir.list()?.isEmpty() != false)) {
            copyAssetDir("xroar/roms", romDir)
        }

        return Paths(
            runtimeRoot = fujinetRoot.absolutePath,
            configPath = File(fujinetRoot, "fnconfig.ini").absolutePath,
            sdPath = File(fujinetRoot, "SD").absolutePath,
            dataPath = File(fujinetRoot, "data").absolutePath,
            romPath = romDir.absolutePath,
        )
    }

    private fun copyAssetDir(assetPath: String, dest: File) {
        val assets: AssetManager = context.assets
        val entries = assets.list(assetPath) ?: emptyArray()
        if (entries.isEmpty()) {
            // It's a file, not a directory.
            dest.parentFile?.mkdirs()
            assets.open(assetPath).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            return
        }
        dest.mkdirs()
        for (entry in entries) {
            copyAssetDir("$assetPath/$entry", File(dest, entry))
        }
    }
}
