# Omarchy Remote — Design v2

Especificação de interface para implementar em Jetpack Compose (Material 3).
Substitui a UI da V0.1 (prints em `docs/screenshots/`).

**Como ler esta pasta**

| Arquivo | Para quê |
|---|---|
| `DESIGN.md` | Este documento. A fonte da verdade. |
| `screens/*.png` | Como cada tela deve ficar (2×, desenhadas a 390 dp de largura). |
| `source/*.html` | O HTML de onde os PNGs saíram. Use para tirar medidas exatas: **1 px no HTML = 1 dp**. |
| `tokens.json` | Cores, tipos, raios e medidas em formato de máquina. |
| `compose/DesignTokens.kt` | Os tokens já em Kotlin, prontos para copiar para `ui/`. |
| `compose/font/` | Archivo e JetBrains Mono em TTF (OFL), com os nomes que o `res/font` aceita. |

As telas foram desenhadas a 390 dp de largura. O S24 Ultra tem ~412 dp: **use pesos
(`Modifier.weight`) na horizontal, nunca larguras fixas**. As alturas em dp valem como estão;
o trackpad absorve o que sobrar na vertical.

---

## v2.1 — correções depois da primeira implementação

Mudanças em cima da v2, a partir de prints do app rodando. As seções abaixo já estão
atualizadas; esta lista é o resumo do que mudar no código que já existe.

1. **Toda camada fecha no mesmo lugar onde abriu.** O dock **nunca some**: a camada de teclas
   termina acima dele, e o botão `Teclas` vira `Fechar teclas` (ou `Fechar` no dock vertical),
   com borda e texto acento e fundo acento 12%. Vale em retrato e em paisagem. A alça e o gesto
   Voltar continuam funcionando, mas não são mais o único jeito de sair. (`screens/03`, `14`)
2. **A camada de texto tem `Fechar` no cabeçalho**, porque o teclado do Android cobre o dock.
   (`screens/04`)
3. **Paisagem é uma grade só.** A linha de status ocupa a largura inteira no topo. Abaixo dela,
   todas as colunas começam na mesma linha e terminam na mesma linha — nada centralizado
   verticalmente por conta própria. (`screens/13`, `14`)
4. **Teclas de paisagem em 4 fileiras de altura igual dos dois lados**, esticando para a altura do
   trackpad. Rótulos inteiros (`Copiar Colar Recortar Desfazer`), nada de `recort`/`desfaz`.
   O dock vertical continua à direita. (`screens/14`)
5. **Margens laterais simétricas em paisagem.** Hoje o recorte da câmera empurra só o lado esquerdo.
   Use o maior entre o inset esquerdo e o direito (`WindowInsets.displayCutout` +
   `systemBars`) nos dois lados, para a tela ficar centrada.
6. **Card de telas com overline por fora**, no mesmo padrão de Atalhos: `TELAS · ONDE ESTÁ O CURSOR`
   à esquerda e o botão `Ver PC` à direita, fora do card. O rótulo que ficava dentro do mapa sai.
7. **Workspaces com largura fixa de 44 dp**, alinhados à esquerda, com `+` tracejado logo depois do
   último. Nada de esticar três botões pela largura toda. (Por Bluetooth, com 10, eles dividem a
   largura.)
8. **Monitores do mapa alinhados pela base**, com altura proporcional à resolução real.
9. **Espaçamento vertical uniforme**: 14 dp entre blocos da base; 8 dp entre um overline e o seu
   conteúdo. O overline pertence ao bloco de baixo.
10. **Dock com ícone + rótulo nos três botões.** O ícone de linhas do `Texto` lia como menu; virou
    `Aa`. Botões laterais passam de 64 para 72 dp.
11. **Folhas em paisagem**: largura máxima 720 dp centrada, começando abaixo da barra de status
    (nunca por baixo dela), com botão de fechar no cabeçalho. **Ajustes em duas colunas** —
    Trackpad à esquerda; Tela do PC, layout e botões à direita — para caber sem rolar.
    (`screens/15`)
