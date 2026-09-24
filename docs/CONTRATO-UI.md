# Contrato UI ↔ lógica (para quem gerar as telas do redesenho)

O app **já tem** um ViewModel real e testado: `app/src/main/java/com/sandevsystems/omarchyremote/KeypadViewModel.kt`. Ele fala Bluetooth HID, rede via Tailscale (host em `host/`), vídeo (Ver PC e Tela extra), workspaces e gestos, e foi validado no S24 Ultra (veja `docs/VALIDACAO.md`).

**Não crie outro ViewModel nem estados paralelos.** As telas do redesenho (canvas "Omarchy Remote — redesenho", páginas "Decidido") devem só **ler** o estado abaixo e **chamar** estas ações. Se faltar algo, peça o acréscimo aqui, com nome e comportamento, em vez de simular na UI.

## Estado (somente leitura na UI)

| Nome | Tipo | Uso no design |
|---|---|---|
| `connection` | `StateFlow<ConnectionState>` | status do topo (ponto, nome do PC), tela "Sem computador". Estados: `PermissionRequired`, `BluetoothOff`, `Starting`, `Ready`, `Connecting(host)`, `Connected(host)`, `Disconnected(reason)`, `Error(message)` |
| `transport` | `Transport.BLUETOOTH \| NETWORK` | texto "Bluetooth"/"Rede" no status; o segmentado da folha Computador |
| `hosts` | `List<Host(name, address, isComputer)>` | lista de pareados (chamar `loadHosts()` ao abrir a folha) |
| `lastHostAddress` | `String?` | marca "último" / "Conectado agora" |
| `networkAddress`, `pairingCode` | `String` (editáveis) | campos da folha Rede; o código é segredo e deve ficar mascarado |
| `workspaces` | `StateFlow<List<Workspace(id, monitor, windows, active, focused)>>` | fileira WS e abas da borda no Ver PC (só pela Rede; vazio no Bluetooth: usar 1–6 fixos) |
| `modifiers` | `Int` (bits `ModifierKeys.CTRL/SHIFT/ALT/SUPER`) | modificadores ativos (tecla acesa) |
| `dragging` | `Boolean` | botão "Segurar" do trackpad aceso |
| `draft`, `layout`, `typing` | texto, `KeyboardLayout.ABNT2 \| US`, envio em andamento | camada Texto |
| `sensitivity`, `naturalScroll`, `tapToClick`, `videoTouchpad` | preferências | Ajustes |
| `hintsSeen` | `Boolean` | mostrar "Dicas de gestos" na primeira vez |
| `message` | `String?` | aviso/erro próximo da ação (`dismissMessage()`) |
| `capsLock` | `StateFlow<Boolean>` | aviso Caps Lock (Bluetooth) |
| `videoMode` | `NONE \| DISPLAY \| SCREEN` | qual tela cheia está aberta |
| `videoSize`, `monitors`, `currentMonitor`, `cursor`, `videoHold` | vídeo | Ver PC / Tela extra |
| `monitorMap` | `StateFlow<List<MonitorInfo(name, x, y, width, height, workspace, extra)>>` | mapa "Onde está o cursor" (só pela Rede; geometria lógica do Hyprland) |
| `cursorAt` | `StateFlow<CursorAt(monitor, x, y)?>` | ponto do cursor no mapa (x/y de 0 a 1 dentro do monitor; ~15 Hz, só quando muda) |
| `agents` | `StateFlow<List<Agent(id, kind, status, title, workspace, cwd, seq)>>` | agentes do herdr (status: idle/working/blocked/done/unknown) |
| `herdrAvailable` | `StateFlow<Boolean>` | falso até o host mandar a lista (host sem herdr não manda nada) |
| `agentText` | `StateFlow<Pair<id, texto>?>` | últimas linhas lidas de um agente |
| `pendingAgent` | `String?` | agente a abrir (notificação tocada) |
| `terminalOpen`, `termModifiers` | `Boolean`, `TermModifiers(ctrl, alt)` | tela do terminal e modificadores armados |
| `followAgents` | `Boolean` | "Acompanhar em segundo plano" |
| `shortcutSlots` | `List<Shortcut>` (5) | fileira de atalhos da base; catálogo em `input/Shortcuts` |

