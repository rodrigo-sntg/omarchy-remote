# Omarchy Remote — plano técnico de implementação

Documento de passagem para outro agente. Preparado em 23/09/2026.

## 1. Objetivo e ordem de execução

Construir um aplicativo Android para um **Samsung Galaxy S24 Ultra** controlar um computador com **Omarchy/Hyprland/Wayland**.

1. **V0.1: controle local por Bluetooth**, usando o telefone como teclado e trackpad, enquanto o usuário olha para os monitores do computador.
2. **V0.2: estabilização no aparelho real**, incluindo gestos, digitação em português, reconexão e ciclo de vida.
3. **V0.3: controle por rede local**, com um pequeno aplicativo auxiliar no Linux e pareamento autenticado.
4. **V0.4: vídeo de um monitor**, integrado ao aplicativo Android, com controle simultâneo.
5. **V0.5: múltiplos monitores**, seleção de qualquer saída autorizada, visão geral, zoom e tratamento de alterações de resolução/topologia.

Executar nessa ordem. Não iniciar infraestrutura de vídeo antes de a entrada funcionar bem no S24 Ultra. O usuário explicitou que quer evoluir o mesmo app, e não receber aplicativos separados para cada etapa.

"Qualquer monitor" significa inicialmente qualquer monitor ativo da sessão gráfica atual e autorizado para captura. Tela de login, BIOS, monitores virtuais, acesso pela internet e conteúdo protegido não fazem parte da primeira entrega. Não prometer esses casos por consequência do controle do desktop.

## 2. Estado real do projeto ao receber este plano

Diretório: `/home/voce/dev/projects/omarchy-remote`.

Foi iniciada uma implementação, interrompida quando o usuário pediu apenas o plano. **O projeto ainda não foi compilado, instalado nem validado.** Não descrever os arquivos existentes como um MVP pronto.

Arquivos já criados:

| Arquivo | Estado |
|---|---|
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties` | Esqueleto Gradle |
| `app/build.gradle.kts` | Configuração Android/Compose e dependências propostas |
| `app/src/main/AndroidManifest.xml` | Permissões Bluetooth e referência a uma `MainActivity` ainda inexistente |
| `app/src/main/res/values/styles.xml` | Tema escuro inicial |
| `app/src/main/res/drawable/ic_keypad.xml` | Ícone vetorial inicial |
| `app/src/main/java/com/sandevsystems/omarchyremote/input/RemoteInput.kt` | Contrato de entrada, `KeyStroke` e máscaras dos modificadores |
| `app/src/main/java/com/sandevsystems/omarchyremote/input/KeyboardLayout.kt` | Conversão preliminar de texto ABNT2 e US para teclas físicas |
| `app/src/main/java/com/sandevsystems/omarchyremote/bluetooth/HidReports.kt` | Descriptor HID composto, reports de teclado/mouse e divisão de movimentos |
| `.gitignore` | Exclusões de build, configuração local e chaves |

Ainda faltam: Gradle Wrapper, Activity, UI, conexão Bluetooth, gerenciamento de sessão, gestos, testes, README e APK.

O diretório `.git` exposto nesta sessão estava vazio e somente leitura. Não presumir que existe repositório Git funcional; verificar antes de comandos de branch/commit. Preservar os arquivos existentes e revisar o esqueleto, em vez de recomeçar automaticamente.

### Ambiente observado

- SDK Android: `/home/voce/Android/Sdk`.
- Platforms instaladas: API 34 e 36.
- Build tools disponíveis: 34.0.0, 35.0.0, 36.0.0 e 36.1.0.
- JDK 17 disponível em `/usr/lib/jvm/java-17-openjdk`; o `java` padrão indicou versão 27. Fixar JDK 17 no comando de build.
- Gradle 8.14.3 em cache: `/home/voce/.gradle/wrapper/dists/gradle-8.14.3-bin/cv11ve7ro1n3o1j4so8xd9n66/gradle-8.14.3/bin/gradle`.
- AGP 8.13.2 e Kotlin 2.1.20 presentes no cache; dependências Compose não foram encontradas nos diretórios consultados. Pode ser necessário acesso à rede.
- `adb devices -l` funcionou fora do sandbox, mas **nenhum dispositivo estava conectado**.
- Configuração local em `~/.config/hypr/input.lua` contém `kb_layout = "br,us"` e `kb_variant = "abnt2,intl"`. Isso é evidência de configuração, não confirmação do grupo ativo em tempo de execução.
- Versão Android/One UI do S24 Ultra, GPU do computador e compatibilidade HID real ainda não foram verificadas.

## 3. Decisões de arquitetura

### Android

- Kotlin e Jetpack Compose, um único módulo `:app` inicialmente.
- `minSdk = 28`, `compileSdk = 36`, `targetSdk = 36` no esqueleto.
- AGP 8.13.2, Kotlin/Compose compiler plugin 2.1.20, Gradle Wrapper 8.14.3, JDK 17.
- Compose BOM 2025.04.01, Activity Compose 1.10.1 e Lifecycle 2.8.7 já declarados. São versões fixadas para esta base, não uma afirmação de que sejam as mais recentes. Alterar somente por uma incompatibilidade demonstrada.
- `ViewModel` + `StateFlow` para estado; coroutines para operações sequenciais e cancelamento.
- Sem Hilt, banco de dados, navegação com múltiplos módulos, backend em nuvem ou arquitetura genérica de plugins.
- `SharedPreferences` basta para endereço do último computador, layout e sensibilidade. Não armazenar estado transitório de teclas pressionadas.

### Separação mínima

```text
Compose: Trackpad / Teclado / Conexão
                 |
          KeypadViewModel
                 |
             RemoteInput
                 |
       BluetoothHidController       V0.1
       NetworkInput                somente V0.3

       RemoteScreenSession         somente V0.4
          vídeo + monitores
