package telas;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
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
import dados.Recursos;
import entidades.Jogador;
import mundo.Arvores;
import mundo.Arvores3D;
import mundo.ConsultaAltura;
import mundo.DistribuidorArvores;
import mundo.GradeColisao;
import mundo.LeitorObj;
import mundo.SorteioLugares;
import mundo.Mundo;
import mundo.TexturasFloresta;
import md2.PersonagemMd2;
import obj.Mapa3D;
import obj.ObjHTGsrtm;
import render.AtmosferaMenu;
import render.Fonte;
import render.Overlay2D;
import render.Texto;
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

	private enum Estado { GERANDO, MONTANDO, SAINDO, ERRO }

	/** O terreno do jogo: 300 x 300 m, 1 m entre pontos, alturas em centímetros. */
	private static final PerfilTerreno PERFIL = PerfilTerreno.FLORESTA;
	private static final float METROS_ENTRE_PONTOS = 1f;
	private static final float METROS_POR_UNIDADE = 1f / PERFIL.unidadesPorMetro(); // centímetros -> metros

	/** O que a thread de geração entrega: só dados, nada de OpenGL. */
	private record DadosTerreno(int[][] alturas, ConsultaAltura chao, GradeColisao colisao,
			List<DistribuidorArvores.Arvore> arvores, List<LeitorObj.ModeloObj> modelosArvores,
			PersonagemMd2.Dados monstro, float[] inicio, List<float[]> provas, float[] univali) {}

	/** Raio, em metros, da clareira sem árvores em volta de cada prova. */
	private static final float CLAREIRA_PROVA = 4f;
	/** Raio, em metros, da clareira sem árvores em volta do início do jogador. */
	private static final float CLAREIRA_INICIO = 6f;
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

	/** O último mundo carregado (para não gerar de novo a mesma seed). */
	private static Mundo mundoCarregado;

	private final GerenciadorTelas gerenciador;
	private final long seed;
	private final AtmosferaMenu atmosfera = new AtmosferaMenu(); // granulação e lâmpada falhando

	private final ProgressoEtapas progresso = new ProgressoEtapas()
			.adicionar("gerando o relevo", 0.66f)        // thread separada
			.adicionar("planejando a fase", 0.02f)       // thread separada (ainda não existe)
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
	private volatile float fracaoRelevo = 0;
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

		reaproveitar = mundoCarregado != null && mundoCarregado.seed == seed;
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
			int[][] alturas = GeradorTerreno.gerar(PERFIL, seed, (geracao, terreno, fitness) -> {
				if (Thread.currentThread().isInterrupted()) {
					throw new CancellationException("geração cancelada");
				}
				fracaoRelevo = (float) geracao / GeradorTerreno.GERACOES;
			});
			ConsultaAltura chao = new ConsultaAltura(alturas, METROS_ENTRE_PONTOS, METROS_POR_UNIDADE);

			// início do jogador: o centro, ou o ponto bom mais próximo (até o AG da fase decidir)
			GradeColisao colisao = new GradeColisao(chao.tamanho());
			float centro = chao.tamanho() / 2;
			float[] inicio = Jogador.pontoDeInicio(chao, colisao, centro, centro);

			// a Univali e as provas (antes das árvores: cada prova pede uma clareira)
			Random sorteio = new Random(seed * 17 + 3);
			float[] univali = SorteioLugares.univali(chao, inicio, sorteio);
			List<float[]> provas = SorteioLugares.provas(chao, inicio, univali, 3, sorteio);
			List<DistribuidorArvores.Clareira> clareiras = new java.util.ArrayList<>();
			clareiras.add(new DistribuidorArvores.Clareira(inicio[0], inicio[1], CLAREIRA_INICIO));
			for (float[] p : provas) {
				clareiras.add(new DistribuidorArvores.Clareira(p[0], p[1], CLAREIRA_PROVA));
			}

			// árvores, com as clareiras, e os troncos na grade de colisão
			List<DistribuidorArvores.Arvore> arvores = DistribuidorArvores.distribuir(chao, seed, clareiras);
			DistribuidorArvores.registrarTroncos(arvores, colisao);

			// os modelos 3D das árvores, se estiverem na pasta (senão, null: árvores planas)
			List<LeitorObj.ModeloObj> modelos = Arvores3D.arquivosPresentes() ? Arvores3D.lerModelos() : null;
			// o Dragon Knight (só leitura dos arquivos; null se o MD2 não estiver na pasta)
			PersonagemMd2.Dados monstro = PersonagemMd2.ler(TelaJogo.PASTA_MONSTRO, TelaJogo.ALTURA_MONSTRO);
			return new DadosTerreno(alturas, chao, colisao, arvores, modelos, monstro, inicio, provas, univali);
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

		if (estado != Estado.SAINDO && gerenciador.entrada().foiPressionada(GLFW_KEY_ESCAPE)) {
			cancelarGeracao();
			gerenciador.trocarPara(new TelaMenu(gerenciador));
			return;
		}

		switch (estado) {
			case GERANDO -> acompanharGeracao();
			case MONTANDO -> executarProximaEtapa();
			case SAINDO -> {
				fade += dt / DURACAO_FADE;
				if (fade >= 1) {
					gerenciador.trocarPara(new TelaJogo(gerenciador, mundoCarregado));
				}
			}
			case ERRO -> { }
		}
	}

	private void acompanharGeracao() {
		progresso.definirFracao(fracaoRelevo);
		if (!tarefa.isDone()) {
			return;
		}
		try {
			dados = tarefa.get();
			for (int i = 0; i < ETAPAS_NA_THREAD; i++) {
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
			estado = Estado.SAINDO;
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
		mundoCarregado = new Mundo(seed, PERFIL, terrenoNovo, dados.chao(), dados.colisao(), arvores, arvores3d,
				monstro, dados.inicio(), dados.provas(), dados.univali());
		dados = null; // tudo já foi para a placa de vídeo ou para o Mundo
		terrenoNovo = null;
	}

	private void carregarTexturas() {
		Recursos.shaderFloresta();
		Recursos.shaderObjeto();
		Recursos.shaderArvore();
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
		} else {
			desenharTitulo(overlay, w, h, tamanhoTitulo);
			desenharBarra(overlay, w, h, tamanhoTexto);
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
	private void desenharBarra(Overlay2D overlay, int w, int h, int tamanhoTexto) {
		float largura = Math.min(w * 0.5f, 640);
		float x0 = (w - largura) / 2;
		float y0 = h * 0.53f;
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
