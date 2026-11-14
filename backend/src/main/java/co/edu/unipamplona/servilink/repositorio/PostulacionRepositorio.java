package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Postulacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostulacionRepositorio extends JpaRepository<Postulacion, Long> {
    List<Postulacion> findBySolicitudId(Long solicitudId);
    java.util.Optional<Postulacion> findBySolicitudIdAndTecnicoId(Long solicitudId, Long tecnicoId);
    boolean existsBySolicitudIdAndTecnicoId(Long solicitudId, Long tecnicoId);
}
