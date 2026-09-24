# Validação — V0.1 (Bluetooth HID)

Atualizado em 23/09/2026.

Legenda: **passou** · **falhou** · **não testado**. "Implementado" significa código escrito e coberto por build/testes. Não significa que funcione no hardware.

## Resumo

| Item | Situação |
|---|---|
| Build, testes JVM e lint | **passou** (local) |
| UI abre e registra o app HID no emulador | **passou** (emulador, não comprova HID real) |
| Teclado e mouse Bluetooth no S24 Ultra → Omarchy | **passou** na prova mínima (mover, clicar/arrastar, digitar `abc123`) em 23/09/2026. O restante da matriz ainda está pendente |

A prova mínima exigida pelo plano (parear, conectar, mover, clicar e digitar `abc123` a partir do S24 Ultra) **passou**. O perfil HID funciona neste aparelho e com este host. Rede (Fase E) e vídeo (Fase F) não foram iniciados: falta completar a matriz de estabilização (V0.2).

## Ambiente

| Componente | Valor observado |
|---|---|
| Computador | Linux 7.2.5-3-omarchy, Hyprland 0.56.2, BlueZ 5.87, adaptador `meu-pc #3` ligado |
| Layout do host | `hyprctl devices`: `l "br,us", v "abnt2,intl", o "compose:caps"`, índice ativo 0 (br/abnt2) em cada teclado |
| Build | JDK 17, Gradle Wrapper 8.14.3 (SHA-256 oficial), AGP 8.13.2, Kotlin 2.1.20, compileSdk/targetSdk 36, minSdk 28 |
| S24 Ultra | SM-S928B (`e3q`), Android 16 (API 36), One UI 8.5 (`ro.build.version.oneui=80500`), build `S928BXXS6DZH2`, Bluetooth `98:D7:42:1F:70:9B` |
| Adaptador usado no PC | `meu-pc #3` 8A:88:1B:60:21:C1 (padrão do BlueZ; o PC tem um segundo adaptador, `meu-pc` BC:C7:46:9E:E7:45) |

Observação sobre o host: com `compose:caps`, a tecla Caps Lock do computador funciona como Compose. É provável que o LED de Caps Lock nunca seja enviado ao celular. O bloqueio de envio com Caps Lock ativo foi implementado, mas não pôde ser exercitado.

## Verificações automatizadas (passou)

Comando:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ANDROID_HOME=/home/voce/Android/Sdk \
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Resultado: build ok, **38/38 testes**, lint sem erros. Avisos restantes: apenas versões mais novas disponíveis para dependências fixadas pelo plano.

| Suíte | Testes | Cobre |
|---|---|---|
| `HidReportsTest` | 7 | bytes exatos dos reports, máscara de botões, soltura zerada, eixos negativos, divisão de movimentos com soma preservada, LED de Caps Lock, IDs do descriptor |
| `KeyboardLayoutTest` | 8 | letras/maiúsculas, `ação`, `Órgão`, `São Paulo`, `ç/Ç`, acentos literais, Unicode decomposto, pontuação de shell ABNT2 vs US, caracteres não suportados |
| `PointerAccumulatorTest` | 5 | subpixel, troca de sinal, soma por frame, limiar de rolagem |
| `TrackpadGestureTest` | 8 | toque = esquerdo, dois dedos = direito, slop, sem clique após rolagem, sem salto ao entrar/sair dedo, cancelamento (inclusive sem dedo na tela, após desconexão) |
| `WorkspacesTest` | 2 | `Super+1…0` (10 = tecla 0) e `Super[+Shift]+Tab`, conforme `default/hypr/bindings/tiling.lua` do Omarchy |
| `InputSessionTest` | 8 | pressão antes de soltura, textos não se intercalam, tecla especial espera o texto em andamento, cancelamento solta a tecla, envio recusado interrompe, sessão fechada descarta texto pendente e nova sessão não recebe nada antigo, arraste mantém botão, rolagem |

Os testes de `HidInputSession` passaram por uma verificação de mutação: sem a soltura no `finally` e sem o bloqueio após `close()`, 4 testes falham.

Os mapeamentos ABNT2 foram conferidos contra `/usr/share/X11/xkb/symbols/br` deste computador. `/` e `?` usam AltGr+Q/W (definidos em `br(abnt2)`), em vez da tecla HID 0x87.

## Revisão de código

Uma revisão independente do projeto inteiro não encontrou nenhum problema crítico e confirmou o mapeamento ABNT2 contra o xkb. Correções aplicadas:

- Após uma desconexão, o primeiro toque no trackpad era ignorado. Corrigido e coberto por teste.
- Uma tecla especial (Enter) podia cair no meio de um texto em envio. Agora espera o texto terminar (coberto por teste) e as teclas ficam desabilitadas durante o envio.
- "Tentar novamente" não reconectava depois de uma desconexão comum. Agora vira "Reconectar" ao último computador.
- A intenção de reconectar ao voltar ao app ficava só na memória. Agora é persistida, porque a Samsung pode encerrar o processo em segundo plano. Também há uma tentativa automática se o desregistro chegar depois da volta.
- Um clique durante o fim de um arraste podia pressionar o botão de novo. Corrigido.

As três últimas correções estão no ViewModel (Android) e não têm teste JVM. Foram verificadas por build, lint e execução no emulador, e ainda precisam ser confirmadas no S24 Ultra.

## Emulador (passou; não substitui o hardware)

AVD `TrainOS_Phone_6` (API 36.1, x86_64), iniciado com `-read-only -no-snapshot` para não alterar o AVD.

| Verificação | Resultado |
|---|---|
| APK instala e abre (cold start ~1,2 s) | passou |
| Sem permissão: aviso + "Permitir"; negar mantém o aviso; aceitar leva a "Pronto" | passou |
| `onAppStatusChanged registered=true` após aceitar | passou (pilha Bluetooth virtual do emulador) |
| Ir para Home → `registered=false`; voltar → `registered=true` de novo | passou (confirma o desregistro automático do Android em segundo plano) |
| Folha "Computador" sem pareados mostra instrução de pareamento | passou |
| Rotação para paisagem: trackpad e controles lado a lado, rascunho preservado | passou |
| Teclado virtual aberto: campo visível e trackpad com cerca de metade da tela | passou (após correção de layout) |
| Ícones da barra de status legíveis no tema escuro | passou (após correção) |

## Hardware: S24 Ultra + Omarchy (23/09/2026)

Os testes foram conduzidos pelo `adb`: toques e arrastes reais injetados na tela do app, que passam pelo mesmo caminho de um dedo (Compose → reconhecedor de gestos → report HID → Bluetooth). O resultado foi medido no host com `hyprctl`, `/proc/bus/input/devices`, a seleção primária do Wayland e um arquivo gravado por `cat` numa janela de teste, para que nenhum texto fosse executado.

| Caso | Resultado | Evidência |
|---|---|---|
| Permissões pelo fluxo do app (Dispositivos próximos) | passou | `BLUETOOTH_CONNECT` e `BLUETOOTH_ADVERTISE` `granted=true, USER_SET` |
| Registro HID em primeiro plano | passou | `onAppStatusChanged registered=true` |
| Tornar visível | passou | `ScanMode: SCAN_MODE_CONNECTABLE_DISCOVERABLE` |
| Pareamento do zero, iniciado no PC, com código confirmado nos dois lados | passou | código 939789; `Pairing successful`; o PC lista o serviço HID `0x1124` |
| Conexão HID | passou | app: `onConnectionStateChanged 8A:88:1B:60:21:C1 state=2`, tela "Conectado a meu-pc #3" |
| Dispositivos criados no Linux | passou | `S24 Ultra de Rodrigo Keyboard` (kbd, leds) e `S24 Ultra de Rodrigo Mouse`; o Hyprland aplica `Portuguese (Brazil)`, índice 0 |
| Movimento em 4 direções e diagonal | passou | cursor: direita +188, esquerda −192, subir −336, diagonal (+308, +336) com arrastes de 400–600 px no celular |
| Clique + arraste (botão Arrastar) | passou | seleção no terminal: `Teste Omarchy Remote: o texto vai para u` |
| **Prova mínima `abc123`** | passou | arquivo: `abc123` + Enter do app |
| Símbolos ABNT2 e de shell | passou | arquivo: `/?\|;:[]{}<>'"!@#$%&*()_+-=,.` (`/ ?` via AltGr, `\ \|` na tecla extra do ABNT2) |
| Tecla especial Enter | passou | quebra de linha no arquivo |
| Teclas especiais Esc, Tab, ⌫, Del, Enter, ←, ↑, ↓, → | passou | janela de captura de bytes crus: `\x1b` `\t` `\x7f` `\x1b[3~` `\r` `\x1b[D` `\x1b[A` `\x1b[B` `\x1b[C` |
| Troca de workspace (fileira Workspaces) | passou | `hyprctl activeworkspace`: 2 → [1] → 1 → [3] → 3 → [‹] → 2 → [›] → 1 → [2] → 2 |
| Reconexão iniciada pelo celular a host já pareado (após reinstalar o app) | passou | `connect` → `state=1` → `state=2` em ~60 ms; lista mostra "meu-pc #3 · último" |

