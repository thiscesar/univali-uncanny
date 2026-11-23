package terreno;

/**
 * Como gerar um tipo de terreno: tamanho, alvos do algoritmo genético, escala e detalhes.
 *
 * ILHA é o terreno feito para o avião (mar, ilhas, montanhas nevadas), mantido para a
 * prévia 2D e para a apresentação. FLORESTA é o do jogo: colinas suaves para andar a pé,
 * poucos lagos pequenos, em metros, com uma faixa plana na borda.
 *
 * @param nome              para mensagens
 * @param tamanhoFinal      pontos por lado da grade entregue ao OpenGL
 * @param tamanhoEvolucao   pontos por lado do mapa pequeno que o AG evolui (depois é ampliado)
 * @param alvoAgua          fração do mapa abaixo do nível da água desejada no fitness
 * @param alvoMontanha      fração do mapa acima do nível de montanha desejada
 * @param alvoRugosidade    diferença média de altura entre vizinhos desejada
 * @param maiorLago         maior fração do mapa que um lago pode ocupar sem penalidade (1 = sem limite)
 * @param escalaVertical    metros por unidade de altura do AG (as alturas do AG vão de 0 a 1)
 * @param forcaColinas      quanto de ruído fractal suave somar na terra firme
 * @param forcaCristas      quanto de cristas afiadas somar nas regiões altas (0 = nenhuma)
 * @param larguraBorda      pontos planos em cada lado do mapa (0 = sem borda); para dentro,
 *                          uma rampa com o dobro dessa largura liga a borda ao relevo
 * @param alturaBorda       metros acima da água em que a borda fica
 * @param unidadesPorMetro  unidade do int[][] de saída: 1 = metros, 100 = centímetros
 */
public record PerfilTerreno(
		String nome,
		int tamanhoFinal,
		int tamanhoEvolucao,
		double alvoAgua,
		double alvoMontanha,
		double alvoRugosidade,
		double maiorLago,
		float escalaVertical,
		double forcaColinas,
		double forcaCristas,
		int larguraBorda,
		float alturaBorda,
		int unidadesPorMetro) {

	/** O comportamento original do gerador, sem nenhuma mudança. */
	public static final PerfilTerreno ILHA = new PerfilTerreno(
			"ilha", 512, 128, 0.30, 0.10, 0.012, 1.0, 750f, 0.12, 0.6, 0, 0f, 1);

	/**
	 * O terreno do jogo: 301 x 301 pontos, 1 m entre eles (mapa de 300 m), ~6% de água em
	 * lagos pequenos, sem montanhas, relevo de poucos metros, borda plana de 15 m.
	 * Alturas em centímetros: em metros inteiros, colinas baixas virariam degraus de 1 m.
	 */
	public static final PerfilTerreno FLORESTA = new PerfilTerreno(
			"floresta", 301, 128, 0.11, 0.0, 0.008, 0.02, 32f, 0.05, 0.0, 15, 0.5f, 100);

	/** O mesmo perfil com outro tamanho final de grade. */
	public PerfilTerreno comTamanho(int tamanho) {
		return new PerfilTerreno(nome, tamanho, tamanhoEvolucao, alvoAgua, alvoMontanha, alvoRugosidade, maiorLago,
				escalaVertical, forcaColinas, forcaCristas, larguraBorda, alturaBorda, unidadesPorMetro);
	}
}
