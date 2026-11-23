import ag.AlgoritmoGenetico;
import ag.Configuracao;
import terreno.DesenhoTerreno;
import terreno.GeradorTerreno;
import terreno.ImagemTerreno;
import terreno.PerfilTerreno;
import terreno.ProblemaTerreno;
import terreno.Terreno;
import visual.Visualizador;

import java.util.Random;

public class MainTerrenoPrevia {
    /** Sem argumentos: perfil ILHA (o original). Com o argumento "floresta": o terreno do jogo. */
    public static void main(String[] args) {
        long seed = 42;
        Random random = new Random(seed);
        PerfilTerreno perfil = args.length > 0 && args[0].equalsIgnoreCase("floresta")
                ? PerfilTerreno.FLORESTA : PerfilTerreno.ILHA;

        // na ILHA: mapa 128 x 128, 30% de água, 10% de montanha, rugosidade 0,012 (os valores de sempre)
        ProblemaTerreno problema = new ProblemaTerreno(
                perfil.tamanhoEvolucao(),
                perfil.alvoAgua(),
                perfil.alvoMontanha(),
                perfil.alvoRugosidade(),
                perfil.maiorLago()
        );

        Configuracao config = new Configuracao(
                60,                      // tamanho da população
                GeradorTerreno.GERACOES, // gerações (300)
                0.5,                     // taxa de mutação (alta: é ela que cria os relevos novos)
                3                        // tamanho do torneio
        );
        AlgoritmoGenetico<Terreno> ag = new AlgoritmoGenetico<>(problema, config, random);

        Visualizador<Terreno> tela = Visualizador.abrir("Terreno com algoritmo genético (" + perfil.nome() + ")", 30,
                new DesenhoTerreno());
        Terreno melhor = ag.executar(tela::atualizar);

        // arquivos para usar no trabalho de computação gráfica
        ImagemTerreno.salvar(ImagemTerreno.alturas(melhor), "heightmap.png");
        ImagemTerreno.salvar(ImagemTerreno.colorida(melhor), "textura.png");
        System.out.println("Salvo: heightmap.png e textura.png");
    }
}