Correções feitas depois do teste no aparelho:

- **Arrastar mudava o layout:** o texto "Arrastando" quebrava em duas linhas na fonte do S24 Ultra e empurrava a tela. Agora o texto é sempre "Arrastar", o estado aparece pelo preenchimento do botão e é anunciado para acessibilidade. Verificado no aparelho: as posições do trackpad e dos botões não mudam ao ativar ou desativar.
- **Digitar não abria o teclado, e texto longo ficava ilegível:** agora "Digitar" foca o campo e abre o teclado do Android (`mInputShown=true`). O painel de texto substitui os controles e fica fixo logo acima do teclado. O campo mostra as últimas 4 linhas até o cursor e rola por dentro. O painel tem Fechar, Enter e Enviar. Voltar primeiro fecha o teclado e depois o painel, sem sair do app. Verificado no aparelho com um texto de cerca de 230 caracteres.
- **Faixa vazia abaixo dos controles:** o limite de metade da altura para os controles reservava espaço sem uso. Agora o trackpad ocupa todo o espaço livre e os controles só rolam quando o teclado do celular está aberto. Verificado no aparelho, com e sem o teclado.

Observações:

- As setas ficam fora da tela, à direita na fileira de teclas, e só aparecem rolando a fileira para o lado. É um problema de usabilidade a resolver.
- Na janela de teste com `cat`, Backspace só apaga a linha ainda não enviada, e Del e as setas não têm efeito visível. Isso é comportamento do `cat`, não do app.

- Micromovimentos abaixo do limiar de toque (cerca de 8 dp) são descartados antes de o gesto virar movimento. É o comportamento previsto (um toque não move o cursor), mas o ajuste fino ficou um pouco "duro". Avaliar no uso real.
- O host aplica `scroll factor: -1.00` ao mouse do celular (configuração do Hyprland). Somado à rolagem natural do app, o sentido final precisa ser avaliado com dois dedos reais.
- O PC tem dois adaptadores Bluetooth, e o gerenciador gráfico mostrou o celular duas vezes. O pareamento foi feito pelo adaptador padrão.
- A lista de pareados do app mostra outros computadores já pareados com o celular (por exemplo um MacBook). O app só conecta ao que for tocado.

## Matriz restante no S24 Ultra + Omarchy

| Caso | Situação |
|---|---|
| Negar permissões no aparelho | não testado |
| Bluetooth desligado e religado com o app aberto | não testado |
| Movimento lento e rápido com o dedo; clique duplo; clique direito (menu de contexto) | não testado (precisa de um dedo real) |
| Toque com dois dedos = clique direito | não testado (o `adb` não injeta multitoque) |
| Rolagem em navegador e terminal, sem cursor saltando | não testado |
| Acentos (`ação`, `Órgão`, `São Paulo`, `ç`, `ü`) | não testado (o `adb input text` não envia texto fora do ASCII) |
| Digitação em editor e navegador | não testado |
| Atalhos `Ctrl+C`, `Ctrl+V`, `Ctrl+Shift+C`, `Alt+Tab`, `Super+1` | não testado |
| Sair/voltar ao app → reconexão ao último host | não testado |
| Alternar orientação e abrir/fechar teclado virtual repetidamente | não testado |
| Desligar Bluetooth durante arraste e durante texto; nada preso ao reconectar | não testado |
| 30 min de uso contínuo e 10 ciclos de conexão | não testado |
| Cursor atravessando todos os monitores | não testado |
| Intervalo de 12 ms entre pressão/soltura adequado (sem teclas perdidas/repetidas) | não testado |
| Sentido e passo da rolagem (18 dp por passo) confortáveis | não testado |

## Roteiro para a prova no hardware

1. Conectar o S24 Ultra por USB com depuração ativada e autorizar o computador. Confirmar o serial com `adb devices -l`. Não usar serial de outro projeto.
2. Registrar a versão: `adb -s <serial> shell getprop ro.build.version.release` e `ro.build.version.oneui` (ou Configurações → Sobre o telefone).
3. Instalar: `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Acompanhar os callbacks: `adb -s <serial> logcat -s KeypadHid`.
5. Seguir o "Primeiro pareamento" do README. No computador, conferir que o dispositivo aparece: `hyprctl devices | grep -i -A2 keypad`.
6. Abrir um editor vazio, mover, clicar e digitar `abc123`. Registrar aqui o resultado de cada linha da matriz.

Se o perfil HID falhar no S24 Ultra, registrar a saída de `logcat -s KeypadHid` (registro, estado de conexão), as permissões concedidas e se o app estava em primeiro plano. O plano prevê usar a etapa de rede como alternativa somente após documentar essa falha.

## Fase E — Rede via Tailscale (23/09/2026)

Mudança em relação ao plano, a pedido do usuário: o transporte de rede usa **Tailscale** e **não segue** a proposta original de WSS + certificado fixado + pareamento por QR. A segurança vem de cinco camadas:

- o serviço escuta só no IP do Tailscale;
- a máquina é autorizada pelo `tailscale whois`, com o nome completo (o nome curto só vale no próprio tailnet);
- é exigido um código de pareamento de 120 bits, gerado no PC e digitado uma vez no app;
- requisições de navegador (`Origin`) são recusadas;
- o tráfego é criptografado pelo WireGuard do tailnet.

No Android, tráfego sem TLS só é permitido para `*.ts.net`. Custo: depende do Tailscale ativo nos dois lados. Uso sem Tailscale na LAN exigiria voltar ao TLS com certificado fixado e ao QR do plano.

| Verificação | Resultado | Evidência |
|---|---|---|
| Testes do host (pytest) | passou | 32/32: tabela HID→evdev (inclui posições ABNT2, sem usar o número HID como código evdev), validação do protocolo (tipos, intervalos, `bool`, versão, 16 KiB, tipo `shell.exec` recusado), sessão (ack, pong, sequência crescente, sessão errada, timeout de 6 s solta tudo, sessão fechada não injeta), servidor (403 antes do upgrade, segundo controlador recusado, desconexão solta tudo) |
| Testes JVM do app para a rede | passou | `NetworkSessionTest` 8 e `NetworkAddressTest` 2 (suíte total 48/48) |
| Host escuta só no Tailscale | passou | `ss -ltn`: `100.64.0.10:8765` e nenhum outro endereço |
| Máquina não autorizada | passou | o próprio PC (`meu-pc`) recebeu 403, com log `refused … (meu-pc)` |
| Injeção real via uinput (cliente local autorizado temporariamente) | passou | cursor +20/−38 px (com a aceleração do Hyprland), `pong`, e o tipo desconhecido encerrou a sessão |
| Dispositivos virtuais | passou | "Omarchy Remote Keyboard" e "Omarchy Remote Mouse" criados sem root (ACL do usuário em `/dev/uinput`). A criação leva ~4 s |
| Revisão independente do transporte de rede | 4 importantes, corrigidos | uma página web ou outro app do celular poderia controlar o PC (agora exige código e recusa `Origin`); o nome curto aceitava a mesma máquina vinda de outro tailnet (agora usa o nome completo); fechamento iniciado pelo PC deixava o app preso em "Conectando" (agora tratado com `onClosing` e limite de 5 s para abrir a sessão); religar o Bluetooth registrava o HID durante o uso pela rede (agora não). Menor também corrigido: a reconexão automática usa o endereço salvo |
| Proteções do host na prática | passou | sem código → 403; código errado → 403; `Origin` + código certo → 403; código certo → sessão e `pong`. Testes do host: 36/36 |
| **S24 Ultra → PC pela rede** | **passou** | ver tabela abaixo |

### Rede no hardware: S24 Ultra (`meu-celular`, 100.64.0.20) → meu-pc via Tailscale

Conexão direta pelo Wi-Fi (`tailscale status`: `active; direct 192.168.2.36`). O host rodou com `--allow meu-celular`. O celular mudou de nome no Tailscale durante o dia (antes `samsung-sm-s928b`), e o `--allow` precisa acompanhar.

| Caso | Resultado | Evidência |
|---|---|---|
| Conectar pela folha (Rede, nome `.ts.net`, código de pareamento) | passou | app: "Conectado a meu-pc"; host: `session … from meu-celular.tail1234.ts.net`; user-agent `okhttp/4.12.0` |
| Só um transporte ativo | passou | em Rede, o teclado/mouse Bluetooth do celular some do Linux (só AVRCP). Trocar para Bluetooth encerra a sessão de rede (12:19:45,970) antes do registro HID (12:19:46,004) |
| Movimento | passou | direita +348, esquerda −226, descer +339, subir −290 |
| Clique + arraste | passou | seleção: `Captura de teclas (bytes crus) - nada e executado` |
| `abc123`, símbolos e Enter do painel, em dois envios seguidos | passou | bytes: `abc123/?\\|;:[]{}<>'"!@#$%&*()_+-=,.\r` |
| Teclas especiais | passou | `\x1b \t \x7f \x1b[3~ \r \x1b[D \x1b[A \x1b[B \x1b[C` |
| Workspaces | passou | 1 (HDMI-A-1) → 2 (DP-1) → 1 |
| Sair do app e voltar | passou | HOME: host `session ended`. Ao voltar: nova sessão automática, sem reenvio |
| **Perda de rede durante arraste** | passou | Wi-Fi e dados desligados às 12:18:47. Host: `timed out` e sessão encerrada às 12:18:53,99 (≈7 s), soltando o botão. App: "Sem resposta do computador há 6 s." + Reconectar. Ao religar: Reconectar → sessão nova, cursor normal |
| Reinstalar/encerrar o app | passou | host encerra a sessão ao cair o TCP |

