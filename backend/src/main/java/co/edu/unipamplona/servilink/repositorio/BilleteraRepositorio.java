package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Billetera;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BilleteraRepositorio extends JpaRepository<Billetera, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Billetera b where b.tecnicoId = :id")
    Optional<Billetera> bloquear(Long id);

    @Query("select coalesce(sum(b.retenido), 0) from Billetera b")
    long totalRetenido();
}
