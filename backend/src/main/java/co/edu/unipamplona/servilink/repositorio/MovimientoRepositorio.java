package co.edu.unipamplona.servilink.repositorio;

import co.edu.unipamplona.servilink.dominio.Movimiento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MovimientoRepositorio extends JpaRepository<Movimiento, Long> {}