Defeito achado e corrigido nesse teste: **o campo de texto perdia o foco e o teclado depois de cada Enviar**, porque ficava desabilitado durante o envio, e o segundo texto não entrava. Agora o campo fica somente leitura durante o envio e mantém o foco. Verificado com dois envios seguidos.

Observação: com o `svc` de modo avião do Samsung o Wi-Fi continuou ligado e a conexão não caiu. O teste válido de perda foi desligando Wi-Fi e dados.

## Tela extra: o celular como mais um monitor do PC (23/09/2026)

Pedido do usuário, fora do plano original (que deixava monitores virtuais fora da primeira entrega de vídeo). Funciona só pela conexão de rede (Tailscale).

- **Host:** o canal `/v1/display` tem a mesma autorização da rede (código de pareamento, `whois` e recusa de `Origin`). Ele cria o monitor `OMARCHYREMOTE` no Hyprland (`output create headless` + `hl.monitor`, com o tamanho da tela do celular em paisagem) e captura com `wf-recorder` em H.264 (`libx264` ultrafast/zerolatency). Toques viram posição absoluta (`hl.dsp.cursor.move` pelo socket do Hyprland) e botão do mouse virtual. Ao sair, solta o botão, para a captura e remove o monitor, e as janelas voltam aos outros monitores.
- **App:** botão "Tela extra" com conexão pela rede. Mostra o vídeo em tela cheia, paisagem, sem barras, decodificado pelo MediaCodec (baixa latência, fila de até 30 quadros e salto ao próximo quadro-chave se atrasar). Toque direto na imagem; "Fechar tela extra" ou Voltar encerra.
- **Codificador:** NVENC falhou com `CUDA_ERROR_OUT_OF_MEMORY` (o ollama ocupa 10,4 de 12 GB da RTX). VAAPI na AMD falhou porque o compositor roda na NVIDIA ("Unsupported format: bgra"). Por isso o host usa x264 na CPU: 2340×1080, ≈58 fps, ≈8,7 Mbit/s com movimento.
- **Defeito achado no aparelho e corrigido:** o x264 de baixa latência divide cada quadro em 16 fatias, e o app mandava cada fatia como um quadro, o que travava o decodificador do Samsung. Agora as fatias são agrupadas em quadros completos (`AccessUnitAssembler`, com teste).

| Caso | Resultado | Evidência |
|---|---|---|
| Testes | passou | host 45/45 (`test_display`, `test_display_server`); app 53/53 (`DisplayLogicTest`) |
| Monitor virtual criado ao abrir | passou | `OMARCHYREMOTE 2340x1080` escala 1.5 em 5360,0, workspace 3 |
| Imagem no S24 | passou | print do celular: papel de parede, barra do Omarchy (workspaces com o 3 ativo), relógio e cursor |
| Toque direto no ponto certo | passou | toques em (1170,540), (234,108), (2000,900) → cursor em (6140,360), (5516,72), (6693,599), idênticos ao cálculo (±1 px). Arraste também |
| Latência toque → cursor | medido | ≈5–25 ms depois de o evento entrar no celular (o primeiro toque ≈370 ms, provavelmente o Wi-Fi saindo da economia de energia) |
| Fechar tela extra | passou | monitor removido, `wf-recorder` encerrado, app volta conectado |
| App encerrado com a tela aberta | passou | o host removeu o monitor sozinho |
| Latência do vídeo, nitidez, uso prolongado, bateria/temperatura | não testado | precisa do uso real |

## Ver PC: ver e usar os monitores do PC no celular (23/09/2026)

Corresponde às Fases F/G do plano (vídeo de um monitor, escolha de monitor, zoom), sobre a conexão por Tailscale.

**Uso:** botão "Ver PC" (conexão por rede). Abre em tela cheia, paisagem, no último monitor usado. A barra do topo tem os monitores (`DP-1` · `HDMI-A-1`), Teclado e Fechar.

| Gesto | Resultado |
|---|---|
| Toque | clique esquerdo no ponto |
| Deslizar um dedo | só move o cursor (sem botão) |
| Segurar e deslizar | arrastar/selecionar (o celular vibra ao "pegar") |
| Segurar e soltar sem mover | clique direito |
| Dois dedos juntos | rolagem (natural) |
| Pinça | zoom local de 1× a 4× (o PC não muda); depois de pinçar, mover os dedos desloca a vista |
| Toque com dois dedos | volta a mostrar o monitor inteiro |

- **Host:** o `/v1/screen` tem a mesma autorização. Ele lista os monitores reais (sem o virtual), captura o escolhido com `wf-recorder -o <monitor>`, reduzido para caber no celular sem aumentar (ultrawide 3440×1440 → 2340×980), e aplica toque, clique direito e rolagem na geometria lógica do monitor. Um vídeo por vez ("Ver PC" ou "Tela extra").
- **App:** `ViewTransform` (zoom e deslocamento limitados, toque → ponto do monitor) e `RemoteGesture` (reconhecedor), com o vídeo em `TextureView` para permitir zoom. O teclado abre em gaveta com texto, Enter, teclas especiais, modificadores e workspaces, pelo canal de controle.

| Caso | Resultado | Evidência |
|---|---|---|
| Testes | passou | host 51/51 (`test_screen`, servidor `/v1/screen`); app 63/63 (`ViewTransformTest` 4, `RemoteGestureTest` 6) |
| Caminho real no PC (host temporário local com Hyprland e captura reais) | passou | lista `DP-1 1920×1080`, `HDMI-A-1 3440×1440`; pedido HDMI-A-1 → `2340×980`; ≈700 KB de H.264 em 2 s (Constrained Baseline 2340×980); `wf-recorder` encerrado ao fechar |
| Toque → ponto do monitor | passou | 7 pontos em HDMI-A-1 e DP-1, incluindo cantos (1.0 → 5359,1439 e 1919,1079), idênticos ao esperado |
| Abrir "Ver PC" no S24 | passou | o DP-1 aparece em tela cheia (print verificado e descartado por conter conteúdo pessoal); host capturando `-o DP-1 … scale=1920:1080` |
| Toque direto no S24 (DP-1 com faixas pretas de 210 px) | passou | (1500,400) → 1290,399; (1900,700) → 1689,699 (≤2 px); arraste até 1588,499; toque na faixa preta não clica |
| Trocar para HDMI-A-1 pelo menu | passou | captura trocada para `-o HDMI-A-1 … scale=2340:980`; toque no centro → 3640,720 exato |
| Vídeo exibido | passou | brilho médio 0 nas faixas e 31,5 na área do vídeo (medido sem ver o conteúdo) |
| Menu discreto (☰ no canto → monitores, Teclado, Fechar) | passou | substitui a barra do topo, que cobria o monitor |
| Gaveta do Teclado | passou | abre com o campo focado e o teclado do Android aberto (`mInputShown=true`) |
| Fechar pelo menu | passou | app volta à tela principal conectado; `wf-recorder` encerrado |
| Pinça, deslocamento e rolagem com dois dedos no aparelho | não testado | o `adb` não injeta multitoque; coberto só pelos testes JVM |

### Ajustes após uso real do Ver PC