```

`RemoteInput` não conhece widgets, `Activity`, SDP, sockets ou frames de vídeo. O backend Bluetooth traduz comandos para reports HID. O backend de rede será acrescentado quando necessário. A sessão de vídeo tem ciclo de vida próprio e não deve entrar no contrato de mouse/teclado.

O contrato inicial existente é suficiente para a primeira versão:

```kotlin
interface RemoteInput {
    fun movePointer(dx: Int, dy: Int)
    fun scroll(steps: Int)
    fun setMouseButtons(buttons: Int)
    suspend fun tapKey(key: KeyStroke): Boolean
    fun releaseAll()
}
```

`KeyStroke.usage` usa códigos da tabela USB HID, não `android.view.KeyEvent`, códigos Linux evdev ou Unicode. A futura conexão Linux deverá traduzir explicitamente HID → evdev.

Na V0.1, modificadores são selecionados na UI e aplicados ao próximo comando; `tapKey` envia pressão e soltura. Se aparecer requisito concreto de teclas continuamente pressionadas, acrescentar `keyDown/keyUp` naquela etapa. Não criar antecipadamente uma API genérica de capacidades.

### Por que Bluetooth primeiro

Utilizar `BluetoothHidDevice`, expondo teclado e mouse no mesmo dispositivo HID. Se o S24 Ultra e a pilha Bluetooth do computador aceitarem esse perfil, o Omarchy recebe entrada pelo caminho normal de dispositivos físicos. Não há servidor Linux próprio nessa fase.

A disponibilidade da API não comprova funcionamento no aparelho. A primeira entrega deve ser uma prova real dessa conexão, antes de polimento visual.

## 4. Organização de arquivos a implementar

```text
app/src/main/java/com/sandevsystems/omarchyremote/
  MainActivity.kt
  KeypadViewModel.kt
  input/
    RemoteInput.kt                 existente; revisar
    KeyboardLayout.kt              existente; revisar
    PointerAccumulator.kt          restos fracionários do gesto
  bluetooth/
    BluetoothHidController.kt
    BluetoothConnectionState.kt
    HidReports.kt                  existente; revisar
  ui/
    KeypadScreen.kt
    ConnectionSheet.kt
    Trackpad.kt
    KeyboardPanel.kt
    Theme.kt
app/src/test/java/com/sandevsystems/omarchyremote/
  KeyboardLayoutTest.kt
  HidReportsTest.kt
  PointerAccumulatorTest.kt
  InputSessionTest.kt
docs/
  PLANO-TECNICO.md
  VALIDACAO.md                     criar durante os testes
README.md
```

Adaptar nomes quando necessário, mantendo responsabilidades simples. Não criar arquivos vazios das etapas futuras.

## 5. Fase A — tornar a base compilável

1. Ler as instruções `AGENTS.md` aplicáveis. Usar a skill Omarchy se houver alterações de configuração do desktop; nunca editar arquivos empacotados em `/usr/share/omarchy/`.
2. Gerar Gradle Wrapper 8.14.3, incluindo `gradlew`, `gradlew.bat`, JAR e properties. Usar distribuição oficial e adicionar `distributionSha256Sum` obtido de fonte oficial.
3. Criar `MainActivity`, tema Compose e tela inicial funcional. Confirmar que manifesto, package e namespace coincidem.
4. Usar `enableEdgeToEdge()`, insets de sistema e IME; testar `targetSdk 36` sem conteúdo sob barras ou teclado.
5. Definir SDK por `ANDROID_HOME` ou `local.properties` ignorado pelo Git. Não inserir caminhos pessoais nos arquivos compartilháveis.
6. Executar assemble, unit tests e lint. Corrigir falhas reais; não suprimir lint globalmente.

Comandos esperados, depois de existir o wrapper:

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
ANDROID_HOME=/home/voce/Android/Sdk \
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

/home/voce/Android/Sdk/platform-tools/adb devices -l
```

