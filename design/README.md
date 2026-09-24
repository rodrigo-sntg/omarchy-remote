# design/

Omarchy Remote's design v2. **Start with [`DESIGN.md`](DESIGN.md)** (in Portuguese); its "v2.1" section lists what changed after the first implementation.

- `screens/`: how each screen should look (2× PNG)
- `source/`: each screen in HTML, 1 px = 1 dp, for exact measurements
- `compose/DesignTokens.kt`: the tokens in Kotlin (colors, type, shapes, sizes, `ColorScheme`)
- `compose/font/`: Archivo and JetBrains Mono as TTF (SIL OFL 1.1), already named for `res/font`

| # | Screen |
|---|---|
| 00 | Visual system |
| 01 | Main: network (cursor map) |
| 02 | Main: Bluetooth |
| 03 | Keys layer |
| 04 | Text layer |
| 05 | No computer |
| 06 | Computer sheet: Bluetooth |
| 07 | Computer sheet: network |
| 08 | Settings sheet |
| 09 | Gesture tips |
| 10 | View PC: at rest |
| 11 | View PC: fan open |
| 12 | View PC: chip menu |
| 13 | Landscape: base |
| 14 | Landscape: keys layer |
| 15 | Settings sheet: landscape |

To implement it with Claude Code, inside the repository:

> Implement the design in `design/DESIGN.md`, following the order of section 7. Use the PNGs in
> `design/screens/` as the visual reference and `design/source/` for exact measurements.
> Start with step 1 and show me before moving on to step 2.
