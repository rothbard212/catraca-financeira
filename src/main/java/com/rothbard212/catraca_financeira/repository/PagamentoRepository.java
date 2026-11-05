package com.rothbard212.catraca_financeira.repository;

import com.rothbard212.catraca_financeira.model.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PagamentoRepository extends JpaRepository<Pagamento, Long> {

}