Saída: `app/build/outputs/apk/debug/app-debug.apk`.

Critério: build reproduzível e tela aberta no dispositivo ou emulador. Emulador comprova UI, não o perfil Bluetooth HID.

## 6. Fase B — prova Bluetooth no S24 Ultra

### Permissões e pareamento

- API 31+: solicitar `BLUETOOTH_CONNECT` e `BLUETOOTH_ADVERTISE` em runtime, no fluxo de conexão.
- API 28–30: manter permissões legadas com `maxSdkVersion=30`.
- Sem scan ativo na primeira versão: listar dispositivos já pareados e tornar o celular detectável mediante `ACTION_REQUEST_DISCOVERABLE`. Não adicionar localização ou `BLUETOOTH_SCAN` sem necessidade.
- Se Bluetooth estiver desligado, solicitar ativação pela UI oficial do Android. Não ligar silenciosamente.
- Exibir dispositivos pareados com nome e endereço; permitir seleção explícita. Não conectar automaticamente a um equipamento arbitrário.
- Para primeiro pareamento, manter o app aberto, habilitar detecção e parear pelo gerenciador Bluetooth do computador. Confirmar o código nos dois lados.
- Persistir apenas o último host escolhido. Nunca remover bonds de outros aparelhos.

### Conexão HID

1. Obter `BluetoothManager.adapter` e abrir `BluetoothProfile.HID_DEVICE` com `getProfileProxy`.
2. Registrar `BluetoothHidDeviceAppSdpSettings` com nome `Omarchy Remote`, descriptor composto e subclass teclado/mouse.
3. Executar callbacks em executor definido; atualizar UI na main thread.
4. Tratar `onAppStatusChanged`: o retorno de `registerApp` indica envio do pedido, não registro confirmado.
5. Somente chamar `connect(host)` depois do registro confirmado.
6. Somente habilitar entrada após `onConnectionStateChanged(... STATE_CONNECTED)`.
7. Implementar `onGetReport`/`onSetReport` pertinentes ao descriptor e acompanhar LEDs, especialmente Caps Lock. Conferir assinaturas na API instalada.
8. Em fechamento definitivo, liberar entrada, desconectar, desregistrar app e fechar proxy.

Estado mínimo exposto à UI: `PermissionRequired`, `BluetoothOff`, `Starting`, `Ready`, `Connecting`, `Connected`, `Disconnected`, `Error(message)`. Não criar framework de máquina de estados.

Erros visíveis necessários: permissões negadas, Bluetooth ausente/desligado, perfil HID indisponível, registro recusado, conexão falhou e envio recusado. Não apresentar "conectado" apenas porque o telefone está pareado.

### Reports HID e ordem

- Teclado: report ID 1, oito bytes: modificadores, reservado, seis usages. Soltura: report zerado.
- Mouse: report ID 2, quatro bytes: máscara dos botões, X relativo, Y relativo, roda vertical.
- `sendReport` recebe ID separado; não repetir o ID no payload.
- Eixos atuais são signed 8-bit: -127 a 127. Dividir deltas maiores e preservar a soma, inclusive para valores negativos.
- Manter o botão esquerdo ativo nos reports de movimento durante arraste.
- Serializar pressão/soltura do teclado; nunca intercalar dois textos em envio. Não bloquear a main thread com sleeps.
- Começar com pequeno intervalo entre pressão e soltura, por exemplo 12 ms com `delay`, e medir no hardware. Não tratar esse valor como requisito do protocolo.
- Verificar o booleano de `sendReport`: falha não equivale a texto entregue. Parar a sequência e informar envio incompleto; não reenviar automaticamente texto parcialmente digitado.

Prova mínima: parear, conectar, mover o cursor, clicar e digitar `abc123` em editor de texto vazio. Registrar versão Android/One UI, computador e resultado em `docs/VALIDACAO.md`.

**Se o perfil HID falhar no S24 Ultra:** registrar callbacks/erro, verificar permissões, registro em foreground e vínculo com o host. Não continuar polindo uma conexão inexistente. Usar o mesmo app com a etapa de rede como alternativa, documentando a mudança de ordem e sua razão.

## 7. Fase C — interface, gestos e teclado

### Interface

Uma tela principal escura com:

