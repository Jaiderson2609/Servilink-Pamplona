package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Calificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CalificacionRepositorio extends JpaRepository<Calificacion, Long> {

    boolean existsBySolicitudIdAndAutorId(Long solicitudId, Long autorId);

    long countByDestinatarioId(Long destinatarioId);

    @Query("select avg(c.estrellas) from Calificacion c where c.destinatarioId = :id")
    Double promedio(Long id);
}
