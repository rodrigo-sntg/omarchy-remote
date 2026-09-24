# Plano técnico v2 — Omarchy Remote como extensão do Omarchy

Data: 23/09/2026. Base: app e host funcionais (Bluetooth HID, rede via Tailscale, Ver PC, Tela extra, workspaces, atalhos, design v2), validados no S24 Ultra (`docs/VALIDACAO.md`). Este plano soma por cima; **nada do que existe é removido**.

Tudo que está marcado como *verificado* foi lido no PC do usuário (Omarchy 4.0.0.alpha, Hyprland 0.56.2, herdr 0.8.2, Tailscale) em 23/09/2026. O resto é decisão de projeto.

## 1. Tese

Quem usa Omarchy está com as mãos no teclado. Ali o celular perde para o teclado em quase tudo. O celular só vira parte do sistema quando cobre o que o teclado não cobre:

| Situação | O que o celular faz | Fase |
|---|---|---|
| Longe do PC, trabalhando (viagem, sofá, outra sala) | Terminal do herdr em texto puro, Ver PC, teclado e trackpad, tudo pela Tailscale | 1, 3 |
| Longe do PC, só acompanhando | Pager dos agentes: avisou que terminou ou travou pedindo aprovação; responder de onde estiver | 1 |
| Na mesa, segunda superfície | Estado de relance, o menu do Omarchy, os atalhos, toques únicos | 4 |
| O que o PC não tem | S Pen, digital, presença | 5 |

E o app precisa **ser** do Omarchy: instalado como plugin, entrada no menu, cores do tema ativo, avisos pela notificação e pelo OSD do Omarchy (fase 2).

## 2. Princípios técnicos

1. **Só a superfície pública do Omarchy.** Comandos `omarchy-*` (cada um com `# omarchy:summary` e `# omarchy:args`), `hyprctl -j`, hooks, `omarchy-menu.jsonc`, plugins do shell. Nunca ler Lua interno nem `hl.dsp`. Checar presença com `omarchy-cmd-present` e degradar sem quebrar.
2. **Só a CLI do herdr.** `herdr agent|pane|tab|workspace|session` devolvem JSON (`{"id","result"}` ou `{"error":{"code","message"}}`, sempre rc 0). O host nunca fala no socket do herdr diretamente.
3. **Uma conexão de controle, várias funções.** Tudo que é estado (workspaces, cursor, agentes, tema) viaja no canal `/v1` já existente, com os envelopes v1 (`v, sessionId, seq, type, payload`, ack, heartbeat 6 s). Fluxos pesados (vídeo, terminal) têm WebSocket próprio.
4. **Segurança igual em todos os canais.** Token `X-Keypad-Token`, `Origin` recusado, `tailscale whois` com lista de máquinas permitidas. Nada é exposto fora da interface Tailscale.
5. **Testável sem hardware.** Toda regra nova nasce como função pura com teste (pytest no host, JUnit no app) antes de ser ligada em socket, uinput ou UI.
6. **Distinguir implementado de provado no aparelho** (`docs/VALIDACAO.md`).

## 3. Arquitetura

```
 S24 Ultra (app)                              PC Omarchy (host, python3 + aiohttp)
 ┌──────────────────────────┐   Tailscale   ┌──────────────────────────────────┐
 │ NetworkController ───────┼── /v1 ───────►│ Session (input, workspaces,      │
 │   workspaces/monitors/   │   controle    │   agentes, tema)  ── uinput      │
 │   cursor/agents/theme    │               │        │  ── hyprctl / socket2   │
 │ VideoPipe ───────────────┼── /v1/screen ►│ captura (wf-recorder) ── H.264   │
 │                          ├── /v1/display►│ monitor virtual                  │
 │ TermChannel + xterm.js ──┼── /v1/term ──►│ PTY ── herdr --session default   │
 │ ConnectionService (FGS)  │               │ HerdrWatch ── herdr agent list   │
 │ notificações Android     │               │ hooks/menu/plugin do Omarchy     │
 └──────────────────────────┘               └──────────────────────────────────┘
```

