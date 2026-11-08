package co.edu.unipamplona.servilink.aplicacion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Al arrancar, crea el administrador inicial si se definieron ADMIN_CORREO y ADMIN_CLAVE (el registro público no crea admins). */
@Component
public class AdminInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInicial.class);

    private final AuthService auth;
    private final String correo;
    private final String clave;

    public AdminInicial(AuthService auth, @Value("${servilink.admin-correo:}") String correo,
                        @Value("${servilink.admin-clave:}") String clave) {
        this.auth = auth;
        this.correo = correo;
        this.clave = clave;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (correo.isBlank() && clave.isBlank()) {
            return;
        }
        if (correo.isBlank() || clave.isBlank()) {
            throw new IllegalStateException("Define ADMIN_CORREO y ADMIN_CLAVE juntas, o ninguna");
        }
        if (auth.asegurarAdmin(correo, clave)) {
            log.info("Administrador inicial creado");
        }
    }
}