12. **O slider alinha com o texto.** O `Slider` do M3 tem respiro interno do tamanho do polegar;
    compense (padding negativo ou trilho próprio) para o começo do trilho coincidir com a margem
    do rótulo "Sensibilidade".

---

## 1. Princípios

Estes quatro princípios decidem qualquer caso que o documento não cobre.

1. **A base nunca muda de lugar.** Em retrato, de cima para baixo: linha de status → card de
   telas → atalhos → trackpad → dock. Sempre nessa ordem, sempre nas mesmas coordenadas.
   Tudo o mais (teclas, texto, folhas) é **camada**: sobe por cima com scrim, é dispensada, e
   devolve a base intacta. Sem abas, sem rolagem, sem refluxo. A mão aprende uma vez.
2. **O polegar manda.** O que se usa mais fica mais embaixo. O trackpad ocupa a metade de baixo
   da tela; o card de telas, que é informação, fica em cima, onde a mão não alcança mesmo.
3. **O olho está no monitor, não no celular.** Posições estáveis, alvos grandes, háptica em toda
   tecla. Nada que exija ler para acertar.
4. **O verde significa alguma coisa.** O acento (`#C5F24A`) aparece só em: ponto de conexão
   ativa, estado ligado (modificador, Segurar, workspace atual), a ação principal da tela e a
   borda do campo em foco. Todo o resto é grafite.

---

## 2. Tokens

Os valores estão em `tokens.json` e `compose/DesignTokens.kt`. Resumo:

### Cor

| Token | Hex | Uso |
|---|---|---|
| `bg` | `#0B0C0D` | Fundo da tela |
| `surface1` | `#121517` | Trackpad, campos, áreas "afundadas" |
| `surface2` | `#16191B` | Cards, folhas, dock |
| `surface3` | `#1D2124` | Teclas |
| `line` | `#2B3034` | Bordas padrão |
| `line2` | `#3A4045` | Bordas fortes, alça das folhas, tracejados |
| `text` | `#ECEEF0` | Texto principal |
| `textDim` | `#99A1A8` | Texto secundário |
| `textMute` | `#808890` | Rótulos, legendas (≥ 4,5:1 sobre `surface2`) |
| `accent` | `#C5F24A` | Ver princípio 4 |
| `onAccent` | `#0B0C0D` | Texto/ícone sobre o acento |
| `danger` | `#E8674B` | Desconectar, Sair, CAPS, erros |
| `scrim` | `#060708` a 60% | Atrás de camadas e folhas |

Tintas do acento: `accent` a 6% (fundo de item conectado), 12–16% (fundo de segmento/workspace
ativo), 35% (borda da faixa de atalhos com modificador). Nunca como fundo de tela inteira.

### Tipografia

Duas famílias, empacotadas no app (`res/font`, arquivos em `compose/font/`):

- **Archivo** 400/500/600/700 — toda a interface.
- **JetBrains Mono** 400/500 — só valores técnicos: nomes de monitor (`DP-1`), MACs, endereços,
  números de workspace, glifos dos atalhos, multiplicador de sensibilidade.

| Estilo | Família | Peso | Tamanho | Extra |
|---|---|---|---|---|
| `sheetTitle` | Archivo | 600 | 21 sp | |
| `emptyTitle` | Archivo | 600 | 19 sp | |
| `hostName` | Archivo | 600 | 15 sp | |
| `key` | Archivo | 500 | 15 sp (14 sp em fileiras de 5) | |
| `keySmall` | Archivo | 500 | 13 sp | Copiar/Colar/Recortar/Desfazer |
| `body` | Archivo | 400 | 14 sp / 20 sp | |
| `caption` | Archivo | 400 | 12–12,5 sp / 18 sp | textos de ajuda |
| `overline` | Archivo | 600 | 11 sp | maiúsculas, `letterSpacing` 0,1 em |
| `macroGlyph` | JetBrains Mono | 500 | 17 sp | |
| `macroCaption` | Archivo | 400 | 10 sp | |
| `mono` | JetBrains Mono | 400 | 11 sp | |
| `monoValue` | JetBrains Mono | 500 | 14 sp | ex.: `1,2×` |

