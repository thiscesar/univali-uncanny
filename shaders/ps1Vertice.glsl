// Efeitos de vértice do modo PS1, para os vertex shaders da cena 3D.
// Incluído com #include "ps1Vertice.glsl".

uniform float modoPs1;       // 1 = modo PS1 ligado
uniform vec2  resolucaoPs1;  // resolução baixa em que a cena é desenhada (ex.: 426 x 240)

// O "tremido" do PlayStation: o console não tinha precisão abaixo do pixel, então cada
// vértice caía no pixel inteiro mais próximo. Ao andar, os vértices pulam de pixel em pixel
// e as bordas tremem. Aqui: posição na tela (de -1 a 1) arredondada para a grade da
// resolução baixa, e de volta ao espaço de recorte.
vec4 tremerPs1(vec4 p){
	if(modoPs1 < 0.5 || p.w <= 0.0){
		return p;
	}
	vec2 meia = resolucaoPs1 * 0.5;
	vec2 ndc = floor(p.xy / p.w * meia + 0.5) / meia;
	return vec4(ndc * p.w, p.z, p.w);
}
