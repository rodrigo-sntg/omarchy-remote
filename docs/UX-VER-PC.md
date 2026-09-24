# Ver PC — análise de usabilidade (23/09/2026)

O Ver PC acumulou recursos (lupa, telas, janela em foco, print, OCR, apresentação), mas o uso do dia a dia continua trabalhoso. A análise abaixo segue as tarefas reais: ler, rolar, clicar com precisão, escrever, trocar de tela e sair.

## O que atrapalha hoje

1. **Rolar exige dois dedos.** Com um dedo, deslizar só move o cursor ("hover") e nada visível acontece. No celular, arrastar um dedo é rolar. É o gesto mais frequente, e hoje ele "não funciona".
2. **Clicar com precisão é adivinhar.** O monitor aparece reduzido (3440 px em 2340 px) e o dedo cobre o alvo. No toque direto não há sinal de onde o clique caiu: sem anel, sem eco e sem vibração no toque.
3. **Escrever tira você da tela.** "Teclado" abre uma camada que cobre o vídeo, e para texto a mensagem manda voltar à tela principal. Não dá para ver o que se digita.
4. **Tudo custa dois toques, e fica escondido.** As ações do dia a dia (clique direito, segurar, teclado, telas, lupa) ficam no leque, que precisa ser aberto primeiro, ou arrastado para o item. O resto (modo, print, texto, apresentação, ajustes) fica no menu do chip. A divisão entre os dois não segue lógica de uso.
5. **O clique direito do leque vai para onde o cursor estava**, não para onde você quer. No toque direto, o cursor está onde foi o último toque, o que raramente é o alvo.
6. **Sair sem querer.** O gesto de voltar do Android (deslizar da borda) fecha o Ver PC na hora, e a borda esquerda é justamente onde ficam as abas de workspace.
7. **Chip poluído.** "meu-pc · HDMI-A-1 · toque direto · 34 ms · 46 qps" é informação demais para uma olhada.

## O que muda

| Tarefa | Antes | Agora |
|---|---|---|
| Rolar | 2 dedos | **1 dedo arrasta e rola** (vertical e horizontal), com inércia ao soltar |
| Clicar | toque, sem retorno | toque + **eco visual no ponto** + vibração curta |
| Precisão | lupa manual pelo leque | **segurar o dedo mostra a lupa sobre o ponto** (acima do dedo); arrastar mantém a lupa |
| Arrastar / selecionar | segurar e deslizar | igual, agora com a lupa acompanhando |
| Clique direito | segurar e soltar, ou leque (no cursor) | segurar e soltar, ou **"Direito" arma o próximo toque** como clique direito, onde você tocar |
| Zoom | pinça; "2 dedos tocam" = ver tudo | pinça; **2 dedos arrastam a vista** com zoom; toque com 2 dedos = ver tudo |
| Escrever | camada que cobre o vídeo | **barra "Digitar"** com o teclado do Android: o texto vai ao PC enquanto você digita, com Esc, Tab, setas e Enter à mão, e a vista aproxima do cursor para você ver o texto |
| Ações | leque (2 toques) + menu do chip | **trilho à direita, 1 toque**: Digitar, Direito, Segurar, Lupa, Telas, Mais. Fica na faixa preta quando o monitor não ocupa a largura e esmaece parado |
| Sair | voltar sai na hora | **voltar duas vezes**; bordas protegidas do gesto do sistema |
| Chip | 5 informações | monitor (ou janela) + latência com cor |

O modo trackpad continua igual (é outro modelo mental: o celular vira um touchpad) e passa para "Mais".
