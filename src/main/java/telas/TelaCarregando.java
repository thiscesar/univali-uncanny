package telas;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.opengl.GL11.*;

import java.awt.Color;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dados.Constantes;
import dados.Opcoes;
import dados.Recursos;
import entrada.Entrada;
import entidades.PredioUnivali;
import mundo.Arvores;
import mundo.Arvores3D;
import mundo.ConsultaAltura;
import mundo.DistribuidorArvores;
import mundo.GradeColisao;
import mundo.DistribuidorPedras;
import mundo.LeitorObj;
import mundo.Pedras;
import mundo.MapaDetalhes;
import mundo.Regioes;
import fase.GeradorFase;
import fase.Placa;
import mundo.Mundo;
import mundo.TexturasFloresta;
import md2.PersonagemMd2;
import obj.Mapa3D;
import obj.ObjHTGsrtm;
import render.AtmosferaMenu;
import render.Fonte;
import render.Overlay2D;
import render.Texto;
import render.TextoTextura;
import terreno.GeradorTerreno;
import terreno.PerfilTerreno;

/**
 * Tela "Carregando mapa...": gera o mapa sem congelar a janela.
 *
 * O algoritmo genético do terreno, que leva vários segundos, roda numa thread separada
 * (um ExecutorService de uma thread). Enquanto isso, a thread principal continua
 * desenhando a tela e lendo o teclado. A thread do AG só produz dados puros (o int[][]
 * de alturas): o OpenGL só pode ser usado na thread principal, então a malha, as texturas
 * e os shaders são criados depois, aqui, uma etapa por quadro, para a barra continuar andando.
 *
 * O progresso do AG chega pelo OuvinteGeracao, num campo volatile (escrito por uma thread,
 * lido pela outra). ESC volta ao menu e interrompe a geração.
 */
public class TelaCarregando implements Tela {

	/** PRONTO: o mapa carregou, mas a história ainda está na tela: espera o Enter. */
	private enum Estado { GERANDO, MONTANDO, PRONTO, SAINDO, ERRO }

	/** O terreno do jogo: 300 x 300 m, 1 m entre pontos, alturas em centímetros. */
	private static final PerfilTerreno PERFIL = PerfilTerreno.FLORESTA;
	private static final float METROS_ENTRE_PONTOS = 1f;
	private static final float METROS_POR_UNIDADE = 1f / PERFIL.unidadesPorMetro(); // centímetros -> metros

	/** O que a thread de geração entrega: só dados, nada de OpenGL. */
	private record DadosTerreno(int[][] alturas, ConsultaAltura chao, GradeColisao colisao,
			List<DistribuidorArvores.Arvore> arvores, List<LeitorObj.ModeloObj> modelosArvores,
			PersonagemMd2.Dados monstro, GeradorFase.Fase fase, List<DistribuidorPedras.Pedra> pedras,
			List<LeitorObj.ModeloObj> modelosPedras, byte[] detalhes, PredioUnivali predio) {}

	/** Quantas etapas rodam na thread separada (as primeiras da lista de progresso). */
	private static final int ETAPAS_NA_THREAD = 3;
	private static final float DURACAO_FADE = 0.6f;     // segundos
	private static final float TROCA_FRASE = 3.2f;      // segundos
	private static final String[] FRASES = {
			"Corrigindo provas...",
			"Escondendo a Univali...",
			"Acordando o Dragon Knight...",
			"Calculando sua média...",
			"Perdendo as provas de novo...",
			"Apagando as luzes do campus...",
			"Plantando árvores suspeitas...",
			"Afiando a escopeta...",
			"Procurando o professor...",
	};

	private static final Color COR_TITULO = new Color(214, 204, 188);
	private static final Color COR_TRILHO = new Color(70, 62, 62, 170);
	private static final Color COR_BARRA = new Color(150, 16, 16);
	private static final Color COR_PONTA = new Color(230, 60, 45);
	private static final Color COR_DETALHE = new Color(110, 104, 98);
	private static final Color COR_FRASE = new Color(170, 160, 150);
	private static final Color COR_ERRO = new Color(220, 45, 40);
	private static final Color COR_HISTORIA = new Color(212, 202, 186);

