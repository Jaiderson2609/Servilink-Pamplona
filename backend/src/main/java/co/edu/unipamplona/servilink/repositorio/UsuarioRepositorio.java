package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Usuario;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByCorreo(String correo);
}
