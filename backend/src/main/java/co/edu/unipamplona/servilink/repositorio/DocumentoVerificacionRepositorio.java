package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.DocumentoVerificacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DocumentoVerificacionRepositorio extends JpaRepository<DocumentoVerificacion, Long> {
    Optional<DocumentoVerificacion> findByTecnicoIdAndTipo(Long tecnicoId, String tipo);

    List<DocumentoVerificacion> findByTecnicoId(Long tecnicoId);

    @Modifying
    @Query("delete from DocumentoVerificacion d where d.tecnicoId = :tecnicoId")
    void borrarDe(Long tecnicoId);
}