	/**
	 * A historinha (como o jogador foi parar ali) é contada no primeiro carregamento depois de abrir
	 * o jogo; nos seguintes (tentar de novo, novo mapa), voltam as frases de humor.
	 */
	private static boolean historiaContada = false;
	private Historia historia;
	/** Os sons da máquina de escrever (arquivos audio/maquina_*.ogg, ou sintetizados). */
	private audio.Efeito[] teclas;
	private audio.Efeito espaco, retorno, folha;
	private static final Color COR_TINTA = new Color(196, 188, 170), COR_TINTA_VERMELHA = new Color(178, 40, 32);

	/** O último mundo carregado (para não gerar de novo a mesma seed). */
	private static Mundo mundoCarregado;
	/** A densidade de árvores com que ele foi gerado (mudou nas opções = gera de novo). */
	private static float densidadeCarregada;
	/** A densidade desta geração (lida das opções ao entrar, para a thread não ler a opção mudando). */
	private float densidade;

	private final GerenciadorTelas gerenciador;
	private final long seed;
	private final AtmosferaMenu atmosfera = new AtmosferaMenu(); // granulação e lâmpada falhando

	private final ProgressoEtapas progresso = new ProgressoEtapas()
			.adicionar("gerando o relevo", 0.58f)        // thread separada (AG do terreno)
			.adicionar("planejando a fase", 0.10f)       // thread separada (AG da fase, com A*)
			.adicionar("espalhando as árvores", 0.04f)   // thread separada
			.adicionar("montando a malha", 0.10f)        // thread principal (OpenGL)
			.adicionar("plantando as árvores e o monstro", 0.06f) // thread principal (OpenGL)
			.adicionar("carregando texturas e sons", 0.12f); // thread principal (OpenGL)

	/** As etapas da thread principal, na ordem, uma por quadro. */
	private final List<Runnable> etapasPrincipais = List.of(
			this::montarMalha,
			this::montarArvores,
			this::carregarTexturas);

	private ExecutorService executor;
	private Future<DadosTerreno> tarefa;
	/** Fração do AG já feita, escrita pela thread do AG e lida pela principal. */
	private volatile float fracaoNaThread = 0;
	/** Qual etapa a thread está fazendo (0 relevo, 1 fase, 2 árvores). */
	private volatile int etapaNaThread = 0;
	private DadosTerreno dados;
	private Mapa3D terrenoNovo;
	private boolean reaproveitar;

	private Estado estado;
	private String mensagemErro = "";
	private float tempo = 0, fade = 0;

	// barra "desenhada à mão": cada segmento com altura e desvio próprios
	private static final int SEGMENTOS = 90;
	private final float[] desvio = new float[SEGMENTOS];
	private final float[] espessura = new float[SEGMENTOS];

	private final Random random = new Random();
	private int frase = 0;
	private float tempoFrase = 0;

	public TelaCarregando(GerenciadorTelas gerenciador, long seed) {
		this.gerenciador = gerenciador;
		this.seed = seed;
		Random r = new Random(seed);
		for (int i = 0; i < SEGMENTOS; i++) {
			desvio[i] = (r.nextFloat() - 0.5f) * 3.5f;
			espessura[i] = 2.5f + r.nextFloat() * 2.5f;
		}
		frase = random.nextInt(FRASES.length);
	}

