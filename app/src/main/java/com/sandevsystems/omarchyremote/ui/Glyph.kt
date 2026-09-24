package com.sandevsystems.omarchyremote.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Stroke icons from the design canvas (24×24, 1.8 stroke, round caps); tinted by Icon. */
object Glyph {
    val ChevronDown: ImageVector by lazy { stroke("ChevronDown", "M6 9l6 6 6-6") }
    val Sliders: ImageVector by lazy { stroke("Sliders", "M4 7h10M18 7h2M4 17h4M12 17h8", "M14,7a2,2 0 1,0 4,0a2,2 0 1,0 -4,0", "M8,17a2,2 0 1,0 4,0a2,2 0 1,0 -4,0") }
    val Expand: ImageVector by lazy { stroke("Expand", "M9 4H4v5M15 4h5v5M15 20h5v-5M9 20H4v-5") }
    val Plus: ImageVector by lazy { stroke("Plus", "M12 5v14M5 12h14") }
    val Monitor: ImageVector by lazy { stroke("Monitor", "M4.5,4h15a2,2 0 0 1 2,2v9a2,2 0 0 1 -2,2h-15a2,2 0 0 1 -2,-2v-9a2,2 0 0 1 2,-2z", "M9 21h6M12 17v4") }
    val Help: ImageVector by lazy { stroke("Help", "M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0", "M9.5 9.5a2.5 2.5 0 1 1 3.2 2.4c-.6.2-.7.7-.7 1.3", "M12 17h.01") }
    val Hand: ImageVector by lazy { stroke("Hand", "M9 11V5.5a1.5 1.5 0 1 1 3 0V11", "M12 11V4.5a1.5 1.5 0 1 1 3 0V11", "M15 11V6.5a1.5 1.5 0 1 1 3 0V13c0 4-2.5 7-6.5 7S6 17.5 6 14v-1.5a1.5 1.5 0 0 1 3 0") }
    val ArrowUp: ImageVector by lazy { stroke("ArrowUp", "M12 19V5M6 11l6-6 6 6") }
    val ArrowLeft: ImageVector by lazy { stroke("ArrowLeft", "M19 12H5M11 6l-6 6 6 6") }
    val ArrowDown: ImageVector by lazy { stroke("ArrowDown", "M12 5v14M6 13l6 6 6-6") }
    val ArrowRight: ImageVector by lazy { stroke("ArrowRight", "M5 12h14M13 6l6 6-6 6") }
    val Lines: ImageVector by lazy { stroke("Lines", "M5 6h14M5 12h9M5 18h12") }
    val ViewPc: ImageVector by lazy { stroke("ViewPc", "M4.5,5h10.0a1.5,1.5 0 0 1 1.5,1.5v7.0a1.5,1.5 0 0 1 -1.5,1.5h-10.0a1.5,1.5 0 0 1 -1.5,-1.5v-7.0a1.5,1.5 0 0 1 1.5,-1.5z", "M8 19h6M10.5 15v4", "M18,10h2.5a1,1 0 0 1 1,1v7a1,1 0 0 1 -1,1h-2.5a1,1 0 0 1 -1,-1v-7a1,1 0 0 1 1,-1z") }
    val Backspace: ImageVector by lazy { stroke("Backspace", "M20 5H9L3 12l6 7h11z", "M14 10l-4 4M10 10l4 4") }
    val Send: ImageVector by lazy { stroke("Send", "M20 12L4 4l6 8-6 8z") }  // paper plane pointing right
    val Bluetooth: ImageVector by lazy { stroke("Bluetooth", "M7 8l10 8-5 4V4l5 4-10 8") }
    val Wifi: ImageVector by lazy { stroke("Wifi", "M2.5 9a15 15 0 0 1 19 0", "M6 12.5a10 10 0 0 1 12 0", "M9.5 16a5 5 0 0 1 5 0", "M12 19.5h.01") }
    val Pointer: ImageVector by lazy { stroke("Pointer", "M5 3l14 8-6.2 1.6L9.6 19z") }
    val Keyboard: ImageVector by lazy { stroke("Keyboard", "M4.5,6h15a2,2 0 0 1 2,2v8a2,2 0 0 1 -2,2h-15a2,2 0 0 1 -2,-2v-8a2,2 0 0 1 2,-2z", "M6.5 10h.01M10 10h.01M13.5 10h.01M17 10h.01M6.5 14h11") }
    val Grid: ImageVector by lazy { stroke("Grid", "M4.5,3h4.0a1.5,1.5 0 0 1 1.5,1.5v4.0a1.5,1.5 0 0 1 -1.5,1.5h-4.0a1.5,1.5 0 0 1 -1.5,-1.5v-4.0a1.5,1.5 0 0 1 1.5,-1.5z", "M15.5,3h4.0a1.5,1.5 0 0 1 1.5,1.5v4.0a1.5,1.5 0 0 1 -1.5,1.5h-4.0a1.5,1.5 0 0 1 -1.5,-1.5v-4.0a1.5,1.5 0 0 1 1.5,-1.5z", "M4.5,14h4.0a1.5,1.5 0 0 1 1.5,1.5v4.0a1.5,1.5 0 0 1 -1.5,1.5h-4.0a1.5,1.5 0 0 1 -1.5,-1.5v-4.0a1.5,1.5 0 0 1 1.5,-1.5z", "M15.5,14h4.0a1.5,1.5 0 0 1 1.5,1.5v4.0a1.5,1.5 0 0 1 -1.5,1.5h-4.0a1.5,1.5 0 0 1 -1.5,-1.5v-4.0a1.5,1.5 0 0 1 1.5,-1.5z") }
    val Phone: ImageVector by lazy { stroke("Phone", "M9.5,2.5h5a2.5,2.5 0 0 1 2.5,2.5v14a2.5,2.5 0 0 1 -2.5,2.5h-5a2.5,2.5 0 0 1 -2.5,-2.5v-14a2.5,2.5 0 0 1 2.5,-2.5z", "M11 18.5h2") }
    val Copy: ImageVector by lazy { stroke("Copy", "M9.5,8h9a1.5,1.5 0 0 1 1.5,1.5v9a1.5,1.5 0 0 1 -1.5,1.5h-9a1.5,1.5 0 0 1 -1.5,-1.5v-9a1.5,1.5 0 0 1 1.5,-1.5z", "M16 8V5.5A1.5 1.5 0 0 0 14.5 4h-9A1.5 1.5 0 0 0 4 5.5v9A1.5 1.5 0 0 0 5.5 16H8") }
    val Paperclip: ImageVector by lazy { stroke("Paperclip", "M20 11.5l-8.2 8.2a5 5 0 0 1-7.1-7.1l8.5-8.5a3.3 3.3 0 0 1 4.7 4.7l-8.5 8.5a1.7 1.7 0 0 1-2.4-2.4l7.8-7.8") }
    val Volume: ImageVector by lazy { stroke("Volume", "M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M15.5 9a4 4 0 0 1 0 6M18.5 6.5a7.5 7.5 0 0 1 0 11") }
    val VolumeOff: ImageVector by lazy { stroke("VolumeOff", "M4 9.5h3.5L12 5.5v13l-4.5-4H4z", "M16 9.5l5 5M21 9.5l-5 5") }
    val BellOff: ImageVector by lazy { stroke("BellOff", "M6 16.5V11a6 6 0 0 1 9.4-4.9M18 11v5.5M4.5 16.5h15M10 19.5a2 2 0 0 0 4 0", "M4 4l16 16") }
    val Coffee: ImageVector by lazy { stroke("Coffee", "M5 8.5h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5z", "M16 10h1.5a2.5 2.5 0 0 1 0 5H16M8 3.5v2M11 3.5v2") }
    val Record: ImageVector by lazy { stroke("Record", "M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0", "M8.5,12a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0") }
    val Image: ImageVector by lazy { stroke("Image", "M5,4h14a2,2 0 0 1 2,2v12a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-12a2,2 0 0 1 2,-2z", "M3 16l5-5 4 4 3-3 6 6", "M15.5 8.5h.01") }
    val Bar: ImageVector by lazy { stroke("Bar", "M5,4h14a2,2 0 0 1 2,2v12a2,2 0 0 1 -2,2h-14a2,2 0 0 1 -2,-2v-12a2,2 0 0 1 2,-2z", "M3 8h18") }
    val Gaps: ImageVector by lazy { stroke("Gaps", "M4,4h6.5v7h-6.5z", "M13.5,4h6.5v16h-6.5z", "M4,14h6.5v6h-6.5z") }
    val Download: ImageVector by lazy { stroke("Download", "M12 4v11M7 10l5 5 5-5", "M5 20h14") }
    val Folder: ImageVector by lazy { stroke("Folder", "M3.5,6.5a1.5,1.5 0 0 1 1.5,-1.5h4.5l2,2h7.5a1.5,1.5 0 0 1 1.5,1.5v9a1.5,1.5 0 0 1 -1.5,1.5h-14a1.5,1.5 0 0 1 -1.5,-1.5z") }
    val File: ImageVector by lazy { stroke("File", "M6.5,3h7.5l4.5,4.5v12a1.5,1.5 0 0 1 -1.5,1.5h-10.5a1.5,1.5 0 0 1 -1.5,-1.5v-15a1.5,1.5 0 0 1 1.5,-1.5z", "M13.5 3v5h5") }
    val Power: ImageVector by lazy { stroke("Power", "M12 3v8.5", "M6.3 6.8a8 8 0 1 0 11.4 0") }
    val Restart: ImageVector by lazy { stroke("Restart", "M20 12a8 8 0 1 1-2.3-5.7", "M20 4v5h-5") }
    val Check: ImageVector by lazy { stroke("Check", "M5 12l4 4L19 6") }
    val Swap: ImageVector by lazy { stroke("Swap", "M4 8h13l-3-3M20 16H7l3 3") }
    val Eye: ImageVector by lazy { stroke("Eye", "M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z", "M9,12a3,3 0 1,0 6,0a3,3 0 1,0 -6,0") }
    val Mouse: ImageVector by lazy { stroke("Mouse", "M12,3h0a6,6 0 0 1 6,6v6a6,6 0 0 1 -6,6h0a6,6 0 0 1 -6,-6v-6a6,6 0 0 1 6,-6z", "M12 3v6") }
    val Terminal: ImageVector by lazy { stroke("Terminal", "M4.5,5h15a2,2 0 0 1 2,2v10a2,2 0 0 1 -2,2h-15a2,2 0 0 1 -2,-2v-10a2,2 0 0 1 2,-2z", "M7 9.5l3 2.5-3 2.5M12 15h5") }
    val Omarchy: ImageVector by lazy { stroke("Omarchy", "M5.5,3h13a2.5,2.5 0 0 1 2.5,2.5v13a2.5,2.5 0 0 1 -2.5,2.5h-13a2.5,2.5 0 0 1 -2.5,-2.5v-13a2.5,2.5 0 0 1 2.5,-2.5z", "M8 8h8M8 12h8M8 16h5") }
    val Search: ImageVector by lazy { stroke("Search", "M4,11a7,7 0 1,0 14,0a7,7 0 1,0 -14,0", "M16.5 16.5L21 21") }
    val ChevronLeft: ImageVector by lazy { stroke("ChevronLeft", "M15 6l-6 6 6 6") }
    val ChevronRight: ImageVector by lazy { stroke("ChevronRight", "M9 6l6 6-6 6") }
    val Play: ImageVector by lazy { stroke("Play", "M8 5v14l11-7z") }
    val Pause: ImageVector by lazy { stroke("Pause", "M8 5v14M16 5v14") }
    val Next: ImageVector by lazy { stroke("Next", "M6 5l10 7-10 7z", "M18 5v14") }
    val Previous: ImageVector by lazy { stroke("Previous", "M18 5L8 12l10 7z", "M6 5v14") }
    val Mic: ImageVector by lazy { stroke("Mic", "M12,3a3,3 0 0 1 3,3v6a3,3 0 0 1 -6,0v-6a3,3 0 0 1 3,-3z", "M5 11a7 7 0 0 0 14 0M12 18v3") }
    val More: ImageVector by lazy { stroke("More", "M5,12a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0", "M10.8,12a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0", "M16.6,12a1.2,1.2 0 1,0 2.4,0a1.2,1.2 0 1,0 -2.4,0") }
    val Gear: ImageVector by lazy { stroke("Gear", "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "M19.4 13.5a7.6 7.6 0 0 0 0-3l2-1.5-2-3.4-2.3.9a7.5 7.5 0 0 0-2.6-1.5L14.1 2.6h-4.2l-.4 2.4a7.5 7.5 0 0 0-2.6 1.5l-2.3-.9-2 3.4 2 1.5a7.6 7.6 0 0 0 0 3l-2 1.5 2 3.4 2.3-.9a7.5 7.5 0 0 0 2.6 1.5l.4 2.4h4.2l.4-2.4a7.5 7.5 0 0 0 2.6-1.5l2.3.9 2-3.4z") }
    val TextAa: ImageVector by lazy { stroke("TextAa", "M3 18l5-12 5 12M4.7 14h6.6", "M15.9 12.4a2.8 2.8 0 1 0 0 5.6a2.8 2.8 0 1 0 0-5.6z", "M18.7 12.4V18") }
    val Lock: ImageVector by lazy { stroke("Lock", "M7 11h10a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2z", "M8 11V8a4 4 0 0 1 8 0v3") }
    val Moon: ImageVector by lazy { stroke("Moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z") }
    val Camera: ImageVector by lazy { stroke("Camera", "M5.5 7h13A2.5 2.5 0 0 1 21 9.5v8a2.5 2.5 0 0 1-2.5 2.5h-13A2.5 2.5 0 0 1 3 17.5v-8A2.5 2.5 0 0 1 5.5 7z", "M8.5 7l1.5-3h4l1.5 3", "M12 10a3.5 3.5 0 1 0 0 7a3.5 3.5 0 1 0 0-7z") }
    val Upload: ImageVector by lazy { stroke("Upload", "M12 16V4M7 9l5-5 5 5", "M4 16v3a1.5 1.5 0 0 0 1.5 1.5h13A1.5 1.5 0 0 0 20 19v-3") }
    val Close: ImageVector by lazy { stroke("Close", "M6 6l12 12M18 6L6 18") }
}

private fun stroke(name: String, vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    for (path in paths) {
        builder.addPath(
            PathParser().parsePathString(path).toNodes(),
            stroke = SolidColor(Color.White), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}