1. Cabeçalho `Omarchy Remote`, estado da conexão e botão "Computador".
2. Trackpad ocupando a maior área disponível.
3. Botões "Esquerdo", "Direito" e "Arrastar", com estado de arraste claramente visível.
4. Faixa de modificadores `Ctrl`, `Alt`, `Super`, `Shift`.
5. Teclas `Esc`, `Tab`, `Backspace`, `Delete`, `Enter`, setas; atalho visual para `C`, `V`, `X`, `A`, `Z` e números 1–9 quando modificadores estiverem selecionados.
6. Botão "Digitar" abre painel com campo de texto, seletor de layout e "Enviar". O painel respeita o teclado virtual e mantém algum espaço útil de trackpad.

Alvos de toque de pelo menos 48 dp, labels acessíveis, estados desabilitados quando desconectado e feedback de erro próximo da ação. Em landscape, dispor trackpad e teclado lado a lado se houver espaço; evitar travar orientação para contornar bugs.

Não adicionar botões de vídeo que aparentem funcionar nessa entrega.

### Gestos

| Ação | Resultado |
|---|---|
| Um dedo deslizando | Movimento relativo |
| Toque curto sem deslocamento significativo | Clique esquerdo |
| Dois dedos deslizando verticalmente | Roda vertical |
| Toque curto com dois dedos | Clique direito |
| "Arrastar" ativado + movimento | Botão esquerdo mantido até desativar/cancelar |

- Usar `pointerInput` e um reconhecedor coordenado; dois detectores independentes não devem consumir o mesmo gesto em conflito.
- Usar `touchSlop` e timeout do Android para distinguir toque de deslocamento.
- Ao entrar o segundo dedo, reancorar a referência. Ao voltar de dois para um, evitar salto do cursor e impedir clique acidental no fim do scroll.
- Calcular movimento em unidades consistentes com densidade; aplicar um único multiplicador de sensibilidade.
- Acumular restos fracionários antes de converter para inteiros. Não perder movimentos lentos de menos de um pixel por evento.
- A roda usa passos inteiros: acumular deslocamento até o limiar; sensibilidade e sentido devem ser documentados e testados.
- Não criar fila ilimitada de movimento. Se agregar eventos por frame, somar os deltas; descartar deltas relativos isolados altera a distância percorrida.
- Cancelamento do gesto, perda da sessão e saída do app devem soltar botões.

### Digitação

Primeira versão: edição local com o teclado Android e envio explícito do texto. Não tentar espelhar a composição interna do Gboard/Samsung Keyboard caractere a caractere nessa fase.

- ABNT2 como padrão inicial, porque foi encontrado na configuração do host.
- US simples como alternativa explicitamente sem acentos; **não confundir com `us(intl)`**, que também aparece na configuração do usuário. Implementar US internacional somente se necessário para o layout ativo real.
- Normalizar Unicode para NFC antes da conversão.
- Letras acentuadas em ABNT2 exigem sequências de dead key + letra; literal `~`, `^`, acento grave e agudo exigem também o espaço que conclui a dead key.
- Revisar mapeamento contra `/usr/share/X11/xkb/symbols/br` e a tabela oficial HID. Validar especialmente `ç`, aspas, barras, colchetes, chaves, ponto e vírgula e AltGr.
- O esqueleto `KeyboardLayout.kt` é preliminar: substituir associações opacas de strings/listas por mapeamentos legíveis onde ajudar. Evitar construir várias tabelas por caractere.
- Validar todo o texto antes do primeiro report. Se houver caractere não suportado, mostrar qual é e preservar o campo, sem envio parcial silencioso.
- Não prometer emoji ou Unicode arbitrário via teclado HID.
- Considerar Caps Lock reportado pelo host: ajustar a codificação ou bloquear o envio com orientação explícita. Não alterar Caps Lock silenciosamente.
- Modificadores selecionados permanecem visíveis e são limpos após o próximo comando, desconexão ou cancelamento.
- Não executar comandos arbitrários no Linux para simular atalhos. `Super+1`, `Ctrl+C` etc. devem ser teclas normais e obedecer aos binds do usuário.

### Ciclo de vida e reconexão

