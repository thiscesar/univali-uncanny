// Iluminação e neblina compartilhadas por todos os shaders 3D do jogo.
// Incluído com #include "comum.glsl" (o Java troca a linha por este arquivo antes de compilar).
//
// Todos os cálculos de luz são feitos em ESPAÇO DA CÂMERA: a câmera fica na origem,
// olhando para -Z. Assim a lanterna, presa ao jogador, tem posição e direção quase
// constantes, e normais, posições e luzes precisam estar todas nesse mesmo espaço.

// ---------- modo PS1 ----------
uniform float modoPs1;           // 1 = ligado: as texturas usam a coordenada "afim" (entorta como no console)

// ---------- ambiente ----------
uniform vec3  corNeblina;        // igual à cor de fundo (glClearColor)
uniform float densidadeNeblina;  // neblina exp²: com 0,05, quase nada aparece depois de 40 m
uniform vec3  luzAmbiente;       // luz fraca da lua, já multiplicada pela intensidade

// ---------- lanterna (espaço da câmera) ----------
uniform float intensidadeLanterna;  // 0 = desligada
uniform vec3  corLanterna;
uniform vec3  posLanterna;
uniform vec3  dirLanterna;          // unitária
uniform float cosInternoLanterna;   // dentro deste ângulo: luz cheia
uniform float cosExternoLanterna;   // fora deste: nada; entre os dois: borda suave
uniform float alcanceLanterna;      // metros

// ---------- clarão do tiro (luz pontual breve, espaço da câmera) ----------
uniform float intensidadeClarao;
uniform vec3  posClarao;
uniform float alcanceClarao;

// ---------- luz de preenchimento (só para a arma) ----------
uniform float intensidadePreenchimento;

// ---------- luzes pontuais fixas (os postes da Univali), espaço da câmera ----------
const int MAX_LUZES = 4;
uniform int  numLuzes;               // quantas das 4 estão em uso
uniform vec3 posLuz[MAX_LUZES];
uniform vec3 corLuz[MAX_LUZES];      // já multiplicada pela intensidade
uniform float alcanceLuz[MAX_LUZES];

// ---------- sombra da lanterna (shadow mapping) ----------
// Antes da cena, o Java desenha a profundidade vista da própria lanterna num mapa. Aqui, cada ponto
// é levado para o espaço da lanterna e comparado com esse mapa: se algo estava mais perto da luz,
// o ponto está na sombra. A sombra só apaga a luz da lanterna (lua, postes e o clarão do tiro não).
uniform float usaSombra;             // 1 = há mapa de sombra neste quadro
uniform float passadaSombra;         // 1 = desenhando o próprio mapa (os fragment shaders saem logo)
uniform sampler2DShadow mapaSombra;  // profundidade vista da lanterna; a placa compara e filtra
uniform mat4  vistaParaLuz;          // espaço da câmera -> recorte da lanterna (vetor-linha, como as outras)
uniform float texelSombra;           // 1 / lado do mapa, em coordenada de textura

// De 0 (sombra) a 1 (luz). n: normal unitária, em espaço da câmera.
float sombraLanterna(vec3 posVista, vec3 n){
	if(usaSombra < 0.5){
		return 1.0;
	}
	// um pouco para fora da superfície (mais longe, mais): evita a "acne", a superfície fazendo
	// sombra nela mesma por falta de precisão do mapa
	vec4 c = vec4(posVista + n * (0.03 + 0.004 * length(posVista)), 1.0) * vistaParaLuz;
	if(c.w <= 0.0){
		return 1.0;
	}
	vec3 p = c.xyz / c.w * 0.5 + 0.5; // de -1..1 para 0..1 (coordenada de textura e profundidade)
	if(p.x <= 0.0 || p.x >= 1.0 || p.y <= 0.0 || p.y >= 1.0 || p.z >= 1.0){
		return 1.0; // fora do que a lanterna enxerga
	}
	// PCF: 9 comparações em volta (cada uma já mistura 4 texels): bordas suaves
	float soma = 0.0;
	for(int y = -1; y <= 1; y++){
		for(int x = -1; x <= 1; x++){
			soma += textureLod(mapaSombra, vec3(p.xy + vec2(x, y) * texelSombra, p.z - 0.0002), 0.0);
		}
	}
	return soma / 9.0;
}

