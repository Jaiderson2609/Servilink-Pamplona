package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.FotoSolicitud;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FotoRepositorio extends JpaRepository<FotoSolicitud, Long> {

    /** Proyección sin el contenido: listar no debe cargar los bytes de cada imagen. */
    interface Resumen {
        Long getId();

        String getTipoMime();

        Integer getTamanoBytes();

        Instant getCreadaEn();

        String getUrl();
    }

    List<Resumen> findBySolicitudIdOrderByIdAsc(Long solicitudId);

    long countBySolicitudId(Long solicitudId);

    Optional<FotoSolicitud> findByIdAndSolicitudId(Long id, Long solicitudId);

    Optional<FotoSolicitud> findByPublicId(String publicId);
}
