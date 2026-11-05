package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Movimiento;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoRepositorio extends JpaRepository<Movimiento, Long> {
    List<Movimiento> findTop20ByTecnicoIdOrderByIdDesc(Long tecnicoId);
}