Novos módulos do host: `herdr.py` (adaptador da CLI, assíncrono), `agents.py` (resumo, diffs, alertas), `term.py` (PTY), `theme.py` (paleta do Omarchy), `omarchy.py` (notificação, OSD, presença de comandos). Novos módulos do app: `network/TermChannel.kt`, `network/Agents.kt`, `ui/TerminalScreen.kt`, `ui/AgentsSheet.kt`, `ConnectionService.kt`, `assets/term/` (xterm.js).

## 4. Fase 1 — herdr em qualquer lugar

Plano executável: `docs/superpowers/plans/2026-09-23-fase1-herdr.md`.

### 4.1 O que o herdr oferece (verificado)

- Servidor persistente com sessão nomeada `default`, socket `~/.config/herdr/herdr.sock`, `detached_server_daemon: true`. Um cliente TUI (`herdr`, ou `herdr --session default`) se conecta a ele; `herdr --remote <ssh>` faz o mesmo por SSH.
- `herdr agent list` → `result.agents[]` com `pane_id` (`w5:p1`), `agent` (`claude`, `codex`…), `agent_status` (`idle | working | blocked | done | unknown`), `workspace_id`, `tab_id`, `cwd`, `terminal_title_stripped`, `state_change_seq`. `blocked` = o herdr reconheceu um pedido de aprovação ou pergunta. `done` = terminou sem ninguém ver.
- `herdr agent read <alvo> --lines N --format text` → texto puro no stdout. `--format ansi` mantém cores.
- `herdr agent send-keys <alvo> <tecla>...` (`esc` é o nome canônico do Escape), `herdr agent prompt <alvo> <texto>` (recusa com `agent_blocked` se o agente está travado), `herdr agent focus <alvo>`, `herdr agent wait <alvo> --until <estado> --timeout <ms>`.
- `herdr pane read|send-text|send-keys`, `herdr workspace list` (`label`, `number`, `agent_status`, `pane_count`), `herdr tab list`.
- Omarchy: `Super+Ctrl+Enter` abre o herdr no terminal (`omarchy-launch-terminal-herdr`); `omarchy-menu-herdr-keybindings`; o config do Omarchy usa prefixo `Ctrl+Espaço`.

### 4.2 Terminal do herdr no celular (`/v1/term`)

- **Host:** WebSocket `/v1/term`, mesma autorização do vídeo. Primeira mensagem `{"type":"term.start","cols":C,"rows":R,"session":"default"}`; validação `20 ≤ cols ≤ 500`, `5 ≤ rows ≤ 200`, `session` casa `^[a-z0-9_-]{1,32}$`. O host abre um PTY (`os.openpty`, `TIOCSWINSZ`) e executa `herdr --session <s>` com `TERM=xterm-256color`, `COLORTERM=truecolor`, ambiente do serviço (que já carrega o da sessão gráfica). Bytes do PTY → frames binários; frames binários do celular → PTY; `{"type":"term.resize","cols","rows"}` → `TIOCSWINSZ`. Fechar o WebSocket envia SIGHUP ao cliente herdr (o servidor do herdr continua). Uma sessão de terminal por vez: a nova assume a anterior (mesma regra do vídeo).
- **App:** `WebView` com xterm.js 5.5 vendorizado em `assets/term/` (MIT), addon-fit, fonte JetBrainsMono Nerd Font (OFL) para os glifos do herdr. O socket é do Kotlin (`TermChannel`, OkHttp, cabeçalho do token), e faz ponte para o JS: saída via `evaluateJavascript("term.write(base64)")`, entrada via `@JavascriptInterface send(data)`, tamanho via `resize(cols, rows)` do addon-fit.
- **Barra de teclas do terminal:** Esc, Tab, Ctrl (armado para a próxima tecla, vira byte de controle), Alt (prefixo ESC), setas, Enter, e **Prefixo** (`Ctrl+Espaço`, byte NUL) para os comandos do herdr; botão "Teclado" abre o teclado do Android sobre o terminal. Reaproveita `Key`/`KeyRow` do design v2.
- **Latência e banda:** texto puro; funciona em 4G. Sem vídeo.

### 4.3 Agentes (pager)

