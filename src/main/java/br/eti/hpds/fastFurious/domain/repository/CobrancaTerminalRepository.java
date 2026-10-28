package br.eti.hpds.fastFurious.domain.repository;

import br.eti.hpds.fastFurious.domain.model.CobrancaTerminal;
import br.eti.hpds.fastFurious.domain.model.StatusCobranca;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CobrancaTerminalRepository extends JpaRepository<CobrancaTerminal, String> {

    /** A cobrança mais antiga no status informado (sem lock; o lock é feito por id em seguida). */
    Optional<CobrancaTerminal> findFirstByStatusOrderByCriadaEmAsc(StatusCobranca status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CobrancaTerminal c where c.id = :id")
    Optional<CobrancaTerminal> buscarParaAtualizar(@Param("id") String id);
}