### Forma (raio)

| Elemento | Raio |
|---|---|
| Tecla | 13 dp |
| Atalho, campo, segmento externo | 14–15 dp |
| Segmento interno | 12 dp |
| Dock, botões grandes | 17 dp |
| Card de telas | 20 dp |
| Trackpad | 24 dp |
| Folhas e camadas (só em cima) | 26–28 dp |
| Leque do Ver PC | círculo |

### Medidas

| Medida | dp |
|---|---|
| Margem lateral | 14 |
| Espaço vertical entre blocos da base | 14 |
| Overline → conteúdo do bloco | 8 |
| Espaço entre teclas | 7 |
| Alvo mínimo de toque | 44 |
| Tecla (camada de teclas, retrato) | 52 |
| Botão de workspace | 44 × 36 |
| Atalho | 56 |
| Barra de clique do trackpad | 62 |
| Dock | 56 (+14 de respiro inferior); laterais 72 de largura; vertical 78 |
| Linha de status | 40 |
| Âncora do Ver PC | 60 |
| Botões do leque | 56, em arco de raio 150 |

### Relevo

Dois efeitos só, os dois discretos:

- **Elevado** (teclas, atalhos, dock): linha de 1 dp branca a 6% no topo interno + sombra
  `0 1 2 rgba(0,0,0,.5)`. Em Compose: `drawBehind` com uma linha no topo; sem `elevation` do M3.
- **Afundado** (trackpad, campos, mapa): sombra interna `inset 0 2 8 rgba(0,0,0,.55)`. Em Compose:
  gradiente vertical de preto 35% → 0 nos primeiros 10 dp dentro da forma.

A textura do trackpad é uma grade de pontos: raio 1,1 dp, cor `#212629`, passo 26 dp.

---

## 3. Componentes

Nomes sugeridos em Compose. Cada um aponta para a tela onde aparece.

### `StatusLine` — `screens/01`
Altura 40. Ponto 8 dp (acento com halo de 4 dp a 13% quando conectado; `line2` quando não) ·
nome do host (`hostName`) · transporte (`mono`, `textMute`: "Bluetooth" ou "Rede · Tailscale") ·
chevron. Toque em qualquer parte do nome abre a folha **Computador**. À direita, botão de 44 dp
com ícone de ajustes → folha **Ajustes**.

Emblemas que aparecem entre o nome e o botão de ajustes, quando for o caso:
- `CAPS` (borda e texto `danger`, `mono` 10 sp) quando `vm.capsLock` for verdadeiro.
- `Ctrl` / `Alt` / `Super` / `Shift` (pílula acento) quando houver modificador marcado e a
  camada de teclas estiver fechada. Tocar solta o modificador.

### `ScreensCard` — `screens/01` (rede) e `screens/02` (Bluetooth)
Mesmo padrão de bloco de `ShortcutRow`: **overline por fora** (linha de 26 dp) + card embaixo, a
8 dp. Card `surface2`, raio 20, padding 10, gap 8.

- **Pela rede**: overline `TELAS · ONDE ESTÁ O CURSOR` e, à direita, botão pequeno `⤢ Ver PC`
  (26 dp de altura, borda `line`) → Ver PC do monitor onde está o cursor.
  Dentro do card, o **mapa do cursor** (altura 108, `surface1` afundado, sem rótulo interno).
  Desenha os monitores em proporção real, lado a lado, **alinhados pela base**, com o número do
  workspace no canto e o nome embaixo (`mono`). O monitor onde o cursor está ganha borda de acento
  a 60% e um ponto de 12 dp na posição do cursor. À direita, um retângulo tracejado **"tela extra"**
  → Tela extra (`vm.openDisplay`). Tocar num monitor → Ver PC daquele monitor.
  Embaixo do mapa, a fileira de workspaces: rótulo `WS` (30 dp), botões de **44 × 36 dp alinhados
  à esquerda**, gap 4, o atual em acento; logo depois do último, um `+` tracejado do mesmo tamanho.