- O Android pode desregistrar HID quando o app sai de foreground. Tratar isso como parte do fluxo, e não presumir serviço permanente.
- Manter tela ligada apenas enquanto a tela de controle estiver ativa, com `FLAG_KEEP_SCREEN_ON`; não adicionar wake lock global.
- Ao pausar/interromper a interação: cancelar digitação em andamento, limpar fila e enviar releases enquanto a conexão ainda existir.
- Distinguir mudança de configuração de encerramento: usar ViewModel/contexto de aplicação para não recriar a conexão a cada rotação.
- Não fechar tudo indiscriminadamente em `onPause`: o prompt de pareamento/permissão também provoca transições de lifecycle.
- Ao voltar, consultar estado efetivo e registrar/reconectar ao último host se apropriado. Não reaplicar teclas ou texto antigos.
- Incluir botão de desconexão e ação para soltar todos os controles.
- Desconexão durante arraste deve limpar também o estado visual. Reconexão começa com reports neutros.
- Não criar foreground service até existir necessidade comprovada de controle com app em segundo plano.

## 8. Fase D — testes e critério de estabilização

### Testes JVM necessários

1. `HidReports`: bytes exatos, máscara dos botões, zero de soltura, negativos e movimento dividido com soma preservada.
2. `KeyboardLayout`: minúsculas/maiúsculas, pontuação de shell, `ação`, `Órgão`, `São Paulo`, `ç/Ç`, acentos literais, Unicode decomposto, caracteres não suportados e diferenças ABNT2/US.
3. Acumulador do pointer: subpixel, mudança de sinal, soma por frame e limiar do scroll.
4. Sessão de entrada com transporte fake: pressão precede soltura; cancelamento durante texto libera teclas; desconexão elimina comandos pendentes; uma nova sessão não recebe texto antigo.

Não escrever testes de screenshots apenas para repetir a estrutura dos composables. Testar comportamento que pode causar erro de entrada ou tecla presa.

### Matriz no S24 Ultra + Omarchy

- Parear do zero; cancelar e aceitar permissões; Bluetooth desligado e religado.
- Conectar ao host escolhido e reconectar após sair/voltar ao app.
- Movimento lento, rápido, diagonal, clique duplo, menu de contexto e seleção por arraste.
- Scroll em navegador e terminal sem cursor saltando.
- Digitação em editor, navegador e terminal; validar acentos e símbolos sem executar textos de teste como comandos.
- Atalhos `Ctrl+C`, `Ctrl+V`, `Ctrl+Shift+C`, `Alt+Tab`, `Super+1` conforme configuração real.
- Alternar orientação e abrir/fechar IME repetidamente.
- Desligar Bluetooth durante arraste e durante texto; nenhuma tecla/botão deve permanecer ativo ao reconectar.
- Testar pelo menos 30 minutos de uso contínuo e 10 ciclos de conexão, registrando falhas e condições.
- Testar o cursor atravessando todos os monitores já conectados. Bluetooth não precisa conhecer sua topologia.

Saída da V0.2: APK, README de instalação/pareamento, comandos de build e relatório distinguindo **passou**, **falhou** e **não testado**. Não concluir a estabilização apenas com build verde.

## 9. Fase E — conexão por rede, antes do vídeo

Esta etapa permite controle além do alcance Bluetooth e prepara sinalização para vídeo. Só criar seus arquivos ao iniciar a fase.

### Stack proposta

- Android: `NetworkInput`, cliente WebSocket com OkHttp e serialização Kotlin.
- Host: Python 3, `aiohttp` para WebSocket/HTTP e `python-evdev` para dispositivos `uinput`.
- Estrutura curta: `host/keypad_host/{main.py,pairing.py,protocol.py,input.py}`; testes em `host/tests/` e dependências fixadas.
- Um processo de usuário; um cliente controlador ativo; teclado/mouse virtuais criados para a sessão autorizada.
- Não usar `xdotool` como backend Wayland. `uinput` cria eventos no subsistema Linux de entrada.
- Não executar o processo inteiro como root. Preparar acesso restrito a `/dev/uinput` via regra/grupo apropriado, explicando o alcance dessa permissão e exigindo elevação só para instalação.

### Transporte e pareamento

- WSS em porta configurada, inicialmente 8765; endereço local explícito na primeira versão.
- Certificado TLS do host com pinning no Android. Para IP local, gerar SAN correspondente ou associar explicitamente a identidade ao pin; nunca instalar `TrustManager` que aceita qualquer certificado.
- Pareamento iniciado no host gera segredo aleatório de uso único, pelo menos 128 bits, expiração de 120 segundos e QR com endereço, fingerprint e segredo.
- Android verifica o fingerprint recebido pelo QR e troca o segredo por credencial individual revogável. Guardar a credencial protegida por Android Keystore; no host, armazenar hash, não segredo em claro.
- QR é apresentado localmente no computador; não enviar segredos para logs/telemetria. Adicionar permissão de câmera somente nesta etapa, ou leitura/importação equivalente.
- Permitir revogação no host e rejeitar novo controlador enquanto a sessão atual estiver ativa, com erro compreensível.
- Heartbeat a cada 2 segundos; após 6 segundos sem tráfego válido, liberar todas as teclas/botões e encerrar a sessão.
- Serviço começa manualmente. Unidade `systemd --user` somente quando o fluxo estiver estável; sem abrir portas no roteador.