	@Override
	public void entrar() {
		gerenciador.definirTitulo("Carregando mapa (seed " + seed + ")");
		gerenciador.entrada().prenderCursor(false);

		if (!historiaContada) {
			historia = new Historia();
			historiaContada = true;
			// as teclas gravadas que existirem (audio/maquina_tecla_1.ogg, _2...); sem nenhuma, quatro sintetizadas
			List<audio.Efeito> gravadas = new java.util.ArrayList<>();
			for (int n = 1; n <= 8 && util.Caminhos.existe("audio/maquina_tecla_" + n + ".ogg"); n++) {
				gravadas.add(gerenciador.audio().carregarEfeito("audio/maquina_tecla_" + n + ".ogg"));
			}
			if (gravadas.isEmpty()) {
				for (int n = 1; n <= 4; n++) {
					gravadas.add(gerenciador.audio().criarEfeito("tecla da máquina " + n + " (sintetizada)",
							audio.Sintetizador.teclaMaquina(44100, n), 44100));
				}
			}
			teclas = gravadas.toArray(new audio.Efeito[0]);
			espaco = somOuSintetizado("audio/maquina_espaco.ogg", () -> audio.Sintetizador.espacoMaquina(44100, 5));
			retorno = somOuSintetizado("audio/maquina_retorno.ogg", () -> audio.Sintetizador.retornoMaquina(44100, 6));
			folha = somOuSintetizado("audio/maquina_folha.ogg", () -> audio.Sintetizador.folhaPapel(44100, 7));
		}
		densidade = Opcoes.atual().densidadeArvores;
		reaproveitar = mundoCarregado != null && mundoCarregado.seed == seed && densidadeCarregada == densidade;
		if (reaproveitar) {
			// a mesma seed já está carregada: pula a geração
			for (int i = 0; i < ETAPAS_NA_THREAD; i++) {
				progresso.concluirEtapa();
			}
			estado = Estado.MONTANDO;
			return;
		}

		executor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "geracao-do-mapa");
			t.setDaemon(true); // não impede o jogo de fechar
			return t;
		});
		tarefa = executor.submit(() -> {
			// tudo aqui roda na thread separada: só dados, nada de OpenGL
			// 1. o relevo (o primeiro AG)
			int[][] alturas = GeradorTerreno.gerar(PERFIL, seed, (geracao, terreno, fitness) -> {
				verificarCancelamento();
				fracaoNaThread = (float) geracao / GeradorTerreno.GERACOES;
			});
			ConsultaAltura chao = new ConsultaAltura(alturas, METROS_ENTRE_PONTOS, METROS_POR_UNIDADE);

			// 2. a fase (o segundo AG, com o A*): início, provas, caixas, Univali, trilhas e placas
			etapaNaThread = 1;
			fracaoNaThread = 0;
			GeradorFase.Fase fase = GeradorFase.gerar(chao, seed, (geracao, layout, fitness) -> {
				verificarCancelamento();
				fracaoNaThread = (float) geracao / GeradorFase.GERACOES;
			});

			// 3. o prédio da Univali (onde a partida começa), com o chão aplainado embaixo dele
			etapaNaThread = 2;
			fracaoNaThread = 0;
			fase.Ponto univali = fase.layout().univali();
			PredioUnivali rascunho = new PredioUnivali(univali.x(), 0, univali.z(), fase.giroPredio(), seed);
			float piso = PredioUnivali.aplainar(alturas, METROS_ENTRE_PONTOS, rascunho.retangulo());
			PredioUnivali predio = new PredioUnivali(univali.x(), piso, univali.z(), fase.giroPredio(), seed);
			chao = new ConsultaAltura(alturas, METROS_ENTRE_PONTOS, METROS_POR_UNIDADE); // o chão já aplainado

			// 4. regiões, pedras e árvores, longe das trilhas, das clareiras da fase e do prédio
			Regioes regioes = new Regioes(seed);
			List<DistribuidorArvores.Clareira> clareiras = new java.util.ArrayList<>(fase.clareiras());
			clareiras.add(new DistribuidorArvores.Clareira(predio.x, predio.z, 31)); // meia diagonal do prédio + folga
			List<LeitorObj.ModeloObj> modelosPedras = Pedras.arquivosPresentes() ? Pedras.lerModelos() : null;
			float[] larguras = modelosPedras != null ? Pedras.larguras(modelosPedras) : null;
			List<DistribuidorPedras.Pedra> pedras = modelosPedras != null
					? DistribuidorPedras.distribuir(chao, larguras, clareiras, seed, regioes) : List.of();
			if (larguras != null) {
				clareiras.addAll(DistribuidorPedras.clareiras(pedras, larguras));
			}
			List<DistribuidorArvores.Arvore> arvores = DistribuidorArvores.distribuir(chao, seed, clareiras, regioes, densidade);
			GradeColisao colisao = new GradeColisao(chao.tamanho());
			DistribuidorArvores.registrarTroncos(arvores, colisao);
			if (larguras != null) {
				DistribuidorPedras.registrar(pedras, larguras, colisao);
			}
			registrarPlacas(fase.placas(), colisao);
			// as paredes do prédio (a casca, as salas e os móveis); as portas trancadas entram e saem durante o jogo
			for (GradeColisao.Retangulo parede : predio.paredes()) {
				colisao.adicionarRetangulo(parede.xMin(), parede.zMin(), parede.xMax(), parede.zMax());
			}
			for (float[] poste : predio.postes()) {
				colisao.adicionar(poste[0], poste[1], 0.15f);
			}
			byte[] detalhes = MapaDetalhes.gerar(chao.tamanho(), fase.trilhas(), regioes, seed);

			// os modelos 3D das árvores, se estiverem na pasta (senão, null: árvores planas)
			List<LeitorObj.ModeloObj> modelos = Arvores3D.arquivosPresentes() ? Arvores3D.lerModelos() : null;
			// o Dragon Knight (só leitura dos arquivos; null se o MD2 não estiver na pasta)
			PersonagemMd2.Dados monstro = PersonagemMd2.ler(TelaJogo.PASTA_MONSTRO, TelaJogo.ALTURA_MONSTRO);
			return new DadosTerreno(alturas, chao, colisao, arvores, modelos, monstro, fase, pedras, modelosPedras, detalhes,
					predio);
		});
		estado = Estado.GERANDO;
	}

	@Override
	public void sair() {
		cancelarGeracao(); // ao voltar ao menu ou fechar o jogo no meio do carregamento
	}

	private void cancelarGeracao() {
		if (executor != null) {
			executor.shutdownNow(); // interrompe a thread; o ouvinte do AG percebe e para
			executor = null;
		}
	}

	@Override
	public void atualizar(long diftime) {
		float dt = diftime / 1000f;
		tempo += dt;
		atmosfera.atualizar(diftime);
		tempoFrase += dt;
		if (tempoFrase >= TROCA_FRASE) {
			tempoFrase = 0;
			frase = (frase + 1 + random.nextInt(FRASES.length - 1)) % FRASES.length; // nunca repete a mesma
		}

		if (historia != null) {
			tocar(historia.atualizar(dt));
			Entrada e = gerenciador.entrada();
			boolean avancar = e.foiPressionada(GLFW_KEY_ENTER) || e.foiPressionada(GLFW_KEY_KP_ENTER)
					|| e.botaoFoiPressionado(GLFW_MOUSE_BUTTON_LEFT);
			if (estado == Estado.PRONTO && (e.foiPressionada(GLFW_KEY_SPACE) || (avancar && historia.terminou()))) {
				estado = Estado.SAINDO; // mapa pronto: Espaço começa a qualquer hora; Enter, depois da última página
			} else if (avancar) {
				int antes = historia.pagina();
				historia.pular(); // termina a página; com ela pronta, passa para a próxima
				if (historia.pagina() != antes && !historia.terminou()) gerenciador.audio().tocar(folha, 0.6f);
			}
		}

		if (estado != Estado.SAINDO && gerenciador.entrada().foiPressionada(GLFW_KEY_ESCAPE)) {
			cancelarGeracao();
			gerenciador.trocarPara(new TelaMenu(gerenciador));
			return;
		}

		switch (estado) {
			case GERANDO -> acompanharGeracao();
			case MONTANDO -> executarProximaEtapa();
			case PRONTO -> { }
			case SAINDO -> {
				fade += dt / DURACAO_FADE;
				if (fade >= 1) {
					gerenciador.trocarPara(new TelaJogo(gerenciador, mundoCarregado));
				}
			}
			case ERRO -> { }
		}
	}

	private audio.Efeito somOuSintetizado(String arquivo, java.util.function.Supplier<short[]> substituto) {
		audio.Audio a = gerenciador.audio();
		return util.Caminhos.existe(arquivo) ? a.carregarEfeito(arquivo) : a.criarEfeito(arquivo + " (sintetizado)", substituto.get(), 44100);
	}

	/** Toca os sons da datilografia que aconteceram neste quadro. */
	private void tocar(List<Historia.Som> sons) {
		audio.Audio a = gerenciador.audio();
		for (Historia.Som s : sons) {
			switch (s) {
			case TECLA -> a.tocar(teclas[random.nextInt(teclas.length)], 0.5f + random.nextFloat() * 0.15f);
			case ESPACO -> a.tocar(espaco, 0.45f);
			case RETORNO -> a.tocar(retorno, 0.5f);
			case PAGINA -> a.tocar(folha, 0.6f);
			}
		}
	}

	private void acompanharGeracao() {
		while (progresso.indiceAtual() < etapaNaThread) {
			progresso.concluirEtapa();
		}
		progresso.definirFracao(fracaoNaThread);
		if (!tarefa.isDone()) {
			return;
		}
		try {
			dados = tarefa.get();
			while (progresso.indiceAtual() < ETAPAS_NA_THREAD) {
				progresso.concluirEtapa(); // relevo, fase e árvores
			}
			estado = Estado.MONTANDO;
		} catch (ExecutionException e) {
			falhar(e.getCause());
		} catch (CancellationException | InterruptedException e) {
			falhar(e);
		} finally {
			cancelarGeracao();
		}
	}

	private void executarProximaEtapa() {
		int indice = progresso.indiceAtual() - ETAPAS_NA_THREAD; // as primeiras etapas são da outra thread
		try {
			etapasPrincipais.get(indice).run();
		} catch (RuntimeException e) {
			falhar(e);
			return;
		}
		progresso.concluirEtapa();
		if (progresso.terminou()) {
			estado = historia != null ? Estado.PRONTO : Estado.SAINDO; // com a história, espera o Enter
		}
	}

	private void falhar(Throwable causa) {
		estado = Estado.ERRO;
		mensagemErro = causa.getClass().getSimpleName()
				+ (causa.getMessage() != null ? ": " + causa.getMessage() : "");
		System.out.println("[TelaCarregando] erro ao gerar o mapa:");
		causa.printStackTrace();
	}

	// ---------- etapas da thread principal (usam OpenGL) ----------

	private void montarMalha() {
		if (reaproveitar) {
			return;
		}
		if (mundoCarregado != null) {
			mundoCarregado.liberar(); // apaga da placa de vídeo a malha do mapa anterior
			mundoCarregado = null;
		}
		ObjHTGsrtm malha = new ObjHTGsrtm(dados.alturas(), METROS_ENTRE_PONTOS, METROS_POR_UNIDADE);
		malha.load();
		terrenoNovo = new Mapa3D(0, 0, 0, 1); // na origem, escala 1: o mapa vai de 0 a 300 m em x e z
		terrenoNovo.model = malha;
		Constantes.mapa = terrenoNovo; // o campo global do projeto original continua apontando para o terreno
	}

	/** A thread de geração para quando a tela pede (ESC ou fechar o jogo). */
	private static void verificarCancelamento() {
		if (Thread.currentThread().isInterrupted()) {
			throw new CancellationException("geração cancelada");
		}
	}

	/** Os postes das placas na grade de colisão: dois na do evento, um na de trilha. */
	private static void registrarPlacas(List<Placa> placas, GradeColisao colisao) {
		for (Placa p : placas) {
			if (p.evento()) {
				// os postes ficam em x = ±1,1 m no modelo; girados: (cos g, -sen g) por metro de x
				float c = (float) Math.cos(p.giro()), s = (float) Math.sin(p.giro());
				colisao.adicionar(p.x() + c * 1.1f, p.z() - s * 1.1f, 0.15f);
				colisao.adicionar(p.x() - c * 1.1f, p.z() + s * 1.1f, 0.15f);
			} else {
				colisao.adicionar(p.x(), p.z(), 0.12f);
			}
		}
	}

	/** O mapa de detalhes (trilhas e regiões) vira uma textura RGB com filtro linear. */
	private static int enviarDetalhes(byte[] rgb) {
		java.nio.ByteBuffer b = org.lwjgl.BufferUtils.createByteBuffer(rgb.length);
		b.put(rgb).flip();
		int id = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, id);
		glPixelStorei(GL_UNPACK_ALIGNMENT, 1); // linhas de 512 x 3 bytes: sem preenchimento
		glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB8, MapaDetalhes.LADO, MapaDetalhes.LADO, 0, GL_RGB, GL_UNSIGNED_BYTE, b);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
		glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
		glBindTexture(GL_TEXTURE_2D, 0);
		return id;
	}

	private void montarArvores() {
		if (reaproveitar) {
			return;
		}
		Arvores arvores = null;
		Arvores3D arvores3d = null;
		if (dados.modelosArvores() != null) {
			arvores3d = new Arvores3D(dados.arvores(), dados.modelosArvores());
			arvores3d.load();
		} else {
			arvores = new Arvores(dados.arvores());
			arvores.load();
		}
		// o monstro vai para a placa de vídeo aqui também (sem o MD2, o jogo usa o substituto)
		PersonagemMd2 monstro = dados.monstro() != null ? new PersonagemMd2(dados.monstro()) : null;
		Pedras pedras = null;
		if (dados.modelosPedras() != null) {
			pedras = new Pedras(dados.pedras(), dados.modelosPedras());
			pedras.load();
		}
		int texturaDetalhes = enviarDetalhes(dados.detalhes());
		densidadeCarregada = densidade;
		mundoCarregado = new Mundo(seed, PERFIL, terrenoNovo, dados.chao(), dados.colisao(), arvores, arvores3d,
				monstro, dados.fase(), pedras, texturaDetalhes, dados.predio());
		dados = null; // tudo já foi para a placa de vídeo ou para o Mundo
		terrenoNovo = null;
	}

	private void carregarTexturas() {
		Recursos.shaderFloresta();
		Recursos.shaderObjeto();
		Recursos.shaderArvore();
		Recursos.shaderObjeto();
		if (Pedras.arquivosPresentes()) {
			Recursos.texturaModelo(Pedras.PASTA + "pedra.png");
			Recursos.shaderArvore3D();
		}
		Recursos.shaderMd2();
		Recursos.shaderBrilho();
		TexturasFloresta.carregar();
		if (Arvores3D.arquivosPresentes()) {
			Recursos.shaderArvore3D();
			for (String material : Arvores3D.MATERIAIS) {
				Recursos.texturaArvore3D(material);
			}
		} else {
			Recursos.texturaArvores();
		}
	}

	// ---------- desenho ----------

	@Override
	public void desenhar() {
		glClearColor(0, 0, 0, 1);
		glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

		int w = gerenciador.larguraJanela(), h = gerenciador.alturaJanela();
		Overlay2D overlay = Recursos.overlay();
		overlay.iniciar(w, h);
		int tamanhoTitulo = Math.max(28, Math.min(72, Math.round(h * 0.06f)));
		int tamanhoTexto = Math.max(14, Math.min(24, Math.round(h * 0.022f)));

		if (estado == Estado.ERRO) {
			desenharErro(overlay, w, h, tamanhoTitulo, tamanhoTexto);
		} else if (historia != null) {
			desenharHistoria(overlay, w, h);
			desenharRodapeHistoria(overlay, w, h, tamanhoTexto);
		} else {
			desenharTitulo(overlay, w, h, tamanhoTitulo);
			desenharBarra(overlay, w, h * 0.53f, tamanhoTexto);
			desenharFrase(overlay, w, h, tamanhoTexto);
		}
		Texto.desenhar(overlay, "seed " + seed, w - 24, h - 36, 16, COR_DETALHE, Texto.Alinhamento.DIREITA);

		atmosfera.desenharEfeitos(overlay, w, h);
		if (fade > 0) {
			overlay.retangulo(0, 0, w, h, new Color(0, 0, 0, Math.round(Math.min(1, fade) * 255)));
		}
		overlay.finalizar();
	}

	/** "Carregando mapa" fixo no centro e as reticências crescendo depois dele (o texto não pula). */
	private void desenharTitulo(Overlay2D overlay, int w, int h, int tamanho) {
		Fonte fonte = Recursos.fonteTitulo();
		String base = "Carregando mapa";
		float largura = Texto.largura(fonte, base, tamanho);
		float x = w / 2f - largura / 2;
		float y = h * 0.42f;
		Texto.desenharAlinhado(overlay, fonte, base, x, y, tamanho, COR_TITULO, Texto.Alinhamento.ESQUERDA, true, true);
		int pontos = (int) (tempo / 0.4f) % 4;
		if (pontos > 0) {
			Texto.desenharAlinhado(overlay, fonte, ".".repeat(pontos), x + largura - 6, y, tamanho, COR_TITULO,
					Texto.Alinhamento.ESQUERDA, true, true);
		}
	}

	/** Barra fina e irregular: segmentos com espessura e altura diferentes, como um traço à mão. */
	private void desenharBarra(Overlay2D overlay, int w, float y0, int tamanhoTexto) {
		float largura = Math.min(w * 0.5f, 640);
		float x0 = (w - largura) / 2;
		float passo = largura / SEGMENTOS;
		float feito = progresso.total() * SEGMENTOS;

		for (int i = 0; i < SEGMENTOS; i++) {
			float x = x0 + i * passo;
			float y = y0 + desvio[i] - espessura[i] / 2;
			overlay.retangulo(x, y, passo + 0.6f, espessura[i], COR_TRILHO);
			if (i < (int) feito) {
				overlay.retangulo(x, y, passo + 0.6f, espessura[i], COR_BARRA);
			} else if (i == (int) feito) {
				// ponta: o segmento parcial, mais claro e tremendo
				float parte = feito - i;
				float tremor = (random.nextFloat() - 0.5f) * 1.5f;
				overlay.retangulo(x, y + tremor, passo * parte + 0.6f, espessura[i] + 1, COR_PONTA);
			}
		}

		String detalhe = progresso.nomeAtual();
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), detalhe, x0, y0 + 26, tamanhoTexto - 4, COR_DETALHE,
				Texto.Alinhamento.ESQUERDA, false, false);
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), Math.round(progresso.total() * 100) + "%",
				x0 + largura, y0 + 26, tamanhoTexto - 4, COR_DETALHE, Texto.Alinhamento.DIREITA, false, false);
	}

	/**
	 * A página datilografada: uma folha escura sobre o fundo preto e as letras batidas uma a uma,
	 * cada uma da sua textura (a fonte é monoespaçada: todas ocupam a mesma largura). Como numa
	 * máquina de verdade, cada letra sai um pouco fora da linha e com a tinta mais forte ou mais
	 * fraca, e de vez em quando o tipo bate duas vezes. Linhas em maiúsculas saem na parte
	 * vermelha da fita. Um cursor pisca onde vai a próxima letra.
	 */
	private void desenharHistoria(Overlay2D overlay, int w, int h) {
		String[] linhas = historia.linhas();
		if (linhas == null) return;
		Fonte fonte = Recursos.fonteMaquina();
		int tamanho = Math.max(16, Math.min(30, Math.round(h * 0.031f)));
		TextoTextura.Imagem m = Recursos.textos().obter("M", fonte, tamanho, COR_TINTA, false, false);
		if (m == null) return;
		float avanco = m.largura() - 4, linha = tamanho * 1.55f; // a textura tem 2 px de margem de cada lado
		// a folha: 46 colunas e 9 linhas, com a textura de papel velho e escuro
		float fl = avanco * 46, fa = linha * 9.5f, fx = (w - fl) / 2, fy = h * 0.45f - fa / 2;
		overlay.imagem(Recursos.texturaGerada("folha da maquina", TelaCarregando::papel, false, false).id(), fx, fy, fl, fa,
				Color.WHITE);
		float alfa = historia.alfa();
		float x0 = fx + avanco * 2, y0 = fy + linha * 1.1f;
		int linhaCursor = -1, colunaCursor = 0;
		for (int l = 0; l < linhas.length; l++) {
			int letras = historia.letrasVisiveis(l);
			if (linhaCursor < 0 && letras < linhas[l].length()) {
				linhaCursor = l;
				colunaCursor = letras;
			}
			boolean vermelha = !linhas[l].isEmpty() && linhas[l].equals(linhas[l].toUpperCase())
					&& linhas[l].chars().anyMatch(Character::isLetter);
			Color tinta = vermelha ? COR_TINTA_VERMELHA : COR_TINTA;
			for (int c = 0; c < letras; c++) {
				char ch = linhas[l].charAt(c);
				if (ch == ' ') continue;
				TextoTextura.Imagem g = Recursos.textos().obter(String.valueOf(ch), fonte, tamanho, tinta, false, false);
				if (g == null) continue;
				// a irregularidade de cada letra é sempre a mesma (sorteada pela posição): não treme
				long semente = (historia.pagina() * 131L + l) * 977L + c;
				java.util.Random r = new java.util.Random(semente);
				float dy = (r.nextFloat() - 0.5f) * tamanho * 0.08f, dx = (r.nextFloat() - 0.5f) * tamanho * 0.04f;
				float tinta01 = 0.68f + r.nextFloat() * 0.32f;
				float x = x0 + c * avanco - 2 + dx, y = y0 + l * linha - g.altura() / 2f + dy;
				overlay.imagem(g.textura(), Math.round(x), Math.round(y), g.largura(), g.altura(),
						new Color(1f, 1f, 1f, alfa * tinta01));
				if (r.nextFloat() < 0.08f) { // batida dupla: um fantasma da letra meio pixel ao lado
					overlay.imagem(g.textura(), Math.round(x) + 1, Math.round(y), g.largura(), g.altura(),
							new Color(1f, 1f, 1f, alfa * 0.3f));
				}
			}
		}
		// o cursor: um traço piscando embaixo da próxima letra
		if (linhaCursor >= 0 && (int) (tempo * 2.5f) % 2 == 0) {
			float x = x0 + colunaCursor * avanco, y = y0 + linhaCursor * linha + tamanho * 0.45f;
			overlay.retangulo(x, y, avanco * 0.9f, Math.max(2, tamanho / 12f), new Color(196, 188, 170, Math.round(200 * alfa)));
		}
	}

	/** Embaixo da folha: o carregamento (barra fina) e as teclas, na letra da máquina. */
	private void desenharRodapeHistoria(Overlay2D overlay, int w, int h, int tamanhoTexto) {
		Fonte fonte = Recursos.fonteMaquina();
		int pequeno = Math.max(13, tamanhoTexto - 4);
		float y = h * 0.86f;
		boolean pronto = estado == Estado.PRONTO || estado == Estado.SAINDO;
		if (!pronto) {
			float largura = Math.min(w * 0.3f, 360), x0 = (w - largura) / 2;
			overlay.retangulo(x0, y - 18, largura, 2, COR_TRILHO);
			overlay.retangulo(x0, y - 18, largura * progresso.total(), 2, COR_BARRA);
			Texto.desenharAlinhado(overlay, fonte, "carregando o mapa... " + Math.round(progresso.total() * 100) + "%", w / 2f,
					y + 6, pequeno, COR_DETALHE, Texto.Alinhamento.CENTRO, false, false);
		}
		String teclas = !pronto ? "[ENTER] próxima página"
				: historia.terminou() ? "[ENTER] começar" : "[ESPAÇO] começar     [ENTER] próxima página";
		float pulso = pronto ? 0.6f + 0.4f * (float) Math.sin(tempo * 3) : 0.8f;
		Texto.desenharAlinhado(overlay, fonte, teclas, w / 2f, h - 44, pequeno,
				new Color(170, 160, 148, Math.round(255 * pulso)), Texto.Alinhamento.CENTRO, false, false);
	}

	/**
	 * Uma folha de papel velho, escura (a cena é de noite): fibras, manchas de umidade, a borda
	 * mais escura e uns vincos. Gerada uma vez, por código.
	 */
	private static java.awt.image.BufferedImage papel() {
		int lw = 512, la = 300;
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(lw, la, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		Random r = new Random(23);
		float[][] manchas = new float[7][];
		for (int i = 0; i < manchas.length; i++) {
			manchas[i] = new float[] { r.nextFloat() * lw, r.nextFloat() * la, 30 + r.nextFloat() * 90, r.nextFloat() * 0.35f };
		}
		for (int y = 0; y < la; y++) {
			for (int x = 0; x < lw; x++) {
				double v = 30 + r.nextGaussian() * 2.2 + Math.sin(x * 0.9 + y * 0.07) * 0.6; // fibras
				for (float[] mc : manchas) {
					double d = Math.hypot(x - mc[0], y - mc[1]) / mc[2];
					if (d < 1) v -= (1 - d) * (1 - d) * 18 * mc[3];
				}
				double borda = Math.min(Math.min(x, lw - 1 - x) / (double) lw, Math.min(y, la - 1 - y) / (double) la);
				v *= 0.55 + 0.45 * Math.min(1, borda * 9);
				if (Math.abs(y - la * 0.34) < 1.2 || Math.abs(x - lw * 0.62) < 1.0) v *= 0.85; // vincos de dobra
				int c = (int) Math.max(0, Math.min(255, v));
				img.setRGB(x, y, new Color(Math.min(255, c + 4), c + 1, Math.max(0, c - 3)).getRGB());
			}
		}
		return img;
	}

	/** Frase de humor trocando a cada poucos segundos, aparecendo e sumindo aos poucos. */
	private void desenharFrase(Overlay2D overlay, int w, int h, int tamanho) {
		float entrada = Math.min(1, tempoFrase / 0.4f);
		float saida = Math.min(1, (TROCA_FRASE - tempoFrase) / 0.4f);
		int alfa = Math.round(Math.max(0, Math.min(entrada, saida)) * 255);
		Color cor = new Color(COR_FRASE.getRed(), COR_FRASE.getGreen(), COR_FRASE.getBlue(), alfa);
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), FRASES[frase], w / 2f, h * 0.66f, tamanho, cor,
				Texto.Alinhamento.CENTRO, false, true);
	}

	private void desenharErro(Overlay2D overlay, int w, int h, int tamanhoTitulo, int tamanhoTexto) {
		Texto.desenharCentralizado(overlay, Recursos.fonteTitulo(), "O mapa não carregou", w / 2f, h * 0.40f,
				tamanhoTitulo, COR_ERRO, true, true);
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), mensagemErro, w / 2f, h * 0.52f, tamanhoTexto,
				COR_FRASE, Texto.Alinhamento.CENTRO, false, true);
		Texto.desenharAlinhado(overlay, Recursos.fonteTexto(), "ESC: voltar ao menu", w / 2f, h * 0.60f, tamanhoTexto,
				COR_DETALHE, Texto.Alinhamento.CENTRO, false, true);
	}
}