// Cai com o quadrado da distância relativa e chega a zero exatamente no alcance.
float atenuacao(float distancia, float alcance){
	float t = clamp(1.0 - distancia / alcance, 0.0, 1.0);
	return t * t;
}

// Quanto da lanterna chega a este ponto (cone e distância), e a direção até ela (L).
float fatorLanterna(vec3 posVista, out vec3 L){
	vec3 paraLuz = posLanterna - posVista;
	float d = length(paraLuz);
	L = paraLuz / max(d, 0.0001);
	float cone = smoothstep(cosExternoLanterna, cosInternoLanterna, dot(-L, dirLanterna));
	return intensidadeLanterna * cone * atenuacao(d, alcanceLanterna);
}

// Cor final de uma superfície: luz ambiente + lanterna + clarão + luzes pontuais + preenchimento.
vec3 iluminar(vec3 corBase, vec3 normalVista, vec3 posVista){
	vec3 n = normalize(normalVista);
	vec3 luz = luzAmbiente;

	vec3 L;
	float lanterna = fatorLanterna(posVista, L);
	if(lanterna > 0.0){
		lanterna *= sombraLanterna(posVista, n);
	}
	luz += corLanterna * lanterna * max(dot(n, L), 0.0);

	if(intensidadeClarao > 0.0){
		vec3 paraClarao = posClarao - posVista;
		float d = length(paraClarao);
		luz += vec3(1.0, 0.75, 0.45) * intensidadeClarao * atenuacao(d, alcanceClarao)
		     * max(dot(n, paraClarao / max(d, 0.0001)), 0.0);
	}
	for(int i = 0; i < MAX_LUZES; i++){
		if(i >= numLuzes) break;
		vec3 paraLuz = posLuz[i] - posVista;
		float d = length(paraLuz);
		luz += corLuz[i] * atenuacao(d, alcanceLuz[i]) * max(dot(n, paraLuz / max(d, 0.0001)), 0.0);
	}
	if(intensidadePreenchimento > 0.0){
		luz += vec3(1.0, 0.85, 0.7) * intensidadePreenchimento * max(dot(n, normalize(vec3(-0.4, 0.6, 0.7))), 0.0);
	}
	return corBase * luz;
}

// Reflexo brilhante da lanterna (especular de Blinn-Phong), para água e metal.
vec3 brilhoLanterna(vec3 normalVista, vec3 posVista, float dureza){
	vec3 L;
	float lanterna = fatorLanterna(posVista, L);
	if(lanterna > 0.0){
		lanterna *= sombraLanterna(posVista, normalize(normalVista));
	}
	vec3 V = normalize(-posVista);
	vec3 H = normalize(L + V);
	return corLanterna * lanterna * pow(max(dot(normalize(normalVista), H), 0.0), dureza);
}

// Neblina exp². Superfícies emissivas (janelas acesas) sofrem menos neblina.
vec3 aplicarNeblina(vec3 cor, float distancia, float emissividade){
	float densidade = densidadeNeblina * (1.0 - 0.5 * clamp(emissividade, 0.0, 1.0));
	float neblina = 1.0 - exp(-pow(densidade * distancia, 2.0));
	return mix(cor, corNeblina, neblina);
}

// Normal mapping sem tangentes pré-calculadas: a base tangente sai das derivadas de tela
// da posição e da coordenada de textura (dFdx, dFdy). Funciona igual para OBJ, MD2 e terreno.
// (Aqui as matrizes são do próprio GLSL, coluna a coluna: mat3 * vetor é o uso normal.)
vec3 perturbarNormal(vec3 normalVista, vec3 posVista, vec2 uv, vec3 amostraNormalMap){
	vec3 n = normalize(normalVista);
	vec3 dp1 = dFdx(posVista), dp2 = dFdy(posVista);
	vec2 duv1 = dFdx(uv), duv2 = dFdy(uv);
	vec3 dp2perp = cross(dp2, n);
	vec3 dp1perp = cross(n, dp1);
	vec3 T = dp2perp * duv1.x + dp1perp * duv2.x;
	vec3 B = dp2perp * duv1.y + dp1perp * duv2.y;
	float escala = inversesqrt(max(dot(T, T), dot(B, B)));
	mat3 TBN = mat3(T * escala, B * escala, n);
	return normalize(TBN * (amostraNormalMap * 2.0 - 1.0));
}
