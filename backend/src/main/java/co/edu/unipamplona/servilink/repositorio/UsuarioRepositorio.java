package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Usuario;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreo(String correo);

    /** Nulo si el usuario no existe; se consulta en cada petición autenticada para respetar las suspensiones. */
    @Query("select u.suspendido from Usuario u where u.id = :id")
    Boolean estaSuspendido(Long id);

    /** El patrón ya viene en minúsculas y con comodines; los parámetros nunca son nulos. */
    @Query("select u from Usuario u where u.rol in :roles and (lower(u.nombre) like :patron or lower(u.correo) like :patron)")
    Page<Usuario> buscar(Collection<String> roles, String patron, Pageable pagina);

    org.springframework.data.domain.Page<Usuario> findByRolAndVerificacion(String rol, String verificacion, Pageable pagina);

    long countByRol(String rol);

    long countBySuspendidoTrue();

    /** Un mismo token de navegador no puede avisar a dos cuentas: se lo quita a las demás. */
    @Modifying
    @Query("update Usuario u set u.tokenPush = null where u.tokenPush = :token and u.id <> :id")
    int liberarToken(String token, Long id);

    /**
     * Técnicos verificados, disponibles y con aviso push activo a quienes les interesa una solicitud nueva:
     * los que la tienen dentro de su radio de trabajo y los que aún no definieron zona (ven todo, como en "Cercanas").
     */
    @Query(value = """
            select u.id
              from usuarios u
              join solicitudes s on s.id = :solicitudId
              left join ubicaciones_tecnico z on z.tecnico_id = u.id
             where u.rol = 'TECNICO' and u.verificacion = 'APROBADA' and u.disponibilidad = 'AVAILABLE'
               and not u.suspendido and u.token_push is not null
               and (z.tecnico_id is null or ST_DWithin(s.punto, z.punto, z.radio_km * 1000.0))
             order by u.id
            """, nativeQuery = true)
    List<Long> tecnicosParaAvisar(Long solicitudId);
}
