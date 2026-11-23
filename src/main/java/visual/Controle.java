package visual;

/**
 * Decide quando o algoritmo pode seguir para a próxima geração.
 * Os botões mudam o estado; a thread do algoritmo pergunta "posso continuar?".
 */
public class Controle {
    private boolean pausado = true;          // começa parado, esperando o Start
    private boolean avancarUmPasso = false;

    public synchronized void iniciar() {
        pausado = false;
        notifyAll();
    }

    public synchronized void parar() {
        pausado = true;
    }

    public synchronized void avancar() {
        avancarUmPasso = true;
        notifyAll();
    }

    /** Chamado pela thread do algoritmo: fica esperando enquanto estiver pausado. */
    public synchronized void aguardarVez() {
        while (pausado && !avancarUmPasso) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        avancarUmPasso = false;
    }
}