### Protocolo v1

JSON UTF-8 em WebSocket ordenado. Cada envelope inclui `v`, `sessionId`, `seq`, `type`, `payload`; o host fornece `sessionId` após autenticação. Sequência monotônica por sessão; reconexão cria nova sessão.

```json
{"v":1,"sessionId":"...","seq":1,"type":"pointer.move","payload":{"dx":12,"dy":-3}}
{"v":1,"sessionId":"...","seq":2,"type":"pointer.buttons","payload":{"mask":1}}
{"v":1,"sessionId":"...","seq":3,"type":"pointer.scroll","payload":{"vertical":-1}}
{"v":1,"sessionId":"...","seq":4,"type":"keyboard.tap","payload":{"usage":6,"modifiers":1}}
{"v":1,"sessionId":"...","seq":5,"type":"input.releaseAll","payload":{}}
```

- `keyboard.tap` é atômico no host: pressão e soltura na mesma sequência de processamento. Traduzir HID para evdev por tabela explícita; não usar o número HID diretamente como `KEY_*`.
- Mensagens de controle críticas recebem confirmação por sequência; o retorno indica processamento, não que o aplicativo de destino aceitou a ação.
- Limitar frame, por exemplo 16 KiB; rejeitar tipos/intervalos inválidos antes de injetar input. Não receber comandos de shell.
- Pressões e solturas não podem ser descartadas por política de backpressure. Movimentos podem ser agregados mantendo a soma.
- Bluetooth e rede nunca enviam a mesma entrada simultaneamente. A troca de transporte primeiro libera/desativa a sessão anterior.

Aceite: mesma matriz de controle funciona em Wi-Fi; dispositivo não pareado não injeta eventos; perda de rede durante arraste libera controles por timeout; não há replay após reconectar.

## 10. Fase F — prova de captura e vídeo de um monitor

### Verificação técnica obrigatória antes de implementar player completo

Coletar em modo somente leitura: versão Hyprland, `hyprctl -j monitors`, GPU/driver, versões PipeWire/portal, `AvailableSourceTypes` e `AvailableCursorModes`, e encoders GStreamer disponíveis.

Baseline proposta:

```text
ScreenCast portal → PipeWire → GStreamer/encoder → WebRTC
                                                   |
                                    Android libwebrtc → Surface

Compose gestures → NetworkInput → WSS → host/uinput
```

Manter entrada em WSS enquanto vídeo usa WebRTC. Não introduzir dois canais concorrentes de input ou transporte de frames por JSON/base64.

### Captura

- Usar `org.freedesktop.portal.ScreenCast` sobre D-Bus: criar sessão, selecionar monitor, iniciar e obter o remote FD PipeWire.
- Tratar resposta assíncrona, cancelamento de consentimento, fechamento de sessão e remoção do monitor.
- Não presumir que node ID é identidade permanente. Preferir serial quando disponível; respeitar a versão do portal e a identidade da sessão.
- Solicitar cursor embutido quando suportado para evitar implementar cursor separado inicialmente. Não desenhar um segundo cursor por cima do vídeo.
- Backend do portal pode não oferecer tudo que a especificação permite. Há relatos no projeto Hyprland de `multiple` ignorado; verificar a instalação atual, sem tratar um relato antigo como diagnóstico atual.

### Media stack e dependências

- Estender o host Python com PyGObject/GStreamer (`pipewiresrc`, encoder, RTP payloader, `webrtcbin`). Usar D-Bus por biblioteca existente; não implementar protocolo PipeWire ou WebRTC manualmente.
- Integrar callbacks GLib ao host de forma explícita: loop GLib dedicado para pipeline, marshaling thread-safe com asyncio. Não acessar pipeline arbitrariamente de diferentes threads.
- Antes de fixar H.264, provar negociação e decode no S24 Ultra. Preferir H.264 por hardware quando ambos os lados suportarem; VP8 por software é fallback de desenvolvimento se não houver encoder disponível. Não presumir VA-API/NVENC sem inspecionar a GPU.
- Android: libwebrtc, `PeerConnectionFactory`, decoder por hardware e renderer integrado ao Compose por `AndroidView`.
- A origem do AAR WebRTC é uma decisão de execução obrigatória: selecionar distribuição mantida com fonte/licença e versão/commit fixados, ou gerar AAR a partir de checkout oficial fixado. Não inventar coordenada Maven nem depender de `latest`. Registrar ABI `arm64-v8a` e checksum do artefato escolhido.
- Sinalização SDP/ICE via WSS já autenticado. Vídeo por DTLS-SRTP. Não usar um servidor público de signaling.
- LAN inicialmente: ICE local, sem TURN e sem prometer travessia de NAT. Falha de ICE deve ser visível.