- **Host:** `HerdrWatch` roda enquanto há um celular conectado: a cada 2 s, `herdr agent list`; resumo por agente (`id = pane_id`, `kind`, `status`, `title`, `workspace`, `cwd` só o último diretório, `seq`). Manda `{"type":"agents","agents":[…]}` ao conectar e quando algo muda. Transições **para** `blocked` ou `done` viram `{"type":"agent.alert","agent":{…}}` (nunca na primeira leitura). Se `herdr` não está instalado ou o servidor não roda, manda `{"type":"agents","agents":[],"available":false}` e o app mostra "herdr não está rodando no PC".
- **Comandos do celular (envelope v1, com ack):** `agent.read {id, lines}` → resposta `{"type":"agent.text","id","text"}`; `agent.keys {id, keys:[…]}` (lista permitida: `enter esc tab up down left right y n ctrl+c`); `agent.prompt {id, text ≤ 4000}`; `agent.focus {id}`. Erros do herdr voltam no ack com `ok:false` e `error`.
- **App:** folha "Agentes" com uma linha por agente (ponto de estado: `working` acento pulsando, `blocked` perigo, `done` acento, `idle` apagado), detalhe com as últimas 40 linhas (mono), teclas rápidas (Enter, Esc, y, n, ↑, ↓), campo de prompt, "Abrir no terminal" (faz `agent.focus` e abre a tela do terminal). Alertas viram notificações do Android (canal "Agentes", permissão `POST_NOTIFICATIONS`), com ação "Abrir".
- **Conexão em segundo plano:** `ConnectionService` em primeiro plano (`foregroundServiceType="connectedDevice"`, coberto pela permissão `BLUETOOTH_CONNECT` já concedida), notificação fixa "Ligado ao meu-pc · N agentes", ligado por "Acompanhar agentes" nos Ajustes. Com ele ligado, `onBackground` não derruba a rede. O `NetworkController` passa a viver no `Application` (não no ViewModel) para sobreviver à Activity. Pede isenção de otimização de bateria uma vez. **Primeira tarefa da fase é a prova de 24 h no S24**: se o Samsung matar a conexão mesmo assim, o pager fica restrito ao app aberto e a fase segue com o terminal.

### 4.4 Critérios de aceite da fase 1

- No S24, pela Tailscale fora da rede local (4G): abrir o terminal, ver o herdr com cores e ícones, trocar de aba com o prefixo, digitar num pane, fechar e reabrir sem perder a sessão.
- Um agente que entra em `blocked` gera notificação no celular em até 5 s com o app em segundo plano; tocar abre o detalhe; Enter/`y` destrava; `done` também notifica.
- `herdr` ausente ou parado: o app explica, nada quebra.
- Testes: host (PTY com `sh -c 'printf ready; cat'`, validações, diffs de agentes, alertas), app (parse de `agents`/`agent.alert`/`agent.text`, teclas → bytes, Ctrl armado).

## 5. Fase 2 — Extensão do Omarchy

### 5.1 Plugin do shell (verificado: `~/.config/omarchy/plugins/<id>/manifest.json`, `schemaVersion: 1`, `kinds`, `entryPoints`; recarrega ao salvar; `omarchy plugin add <git> --enable`)

- Repositório `omarchy-phone` (ou o próprio `omarchy-remote`) com `manifest.json`: `id: "rodrigo.phone"`, `kinds: ["bar-widget","service"]`, `entryPoints: {"barWidget":"BarWidget.qml","service":"Service.qml"}`, `barWidget.defaultSection: "right"`.
- `Service.qml`: `Process` do Quickshell mantendo `python3 -m keypad_host --allow …` vivo (relança ao sair). Substitui a unit do systemd; a unit continua documentada como alternativa.
- `BarWidget.qml`: ícone de celular (Nerd Font) aceso quando há sessão, painel com: nome do celular, bateria (o app manda `{"type":"phone","battery":…}` a cada 60 s), botões "Enviar área de transferência", "Ver no celular" (abre `/v1/screen` do lado do PC = manda `{"type":"open","what":"screen"}` ao app), "Parear (QR)".
- **Host sem venv:** `omarchy-pkg-add python-aiohttp python-evdev` no `install.sh` do plugin; `run.sh` usa `python3` do sistema quando os módulos importam.

### 5.2 Menu, hooks, feedback