## Ações

- **Conexão:** `start()`, `onForeground()`/`onBackground()` (ciclo de vida da Activity), `chooseTransport(t)`, `loadHosts()`, `connect(host)`, `connectNetwork()`, `disconnect()`, `retry()`, `releaseAll()`.
- **Trackpad da base:** o reconhecedor é `input/TouchpadGesture` (testado). Ligar as ações dele em `move(dx, dy)` (em dp), `click(MouseButtons.LEFT|RIGHT|MIDDLE)`, `setDrag(on)`, `scrollSteps(n)` e `stepWorkspace(±1)`. A barra Esquerdo/Segurar/Direito chama `click(...)` e `toggleDrag()`.
- **Teclas:** `pressKey(usageHID)` (usa `modifiers` e os solta depois: o modificador vale para a próxima tecla), `toggleModifier(mask)`, `releaseModifiers()`, `pressShortcut(KeyStroke)` (atalho completo, ex. `KeyStroke(0x06, CTRL)` = Ctrl+C; uso 0 = só o modificador, ex. Super sozinho).
- **Atalhos da base:** `setShortcutSlot(posição, idDoCatálogo)` (salvo).
- **herdr:** `readAgent(id)`, `agentKeys(id, keys)` (enter, esc, tab, up, down, left, right, y, n, ctrl+c), `agentPrompt(id, texto)`, `focusAgent(id)`, `openTerminal()`, `closeTerminal()`, `startTerminal(cols, rows)` (chamado pela página), `termType(texto)`, `termKey(nome)` (esc, tab, enter, backspace, setas, prefix, ctrl, alt), `termResize(cols, rows)`, `termSink` (a página recebe a saída), `changeFollowAgents(on)`.
- **Omarchy:** `pairFromQr(texto)` (QR `keypad://pair?host=…&code=…`), `pendingOpen` ("screen" | "extra" | "terminal", pedido pelo menu Phone do PC). O tema chega pela rede e é aplicado em `KeypadColors` (estado do Compose; `ThemePalette` faz o mapeamento).
- **Mensagens:** `showMessage(texto)`, `dismissMessage()`.
- **Texto:** `draft = ...`, `changeLayout(l)`, `sendText()`, `cancelTyping()`.
- **Workspaces:** `goToWorkspace(id)`, `stepWorkspace(±1)` (pela rede usa o Hyprland; pelo Bluetooth, `Super+N`).
- **Vídeo:** `openScreen(wPx, hPx, monitor?)` (Ver PC), `openDisplay(wPx, hPx)` (Tela extra), `switchMonitor(name)`, `closeVideo()`, `attachVideoSurface(surface)`/`detachVideoSurface()`, e os eventos `videoPointer(...)` (modo trackpad) e `videoTouch(...)` (toque direto), `toggleVideoHold()`.
- **Ajustes:** `changeSensitivity`, `changeNaturalScroll`, `changeTapToClick`, `changeVideoTouchpad`, `markHintsSeen`, `showHints`.

## Pendências do redesenho: resolvidas

1. **Miniatura ao vivo** → substituída pelo mapa do cursor (`monitorMap` + `cursorAt`), decisão do design v2. O código de miniatura foi removido.
2. **Modificadores** → ficam valendo para a próxima tecla (DESIGN v2: "Ctrl vale para a próxima tecla · Soltar").
3. **Super sozinho** → suportado pelo host (uso 0 = só modificadores).
4. **Editar atalhos** → implementado (`shortcutSlots`, `setShortcutSlot`).

A UI do design v2 está em `ui/` (`Base`, `Layers`, `Sheets`, `MainScreen`, `RemoteScreen`, `FullScreen`, `Primitives`, `Glyph`, `Theme`, `DesignTokens`).
