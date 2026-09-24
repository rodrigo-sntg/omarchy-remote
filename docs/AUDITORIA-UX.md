# Auditoria de UX — "o Steve Jobs aprovaria?" (24/09/2026)

Revisão crítica de todas as telas, com prints no S24 e um emulador de revisão (inglês e português). Achados de um revisor independente e da navegação real, corrigidos em lotes.

## O que estava ruim, e o que ficou

| Onde | Antes | Agora |
|---|---|---|
| Estado com tema monocromático (Solitude) | "Ligado" e "desligado" ficavam iguais: tudo cinza (bolinha de conexão, switches, abas, Ver PC) | Acento cinza vira preenchimento claro; conexão verde e "precisa de você" âmbar em qualquer tema; segmento escolhido é uma pílula sólida |
| Barra de status | "meu-pc Rede · Tailsc…", ícone do Omarchy parecendo documento, "1 agentes" | Só o nome do PC; ícone de grade ("O PC") e engrenagem; plural certo, âmbar quando alguém espera |
| Card de telas | "ONDE ESTÁ O CURSOR", DP-1/HDMI-A-1, "WS", "+", números soltos | "Telas" com "Celular como tela" e "Ver PC"; monitores por posição (Esquerda/Direita); workspaces em chips com "Novo" |
| Atalhos e dock | "C" parecia a letra; "Esc · sair"; ícones sem nome | "⌃C"; "Esc · cancelar"; cada botão com nome (Teclado, Texto, Terminal, Ver PC) |
| Primeira vez | Começava no Bluetooth pedindo permissão, com workspaces/atalhos/dock desabilitados em volta | Começa na rede e mostra só "Escolha um PC" |
| Folha do PC | Abas que trocavam a conexão ao tocar (derrubava a rede); comando `host/run.sh` na tela; QR secundário | Abas só mostram; conectar é que troca; QR é o caminho; digitar é a alternativa |
| Erros | "--allow", "perfil HID", "registro HID", título "Erro" | O que houve e o que fazer; "Não conectou" com "Tentar de novo" e "Trocar de PC" |
| Bateria | Pedia isenção a cada abertura | Só ao ligar "Avisar quando um agente precisar de mim", com explicação |
| Ajustes | Ordem aleatória; Idioma no topo | Trackpad, Tela do PC, Teclado do PC, Avisos, Atalhos, Idioma, versão |
| Folha do Omarchy | "Agora" misturava tudo; carregamentos eternos; fechar janela sem volta | "PC" com Controles primeiro; "O PC não respondeu" + "Tentar de novo"; fechar janela pede confirmação |
| Agentes | "à espera" (ocioso) vs "esperando você" (precisa); prévia em 10,5 sp; voltar fechava tudo; "w9" | "parado"; prévia legível; voltar retorna à lista; sem jargão |
| Mensagens | Erro adivinhado por palavras; 4 s para tudo; TalkBack mudo | ✓ ou !, tempo pelo tamanho, anunciadas |
| Toque | Alvos de 26–44 dp | 48 dp |
| Camadas | Teclado e Texto sem saída visível; "Trazer"/"Pôr este texto" | Título + "Fechar"; Home/End/PgUp/PgDn; "Copiar do PC"/"Colar no PC" |
| Terminal | "meu-pc herdr", botões de 34 dp, "Prefixo" | "Terminal · meu-pc", 48 dp, "Ctrl+␣" |
| Ver PC | ms/qps sem sentido; "Mais" cortado; carregando sem saída; beco sem saída no teclado completo | Só avisa "rede lenta"; "Mais" em duas colunas; "Cancelar" e desistência clara; "Digitar" no lugar do beco |
| Idioma | Só português | Português ou inglês (segue o celular; escolha em Ajustes), troca na hora |

Detalhes do Ver PC: `docs/UX-VER-PC.md` e o canvas de design.
