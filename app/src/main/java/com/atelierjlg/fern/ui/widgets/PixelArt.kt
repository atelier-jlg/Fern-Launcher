package com.atelierjlg.fern.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.atelierjlg.fern.widgets.WeatherKind
import kotlin.math.floor

/*
 * Petits dessins en pixel art, comme le fond d'écran.
 * Un dessin = des lignes de texte, un caractère = un pixel (« . » = transparent),
 * et une palette qui dit quelle couleur va avec quel caractère.
 * C'est comme dessiner sur du papier quadrillé : facile à retoucher à la main.
 */

/** Dessine un sprite, centré horizontalement et posé en bas de la zone, pixels bien carrés. */
@Composable
fun PixelSprite(rows: List<String>, palette: Map<Char, Color>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val cols = rows.maxOf { it.length }
        val cell = floor(minOf(size.width / cols, size.height / rows.size))
        if (cell <= 0f) return@Canvas
        val left = (size.width - cell * cols) / 2
        val top = size.height - cell * rows.size
        rows.forEachIndexed { y, line ->
            line.forEachIndexed { x, ch ->
                val color = palette[ch] ?: return@forEachIndexed
                // +0.5 px pour éviter les fines lignes entre les pixels.
                drawRect(color, Offset(left + x * cell, top + y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/** Le chat. b = pelage, s = ombre, p = rose (nez, oreilles), e = yeux, d = yeux fermés, t = queue, z = « z ». */
object CatSprites {
    val sitA = listOf(
        "..b......b....",
        "..bb....bb....",
        "..bpb..bpb....",
        "..bbbbbbbb....",
        ".bbbbbbbbbb...",
        ".bbebbbbebb...",
        ".bbbbppbbbb...",
        "..bbbbbbbb....",
        "...bbbbbb.....",
        "..bbbbbbbb....",
        ".bbbbbbbbbb...",
        ".bbbbbbbbbb.t.",
        ".bbbbbbbbbb.t.",
        ".bbbbbbbbbb.t.",
        ".sbbbbbbbbs.t.",
        ".sbbbbbbbbstt.",
        "..ss.ss.ss....",
    )

    /** Même pose, yeux fermés (clignement) et queue de l'autre côté. */
    val sitB = sitA.mapIndexed { i, line ->
        when (i) {
            5 -> ".bbdbbbbdbb..."
            11, 12 -> line.substring(0, 11) + "..t"
            13 -> line.substring(0, 11) + ".t."
            else -> line
        }
    }

    /** L'étirement du matin : les pattes avant en avant, le dos qui s'allonge. */
    val stretch = listOf(
        "..................",
        "..................",
        "..................",
        "..................",
        "..................",
        "...............t..",
        "..............t...",
        "..........bbbbt...",
        ".b...b..bbbbbbbb..",
        ".bb.bb.bbbbbbbbb..",
        ".bpbpbbbbbbbbbbb..",
        ".bbbbbbbbbbbbbbb..",
        ".bdbbdbbbbbbbbbb..",
        ".bbppbb.....bbbb..",
        "bbbbbbbb.....bb...",
        "ssssssss.....ss...",
        "..................",
    )

    /** Endormi, roulé en boule. */
    val sleep = listOf(
        ".......................",
        ".......................",
        ".......................",
        ".......................",
        ".......................",
        ".......................",
        ".......................",
        "...b...b...............",
        "...bb.bbb..............",
        "..bbbbbbbbbbbbbb.......",
        "..bdbbbdbbbbbbbbbb.....",
        "..bbbppbbbbbbbbbbbb....",
        "...bbbbbbbbbbbbbbbbb...",
        ".ttsbbbbbbbbbbbbbbbbb..",
        "tbbbbbbbbbbbbbbbbbbbs..",
        ".sssssssssssssssssss...",
        ".......................",
    )
}

/** Les icônes du temps (12 × 12). c = nuage, w = reflet, y = soleil, m = lune, r = pluie, n = neige, l = éclair, f = brume. */
object WeatherSprites {
    private val sun = listOf(
        ".....y......",
        ".y...y...y..",
        "..y.....y...",
        "....yyy.....",
        "...yyyyy....",
        "yy.yyyyy.yy.",
        "...yyyyy....",
        "....yyy.....",
        "..y.....y...",
        ".y...y...y..",
        ".....y......",
        "............",
    )
    private val moon = listOf(
        "....mmmm....",
        "..mmmm......",
        ".mmm........",
        ".mmm........",
        "mmm.........",
        "mmm.........",
        "mmm.........",
        "mmmm........",
        ".mmmm.....m.",
        ".mmmmmmmmmm.",
        "...mmmmmmm..",
        "............",
    )
    private val cloud = listOf(
        "............",
        "............",
        "............",
        "....www.....",
        "..wwwwwww...",
        ".wcccccccww.",
        "cccccccccccc",
        "cccccccccccc",
        ".cccccccccc.",
        "............",
        "............",
        "............",
    )
    private fun withRows(base: List<String>, extra: List<String>) = base.take(base.size - extra.size) + extra
    private val empty = "............"
    /** Le nuage remonté, pour laisser 3 lignes de pluie dessous. */
    private val cloudHigh = listOf(empty, empty) + cloud.subList(3, 9) + List(4) { empty }

    /** Le dessin du temps ; `frame` (0/1) fait tomber la pluie ou la neige. */
    fun sprite(kind: WeatherKind, isDay: Boolean, frame: Int): List<String> = when (kind) {
        WeatherKind.Soleil -> if (isDay) sun else moon
        WeatherKind.Eclaircies -> {
            val sky = if (isDay) sun else moon
            // Le soleil (ou la lune) en haut à gauche, un nuage devant en bas à droite.
            sky.indices.map { y ->
                val back = sky[y]
                val front = if (y >= 5) "...." + cloud[y - 2].take(8) else "............"
                back.indices.joinToString("") { x -> if (front[x] != '.') front[x].toString() else back[x].toString() }
            }
        }
        WeatherKind.Nuages -> cloud
        WeatherKind.Brouillard -> listOf(
            "............", "............", "ffffffff....", "............", "..ffffffffff",
            "............", "ffffffffff..", "............", "...fffffffff", "............",
            "fffffff.....", "............",
        )
        WeatherKind.Bruine -> withRows(cloudHigh, if (frame == 0) listOf("..r...r...r.", "............", "....r...r...") else listOf("............", "..r...r...r.", "............"))
        WeatherKind.Pluie -> withRows(cloudHigh, if (frame == 0) listOf(".r..r..r..r.", "r..r..r..r..", "............") else listOf("............", ".r..r..r..r.", "r..r..r..r.."))
        WeatherKind.Neige -> withRows(cloudHigh, if (frame == 0) listOf(".n...n...n..", "............", "...n...n...n") else listOf("...n...n...n", ".n...n...n..", "............"))
        WeatherKind.Orage -> withRows(cloudHigh, if (frame == 0) listOf(".....ll.....", "....ll......", ".....l......") else listOf("....ll......", ".....ll.....", "....l......."))
    }
}