- **"Ao mover, fica selecionando":** era o modelo de gestos, não uma falha. Deslizar o dedo arrastava com o botão apertado. Agora deslizar só move o cursor, e arrastar exige segurar antes (com vibração). A Tela extra usa o mesmo modelo e a mesma tela. Testes: `RemoteGestureTest` com deslizar, segurar e deslizar, e segurar e soltar (app 64/64). No S24: deslizar moveu o cursor para (1788,599) com seleção 0; segurar e deslizar selecionou 23 caracteres; deslizar depois voltou a ter seleção 0 (botão solto).
- **"Vídeo indisponível ou já em uso por outra tela" ao trocar de monitor:** o host só liberava a vaga depois de encerrar o vídeo anterior, e o pedido novo chegava antes. Agora uma sessão nova encerra a anterior e assume. Teste `test_new_video_session_takes_over_the_previous_one` (host 52/52).
- O menu só lista os monitores ligados no momento: com o HDMI-A-1 desligado no KVM, só aparece o DP-1.

## Controle do PC repensado para uso real (23/09/2026)

Desenho aprovado pelo usuário: dois modos (Trackpad e Toque direto, abre no último usado), gestos multi-dedo de trackpad, barra inferior grande, workspaces reais, atalhos rápidos e ajustes.

| Onde | Gesto / controle | Ação |
|---|---|---|
| Trackpad (tela principal e Ver PC/Tela extra no modo Trackpad) | 1 dedo | move o cursor (relativo; no vídeo, mais fino com zoom) |
| | toque / dois toques | clique / clique duplo |
| | dois toques e segurar, depois deslizar | arrastar/selecionar |
| | 2 dedos: toque / deslizar | clique direito / rolagem vertical e horizontal |
| | 3 dedos: toque / deslizar para os lados | clique do meio / workspace anterior-próximo |
| | pinça (só no vídeo) | zoom local; a vista segue o cursor |
| Barra do vídeo | Esquerdo, Direito, Segurar, Workspaces, Teclado, ⋯, ▾ | cliques no cursor, trava do botão, faixa de workspaces, teclado com atalhos, ajustes, esconder |
| Ajustes (⋯) | monitor, modo, velocidade, rolagem natural, tocar para clicar, dicas | salvos |

- **Host:** `workspace.go`/`workspace.step` no canal de controle, pelo Hyprland (`hl.dsp.focus`; `e±1` como os atalhos do Omarchy). A lista de workspaces é enviada a cada mudança. O canal de vídeo ganhou `pointer` (rel, click 1/2/3, press, release, scroll, hscroll), mantém o cursor no monitor visto e informa a posição. O mouse virtual ganhou rolagem horizontal. Testes: host 61/61.
- **App:** `TouchpadGesture` (10 testes) substitui o reconhecedor antigo. Workspaces reais na tela principal e no vídeo, com Bluetooth mantendo os atalhos `Super+N`. Atalhos rápidos: Copiar, Colar, Recortar, Desfazer, Tudo, Fechar janela (Super+W), Menu (Super+Space), Terminal (Super+Enter). Testes: app 70/70; lint limpo.
- Ruling: pela rede, a troca de workspace usa a interface do Hyprland, e não o atalho de teclado, porque independe da janela focada. No Bluetooth continua o atalho.

| Verificação no S24 / PC | Resultado |
|---|---|
| Workspaces reais na tela principal ("1 no DP-1, 4 janelas", "2… visível", "+") | passou |
| Tocar no workspace 1 troca o PC para o 1 | passou |
| Barra inferior do vídeo presente | passou |
| Trackpad no vídeo: deslizar no canto do celular move o cursor a partir de onde está (+88 px), sem pular para o dedo | passou |
| Toque não deve mover o cursor | inconclusivo: o usuário usava o celular ao mesmo tempo |
| Segurar, gestos de 2 e 3 dedos, pinça, ajustes | não testado no aparelho (precisa de dedos reais; `adb` não faz multitoque) |
| `adb` por Tailscale (`100.64.0.20:5555`) | ativo até reiniciar o celular, contornando o cabo instável |

## Design v2 no S24 Ultra (23/09/2026)

Implementado a partir de `design/DESIGN.md` (paleta grafite, acento só para conexão/ativo/primário, Archivo + JetBrains Mono, base fixa e camadas por cima). Host §6: o canal de controle envia `monitors` (geometria) e `cursor` (monitor + posição, ~15 Hz quando muda). Testes: app 77/77, host 70/70, lint limpo.

Comparação com `design/screens/` no S24 (fonte do sistema em 1,15×), pela Rede:

| Tela | Resultado |
|---|---|
| 05 Sem computador | confere após ajustes: Ver PC tracejado sem conexão, WS "0", nome do PC não é cortado (o detalhe encolhe primeiro) |
| 07 Folha Computador · Rede | confere após incluir o subtítulo; código mascarado com botão de olho |
| 01 Principal · Rede | confere após ajustes: ajustes no canto direito, nomes dos monitores numa linha só e inteiros, "tela extra" inteiro, ponto do cursor não é cortado na borda |
| 03 Camada de teclas (com e sem Ctrl) | confere; com Ctrl aparece a faixa "vale para a próxima tecla" e Soltar |
| 04 Camada de texto | confere (Enviar fica inativo com o campo vazio) |
| 08 Ajustes | confere após trocar o slider pelo trilho fino com polegar redondo |
| 13/14 Paisagem base e teclas | confere após pôr o ícone no botão Ver PC |
| 10/11/12 Ver PC | conferido pela árvore de acessibilidade (sem abrir a imagem, que mostra o conteúdo do PC): borda de workspaces, chip "meu-pc · monitor · modo", âncora; leque com Direito/Segurar/Teclado/Telas/Modo; menu com Ajustes/Dicas de gestos/Sair |
| 02, 06 Bluetooth | não testado nesta rodada (transporte ativo era a Rede) |
| 09 Dicas de gestos | não testado nesta rodada |

Mudanças de comportamento feitas durante a verificação:
- Abrir o app com a Rede escolhida e endereço salvo conecta sozinho (uma tentativa). Antes só reconectava se o app tinha saído conectado.
- Ver PC abre no monitor onde o cursor está; o último monitor visto só vale quando a posição do cursor é desconhecida.
- Ver PC sem conexão mostra a mensagem "Conecte ao computador pela rede para ver a tela." em vez de tentar abrir.

Configurações do celular alteradas para as capturas (rotação) foram restauradas: rotação automática ligada.

## Ver PC: trocar de monitor (23/09/2026, relato do usuário "fica só em uma tela")

Causas encontradas no S24 e corrigidas:
1. **A imagem não trocava**: ao trocar, o app soltava a superfície de vídeo, e a tela (que continua aberta) não a entrega de novo; o chip mudava de nome, mas a imagem ficava parada. `VideoPipe.close()` agora mantém a superfície.
2. **Cursor preso**: no modo trackpad, o PC prendia o cursor no monitor visto. Agora ele atravessa, e o PC informa para qual monitor foi (`cursor_message`); o Ver PC troca sozinho para lá (`followMonitor`, 4 testes). A Tela extra continua prendendo o cursor.
3. **Ver PC fechava na troca**: movimentos do dedo chegavam ao canal novo antes do pedido do monitor e o PC respondia "monitor inválido". O PC ignora toques antes do pedido, e o app só envia toques quando o vídeo já começou.
4. **Tela preta ao abrir até algo mudar no PC** e **faixa verde na troca**: o app só sabe que um quadro terminou quando o próximo começa. Com a tela parada, o último quadro nunca era mostrado. Uma primeira correção por tempo no app (25 ms sem dados) cortava quadros-chave grandes que chegavam em pedaços pela rede, o que deixava a faixa verde. Correção final: o PC marca o fim de cada quadro com um delimitador H.264 (AUD) depois de 4 ms sem saída do codificador (local, sem pausas no meio do quadro), e o app o reconhece na hora.
5. **"Já existe outro controlador" ao reabrir o app** logo depois de fechado à força: o mesmo celular agora assume a própria sessão antiga; outra máquina continua recusada.

Testes: app 83/83, host 76/76, lint limpo.

| Verificação no S24 (medida sem abrir a imagem: brilho e fração de pixels verde puro) | Resultado |
|---|---|
| Deslizar para a esquerda no HDMI-A-1 leva o cursor e a imagem ao DP-1, e de volta | passou (chip e imagem mudam) |
| Imagem ao abrir o Ver PC com o PC parado | passou (aparece em menos de 1 s) |
| Faixa verde após 4 trocas | passou (0,0% de pixels verdes) |
| Fechar o app à força e reabrir | passou (reconecta sem "outro controlador") |
| Trocar pelo menu Telas e pelo mapa da tela principal | passou |

## Fase 1 — herdr em qualquer lugar (23/09/2026)