### Primeira entrega de vídeo

1. Autorizar um monitor no computador.
2. Transmitir inicialmente em 1280×720, 30 fps, bitrate aproximado de 3–5 Mbps.
3. Mostrar imagem inteira preservando proporção, sem corte involuntário.
4. Oferecer overlay de trackpad relativo e teclado; foco e botões não podem acionar controles remotos sem intenção.
5. Preservar orientação do conteúdo; rotação do telefone muda viewport, não resolução do monitor automaticamente.
6. Recuperar sessão após perda de rede sem enviar entrada acumulada.
7. Só subir para 1080p/60 depois de medir CPU, temperatura, perdas e latência.

Meta inicial, não promessa: vídeo utilizável em LAN com latência visual abaixo de aproximadamente 150 ms sob condições registradas. Medir ponta a ponta por filmagem/contador visível; RTT de WebRTC sozinho não mede latência de vídeo.

Aceite: 20 minutos de vídeo + entrada no S24 Ultra, sem fila de frames crescendo, com encerramento limpo e reconexão. Se a prova de mídia falhar, documentar a falha concreta e resolver antes de começar múltiplos monitores.

## 11. Fase G — qualquer monitor e visão geral

Separar requisitos em duas entregas: **selecionar qualquer monitor autorizado** e depois **ver todos simultaneamente**. A primeira não prova a segunda.

### Inventário e identidade

Host mantém catálogo com `monitorId`, nome do conector, descrição, retângulo lógico global, resolução de captura, escala, transformação e estado de captura/autorização. Acrescentar `topologyVersion` para invalidar coordenadas antigas.

- Obter geometria do Hyprland por API/IPC documentada, começando por `hyprctl -j monitors` para inventário e verificando unidades dos campos na versão instalada.
- Correlacionar monitor e stream usando metadados disponíveis; não usar apenas a ordem de arrays ou PipeWire node ID.
- Não prometer identificador persistente entre boots a partir de um índice numérico temporário.
- Preferir eventos de mudança de monitor; uma atualização manual serve como primeira etapa. Não fazer polling por frame.
- Monitores não autorizados aparecem como indisponíveis para vídeo, sem reutilizar silenciosamente captura de outro monitor.

### Seleção e captura simultânea

1. Lista no Android com monitores disponíveis; selecionar inicia/substitui stream do monitor escolhido.
2. Ao trocar, bloquear apontamento direto até receber metadados e primeiro frame do novo monitor. Limpar input pendente do stream anterior.
3. Testar `multiple=true` no portal instalado. Se não retornar todas as telas autorizadas, testar sessões de captura separadas, uma por monitor, com consentimento.
4. Se o backend não aceitar nem sessões simultâneas, a visão geral continua pendente: decidir integração de captura específica do Hyprland como tarefa explícita, com evidência e custo registrados. Não marcar multimonitor como pronto mostrando a mesma tela duplicada.
5. Para visão geral, criar uma track por monitor autorizado na mesma conexão WebRTC e mapear `mid/trackId → monitorId`; compor no Android conforme retângulos lógicos. Começar com taxa/resolução menores nas miniaturas.
6. Ampliar o monitor selecionado sem alterar a topologia real do desktop. O número de streams ativos e orçamento de bitrate depende de medição no hardware, não do número máximo teórico de monitores.

### Coordenadas e toque direto

Trackpad relativo continua disponível e não depende de coordenadas de vídeo. Toque direto é uma capacidade posterior, separada do contrato inicial:

```json
{"v":1,"sessionId":"...","seq":81,"type":"pointer.absolute","payload":{"monitorId":"DP-1","topologyVersion":4,"x":0.25,"y":0.60}}
```

`x/y` normalizados em [0,1] referem-se à imagem orientada daquele monitor. O Android deve desfazer zoom/pan e remover letterbox antes da normalização; toque fora da imagem não gera input.

Se a imagem já estiver orientada igual ao compositor, a conversão básica é:

```text
globalX = logicalRect.x + normalizedX × logicalRect.width
globalY = logicalRect.y + normalizedY × logicalRect.height
```

Limitar o resultado ao interior do retângulo: a borda direita/inferior é exclusiva, portanto `x=1` ou `y=1` não pode colocar o cursor no monitor vizinho. Se o buffer tiver outra orientação, aplicar a transformação inversa antes. Retângulos lógicos podem ter origem negativa; pixels do stream não são necessariamente unidades do compositor. Tratar escala fracionária e monitores rotacionados com testes, sem dividir por escala duas vezes.

