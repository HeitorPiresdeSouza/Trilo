package br.eti.hpds.fastFurious.domain.repository;

import br.eti.hpds.fastFurious.domain.model.Estorno;
import br.eti.hpds.fastFurious.domain.model.RotaEstorno;
import br.eti.hpds.fastFurious.domain.model.StatusEstorno;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EstornoRepository extends JpaRepository<Estorno, String> {

    List<Estorno> findByPedidoIdOrderByCriadoEmDesc(Long pedidoId);

    List<Estorno> findByStatus(StatusEstorno status);

    /** O estorno mais antigo à espera da maquininha (sem lock; o lock é feito por id em seguida). */
    Optional<Estorno> findFirstByStatusAndRotaOrderByCriadoEmAsc(StatusEstorno status, RotaEstorno rota);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Estorno e where e.id = :id")
    Optional<Estorno> buscarParaAtualizar(@Param("id") String id);
}