- **Por Bluetooth**: overline `WORKSPACES` e, no card, só `1 2 3 4 5 6 7 8 9 0` dividindo a largura,
  sem destaque (o PC não informa qual é o atual) e sem `+`. O trackpad ganha a altura que sobra.
- **Sem dados de monitor** (rede conectada, mas o PC ainda não mandou geometria): igual ao
  Bluetooth, mas com os workspaces reais.

> O mapa **não é vídeo**. Ver seção 6 sobre o que o host precisa mandar.

### `ShortcutRow` — `screens/01`
Cabeçalho: overline "ATALHOS · UM TOQUE" + botão "Editar" (26 dp de altura, borda `line`).
Cinco `MacroKey` em grade de pesos iguais, gap 7. Cada uma: glifo (`macroGlyph`) em cima e o
que faz (`macroCaption`) embaixo, altura 56, raio 14, elevada.

Padrão: `Ctrl+C` (C · copiar), `Ctrl+V` (V · colar), `Ctrl+Z` (Z · desfazer),
`Ctrl+W` (W · fechar aba), `Esc` (Esc · sair). Um toque = o atalho inteiro via
`vm.pressShortcut(KeyStroke)`. Esta fileira é o que mata o "acorde" no uso comum: ninguém precisa
marcar Ctrl e depois apertar C.

### `Trackpad` com `ClickBar` integrada — `screens/01`
Um objeto só: superfície afundada (`surface1`, raio 24, borda `line`) com a textura de pontos,
rótulo "TRACKPAD" (`mono` 10 sp, `textMute`, espaçado) no canto superior esquerdo e botão `?`
(44 dp) no canto superior direito → diálogo **Dicas de gestos**.

A borda de baixo **é** a barra de clique, 62 dp, `surface2`, separada por uma linha `line`:
`Esquerdo` (peso 3) | `Segurar` com ícone de mão (peso 2) | `Direito` (peso 3), com divisórias
verticais de 1 dp. Segurar é alternância: ligado, a zona fica com fundo acento 14% e texto acento.
Substitui o antigo botão "Arrastar". Os gestos do trackpad não mudam (`TouchpadGesture.kt`).

### `Dock` — `screens/01`
Três botões, 56 dp de altura, cada um com ícone (20 dp) em cima e rótulo (Archivo 600 11 sp) embaixo:
- **Teclas** (peso 1): ícone de teclado. Abre a camada de teclas. **Com a camada aberta vira
  `Fechar teclas`** (chevron para baixo, borda e texto acento, fundo acento 12%) — é o jeito
  principal de sair.
- **Texto** (72 dp): glifo `Aa` (Archivo 600 17 sp). Abre a camada de texto.
- **Ver PC** (72 dp): acento cheio pela rede. **Por Bluetooth**: borda tracejada `line2`, ícone e
  rótulo `textMute`; toque mostra a mensagem "Ver PC funciona pela rede (Tailscale)". Não esconder:
  a base não muda de lugar.

O dock fica **acima** do scrim quando há camada aberta: continua visível e tocável.
Em paisagem ele é vertical (78 dp de largura), os três botões dividem a altura igualmente, e o
primeiro diz só `Fechar` quando a camada está aberta.

### `KeysLayer` — `screens/03`
Camada que sobe por cima do trackpad e **para 10 dp acima do dock** (o dock continua visível, com
`Teclas` virando `Fechar teclas`). Cartão flutuante `surface2`, raio 26 nos quatro cantos, 8 dp de
margem lateral, scrim atrás. De cima para baixo:
1. Alça (38×4 dp em área de toque 72×20) — tocar ou arrastar para baixo também fecha.
2. **Faixa do modificador** (só com modificador marcado): borda acento 35%, fundo acento 7%,
   texto "CTRL VALE PARA A PRÓXIMA TECLA" + botão "Soltar". Sete teclas de 42 dp:
   `C V X A Z T 1`. Mantém o comportamento atual (vale para a próxima tecla, limpa depois).
