package com.atelierjlg.fern.apps

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Un pack d'icônes installé (Renkin, Arcticons…). */
data class IconPackInfo(val packageName: String, val label: String)

/**
 * Un pack d'icônes chargé : la table « appli → nom de l'image » lue dans son `appfilter.xml`
 * (le format standard créé par ADW Launcher, utilisé par presque tous les packs).
 */
class IconPack(
    private val resources: Resources,
    private val packageName: String,
    /** « paquet/Activité » → nom de l'image dans le pack. */
    private val byComponent: Map<String, String>,
    /** « paquet » → nom de l'image (secours si l'activité a changé). */
    private val byPackage: Map<String, String>,
) {
    @SuppressLint("DiscouragedApi")
    fun drawableFor(component: ComponentName): Drawable? {
        val name = byComponent[component.flattenToString()]
            ?: byComponent["${component.packageName}/${component.className}"]
            ?: byPackage[component.packageName]
            ?: return null
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return runCatching { resources.getDrawable(id, null) }.getOrNull()
    }
}

/** Trouve et charge les packs d'icônes installés. */
class IconPackManager(private val context: Context) {

    private val cache = mutableMapOf<String, IconPack?>()

    /** Les packs installés : ils se déclarent avec l'une de ces actions (convention des lanceurs). */
    fun installedPacks(): List<IconPackInfo> {
        val pm = context.packageManager
        val actions = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.teslacoilsw.launcher.THEME",
        )
        return actions
            .flatMap { action -> runCatching { pm.queryIntentActivities(Intent(action), 0) }.getOrDefault(emptyList()) }
            .map { it.activityInfo.packageName }
            .distinct()
            .map { pkg ->
                val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
                IconPackInfo(pkg, label)
            }
            .sortedBy { it.label.lowercase() }
    }

    /** Le pack Arcticons installé, s'il y en a un (source des pictos des plaques). */
    fun findArcticons(): String? =
        installedPacks().map { it.packageName }.firstOrNull { it.contains("arcticons", ignoreCase = true) }

    @Synchronized
    fun load(packageName: String): IconPack? = cache.getOrPut(packageName) { parse(packageName) }

    @Synchronized
    fun clearCache() = cache.clear()

    @SuppressLint("DiscouragedApi")
    private fun parse(packageName: String): IconPack? = try {
        val res = context.packageManager.getResourcesForApplication(packageName)
        val parser: XmlPullParser = run {
            val xmlId = res.getIdentifier("appfilter", "xml", packageName)
            if (xmlId != 0) {
                res.getXml(xmlId)
            } else {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("appfilter.xml"), "UTF-8")
                }
            }
        }
        val byComponent = HashMap<String, String>()
        val byPackage = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                // Format : ComponentInfo{paquet/Activité}
                val inner = component?.substringAfter('{', "")?.substringBefore('}', "")
                if (!inner.isNullOrEmpty() && !drawable.isNullOrEmpty() && '/' in inner) {
                    val pkg = inner.substringBefore('/')
                    val cls = inner.substringAfter('/').let { if (it.startsWith(".")) pkg + it else it }
                    byComponent["$pkg/$cls"] = drawable
                    byPackage.putIfAbsent(pkg, drawable)
                }
            }
            event = parser.next()
        }
        IconPack(res, packageName, byComponent, byPackage)
    } catch (e: Exception) {
        Log.w("FernIcons", "Pack illisible : $packageName", e)
        null
    }
}