- `~/.config/omarchy/extensions/omarchy-menu.jsonc` (verificado) com `"phone": {"icon":"󰄜","label":"Phone"}` e filhos: `phone.pair` (`omarchy-launch-floating-terminal-with-presentation "omarchy-remote pair"` mostra o QR com `qrencode -t ANSIUTF8`), `phone.clipboard` (`omarchy-remote send-clipboard`), `phone.screen`, `phone.extra`, `phone.lock-away` (`checked` lê `omarchy-toggle-enabled phone-lock`).
- Hooks (verificado: `~/.config/omarchy/hooks/<nome>.d/`): `theme-set.d/omarchy-remote` → `pkill -USR1 -f keypad_host`; o host, em SIGUSR1 e a cada nova sessão, roda `omarchy-theme-color --all` e manda `{"type":"theme","name","mode","colors":{accent,background,dark_background,foreground,muted,selection,red…}}`. `battery-low.d/` → alerta no celular. `post-boot.d/` → nada (o plugin já sobe o host).
- Feedback no PC: `omarchy-notification-send -g 󰄜 "Celular conectado" "meu-pc ↔ s24"` ao abrir sessão; `omarchy osd -m "Área de transferência enviada"` nas ações.

### 5.3 Tema dinâmico no app

- `KeypadColors` deixa de ser `object` fixo e vira `data class KeypadPalette` num `CompositionLocal`; `DesignTokens.kt` mantém a paleta padrão (grafite + lima) como fallback quando o tema do PC não chegou. Mapeamento: `Accent ← accent`, `Bg ← darker_background`, `Surface1 ← dark_background`, `Surface2 ← background`, `Surface3 ← selection`, `Text ← foreground`, `TextDim ← bright_foreground`, `TextMute ← muted`, `Danger ← bright_red`, `Line ← muted` a 60%. Contraste mínimo do acento sobre o fundo checado (WCAG 3:1); se falhar, clareia o acento.
- Fonte: quando `omarchy-font-current` for JetBrainsMono, o app já a tem; outras fontes ficam no padrão.
- Persistir a última paleta recebida para abrir já com o tema certo.

### 5.4 Pareamento por QR

- `omarchy-remote pair` imprime `keypad://pair?host=meu-pc.tail1234.ts.net&code=<token>` em QR (qrencode, verificado instalado). No app, "Ler QR" na folha Computador usa `com.google.android.gms:play-services-code-scanner` (sem permissão de câmera); preenche endereço e código e conecta. O token passa a ser guardado com `EncryptedSharedPreferences` (Keystore).

### 5.5 Aceite

`omarchy plugin add … --enable` deixa o ícone na barra e o host de pé; `Super+Espaço → Phone` funciona; mudar o tema no PC muda o app em < 2 s; QR conecta sem digitar nada.

## 6. Fase 3 — Trabalhar longe (desempenho)

- **Latência medida:** o host insere o carimbo de tempo (SEI H.264 `user_data_unregistered` ou mensagem JSON `{"type":"frame","t":…}` por quadro) e o app mede até o `onSurfaceTextureUpdated`; mostra no chip do Ver PC ("Ver PC · 84 ms · 41 fps"). Sem medir não se otimiza.
- **Troca de monitor sem reabrir:** `/v1/screen` aceita `{"type":"screen.switch","monitor"}`: o host troca só a captura na mesma conexão e manda `{"type":"screen",…}` novo; o app reconfigura o decodificador na mesma superfície. Meta < 300 ms.
- **Qualidade adaptável:** o app informa `{"type":"screen.quality","fps","maxWidth"}` quando a fila do decodificador cresce ou a rede engasga; o host reinicia a captura com o novo tamanho. Presets "Nítido / Equilibrado / Leve (4G)".
- **Codificador:** testar `gpu-screen-recorder` (instalado; usado pelo Omarchy) com saída para pipe; NVENC depende de VRAM livre (ollama). Manter x264 como fallback.
- **Eventos do Hyprland:** `socket2` (`workspace`, `focusedmon`, `openwindow`, `closewindow`, `windowtitle`) substitui a consulta a cada 1/15 s para workspaces/monitores; o cursor só é consultado com o mapa visível (o app manda `{"type":"watch","cursor":true|false}`).
- **Área de transferência nos dois sentidos:** `clipboard.set {text}` → `wl-copy`; `clipboard.get` → `wl-paste -n` (limite 64 KB); botão "Colar do PC" na camada Texto e "Copiar para o PC" no compartilhamento do Android (`ACTION_SEND` de texto/URL).
- **Teclado físico no celular:** `onKeyEvent` na Activity converte teclas do teclado Bluetooth/USB do celular em `keyboard.tap` (usos HID) quando a camada de teclas ou o Ver PC está aberto.