3. `Ctrl Alt Super Shift` — alternâncias, 52 dp.
4. `Esc Tab ⌫ Del Enter` — 52 dp.
5. `← ↑ ↓ →` — 52 dp.
6. `Copiar Colar Recortar Desfazer` — 46 dp, `keySmall`.
7. `ClickBar` compacta (44 dp, raio 14): a camada tapa o trackpad, mas o clique não pode sumir.

Cabe inteira sem rolagem em 844 dp de altura, entre o card de telas e o dock. Se não couber (escala de fonte grande), a camada
rola por dentro; a base continua parada.

### `TextLayer` — `screens/04`
Camada presa **acima do teclado do Android** (use `imePadding()`). Como o teclado do sistema cobre
o dock, o fechar fica no cabeçalho da camada: overline `TEXTO PARA O COMPUTADOR` à esquerda e
`Fechar ⌄` (alvo de 44 dp) à direita. Campo afundado com borda acento, o texto em `body` 16 sp;
dentro do campo, "Layout do PC" + segmento `ABNT2 | US`. Embaixo: `Limpar` · `Enter` ·
**`Enviar`** (peso 2, acento).
- Com CAPS ligado: `Enviar` desabilitado e uma linha `danger` "Caps Lock está ligado no computador".
- Caractere que o layout não produz: mensagem atual do app, sem enviar nada.

### `Sheet` (folhas) — `screens/06`, `07`, `08`
`ModalBottomSheet` com `skipPartiallyExpanded = true`, fundo `surface2`, raio 28 em cima,
padding 18 lateral, gap 16. Alça 38×4 `line2`. Título `sheetTitle`.

### `Segmented`
Trilho `surface1` com borda `line`, raio 15, padding 4. Segmento ativo: borda acento, fundo
acento 12%, texto acento, `600`. Inativo: sem borda, texto `textMute`. Altura 44 (38 dentro de
campos).

### `SettingToggle`, `SettingSlider`
Toggle: rótulo 15 sp + legenda opcional 12 sp `textMute`; trilho 48×28, ligado = acento com bolinha
`onAccent`. Slider: rótulo à esquerda, valor em `monoValue` acento à direita, trilho de 6 dp,
polegar de 22 dp acento com anel `bg` de 3 dp. Pode usar `Switch`/`Slider` do M3 com essas cores.

### `EmptyState` — `screens/05`
O trackpad vira um quadro tracejado com a ilustração (celular · · · monitor, traço `line2`),
título, texto e **os botões no fim do quadro**, na zona do polegar: `Bluetooth` (acento) e `Rede`.

### Ver PC: `RemoteChip`, `WorkspaceEdge`, `ArcMenu` — `screens/10`, `11`, `12`
Ver seção 4.

---

## 4. Telas

### 4.1 Principal — `screens/01` (rede), `screens/02` (Bluetooth)

`StatusLine` → `ScreensCard` → `ShortcutRow` → `Trackpad` (peso 1, pega o resto) → `Dock`.
Nenhuma rolagem. Some da tela atual: o título "Omarchy Remote", o botão "Computador", a fileira
mista de monitor/workspace, a pilha de fileiras rolável, o parágrafo de gestos dentro do pad.

### 4.2 Estados de conexão (`ConnectionState`)

A base é a mesma em todos; muda o conteúdo do trackpad e o status.

