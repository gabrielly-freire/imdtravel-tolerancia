package br.ufrn.imd.imdtravel.repositories;

import java.util.List;

import br.ufrn.imd.imdtravel.models.Sale;

/** Registro local de vendas, gravado no volume (SALES_DIR). */
public interface SalesRepository {

    /** Grava uma venda concluída. Precisa ser seguro com chamadas simultâneas. */
    void save(Sale sale);

    /** Todas as vendas gravadas, na ordem em que foram feitas. */
    List<Sale> findAll();
}