## 7. Fase 4 — Segunda tela na mesa

- **Tela "Agora":** cartões vindos do canal de controle: agentes (fase 1), notificações acionáveis do PC (o host lê o histórico do shell de notificações ou monitora `org.freedesktop.Notifications` no D-Bus; só `app-name` de interesse: omarchy-action, herdr, crash), lembrete ativo (`omarchy-reminder show --json`), mídia (`omarchy-shell media`), atualização disponível (`omarchy-update-available`).
- **Menu do Omarchy no celular:** o host mescla `default/omarchy/omarchy-menu.jsonc` com o do usuário, avalia `when`/`checked` e manda a árvore; o app renderiza com os mesmos glifos e busca; tocar executa `action` no PC (`bash -lc`). Provedores dinâmicos (`apps`) viram pedidos sob demanda.
- **Paleta de atalhos:** `hyprctl binds -j` (verificado: 240 atalhos com `description`) → busca no celular; executar = `hyprctl dispatch` do `dispatcher`+`arg` do bind, não tecla.
- **Janelas:** `hyprctl clients -j` → lista por workspace com título e classe; ações `focuswindow`, `movetoworkspace`, `togglefloating`, `fullscreen`, `movetoworkspacesilent special:scratchpad`.

## 8. Fase 5 — O que só o S24 Ultra tem

- **S Pen como mesa digitalizadora:** eventos `MotionEvent.TOOL_TYPE_STYLUS` (pressão, inclinação, hover) no Ver PC ou na Tela extra → `/v1/screen` `{"type":"pen","x","y","pressure","tilt","down"}` → dispositivo uinput separado `BTN_TOOL_PEN`, `ABS_X/Y/PRESSURE/TILT_X/TILT_Y` com `INPUT_PROP_DIRECT`.
- **Ditado:** `SpeechRecognizer` do Android → texto na camada Texto → `sendText`.
- **Presença:** RSSI do Bluetooth (já pareado) abaixo de um limite por 30 s → `omarchy-system-lock`. Destravar fica de fora.
- **Bloco de Configurações Rápidas** (`TileService`) "Ver PC" e widget de agentes.

## 9. Riscos e como cada um é tratado

| Risco | Tratamento |
|---|---|
| Samsung mata a conexão em segundo plano | Prova de 24 h é a tarefa 1 da fase 1; FGS `connectedDevice` + isenção de bateria; se falhar, pager só com o app aberto |
| Omarchy 4 alpha muda a API | Só comandos `omarchy-*` e arquivos documentados no skill oficial (`default/agents/skills/omarchy/*.md`); `omarchy-cmd-present` antes de usar |
| herdr muda a CLI | Adaptador único (`herdr.py`) com testes que fixam a forma do JSON; versão mínima 0.8 checada em `herdr --version` |
| xterm.js no WebView do Samsung (composição do teclado) | Teste manual no S24 na tarefa do terminal; alternativa registrada: `terminal-view` do Termux |
| Token em texto claro no celular | `EncryptedSharedPreferences` na fase 2 |
| Agente recebe tecla errada | Lista permitida de teclas; `agent.prompt` só quando `idle`/`done`; confirmação para `ctrl+c` |

## 10. Ordem e entregas

1. **Fase 1 — herdr em qualquer lugar** (terminal, agentes, segundo plano). Plano executável pronto.
2. **Fase 2 — Extensão do Omarchy** (plugin, menu, hooks, tema, QR, host sem venv).
3. **Fase 3 — Trabalhar longe** (latência, troca rápida, qualidade, eventos, clipboard, teclado físico).
4. **Fase 4 — Segunda tela** (Agora, menu do Omarchy, atalhos, janelas).
5. **Fase 5 — S Pen, ditado, presença, tile.**

Cada fase termina com `docs/VALIDACAO.md` atualizado, separando o que foi implementado do que foi provado no S24. Os planos executáveis das fases 2 a 5 são escritos quando cada uma começa, com o que a fase anterior ensinou.
