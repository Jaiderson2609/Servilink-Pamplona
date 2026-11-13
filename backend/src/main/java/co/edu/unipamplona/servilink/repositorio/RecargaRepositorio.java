package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Recarga;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface RecargaRepositorio extends JpaRepository<Recarga, Long> {

    /** Serializa los eventos repetidos o simultáneos de una misma referencia. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Recarga r where r.referencia = :referencia")
    Optional<Recarga> bloquearPorReferencia(String referencia);

    Optional<Recarga> findByReferenciaAndTecnicoId(String referencia, Long tecnicoId);

    long countByTecnicoIdAndCreadaEnAfter(Long tecnicoId, Instant desde);

    boolean existsByTransaccionWompiAndReferenciaNot(String transaccionWompi, String referencia);
}
