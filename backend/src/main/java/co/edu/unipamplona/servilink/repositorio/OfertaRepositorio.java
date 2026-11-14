package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Oferta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfertaRepositorio extends JpaRepository<Oferta, Long> {
    List<Oferta> findByPostulacionIdOrderBySecuencia(Long postulacionId);
    boolean existsByPostulacionIdAndAutorAndEstado(Long postulacionId, String autor, String estado);
}
