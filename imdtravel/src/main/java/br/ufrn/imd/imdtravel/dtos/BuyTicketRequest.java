package br.ufrn.imd.imdtravel.dtos;

/** Corpo de POST /buyTicket. */
public record BuyTicketRequest(String flight, String day, String user) {
}