Plano: `docs/superpowers/plans/2026-09-23-fase1-herdr.md` (spec: `docs/PLANO-V2.md` §4). Implementado no branch `fase1-herdr`. Testes: host 112/112, app 98/98, lint limpo (depois das correções da revisão final: ids reais do herdr como `wD:p1`, comandos do herdr fora do laço de entrada, controle de fluxo do terminal, WebView presa à própria página, notificação fixa sem reiniciar o serviço, "Desconectar" respeitado com o acompanhamento ligado).

O S24 estava com o usuário, fora de casa; os testes no aparelho usaram o **S10e (SM-G970F, Android 12)** deixado no cabo. Ele não tem Tailscale (entrar na conta do usuário não cabe a mim), então usei o modo de teste novo: host `--dev-loopback` em 127.0.0.1 + `adb reverse` + build de debug aceitando `localhost`. O caminho de rede é o mesmo; muda só a autorização da máquina (loopback em vez de `tailscale whois`).

| Verificação no S10e (herdr real do PC) | Resultado |
|---|---|
| Conectar pela rede com endereço salvo | passou |
| "Acompanhar em segundo plano": notificação fixa, sessão mantida 25 s na tela inicial | passou |
| Notificação fixa conta agentes do herdr real ("Ligado a localhost · 8 agentes · 1 esperando você") | passou |
| Canais de notificação `agentes` e `conexao` criados | passou |
| Doze profundo forçado por 2 min com o app em segundo plano | passou (sessão não caiu, sem reconexão) |
| Terminal: herdr desenhado (50×37 no retrato), Voltar fecha, sessão `default` continua rodando | passou (conferido por log e contraste da captura, sem ver o conteúdo) |
| Linha de status "8 agentes"; folha lista os 8 com estado; detalhe mostra as últimas linhas (2682 caracteres) e os botões | passou |
| Digitar no terminal, Prefixo, responder um agente (y/Enter), prompt, alerta real de "precisa de você"/"terminou" | **não testado no aparelho**: iria para os agentes reais do usuário. Coberto por testes (host e app) e fica para o usuário |
| Prova de 24 h em segundo plano | em andamento no S10e desde 17:42; leitura pendente |
| Tudo acima no S24 pela Tailscale | **não testado** (aparelho com o usuário) |

Problemas achados no hardware e corrigidos: o herdr recusava rodar "aninhado" quando o host herdava `HERDR_*` (o PTY agora limpa essas variáveis); o WebView do terminal ficava com altura 0 dentro do Compose (`MATCH_PARENT`); a página mandava um tamanho inválido antes do layout (agora só se anuncia com tamanho válido). A revisão de segurança automática apontou que um prompt começando com `-` viraria opção do `herdr`; o texto agora vai depois de `--`.

### Ajustes após o uso real (23/09/2026, 18:35)

- **Prompt não chegava ao agente** (relato do usuário): a correção de segurança usava o separador `--`, que o herdr não conhece ("unknown option"), então todo prompt falhava. Agora um texto que começa com `-` ganha um espaço na frente, e o resto vai como está. Conferido no herdr real com um alvo inexistente: o texto é aceito como texto. O teste do herdr falso passou a imitar o analisador real.
- **Folha de agentes redesenhada:** lista agrupada ("Precisam de você", "Trabalhando", "À espera") com o estado em pílula; detalhe com cabeçalho de ícones (voltar, abrir no terminal), tela do agente em mono menor, teclas do Claude Code (1, 2, 3, setas, Esc e Enter, que fica em destaque quando o agente pede aprovação) e campo de mensagem com botão de enviar embutido. O prompt funciona também com o agente trabalhando; só fica bloqueado enquanto há um pedido de aprovação aberto.
- **Topo do terminal:** "Agentes" e "Fechar" cortados viraram um chip "Agentes" (com o número em destaque quando alguém espera) e um botão de fechar.
- Host: teclas 1–9 liberadas para responder escolhas numeradas. Testes: host 113/113, app 99/99.
- Não testado: mandar um prompt ou uma tecla a um agente real (seria nos agentes do usuário, inclusive nesta sessão). Fica para o usuário.

## Fase 2 — Extensão do Omarchy (23/09/2026)

Plano: `docs/superpowers/plans/2026-09-23-fase2-omarchy.md`. Testes: host 126/126, app 107/107, lint limpo.

| Verificação | Onde | Resultado |
|---|---|---|
| `omarchy/install.sh`: CLI no PATH, plugin validado e ativado na barra (seção direita), bloco Phone no menu com cópia de segurança, hook `theme-set.d/omarchy-remote` | PC | passou (idempotente; 2ª execução não duplica) |
| Ícone do celular na barra (apagado sem celular no host de produção) | PC | passou (captura só da faixa da barra) |
| Super+Espaço → Phone abre; sem celular mostra só "Pair phone (QR)" | PC | passou |
| `omarchy-hook theme-set` roda o hook sem erro | PC | passou |
| Tema "solitude" aplicado no app (acento cinza) e guardado | S10e | passou |
| Bateria no `state.json` (100%, carregando) | S10e | passou |
| `omarchy-remote open terminal` abre o herdr no celular | S10e (host de teste) | passou |
| `omarchy-remote send-clipboard` / `clipboard` aceito pelo celular | S10e | passou no protocolo; a área de transferência do celular não foi lida (o Android 12 não expõe isso pelo adb) |
| Código de pareamento migrado para o Keystore (só `pairing_code_enc` nas preferências; reconectou) | S10e | passou |
| Leitor de QR do Google abre a partir da folha Computador | S10e | passou |
| Ler um QR de verdade e conectar | — | **não testado** (precisa apontar a câmera) |
| Pedido "abrir" com o app em segundo plano vira notificação | — | **não testado** |
| Tudo no S24 pela Tailscale | — | **não testado** (aparelho com o usuário) |

## Fase 3 — Trabalhar longe + menu do Omarchy + cores (23/09/2026)

Planos: `docs/superpowers/plans/2026-09-23-fase3-longe.md`. Testes: host 150/150, app com lint limpo.

| Verificação | Onde | Resultado |
|---|---|---|
| Layout do desktop atualizado por eventos do Hyprland; cursor lido só com o mapa na tela | testes | passou (sem leitura repetida com nada mudando) |
| Área de transferência: celular → PC (camada Texto e "Compartilhar" do Android) e PC → celular | testes + S10e | protocolo passou; conteúdo no celular não lido pelo adb |
| Trocar de monitor na mesma conexão (Telas) | S10e | passou: chip "DP-1 · 13 ms · 38 qps", sem reconectar |
| Latência e qps no chip do Ver PC | S10e | passou (11–18 ms pelo cabo; "qps" some com a tela parada) |
| Qualidade Nítido/Equilibrado/Leve | testes | passou (host troca captura na mesma conexão) |
| Teclado físico ligado ao celular digita no PC | — | **não testado**: o adb só injeta teclas virtuais, que o app ignora de propósito |
| Menu do Omarchy no celular (mesma árvore, ícones, busca, apps) | S10e | passou; "No PC" abre o menu real no PC e o Ver PC |
| Cores do tema como o shell do Omarchy (um fundo, camadas do foreground, acento no ativo) | S10e | passou em 6 temas via `theme-preview`, incluindo tema claro |
| Host antigo não derruba app novo (tipos desconhecidos são ignorados) | testes | passou |

Observação: os pedidos de senha ao trocar tema vêm do `omarchy-theme-set-browser-policy` (cor da barra dos navegadores em `/etc/*/policies`). O Omarchy prevê uma regra `/etc/sudoers.d/omarchy-theme-browser` para ele rodar sem senha; aqui ela não pôde ser conferida (pasta só do root). Para testes de tema, `omarchy-remote theme-preview <tema>` não muda nada no PC.

## Fases 4 e 5 — Segunda tela e o que só o celular tem (23/09/2026)

Testes: host 161/161, app (unitários) verdes, lint limpo.

| Recurso | Onde | Resultado |
|---|---|---|
| Folha Omarchy, aba **Agora**: agentes, mídia tocando no PC com controles, lembrete, atualização | S10e | passou (cards de agentes e mídia; controles não acionados) |
| Aba **Menu**: o Super+Espaço do Omarchy (255 itens, ícones Nerd Font, busca, apps) e "No PC" | S10e | passou |
| Aba **Atalhos**: 125 atalhos do Omarchy com as teclas; tocar pressiona a combinação | S10e | lista passou; execução não acionada (mexeria no desktop) |
| Aba **Janelas**: janelas por workspace; focar, fechar, flutuar, tela cheia, mover | S10e | lista passou (6 janelas, 2 workspaces); ações não acionadas |
| **Ditado** na camada Texto e no campo do agente | S10e | passou (abre o reconhecedor de voz do Google em pt-BR) |
| **Blocos** "Ver PC" e "herdr no PC" nas Configurações Rápidas | S10e | passou ("herdr no PC" abriu o terminal; bloco removido depois) |
| **Widget** de agentes na tela inicial | S10e | registrado e com texto; colocar na tela inicial é gesto do usuário |
| **S Pen** como mesa digitalizadora (pressão, passar por cima) | PC | dispositivo criado; **não testado** com caneta (S10e não tem; S24 com o usuário) |
| **Bloqueio por presença** | — | **não feito**: sem sinal confiável sem root (o PC não vê o RSSI de um celular não conectado; `l2ping` exige root). O Omarchy já tem System → Lock no menu, que o app alcança pela aba Menu |

