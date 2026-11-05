package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Postulacion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostulacionRepositorio extends JpaRepository<Postulacion, Long> {
    List<Postulacion> findBySolicitudId(Long solicitudId);
    boolean existsBySolicitudIdAndTecnicoId(Long solicitudId, Long tecnicoId);
}
