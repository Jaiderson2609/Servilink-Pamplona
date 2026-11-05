package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Solicitud;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface SolicitudRepositorio extends JpaRepository<Solicitud, Long> {

    /** SELECT … FOR UPDATE: serializa las operaciones simultáneas sobre la misma solicitud. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Solicitud s where s.id = :id")
    Optional<Solicitud> bloquear(Long id);

    boolean existsByTecnicoIdAndEstadoIn(Long tecnicoId, Collection<String> estados);

    boolean existsByHogarIdAndEstadoIn(Long hogarId, Collection<String> estados);

    List<Solicitud> findByHogarIdOrderByIdDesc(Long hogarId);

    List<Solicitud> findByEstadoInAndExpiraEnAfterOrderByIdDesc(Collection<String> estados, Instant ahora);

    @Query("select s from Solicitud s where s.tecnicoId = :t or s.id in "
            + "(select p.solicitudId from Postulacion p where p.tecnicoId = :t) order by s.id desc")
    List<Solicitud> deTecnico(Long t);

    /** RF-026: pasa a E06 las solicitudes cuya ventana ya terminó (TR-06 y TR-07). */
    @Modifying
    @Query("update Solicitud s set s.estado = 'E06' where s.estado in ('E01', 'E02') and s.expiraEn <= :ahora")
    int expirarVencidas(Instant ahora);

    @Modifying
    @Query("update Solicitud s set s.estado = 'E06' where s.hogarId = :hogarId and s.estado in ('E01', 'E02') "
            + "and s.expiraEn <= :ahora")
    int expirarVencidasDe(Long hogarId, Instant ahora);
}