## Revisão final das fases 3–5 (23/09/2026, 21:25)

Revisor independente: nada crítico, cinco pontos importantes, todos corrigidos com teste que falhava antes:

- **Menu do Omarchy:** uma entrada do usuário na mesma linha da `{` era perdida ao instalar o bloco "Phone"; agora fica, e o instalador se recusa a gravar se alguma entrada sumiria.
- **Pedidos lentos** (menu com as checagens `when`/`checked`, área de transferência, player, hyprctl) saíram do laço de entrada: teclado, touchpad e pings não esperam mais por eles. As checagens do menu valem por 10 s.
- **Texto longo com acento/emoji** passava do limite de 16 KB em bytes e derrubava a sessão; o celular não manda mais e o host recusa (ack falso) sem cair.
- **Ver PC congelava** ao trocar para um monitor de outro tamanho (o começo do novo vídeo ia para o decodificador antigo). S10e: HDMI-A-1 (2280×954) → DP-1 (1920×1080) → HDMI-A-1 na mesma conexão, imagem na hora, 39 qps no DP-1.
- **Caneta e captura:** se a conexão cai com a caneta na tela, o host tira a caneta; uma captura nova iniciada durante a queda é encerrada.

Host: 167 testes. App: testes, build e lint verdes. Instalado no S10e (dev-loopback), conectado.

## S24 Ultra: instalação, temas e reconexão (23/09/2026, 22:20)

- Instalado no S24 (adb por Wi‑Fi; o cabo USB caía). Conectou pela Tailscale.
- Tema: gruvbox, Catppuccin Latte (claro) e Tokyo Night trocados de verdade no PC (`omarchy-theme-set`), mais 8 temas por `theme-preview`: o fundo do app bate exatamente com o `background` de cada tema, o acento acompanha, e no tema claro os ícones da barra ficam escuros. De volta ao Solitude.
- Reconexão: antes, o host reiniciando deixava o app em "Desconectado" até voltar a ele. Agora, com o app aberto, ele tenta de novo sozinho (2, 4, 8, 16 s, depois a cada 30 s; nunca depois de "Desconectar"). S24: host reiniciado → conectado de novo em ~4 s.

## Fase 6 — Dia a dia (23/09/2026, noite)

Plano: `docs/superpowers/plans/2026-09-23-fase6-dia-a-dia.md`. Host 185 testes; app testes, build e lint verdes.

- **Lupa no cursor** (Ver PC, leque → Lupa: 2×, 3×, desligada): lente redonda acima do cursor, copia o que o celular já mostra (PixelCopy), nada no PC.
- **Botões espremidos** ("Ver", "Atualizar no PC", "Trazer", "Pôr este texto"): `ActionKey` com respiro e largura mínima.
- **Ações do PC** (Omarchy → Agora → Este PC): Bloquear (`omarchy-system-lock`, que também apaga as telas), Suspender (segundo toque confirma), Apresentação. Desligar só a tela ficou de fora: o Omarchy já faz isso ao bloquear.
- **Botões de volume**: sobem/descem o som do PC (`omarchy-audio-output-volume`, com o OSD); em modo apresentação, volume − avança e volume + volta o slide. Opção em Ajustes.
- **Arquivos celular → PC**: compartilhar qualquer arquivo (um ou vários) ou "Enviar arquivo" no card do PC → `~/Downloads`, nome saneado e sem sobrescrever, aviso no PC. Validado por HTTP no host de teste: arquivo idêntico, nome com acento.
- **Arquivos e prints PC → celular**: `omarchy-remote send-file`, `omarchy-remote screenshot` (área com slurp) e o menu Phone ("Send file to phone", "Screenshot to phone"); no celular "Print do PC" e "Print deste monitor". Chegam em Download/Omarchy Remote e Imagens/Omarchy Remote, com aviso para abrir/compartilhar. Validado no host de teste com um cliente no lugar do celular: print de região e da área de trabalho (PNG em resolução cheia), send-file idêntico.
- **Copiar texto da tela** (Ver PC → menu → Copiar texto da tela): arrastar um retângulo; o PC tira um print só daquela área em resolução cheia e o celular lê com ML Kit (no aparelho). Folha com o texto editável: copiar no celular ou pôr no PC. APK só arm64 (a biblioteca de OCR pesa ~11 MB por arquitetura).
- **Ver PC só da janela em foco** (Telas → "Só a janela em foco"): captura o retângulo da janela ativa, toque mapeado nela, troca junto com o foco (a cada 0,5 s). Validado no host de teste: 940×1030 com vídeo fluindo.
- **Avisos do PC no celular**: `busctl monitor` das notificações; cada uma vira `pc.notification` e aparece no canal "Avisos do PC" (opção em Ajustes). Validado no host de teste com uma notificação real.
- **S Pen**: tentativas (rejeição de palma, pressão mínima) não resolveram os riscos interrompidos; fica em aberto.

## Ver PC repensado + terminal (23/09/2026, 23:35)

- Análise e mudanças em `docs/UX-VER-PC.md`. Toque direto como tela de celular: 1 dedo rola (com inércia), segurar amplia o ponto (lupa sob o dedo) e depois arrasta ou (soltando) dá clique direito, eco visual e vibração no toque, dois dedos movem a vista com zoom. Trilho à direita com 1 toque (Digitar, Direito, Segurar, Lupa, Telas, Mais) no lugar do leque; "Direito" no toque direto arma o próximo toque. "Digitar": teclado do Android escrevendo direto no PC com a tela à vista (Esc, Tab, setas, Ctrl, Enter), a vista aproxima do cursor. Voltar duas vezes para sair; bordas protegidas do gesto do sistema. Host aceita rolagem horizontal no toque direto.
- Terminal (herdr): com o teclado Samsung cada espaço saía duas vezes e palavras eram reenviadas (o xterm.js repetia o que o teclado confirmava). Reproduzido no navegador com a página real e uma sequência de composição igual à do Samsung: antes "ola␣␣tudo␣␣bem␣␣", depois "ola tudo bem"; autocorreção vira ⌫ + texto novo; Enter e backspace saem uma vez.
- Host 185 testes; app testes, build e lint verdes. Instalado no S24 e conectado. O host principal precisa ser reiniciado para as novidades da fase 6 e para a rolagem horizontal.

## Auditoria de UX e inglês (24/09/2026, madrugada)

- Auditoria completa em `docs/AUDITORIA-UX.md` (revisor independente + prints de todas as telas). Corrigido em lotes A–E e numa revisão final tela a tela.
- Português/inglês: ~600 textos em pares `tr()`; recursos Android (nome, blocos, widget, compartilhar) com `values-en`. Troca em Ajustes → Idioma, na hora. Validado no emulador de revisão nas duas línguas.
- Prévia do agente: com o agente trabalhando, o herdr recusava ler o histórico e a prévia ficava em "Lendo…"; agora lê a tela visível. Validado no emulador contra o host de teste.
- Os dois celulares estavam bloqueados (PIN) durante a noite: a revisão visual foi num emulador criado para isso (`keypad_review`, Android 16, 1080×2340), conectado ao host de teste. Instalado no S24 e no S10e.
- Host 189 testes; app testes, build e lint verdes. O host principal precisa ser reiniciado para as correções do lado do PC (prévia do agente, uso das IAs, arquivos, notificações, janela em foco).

## Omarchy Remote: quatro lugares (24/09/2026, manhã)

- Nome do app: **Omarchy Remote** (celular, widget, serviço, Bluetooth, pastas de arquivos, menu e textos do Omarchy). Nomes internos (pacote, CLI `omarchy-remote`, serviço do PC) ficaram.
- Reestruturação por propósito, com barra de abas embaixo (trilho lateral na horizontal), desenhada no canvas "Novo app" (variante 1A):
  - **Controle**: PC e áreas de trabalho no topo, faixa dos monitores em proporção com o cursor (toque abre o Ver PC), trackpad, atalhos (segurar edita) e um só botão Teclado, que junta digitação ao vivo, Ctrl, Esc/Tab/setas, mais teclas, ditado e Enter.
  - **Tela**: cada monitor com miniatura e cursor, janela em foco, celular como tela, áreas de trabalho.
  - **Agentes**: anéis de uso de Claude/Codex (5 h e semanal, com marca do tempo decorrido; toque abre os detalhes), agentes esperando e trabalhando com trecho ao vivo da tela (sem a moldura do Claude Code) e resposta 1/2/3 ali mesmo; parados em lista compacta. Aviso na aba quando alguém espera.
  - **PC**: busca no menu, Bloquear, Suspender (segundo toque confirma), Print, Enviar arquivo, Apresentar, Terminal; mídia, atualização do Omarchy, janelas, menu e atalhos do Omarchy.
