# design/

Design v2 do Omarchy Remote. **Comece por [`DESIGN.md`](DESIGN.md).** A seção "v2.1" lista o que mudou depois da primeira implementação.

- `screens/` — como cada tela deve ficar (PNG 2×)
- `source/` — HTML das telas; 1 px = 1 dp, para medir
- `tokens.json` — tokens em formato de máquina
- `compose/DesignTokens.kt` — tokens em Kotlin (cores, tipos, formas, medidas, `ColorScheme`)
- `compose/font/` — Archivo e JetBrains Mono em TTF (SIL OFL 1.1), já com nomes de `res/font`

| # | Tela |
|---|---|
| 00 | Sistema visual |
| 01 | Principal — rede (mapa do cursor) |
| 02 | Principal — Bluetooth |
| 03 | Camada de teclas |
| 04 | Camada de texto |
| 05 | Sem computador |
| 06 | Folha Computador — Bluetooth |
| 07 | Folha Computador — rede |
| 08 | Folha Ajustes |
| 09 | Dicas de gestos |
| 10 | Ver PC — repouso |
| 11 | Ver PC — leque aberto |
| 12 | Ver PC — menu do chip |
| 13 | Paisagem — base |
| 14 | Paisagem — camada de teclas |
| 15 | Folha Ajustes — paisagem |

Para implementar com o Claude Code, dentro do repositório:

> Implemente o design em `design/DESIGN.md`, seguindo a ordem da seção 7. Use os PNGs de
> `design/screens/` como referência visual e `design/source/` para medidas exatas.
> Comece pelo passo 1 e me mostre antes de seguir para o 2.
