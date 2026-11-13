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

    org.springframework.data.domain.Page<Solicitud> findByEstado(String estado, org.springframework.data.domain.Pageable pagina);

    @Query("select s.estado, count(s) from Solicitud s group by s.estado")
    List<Object[]> contarPorEstado();

    /** Solicitud abierta con su distancia en metros al punto base del técnico. */
    interface Cercana {
        Long getId();

        Double getDistancia();
    }

    /**
     * Solicitudes abiertas dentro del radio del técnico, de la más cercana a la más lejana.
     * ST_DWithin usa el índice GIST; ST_Distance sobre geography devuelve metros.
     */
    @Query(value = """
            select s.id as id, ST_Distance(s.punto, u.punto) as distancia
              from solicitudes s
              join ubicaciones_tecnico u on u.tecnico_id = :tecnicoId
             where s.estado in ('E01', 'E02')
               and s.expira_en > :ahora
               and ST_DWithin(s.punto, u.punto, u.radio_km * 1000.0)
             order by distancia asc, s.id desc
            """, nativeQuery = true)
    List<Cercana> cercanas(Long tecnicoId, Instant ahora);

    List<Solicitud> findByEstadoInAndExpiraEnAfterOrderByIdDesc(Collection<String> estados, Instant ahora);

    @Query("select s from Solicitud s where s.tecnicoId = :t or s.id in "
            + "(select p.solicitudId from Postulacion p where p.tecnicoId = :t) order by s.id desc")
    List<Solicitud> deTecnico(Long t);

    /** RF-026: pasa a E06 las solicitudes cuya ventana ya terminó (TR-06 y TR-07). */
    @Modifying
    @Query("update Solicitud s set s.estado = 'E06' where s.estado in ('E01', 'E02') and s.expiraEn <= :ahora")
    int expirarVencidas(Instant ahora);

    /** Las solicitudes que el barrido de expiración va a pasar a E06 (para avisar a sus partes). */
    java.util.List<Solicitud> findByEstadoInAndExpiraEnLessThanEqual(java.util.List<String> estados, Instant ahora);

    @Modifying
    @Query("update Solicitud s set s.estado = 'E06' where s.hogarId = :hogarId and s.estado in ('E01', 'E02') "
            + "and s.expiraEn <= :ahora")
    int expirarVencidasDe(Long hogarId, Instant ahora);
}