- Validado no emulador de revisão (vertical e horizontal) contra o host de teste: uso chegou (Claude 9%/95%, Codex 0%/100%), trecho do agente limpo, respostas inline. Host 189 testes; app testes, build e lint verdes. Instalado no S24 e reconectado.

## Área de transferência compartilhada (24/09/2026)

- PC → celular automático: o host vigia o clipboard (`wl-paste --watch`) e cada cópia nova vai ao celular conectado, que a põe na área de transferência em silêncio. O que já estava copiado quando o host sobe não vai; cópias marcadas por gerenciador de senha (`x-kde-passwordManagerHint`) não saem do PC; até 64 mil caracteres.
- Celular → PC: o Android (10+) só deixa o app em foco ler a área de transferência. A cópia nova vai ao PC quando o app volta à tela (ou conecta com ele aberto), pelo atalho rápido "Copiar pro PC" (atividade transparente pega o foco por um instante) e, como antes, por Compartilhar. Só lê quando o carimbo de hora da cópia mudou; cópias marcadas como sensíveis ficam no celular.
- Sem eco: o que um lado mandou não volta (host guarda o que o celular pôs; o app guarda o que veio do PC). Liga/desliga em Ajustes → Avisos.
- Com o app fechado a cópia do PC chega só se a conexão ficar aberta em segundo plano ("Avisar quando um agente precisar de mim").
- Validado no emulador contra um host de teste com área de transferência falsa (a real do PC não foi tocada): PC→celular com o app aberto e fechado, celular→PC ao voltar ao app (inclusive com uma folha aberta, que é outra janela) e pelo atalho rápido, sem eco. Host 194 testes; app testes, build e lint verdes.

## Refino Apple: Tela, Agentes e Agente (24/09/2026)

- Do canvas "Novo app" (3B, 4B, 5C/5D, com 5B de reserva). Listas agrupadas no estilo iOS (título grande, rótulos de seção, grupos arredondados, linhas com ícone), barra de abas com ícones maiores e sem pílula.
- Tela: monitores lado a lado em proporção, "cursor aqui", workspace em controle segmentado; "Outras fontes" (janela em foco, celular como tela).
- Agentes: subtítulo "1 precisa de você · 1 rodando · 4 parados"; limites em barras por plano, com "Esgotado" em vermelho; "Precisa de você" com o comando pedido e Permitir/Sempre/Negar; "Rodando" com o que o agente faz agora ("Rodando um comando · 13m"); "Parados" com "Sem limite" quando o plano acabou. "+" abre o terminal.
- Agente: a tela do Claude Code lida como conversa (`AgentConversation`): suas mensagens em balões, ações num chip que abre a lista ("Leu 1 arquivo, rodou 3 comandos"), resposta como texto, trabalho e contexto no fim; pedido de permissão como cartão (Sim / Sim, e não perguntar de novo / Não, dizer o que fazer), e escrever no campo recusa e manda o texto. Outros agentes (Codex) aparecem como tela, sem a caixa de entrada e as faíscas do Codex. Teclas do terminal a um toque; o campo sobe com o teclado; sem a barra de abas.
- Validado no emulador contra o host de teste com um agente de mentira pedindo permissão: Sim → 1, texto → 3 e depois o texto, Permitir → 1. Testes do app (parser da conversa, palavras das ações, tela do Codex), build e lint verdes.

## Agente como chat, com o histórico inteiro (24/09/2026)

- O PC lê a conversa do arquivo de sessão do agente (herdr diz qual: `agent_session`): Claude Code em `~/.claude/projects/…/<id>.jsonl`, Codex em `~/.codex/sessions/…`. Páginas lidas de trás para frente a partir de um byte (1,6 ms para uma página de um arquivo de 81 MB; ~30 KB por página), o novo lido a partir do último byte e empurrado ao celular (inotify) só enquanto o agente está aberto. Nada toca o terminal do PC.
- No celular: lista de chat (a mais nova embaixo), páginas anteriores carregam antes de chegar ao topo, mensagens novas mantêm o fim à vista. Suas mensagens em balões (inclusive as mandadas no meio do trabalho), respostas em Markdown (títulos, listas, `código`, **negrito**, *itálico*, blocos de código com linguagem e "Copiar", tabelas), texto selecionável.
- Ações como cartão, uma linha cada (`$ comando`, `Edit arquivo +3 −1`, ✓/✕): abrem a saída (rolagem lateral, erro em vermelho, "Buscar a saída inteira") ou o diff em vermelho/verde (Edit/MultiEdit/Write, patch do Codex). Sequências longas mostram as 3 últimas e "Mais N ações".
- O que acontece agora (pedido de permissão, tempo de trabalho, contexto) continua vindo da tela. Sem arquivo de sessão, a tela como antes.
- Validado no emulador contra o host de teste com a sessão real (81 MB): páginas antigas até horas atrás, sem saltos. Host 216 testes; app testes, build e lint verdes.

## Arquivos do celular para o agente (24/09/2026)

- No chat do agente, o clipe no campo de mensagem escolhe arquivos (fotos, logs, PDFs…). O PC os põe no projeto do agente, em `<projeto>/.omarchy-remote/` (nome sem espaços nem parênteses, nunca sobrescreve), e acrescenta a pasta ao `.git/info/exclude` do repositório (local, nunca commitado): some do `git status`. A mensagem ganha a menção: `@.omarchy-remote/arquivo` para o Claude Code (lê arquivos e imagens assim), o caminho para os outros.
- O projeto vem do herdr (`cwd` do painel); agente que sumiu → o celular avisa, nada vai para lugar errado. Upload em streaming com notificação de progresso, como os outros arquivos.
- Validado no emulador contra o host de teste: arquivo escolhido no seletor chegou à pasta do projeto de teste, exclude escrito uma vez, menção no campo. Host 220 testes; app testes, build e lint verdes.

## Comandos sugeridos com um toque (24/09/2026)

- Blocos de código de comando nas respostas do Claude Code (`bash`/`sh`/`shell`/`console`, ou sem linguagem com linhas começando em `!`/`$`; até 5 linhas, comentários fora) ganham "Rodar" ao lado de "Copiar". Sobre o campo, os comandos da última resposta, numerados na ordem sugerida; os já rodados ficam com ✓.
- Cada execução pede confirmação mostrando o comando exato; vai ao agente como `! comando` (modo bash do Claude Code: roda na sessão, na pasta do projeto, e o agente vê a saída). Só no Claude Code; nos outros, só "Copiar".
- Leitura da tela (reserva): os resumos do Claude Code sem o ● ("Ran 3 shell commands", "Running 1 shell command…", "Background command … completed") viram ações, não texto.
- Validado no emulador com uma sessão de exemplo: "Rodar", barra numerada, confirmação e o texto `! systemctl …` chegando ao agente. Falta conferir num Claude Code real que o `!` enviado pelo herdr ativa o modo bash.

## Menu "/" dos agentes (24/09/2026)

- No campo do agente, "/" no começo abre os comandos daquele agente, filtrando enquanto digita (nome que começa, nome que contém, descrição); no Codex, "$" abre as skills. Nome em monoespaçada, de quem é (plugin, skill, projeto), descrição em português para os comandos próprios; ícone de terminal nos que abrem uma tela. Tocar põe "/comando " no campo com o cursor no fim.
- De onde vem: os comandos próprios foram lidos dos menus reais (Claude Code 2.1.280: 148 itens, inclusive skills embutidas e as sincronizadas da conta; Codex 0.155.1: 49), em `host/keypad_host/agent_commands.json`; o que está instalado é lido do disco a cada pedido (skills e comandos do usuário e do projeto, plugins habilitados em `installed_plugins.json`, skills do Codex): 192 itens em 8 ms, 33 KB. No Codex, prompts não existem mais (o menu diz "no matches"); skills são "$nome".
- Comandos que abrem tela no terminal (/model, /config, /resume…): ao mandar, abre o "Terminal do agente" — a tela ao vivo (lida a cada segundo enquanto aberto) e as teclas (1 2 3, setas, Tab, Esc, Enter). O mesmo painel no botão de teclado.
- Validado no emulador: menu da sessão real, filtro, escolha e envio de "/model" ao agente de teste, painel aberto sozinho. Host 226 testes; app testes, build e lint verdes.

## Terminal do agente com cores (24/09/2026)

