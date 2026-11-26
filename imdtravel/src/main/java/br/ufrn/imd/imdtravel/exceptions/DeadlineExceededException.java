package br.ufrn.imd.imdtravel.exceptions;

/**
 * Lançada quando o orçamento total de tempo da requisição de compra é esgotado.
 */
public class DeadlineExceededException extends RuntimeException {

    private final String stage;
    private final long elapsedMs;

    public DeadlineExceededException(String stage, long elapsedMs) {
        super("prazo interno excedido antes da etapa " + stage + " (" + elapsedMs + " ms)");
        this.stage = stage;
        this.elapsedMs = elapsedMs;
    }

    public String getStage() {
        return stage;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }
}
