package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Usuario;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreo(String correo);

    /** Nulo si el usuario no existe; se consulta en cada petición autenticada para respetar las suspensiones. */
    @Query("select u.suspendido from Usuario u where u.id = :id")
    Boolean estaSuspendido(Long id);

    /** El patrón ya viene en minúsculas y con comodines; los parámetros nunca son nulos. */
    @Query("select u from Usuario u where u.rol in :roles and (lower(u.nombre) like :patron or lower(u.correo) like :patron)")
    Page<Usuario> buscar(Collection<String> roles, String patron, Pageable pagina);

    long countByRol(String rol);

    long countBySuspendidoTrue();
}