- /status do Claude Code abre um painel com abas (Settings · Status · Config · Usage · Stats), trocadas com ← → (ou Tab), Esc fecha. A aba atual (e o item escolhido de qualquer seletor) só aparece em cor/vídeo inverso; o painel lia texto puro e não dava para saber onde se estava.
- Agora o painel "Terminal do agente" lê a tela com cores (herdr `--format ansi`, resposta `agent.screen`, até 60 KB) a cada segundo enquanto aberto e a desenha como o terminal: cores 16/256/truecolor, negrito, esmaecido, vídeo inverso, sublinhado. Dica de navegação sob a tela; teclas com contraste.
- Validado no emulador com a tela real do /status (capturada num terminal próprio) no agente de teste: aba "Status" destacada. Host 229 testes; app testes (leitor ANSI), build e lint verdes. A conferir no uso: nomes "tab"/"left"/"right" no send-keys do herdr.

## Ver PC travava ao ir para a janela em foco (24/09/2026)

- Sintoma: "Ver PC" não abria; no PC ficavam duas capturas vivas (monitor e janela em foco) depois de o celular sair.
- Causa: ao trocar de captura, o host mandava SIGTERM ao wf-recorder e esperava; o wf-recorder trata SIGTERM terminando de escrever a saída, que ninguém lia mais — espera eterna. A troca travava, a tela não começava e a sessão travada não limpava as capturas. No app, o pedido de "Janela em foco" ficava pendente e transformava o próximo "Ver PC" em janela.
- Correção: capturas param com SIGKILL e espera limitada (`stop_capture`, testado com um processo que ignora SIGTERM); "Ver PC" comum limpa o pedido de janela. De quebra: o celular lembra entre reinícios qual cópia já olhou, e não reenvia a área de transferência ao PC após instalar.
- Host 230 testes; app testes, build e lint verdes.

## Rodada de integração com o Omarchy (24/09/2026, tarde)

- **Modelo do agente**: chip no cabeçalho com o modelo em uso (lido da linha de status); um toque manda /model, lê a lista numerada da tela e mostra nativa, com ✔ no atual; escolher anda o cursor (↑/↓) e dá Enter; um segundo passo (esforço do Codex) abre outra lista. Só entre tarefas. Qualquer seletor na tela vira botões no painel do terminal.
- **Notificação do agente**: pedido de permissão mostra o comando com Permitir / Negar / Responder; terminou mostra o começo da resposta com Responder. Responde sem abrir o app (tela bloqueada também), pela conexão em segundo plano.
- **Subagentes**: o PC conta os subagentes rodando de cada agente (`<sessão>/subagents`, só arquivos ativos nos últimos 10 min são abertos: 0,2 ms); a lista mostra "2 subagentes" e o agente vai para Rodando; dentro dele, a faixa "2 subagentes rodando" abre cada um (para quê, tipo, desde quando, a resposta dos que terminaram).
- **Novo agente pelo celular**: "+" em Agentes: Claude ou Codex, projeto (os abertos no herdr primeiro, depois os recentes do Claude), tarefa; o PC abre uma aba no workspace do projeto (ou um workspace próprio), inicia o agente, manda a tarefa, e o app abre o agente.
- **Central do PC** (aba PC): volume com saída e mudo; luz noturna, não perturbe, acordado, microfone, gravar tela, Bluetooth, próximo fundo, barra, espaços; perfil de energia. Estado lido como a barra do Omarchy lê (90 ms), ações pelos comandos do próprio Omarchy.
- **Abrir no PC** (menu Compartilhar): abre o primeiro link do texto no navegador do PC (só http/https). **Pegar do PC**: navega pelas pastas do PC (só dentro do home, links simbólicos resolvidos, ocultos fora) e traz um arquivo.
- **Serviço do PC**: impressão digital do código ao iniciar; se os arquivos mudaram, o app oferece "Reiniciar" (aba PC e Ajustes); o serviço para limpo e sai com 75, que o systemd reinicia.
- **Git do agente**: linha com o branch, arquivos mudados e ↑/↓; abre as mudanças com +/−, os últimos commits e o diff de cada arquivo. Só leitura, sem pegar o lock do índice.
- **Estado do PC**: CPU, RAM, temperatura, bateria (notebook) e tempo ligado sob o título da aba PC, a cada 5 s.
- **Miniaturas ao vivo**: os monitores no mapa do Controle e da Tela mostram o que está neles (JPEG de 480 px, ~20 KB, a cada 3 s, só com o mapa na tela), só no Wi-Fi; no 4G/5G fica o desenho. Opção em Ajustes.
- **Ditado**: segurar o microfone no campo do agente envia o que foi dito.
- Janelas (mover de workspace, tela cheia, flutuar, fechar) já estavam na aba Janelas do Omarchy.
- Fora: bloquear o PC quando o celular se afasta — a conexão cair não quer dizer que o celular se afastou (Wi-Fi trocando, app fechado); falsos bloqueios.
- Validado no emulador contra o host de teste (agente de mentira para permissão, subagentes, início de agente e modelo; sessão real para git e histórico; estado real do PC só lido). Host 257 testes; app testes, build e lint verdes.

## Designs 5C/5D e 6B/6C (24/09/2026, noite)

- **Agente (5C/5D)**: ações rápidas próprias de Claude e Codex sob o chat (não respostas sugeridas): ex. /compact quando o contexto enche, /review e diff com mudanças, Enter/Esc quando há algo na tela. Confirmar um comando sugerido usa o cartão do 5D (pergunta, comando em caixa própria, Rodar branco, Cancelar). Separadores de hora entre mensagens distantes; "Terminou há …".
- **Limites**: os cartões de Claude e Codex têm sempre a mesma altura; esgotado mostra "Esgotado · <quando volta>" numa linha.
- **Aba PC (6B)**: estado do PC sob o título; busca abre o menu do Omarchy; bloco de 4 botões redondos (Bluetooth azul, Microfone, Não perturbe, Luz noturna); bloco "Tocando" com o app do player (lido do playerctl), título, anterior/tocar/próxima; Acordado largo, Captura, Gravar; volume como barra larga preenchida até o nível (arrastar, tocar, alto-falante silencia) e botão de trocar a saída; modo de energia segmentado; atalhos redondos (Enviar ao PC, Pegar do PC, Apresentar, Terminal); Aparência em três colunas (Fundo · próximo, Barra, Espaços); janelas abertas com a letra colorida do app; Omarchy.
- **Energia (6C)**: botão redondo no canto abre Bloquear, Suspender, Reiniciar e, separado e em vermelho, Desligar…; Reiniciar e Desligar pedem confirmação no cartão do 5D (Desligar em vermelho). O host passa a aceitar reboot e shutdown pelos comandos do Omarchy.
- Validado no emulador contra o host de teste com o estado real do PC só lido (nenhum botão de controle ou energia acionado; a confirmação de Desligar aberta e cancelada). Host 258 testes; app testes, build e lint verdes.

## Transferências visíveis (24/09/2026, noite)

- Sintoma: em "Pegar do PC", tocar num arquivo não mostrava nada (o aviso ficava atrás da folha; o progresso só na notificação). Enviar também não dizia quando terminava.
- Agora: cartão de transferência no app — seta com anel de progresso, "Trazendo · 45% · 3,2 de 7,0 MB", depois ✓ "No celular · Download/Omarchy Remote" com **Abrir**, ou "No PC · ~/Downloads" (o PC devolve onde salvou); falha em vermelho com o motivo. Na folha "Pegar do PC" o cartão aparece no topo e a linha do arquivo ganha anel/✓; fora dela, o cartão fica acima da barra de abas por 8 s depois de terminar.
- Host: a pasta de destino é recriada se sumiu (antes: erro 500).
- Validado no emulador: trazer um arquivo de 57 MB do PC e enviar um .txt (arquivos de teste apagados depois). Host 259 testes; app testes, build e lint verdes.

## Projeto renomeado para omarchy-remote (24/09/2026, noite)

- Pasta `~/dev/projects/omarchy-remote`; app `com.sandevsystems.omarchyremote` (app novo no celular: parear de novo pelo QR e desinstalar o antigo); CLI `omarchy-remote`; serviço `omarchy-remote-host.service`; `~/.config/omarchy-remote`, `$XDG_RUNTIME_DIR/omarchy-remote`; monitor virtual `OMARCHYREMOTE`; dispositivos "Omarchy Remote Keyboard/Mouse/Pen".
- Migração: o serviço move `~/.config/android-keypad` (código de pareamento) para o nome novo na primeira vez; o instalador troca o bloco antigo do menu Phone pelo novo e apaga a CLI e o hook antigos. Plugin da barra continua `rodrigo.phone`.
- Host 263 testes; app testes, build e lint verdes.
