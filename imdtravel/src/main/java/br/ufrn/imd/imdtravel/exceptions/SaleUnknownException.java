package br.ufrn.imd.imdtravel.exceptions;

/**
 * O POST /sell saiu, mas não há confirmação do resultado: o assento pode ou não ter sido vendido.
 * Quem recebe esta exceção não pode repetir a venda nem tratá-la como falha comum.
 */
public class SaleUnknownException extends RuntimeException {

    private final String reason;

    public SaleUnknownException(String reason, Throwable cause) {
        super("resultado do /sell desconhecido: " + reason, cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
