# Univali Uncanny

Jogo de terror em primeira pessoa feito em **Java + LWJGL 3 (OpenGL 3.3)** para a disciplina de
Computação Gráfica da **Univali**, construído sobre o projeto de OpenGL do professor.

Você acorda na carteira, sozinho, com o relógio parado em 23:58. O professor não aceita ninguém
sem as três provas (**M1, M2 e M3**), e elas estão perdidas na floresta em volta do campus. Na
neblina vive o **Dragon Knight**: quando ele aparece, você tem **5 segundos** para derrubá-lo com a
escopeta. Se não conseguir, reprovado.

Cada partida tem um mapa diferente: o relevo e a posição das provas, das caixas de munição e da
Univali são gerados por **algoritmo genético**, com o **A\*** garantindo que tudo é alcançável.

## Baixar e jogar

Baixe a versão mais recente em **[Releases](https://github.com/thiscesar/univali-uncanny/releases/latest)**.
Não precisa instalar Java: ele já vem dentro do pacote.

| Sistema | Como abrir |
|---|---|
| **Windows** | Extraia o `UnivaliUncanny-windows.zip` e clique duas vezes em `UnivaliUncanny.exe`. Se aparecer "o Windows protegeu o computador", clique em *Mais informações* → *Executar assim mesmo* (o programa não tem assinatura digital). |
| **Linux** | Extraia o `UnivaliUncanny-linux.tar.gz` e abra `UnivaliUncanny/bin/UnivaliUncanny`. Para ter o atalho com ícone no menu de aplicativos, rode uma vez `./criar-atalho-linux.sh` dentro da pasta. |

Requisitos: placa de vídeo com **OpenGL 3.3** e cerca de **1 GB de memória livre**.

## Controles

| Ação | Tecla |
|---|---|
| Andar / olhar | W A S D / mouse |
| Correr | Shift |
| Atirar / recarregar | botão esquerdo / R |
| Lanterna | F |
| Usar (portas, crachá, professor, provas, caixas) | E |
| Pular a introdução | Enter ou Espaço |
| Pausa | ESC |

## Como funciona

- **Terreno e fase por algoritmo genético** (`ag`, `terreno`, `fase`): um AG genérico evolui o
  relevo (água, montanhas, rugosidade, tamanho dos lagos) e outro posiciona provas, caixas e o
  prédio, premiando distância e progressão.
- **A\*** (`grafos`): busca em grade sobre o relevo, usada para validar que toda fase é jogável.
- **Monstro MD2** (`md2`): leitor do formato do Quake II, com a interpolação entre quadros feita no
  shader.
- **Renderização** (`render`, `shaders`): texture splatting no terreno, neblina exponencial,
  lanterna com sombras (shadow mapping com PCF), árvores com instancing, escopeta como viewmodel,
  normal mapping e pós-processamento (vinheta, granulado e um filtro opcional no estilo PS1).
- **Som 3D** (`audio`): OpenAL com OGG (stb_vorbis); o que não tem arquivo gravado é sintetizado
  por código.
- **Telas** (`telas`): menu, carregamento com a história em máquina de escrever, jogo, pausa,
  vitória e derrota.

O código original do professor está em `main`, `obj`, `shaders`, `util`, `dados` e `Model`; cada
alteração nele está marcada no código com `// ALTERADO`.

## Rodar pelo código

Precisa de **Java 17+** e **Maven**. Rode sempre a partir da raiz do projeto:

```bash
mvn -q compile exec:java -Dexec.mainClass=main.Main3D
```

Testes automatizados (AG, A\*, MD2, regras, nota e outras partes de lógica):

```bash
mvn test
```

Prévia em 2D do terreno gerado pelo AG: classe `MainTerrenoPrevia` (argumento `floresta` para o
terreno do jogo).

## Gerar os pacotes

```bash
bash empacotamento/empacotar.sh
```

Gera `dist/UnivaliUncanny-linux.tar.gz` com `jpackage`. No Windows, o equivalente é
`empacotamento\empacotar.ps1`. Uma tag `v*` enviada ao GitHub gera os dois pacotes e cria a
Release automaticamente (`.github/workflows/pacotes.yml`).

## Autores

**Cesar Luiz de Sousa Júnior** e **Vitor Borges Paes**, Ciência da Computação, Univali, Campus
Kobrasol.

Modelos, sons, fontes e bibliotecas de terceiros estão listados com autores e licenças em
[`creditos.txt`](creditos.txt) (também visíveis no menu do jogo).
