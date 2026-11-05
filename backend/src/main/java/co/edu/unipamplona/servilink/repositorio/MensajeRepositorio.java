package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Mensaje;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MensajeRepositorio extends JpaRepository<Mensaje, Long> {
    List<Mensaje> findBySolicitudIdAndIdGreaterThanOrderByIdAsc(Long solicitudId, Long despuesDe, Pageable pagina);
}