Para posicionamento absoluto no Hyprland, validar um backend específico usando `movecursor`/IPC documentado; iniciar com cliques discretos e medir. Não assumir que um dispositivo `uinput` absoluto será automaticamente mapeado para o desktop inteiro. Se houver movimento contínuo direto, evitar iniciar subprocesso por evento; usar IPC ou protocolo suportado na instalação. Botões/teclado continuam via uinput.

### Aceite multimonitor

- Dois monitores em horizontal e vertical.
- Monitor à esquerda/acima da origem, com coordenadas negativas.
- Escalas diferentes e pelo menos uma escala fracionária.
- Monitor rotacionado, resoluções diferentes, encaixe integral e zoom.
- Troca repetida entre monitores e visão geral real.
- Desconectar monitor selecionado, reconectar, mudar resolução e suspender/retomar sessão.
- Toque em pontos conhecidos atinge o monitor/local correto; versão de topologia antiga é rejeitada.

## 12. Entregas e sequência de commits sugerida

Somente se houver repositório gravável e commits fizerem parte do fluxo autorizado:

| Ordem | Entrega | Critério para avançar |
|---|---|---|
| 1 | Base Gradle/Activity/tema | APK compila e abre |
| 2 | Conexão e reports HID | Mouse + `abc123` no S24 Ultra real |
| 3 | Gestos e botões | Clique, scroll e arraste confiáveis |
| 4 | Teclado ABNT2 e atalhos | Português e modificadores corretos |
| 5 | Lifecycle/reconexão/documentação | Matriz V0.2 registrada |
| 6 | Host local + WSS autenticado | Controle por rede, revogação e timeout |
| 7 | Prova PipeWire/WebRTC | Um monitor com vídeo e input |
| 8 | Player e recuperação de vídeo | Uso sustentado no S24 Ultra |
| 9 | Seleção de monitores | Qualquer monitor autorizado |
| 10 | Visão geral e toque direto | Matriz multimonitor aprovada |

Não incluir vídeo, serviço Linux, permissões de câmera ou dependências de rede nos primeiros commits por conveniência futura.

## 13. Handoff e limites de conclusão

O próximo agente deve começar pela Fase A e pela prova Bluetooth. Entregar progresso implementado e verificado, não só nova análise. Quando faltar o aparelho, concluir build/testes locais e deixar explícito o teste físico pendente; não simular sucesso de Bluetooth em emulador.

Para teste real será necessário o usuário conectar o S24 Ultra, habilitar depuração USB e autorizar o computador, ou instalar manualmente o APK. Confirmar o serial real antes de instalar. Não reutilizar serial de outro projeto/conversa.

O fluxo de pareamento exige interação com os prompts dos dois dispositivos. Não alterar remotamente binds, layout global, firewall ou serviços do Omarchy apenas para esconder uma falha do app. Fazer mudanças estritamente necessárias e documentá-las.

Entrega de cada fase contém: código, instruções reproduzíveis, artefato quando aplicável, validação realizada, limitações observadas e próxima etapa. Separar claramente código implementado de capacidade comprovada no hardware.

## 14. Fontes técnicas consultadas

- Android HID: [BluetoothHidDevice](https://developer.android.com/reference/android/bluetooth/BluetoothHidDevice) — registro, callbacks, reports e restrição de foreground.
- Android: [Permissões Bluetooth](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions).
- Android: [Gestos em Compose](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures).
- Build: [AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes) e [Compose/Kotlin](https://developer.android.com/jetpack/androidx/releases/compose-kotlin).
- Linux: [uinput](https://www.kernel.org/doc/html/latest/input/uinput.html).
- Captura: [XDG ScreenCast portal](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.ScreenCast.html).
- Hyprland: [portal](https://wiki.hypr.land/hypr-ecosystem/user/xdg-desktop-portal-hyprland/) e [hyprctl](https://wiki.hypr.land/configuring/core/advanced-configuration/using-hyprctl/).
- Histórico de captura: [issue 217 no projeto do portal](https://github.com/hyprwm/xdg-desktop-portal-hyprland/issues/217). É evidência de risco a testar, não confirmação de defeito atual nesta máquina.
- Mídia: [GStreamer webrtcbin](https://gstreamer.freedesktop.org/documentation/webrtc/) e [WebRTC Android](https://webrtc.googlesource.com/src/+/main/docs/native-code/android/).

As escolhas futuras de Python/WSS/GStreamer/libwebrtc e os critérios de aceite são propostas deste plano. A documentação confirma as APIs, mas não comprova a integração completa neste computador.
