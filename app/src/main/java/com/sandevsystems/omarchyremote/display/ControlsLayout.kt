package com.sandevsystems.omarchyremote.display

import com.sandevsystems.omarchyremote.ui.tr

/**
 * Ver PC's controls, arranged by the person (docs/UX-VER-PC.md): which tool buttons show, their size,
 * what they do when nothing is touched, and where each group sits — the tools group and the Digitar
 * button, each placed by its center as a fraction of the screen so it survives other sizes.
 */
data class ControlsLayout(
    val tools: Set<Tool>,
    val size: Size,
    val idle: Idle,
    val toolsAt: Pair<Float, Float>,
    val typeAt: Pair<Float, Float>,
) {
    /** The tools that can sit in the group ("Mais" is always there: it is the way back to this). */
    enum class Tool(private val pt: String, private val en: String) {
        RIGHT("Direito", "Right click"), HOLD("Segurar", "Hold"), LOUPE("Lupa", "Magnifier"), SCREENS("Telas", "Screens"),
        TEXT("Ler texto", "Read text"), PRINT("Print", "Screenshot"), PRESENT("Apresentar", "Present"),
        COPY("Copiar", "Copy"), PASTE("Colar", "Paste");

        val label: String get() = tr(pt, en)
    }

    enum class Size(private val pt: String, private val en: String, val dp: Int) {
        SMALL("P", "S", 40), MEDIUM("M", "M", 46), LARGE("G", "L", 54);

        val label: String get() = tr(pt, en)
    }

    /** Nothing touched for a moment: the controls disappear (a tap anywhere near brings them back), fade, or stay. */
    enum class Idle(private val pt: String, private val en: String) {
        HIDE("Some", "Hide"), FADE("Esmaece", "Fade"), STAY("Fica", "Stay");

        val label: String get() = tr(pt, en)
    }

    fun encode(): String = listOf(
        tools.joinToString(",") { it.name }, size.name, idle.name,
        "${toolsAt.first}:${toolsAt.second}", "${typeAt.first}:${typeAt.second}", VERSION,
    ).joinToString("|")

    companion object {
        val DEFAULT = ControlsLayout(setOf(Tool.RIGHT, Tool.HOLD, Tool.LOUPE, Tool.COPY, Tool.PASTE), Size.MEDIUM, Idle.FADE, 0.96f to 0.72f, 0.05f to 0.88f)

        /** "2": Copiar and Colar exist. A layout saved before gets them once; after that it is the person's choice. */
        private const val VERSION = "2"

        fun decode(text: String?): ControlsLayout = runCatching {
            val parts = text!!.split("|")
            fun pair(s: String) = s.split(":").let { (x, y) -> x.toFloat().coerceIn(0f, 1f) to y.toFloat().coerceIn(0f, 1f) }
            val tools = parts[0].split(",").filter { it.isNotEmpty() }.map { Tool.valueOf(it) }.toSet()
            ControlsLayout(
                if (parts.getOrNull(5) == VERSION) tools else tools + Tool.COPY + Tool.PASTE,
                Size.valueOf(parts[1]), Idle.valueOf(parts[2]), pair(parts[3]), pair(parts[4]),
            )
        }.getOrDefault(DEFAULT)

        /**
         * Where a dragged group lands (its top-left, px): inside the screen by [margin], and glued to an
         * edge when dropped within [pull] of it — the edges are where it is out of the way.
         */
        fun snap(x: Float, y: Float, w: Float, h: Float, viewW: Float, viewH: Float, margin: Float, pull: Float): Pair<Float, Float> {
            val maxX = (viewW - w - margin).coerceAtLeast(margin)
            val maxY = (viewH - h - margin).coerceAtLeast(margin)
            var nx = x.coerceIn(margin, maxX)
            var ny = y.coerceIn(margin, maxY)
            if (nx - margin < pull) nx = margin
            if (maxX - nx < pull) nx = maxX
            if (ny - margin < pull) ny = margin
            if (maxY - ny < pull) ny = maxY
            return nx to ny
        }

        /** A column along the left and right edges, a row along the top and bottom. */
        fun vertical(fx: Float, fy: Float): Boolean {
            val side = minOf(fx, 1 - fx)
            val topBottom = minOf(fy, 1 - fy)
            return side <= topBottom
        }
    }
}
