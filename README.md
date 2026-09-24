# Omarchy Remote

Aplicativo Android que transforma o celular (alvo: Samsung Galaxy S24 Ultra) em **teclado e trackpad Bluetooth** para um computador com Omarchy/Hyprland. Usa o perfil Bluetooth HID do Android (`BluetoothHidDevice`): o computador vê um teclado + mouse comuns, sem servidor ou programa auxiliar no Linux.

Estado atual: **V0.1 implementada, ainda não validada no S24 Ultra.** Veja [`docs/VALIDACAO.md`](docs/VALIDACAO.md) para o que foi testado e o que falta. O plano completo está em [`docs/PLANO-TECNICO.md`](docs/PLANO-TECNICO.md).

## Build

Requisitos: JDK 17 e Android SDK (platform 36). O SDK é lido de `ANDROID_HOME` ou de `local.properties` (`sdk.dir=...`, ignorado pelo Git).

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk \
ANDROID_HOME=$HOME/Android/Sdk \
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Instalação

Com depuração USB ativada no celular e o computador autorizado:

```bash
adb devices -l                 # confirme o serial do S24 Ultra
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Ou copie o APK para o celular e instale manualmente.

## Primeiro pareamento

1. Abra o app e toque em **Permitir** (permissão "Dispositivos próximos"). Se o Bluetooth estiver desligado, use **Ligar Bluetooth**.
2. Espere o estado **"Pronto — escolha um computador"** (o app registrou o teclado/mouse HID).
3. Toque em **Computador → Tornar visível para parear (2 min)** e mantenha o app aberto.
4. No computador, pareie pelo gerenciador Bluetooth (por exemplo `bluetoothctl`: `scan on`, `pair <MAC>`, `trust <MAC>`, `connect <MAC>`). Confirme o código nos dois aparelhos.
5. Se o computador não iniciar a conexão HID, toque em **Computador** e escolha o computador na lista de pareados.

O app nunca remove pareamentos existentes e só se conecta ao computador escolhido. O último computador é lembrado para reconexão ao voltar ao app.

## Uso

| Ação no trackpad | Resultado |
|---|---|
| Um dedo deslizando | Move o cursor |
| Toque curto | Clique esquerdo |
| Toque curto com dois dedos | Clique direito |
| Dois dedos deslizando na vertical | Rolagem natural (dedos para cima rolam a página para baixo, como no celular) |
| **Arrastar** ativado + movimento | Botão esquerdo mantido até tocar em Arrastar de novo |

- **Workspaces** (fileira `‹ 1 2 … 10 ›`): troca de workspace do Omarchy com os atalhos padrão, enviados como teclas normais: `Super+1…0` para ir direto, `Super+Tab` e `Super+Shift+Tab` para o próximo e o anterior (entre os workspaces ocupados). Se você mudar esses binds no Hyprland, os botões seguem o que estiver configurado.
- **Ctrl / Alt / Super / Shift** ficam marcados e se aplicam à próxima tecla (inclusive atalhos C, V, X, A, Z e 1–9 que aparecem quando há modificador). São limpos depois dessa tecla, ao desconectar ou ao sair do app.
- **Digitar** abre um campo de texto editado com o teclado do Android; **Enviar** digita o texto no computador. O layout escolhido deve ser o layout **ativo no computador** (padrão: Português ABNT2). Caracteres que o layout não produz (emoji, `€`, etc.) são apontados e nada é enviado.
- Envio de texto é bloqueado se o computador indicar Caps Lock ativo.
- **Computador → Soltar tudo** libera teclas e botões; **Desconectar** encerra a conexão. A sensibilidade do trackpad fica na mesma tela.

## Conexão pela rede (Tailscale)

Alternativa ao Bluetooth, sem limite de alcance: o celular manda os comandos por WebSocket para um pequeno serviço no PC (`host/`), que cria teclado e mouse virtuais via `uinput`. Nunca há dois transportes ativos ao mesmo tempo. Ao escolher Rede, o celular deixa de ser um dispositivo Bluetooth HID.

Requisitos: Tailscale ativo no PC e no celular, e acesso do seu usuário a `/dev/uinput`.

No PC, uma vez:

```bash
yay -S omarchy-remote-git        # ou, a partir deste repositório: host/omarchy-remote-setup
omarchy-remote-setup             # confere o Tailscale, liga o serviço, integra ao Omarchy e mostra o QR
```

- Na primeira execução o serviço cria um **código de pareamento** (`~/.config/omarchy-remote/token`, legível só pelo seu usuário). O QR de `omarchy-remote pair` (ou Super+Espaço › Phone › Pair phone) o leva ao app sem digitar nada.
- O serviço escuta **só no IP do Tailscale** (porta 8765). Uma conexão só é aceita com as três coisas: o código de pareamento correto, uma máquina **sua** neste tailnet (dono conferido com `tailscale whois`; máquinas com tag e de outros tailnets ficam de fora) e nenhum `Origin` de navegador. Para restringir a aparelhos específicos: `--allow <máquina>` (via `systemctl --user edit omarchy-remote-host.service`).
- **Aparelhos**: `omarchy-remote devices` lista os celulares que conectaram; `omarchy-remote devices revoke <nome>` barra um deles mesmo com o código; `omarchy-remote new-code` troca o código (todos pareiam de novo) e mostra o QR novo.
- Um controlador por vez. Sem mensagens por 6 s, o serviço solta todas as teclas e botões e encerra a sessão. Ao desconectar, também solta tudo. Nada é reenviado ao reconectar.
- Só aceita os tipos do protocolo v1 (movimento, botões, rolagem, tecla, soltar tudo, workspace, ping e os `agent.*` do herdr). O único programa que ele executa é a CLI do `herdr`, com argumentos validados: alvo `w1:p1` ou nome de agente, teclas de uma lista fixa (Enter, Esc, Tab, setas, y, n, Ctrl+C) e o texto do prompt sempre depois de `--`.

No celular: **Computador → Rede (Tailscale)**, digite o nome MagicDNS do PC (por exemplo `meu-pc.tail1234.ts.net`) e o código de pareamento, e toque em **Conectar pela rede**. O app só aceita nomes `*.ts.net`: o WebSocket vai sem TLS porque o Tailscale já criptografa o tráfego (WireGuard), e o Android só libera tráfego sem TLS para esse domínio.

Testes do host: `cd host && .venv/bin/python -m pytest -q`.

## No Omarchy

`omarchy/install.sh` (pode rodar de novo quando quiser) deixa o app parte do Omarchy:

- **Ícone na barra** (plugin `rodrigo.phone`): aceso com o celular conectado; a dica mostra a bateria e quantos agentes do herdr esperam por você. Clique abre o menu Phone; clique do meio manda a área de transferência para o celular.
- **Super+Espaço → Phone**: parear por QR, mandar a área de transferência, abrir a tela do PC, a tela extra ou o herdr no celular (os itens de ação só aparecem com o celular conectado). O instalador acrescenta o bloco uma vez em `~/.config/omarchy/extensions/omarchy-menu.jsonc` e guarda a versão anterior em `.bak-omarchy-remote`.
- **Tema**: o app usa as cores do tema ativo do Omarchy e troca junto (hook `theme-set.d/omarchy-remote`). Temas claros emprestam só a cor de destaque; o app é escuro.
- **Avisos** do Omarchy quando o celular conecta ou desconecta.
- **CLI** `omarchy-remote`: `status`, `connected`, `pair`, `send-clipboard`, `send-file`, `screenshot`, `ring` (tocar o celular, mesmo no silencioso), `devices`, `new-code`, `theme-changed`, `open screen|extra|terminal`. Fala com o serviço por um socket local só do seu usuário (`$XDG_RUNTIME_DIR/omarchy-remote/control.sock`).

No celular, **Computador → Rede → Ler QR do PC** lê o QR de `Super+Espaço → Phone → Pair phone (QR)` e conecta sem digitar nada. O código de pareamento fica cifrado com uma chave do Keystore do Android.

## Ligar o PC pelo celular

Com o PC suspenso ou desligado (na tomada), o app mostra **Ligar o PC**: manda o pacote mágico (Wake-on-LAN) pela rede de casa e reconecta sozinho quando o PC volta. O `omarchy-remote-setup` oferece ligar o Wake-on-LAN na placa com cabo (NetworkManager); em alguns PCs é preciso ativar também na BIOS. O celular precisa estar no Wi-Fi da mesma casa.

## Distribuição

- **AUR**: `packaging/aur/` tem o `PKGBUILD`, o `.install` e o `.SRCINFO` do `omarchy-remote-git` (mesmo modelo do `omarchy-ai-usage-git`): serviço em `/usr/lib/omarchy-remote`, unidade systemd de usuário, regra udev do `uinput`, integração ao Omarchy aplicada por usuário pelo `omarchy-remote-setup`.
- **APK**: `scripts/release-apk.sh` gera `dist/omarchy-remote-<versão>.apk` assinado e minificado. A chave fica fora do repositório (`~/.config/omarchy-remote-release/`); sem ela nenhuma atualização instala por cima.

## herdr em qualquer lugar

Com o [herdr](https://herdr.dev) rodando no PC (no Omarchy, `Super+Ctrl+Enter`), pela rede:

- **Terminal**: o botão de terminal no dock (ou "herdr" na coluna da paisagem) abre o herdr do PC em texto puro. O host liga um PTY a `herdr --session default` (`/v1/term`); fechar encerra só esse cliente, a sessão do herdr continua. A barra tem Esc, Tab, Ctrl e Alt (valem para a próxima tecla), **Prefixo** (Ctrl+Espaço, o prefixo do herdr no Omarchy), setas, Enter e o teclado do Android.
- **Agentes**: a linha de status mostra quantos agentes o herdr tem e quantos esperam por você. A folha lista cada um (trabalhando, precisa de você, terminou, à espera), mostra as últimas linhas, responde com Enter, Esc, y, n e setas, manda um prompt novo quando o agente está parado e abre o pane no terminal.
- **Em segundo plano**: em **Ajustes → Acompanhar em segundo plano**, um serviço fixo mantém a conexão (notificação "Ligado a meu-pc · N agentes") e reconecta sozinho a cada 30 s se cair. Quando um agente trava pedindo aprovação ou termina, chega uma notificação; tocar abre esse agente. O Android pede para liberar a otimização de bateria.

Sem Tailscale no celular, para testes: `host/run.sh --dev-loopback` escuta em 127.0.0.1, `adb reverse tcp:8765 tcp:8765` leva a porta do celular ao PC, e o build de debug aceita o endereço `localhost`. O código de pareamento continua obrigatório; builds de release não aceitam `localhost`.

## Limitações conhecidas

- O Android encerra o registro HID quando o app sai da tela. Ao voltar, o app registra de novo e reconecta ao último computador se estava conectado.
- Em segundo plano só a conexão pela rede continua, e só com "Acompanhar em segundo plano" ligado (sem ele a conexão é encerrada ao sair do app e refeita ao voltar). Vídeo e terminal fecham ao sair do app.
- O serviço do PC roda na sessão do usuário e é iniciado manualmente. Não funciona na tela de login nem com o PC suspenso.
- Layout US é o `us` simples, sem acentos; `us(intl)` não é suportado.