| Estado | Status | Trackpad | Resto |
|---|---|---|---|
| `PermissionRequired` | "Permissão necessária" | EmptyState: "Permita Dispositivos próximos" + botão `Permitir` | Atalhos a 30%, sem toque |
| `BluetoothOff` | "Bluetooth desligado" | EmptyState + `Ligar Bluetooth` | idem |
| `Starting` | "Preparando…" (ponto `line2`) | vazio, sem toque | idem |
| `Ready` | "Nenhum computador" | EmptyState de `screens/05` | idem |
| `Connecting(host)` | nome do host + "conectando…" (ponto pulsando a 1 Hz, `textDim`) | vazio, sem toque | idem |
| `Connected(host)` | nome + transporte, ponto acento | ativo | tudo ativo |
| `Disconnected(reason)` | "Desconectado" | EmptyState com `reason` e botão acento `Reconectar a <host>` + `Escolher outro` | idem |
| `Error(message)` | "Erro" (ponto `danger`) | EmptyState com `message` em `danger` + `Tentar de novo` (`vm.retry`) | idem |

### 4.3 Camadas — `screens/03`, `screens/04`
Ver `KeysLayer` e `TextLayer`. Regras:
- Uma camada por vez. Abrir uma fecha a outra.
- **Voltar** do Android fecha a camada; só com a base limpa ele sai do app.
- A base por trás fica com scrim 60% e não recebe toque.

### 4.4 Folha Computador — `screens/06` (Bluetooth), `screens/07` (rede)
Segmento `Bluetooth | Rede (Tailscale)` (`vm.chooseTransport`).

**Bluetooth**: "CONECTADO" com o host atual em card de borda acento e botão `Desconectar`
(`danger`, dentro do card — o card não é clicável quando tem ação). "COMPUTADORES PAREADOS": só
os hosts com `isComputer = true`; linha "Mostrar outros N aparelhos (fones, relógios…)" expande o
resto. Embaixo: `Tornar visível · 2:00` (a contagem regressiva substitui o texto) e `Soltar tudo`.
Legenda curta de como parear.

**Rede**: campo "NOME DO PC NO TAILSCALE" (mono), campo "CÓDIGO DE PAREAMENTO" mascarado com botão
olho, botão acento `Conectar pela rede`, e o card "NO COMPUTADOR" com o comando
`host/run.sh --allow <celular>` em mono acento.

A **sensibilidade saiu desta folha** e foi para Ajustes.

### 4.5 Folha Ajustes — `screens/08`
Aberta pelo botão de ajustes da `StatusLine` e pelo menu do Ver PC. Uma folha só para tudo:
- **TRACKPAD**: Sensibilidade (slider, `vm.sensitivity`, mostra `1,2×`), Rolagem natural (com a
  legenda "Dedos para cima rolam a página para baixo"), Tocar para clicar.
- **NA TELA DO PC**: segmento `Trackpad | Toque direto` (`vm.videoTouchpad`) + legenda do modo.
- **LAYOUT ATIVO NO COMPUTADOR**: `Português · ABNT2 | Inglês · US` (`vm.layout`).
- Botões: `Editar atalhos` · `Dicas de gestos`.

### 4.6 Dicas de gestos — `screens/09`
Diálogo `surface2`, raio 26. Título "Modo trackpad" (ou "Modo toque direto") e legenda. Cada gesto
numa linha: quadrado 38 dp `surface3` com o número de dedos em mono acento + texto com o gesto em
`text` 600 e o efeito em `textDim`. Rodapé: "Troque o modo e a velocidade em Ajustes." + `Entendi`
(acento). Aparece sozinho na primeira vez (`vm.hintsSeen`).

### 4.7 Paisagem — `screens/13` (base) e `screens/14` (camada de teclas)
**Grade**: padding 8 dp em cima, 14 dp embaixo, laterais simétricas (v2.1, item 5). Linha de
status com a **largura inteira** (host, `Rede · DP-1 · 1,4×`, emblemas, ajustes à direita). Abaixo,
uma `Row` com `gap` 12 e `verticalAlignment = Top` onde **toda coluna usa `fillMaxHeight()`**:
todas começam e terminam nas mesmas linhas.

**Base**: esquerda 252 dp — card compacto (mapa 84 dp + workspaces) e, embaixo, overline
`ATALHOS · Editar` e os cinco atalhos numa grade 3×2 que **estica até o fim da coluna** (a sexta
célula é um `+` tracejado para adicionar). Centro: trackpad com `ClickBar` (peso 1). Direita: dock
vertical de 78 dp, três botões de altura igual.

