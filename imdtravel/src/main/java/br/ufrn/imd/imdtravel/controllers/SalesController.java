package br.ufrn.imd.imdtravel.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufrn.imd.imdtravel.models.Sale;
import br.ufrn.imd.imdtravel.repositories.SalesRepository;

@RestController
public class SalesController {

    private final SalesRepository salesRepository;

    public SalesController(SalesRepository salesRepository) {
        this.salesRepository = salesRepository;
    }

    @GetMapping("/sales")
    public List<Sale> getSales() {
        return salesRepository.findAll();
    }
}
