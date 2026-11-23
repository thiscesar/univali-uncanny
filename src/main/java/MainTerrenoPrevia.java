import ag.AlgoritmoGenetico;
import ag.Configuracao;
import terreno.DesenhoTerreno;
import terreno.ImagemTerreno;
import terreno.ProblemaTerreno;
import terreno.Terreno;
import visual.Visualizador;

import java.util.Random;

public class MainTerrenoPrevia {
    public static void main(String[] args) {
        long seed = 42;
        Random random = new Random(seed);

        ProblemaTerreno problema = new ProblemaTerreno(
                128,   // tamanho do mapa (128 x 128)
                0.30,  // 30% de água
                0.10,  // 10% de montanha
                0.012  // rugosidade desejada
        );

        Configuracao config = new Configuracao(
                60,   // tamanho da população
                300,  // gerações
                0.5,  // taxa de mutação (alta: é ela que cria os relevos novos)
                3     // tamanho do torneio
        );
        AlgoritmoGenetico<Terreno> ag = new AlgoritmoGenetico<>(problema, config, random);

        Visualizador<Terreno> tela = Visualizador.abrir("Terreno com algoritmo genético", 30, new DesenhoTerreno());
        Terreno melhor = ag.executar(tela::atualizar);

        // arquivos para usar no trabalho de computação gráfica
        ImagemTerreno.salvar(ImagemTerreno.alturas(melhor), "heightmap.png");
        ImagemTerreno.salvar(ImagemTerreno.colorida(melhor), "textura.png");
        System.out.println("Salvo: heightmap.png e textura.png");
    }
}