**Camada de teclas em paisagem** (tela cheia; o dock continua na direita e o primeiro botão vira
`Fechar`). Quatro colunas depois do status: teclas esquerdas (204 dp) · trackpad (peso 1) · teclas
direitas (204 dp) · dock (78 dp). Cada coluna de teclas tem **4 fileiras de altura igual** que
somadas dão a altura do trackpad:
- Esquerda: `· ↑ ·` / `← ↓ →` / `Ctrl Alt` / `Super Shift`.
- Direita: `Esc Tab ⌫` / `Del Enter` / `Copiar Colar` / `Recortar Desfazer`.

Com modificador marcado, a pílula `Ctrl · soltar` aparece na linha de status (não há espaço para a
faixa de atalhos do retrato). Girar o celular **não** abre esta camada sozinho.

### 4.7b Folhas em paisagem — `screens/15`
Largura máxima 720 dp, centrada. Começa abaixo da barra de status (topo a 32 dp), nunca por baixo
dela. Cabeçalho com título e botão de fechar (44 dp, chevron para baixo). Conteúdo em duas colunas
separadas por uma linha vertical `line`. **Ajustes**: esquerda = Trackpad (sensibilidade e os dois
toggles); direita = Na tela do PC, Layout ativo, e `Editar atalhos` · `Dicas de gestos` no pé.

### 4.8 Ver PC e Tela extra — `screens/10`, `11`, `12`
Só em paisagem, como hoje. Tela cheia com o vídeo. Três peças por cima:

- **`RemoteChip`** (canto superior esquerdo, 14/34 dp): ponto + "meu-pc · DP-1 · toque direto" em
  mono. Some para 45% de opacidade depois de 2 s sem toque. Tocar abre um menu (`screens/12`):
  `Ajustes`, `Dicas de gestos`, divisória, **`Sair da tela do PC`** (`danger`).
- **`WorkspaceEdge`** (borda esquerda): uma aba por workspace, a atual larga (26 dp) em acento com
  o número, as outras 14 dp translúcidas. Tocar troca. A 30% em repouso.
- **Âncora** (60 dp, canto inferior direito, 16 dp de margem): em repouso (`screens/10`) é
  translúcida com borda acento 55% e ícone de cursor acento, e mais nada na tela. **Toque abre o
  leque** (`screens/11`): scrim 50%, a âncora vira acento cheio com chevron para baixo (fechar), e
  cinco botões de 56 dp num quarto de círculo de raio 150 dp, de 90° (em cima) a 180° (à
  esquerda), com rótulo 11 sp a 196 dp do centro: `Direito`, `Segurar`, `Teclado`, `Telas`, `Modo`.
  - Direito: clique direito onde o cursor está. Segurar: alterna `vm.videoHold` (acento enquanto
    ligado; o leque fica aberto). Teclado: camada de teclas de paisagem. Telas: bandeja com os
    monitores (`vm.monitors`) e os workspaces. Modo: alterna Trackpad/Toque direto e mostra a
    mensagem com o modo novo.
  - Gesto rápido: segurar a âncora, arrastar na direção do item e soltar = seleciona sem abrir.
    É por direção, não por posição — dá para acertar sem olhar.
- Voltar do Android: fecha o leque/menu se aberto; senão sai do Ver PC.

A **Tela extra** usa exatamente as mesmas três peças.

### 4.9 Mensagens (`vm.message`)
Faixa flutuante acima do dock (retrato) ou acima da âncora (Ver PC): `surface3`, raio 14, texto
14 sp, ícone à esquerda (`danger` para erro). Some em 4 s ou no toque. Não usar faixa colorida na
borda esquerda.

---

## 5. Comportamento

- **Háptica**: toda tecla, atalho, zona de clique e workspace → `HapticFeedbackConstants.KEYBOARD_TAP`.
  Ligar alternância (modificador, Segurar) → `CONFIRM`; desligar → `CLOCK_TICK`. É o que permite
  usar sem olhar; não é opcional.
