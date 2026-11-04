package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Solicitud;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface SolicitudRepositorio extends JpaRepository<Solicitud, Long> {

    /** SELECT … FOR UPDATE: serializa las operaciones simultáneas sobre la misma solicitud. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Solicitud s where s.id = :id")
    Optional<Solicitud> bloquear(Long id);

    boolean existsByTecnicoIdAndEstadoIn(Long tecnicoId, Collection<String> estados);
}
