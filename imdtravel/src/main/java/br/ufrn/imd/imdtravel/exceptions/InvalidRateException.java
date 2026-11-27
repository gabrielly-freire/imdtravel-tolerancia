package br.ufrn.imd.imdtravel.exceptions;

/** O Exchange respondeu, mas a taxa não pode ser usada: ilegível, ausente ou fora da faixa. */
public class InvalidRateException extends RuntimeException {

    public InvalidRateException(String message, Throwable cause) {
        super(message, cause);
    }
}