- **Pressionado**: tecla ganha `surface2` + escala 0,97 por 80 ms. Sem ripple do M3.
- **Transições**: camadas sobem em 220 ms (`FastOutSlowIn`), descem em 180 ms. O leque abre em
  160 ms com os botões saindo da âncora em sequência de 20 ms.
- **Rotação** não perde conexão, arrasto nem camada aberta (o app já cuida disso no ViewModel).
- **Editar atalhos**: toque longo num atalho ou `Editar` → folha com a lista de combinações
  disponíveis (`KeyStroke`), agrupadas: Edição, Navegador, Janela/Hyprland, Teclas soltas. Salvar
  em `prefs`. v1 pode ser só escolher de uma lista fixa; gravar combinação livre fica para depois.

---

## 6. O que depende do host (`host/`)

O **mapa do cursor** precisa de duas coisas que hoje só existem durante o vídeo:

1. Geometria dos monitores (nome, largura, altura, posição) ao abrir a sessão de rede.
2. Posição do cursor (monitor + x,y normalizados), até 15 vezes por segundo, enquanto a sessão
   de rede estiver aberta — **sem vídeo**.

Enquanto isso não existir, o card mostra a fileira de workspaces sozinha (o caso "sem dados de
monitor" da seção 3). O resto do design não depende do host.

---

## 7. Ordem de implementação sugerida

Cada passo deixa o app utilizável.

1. Tokens, fontes e primitivas (`DesignTokens.kt`, `res/font`, `Key`, `MacroKey`, `Segmented`,
   folha base).
2. Base em retrato **sem** o mapa: `StatusLine`, fileira de workspaces, `ShortcutRow`, `Trackpad` +
   `ClickBar`, `Dock`. Estados de conexão da tabela 4.2.
3. Camadas de teclas e de texto. Voltar fecha camada.
4. Folhas Computador e Ajustes (mover a sensibilidade). Dicas de gestos.
5. Paisagem: base e camada de teclas.
6. Ver PC / Tela extra: chip com menu, borda de workspaces, âncora e leque.
7. Mapa do cursor (depois do passo do host, seção 6).
8. Editar atalhos.

---

## 8. Checklist de aceite

- [ ] Em retrato no S24 Ultra, a base não rola e o trackpad tem pelo menos 40% da altura da tela.
- [ ] Abrir e fechar Teclas, Texto e qualquer folha não move status, card, atalhos nem trackpad
      (compare prints antes/depois).
- [ ] Todo alvo de toque tem ≥ 44 dp; teclas ≥ 48 dp.
- [ ] Nenhum rótulo cortado a 412 dp, com escala de fonte 1,0 e 1,3.
- [ ] Contraste de texto ≥ 4,5:1 (≥ 3:1 a partir de 24 sp).
- [ ] O acento só aparece nos lugares do princípio 4.
- [ ] Toda tecla, atalho e zona de clique vibra.
- [ ] Voltar fecha a camada/folha/leque do topo antes de sair da tela.
- [ ] A camada de teclas tem clique esquerdo/direito/Segurar sem fechar.
- [ ] Por Bluetooth, Ver PC aparece tracejado e explica por que não abre.
- [ ] Com CAPS ligado, o emblema aparece no status e Enviar fica bloqueado com o motivo.
- [ ] Ver PC abre em repouso: só a âncora, o chip e a borda de workspaces, todos translúcidos.
- [ ] A folha Computador por Bluetooth mostra só computadores por padrão.
- [ ] Toda camada tem um botão visível para fechar; o de teclas fica no mesmo lugar do botão que
      a abriu.
- [ ] Em paisagem, as colunas têm o mesmo topo e a mesma base (compare com `screens/13` e `14`).
- [ ] Em paisagem, as margens esquerda e direita são iguais apesar do recorte da câmera.
- [ ] Nenhuma folha desenha por baixo da barra de status.
