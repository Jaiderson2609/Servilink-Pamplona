package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.NotificacionPendiente;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface NotificacionRepositorio extends JpaRepository<NotificacionPendiente, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificacionPendiente n where n.id = :id")
    Optional<NotificacionPendiente> bloquear(Long id);

    @Query("select n.id from NotificacionPendiente n where n.estado = 'PENDIENTE' order by n.id")
    List<Long> idsPendientes();
}
