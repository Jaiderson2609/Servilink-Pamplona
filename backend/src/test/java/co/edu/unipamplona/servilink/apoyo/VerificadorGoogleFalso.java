package co.edu.unipamplona.servilink.apoyo;

import co.edu.unipamplona.servilink.aplicacion.Codigo;
import co.edu.unipamplona.servilink.aplicacion.ErrorNegocio;
import co.edu.unipamplona.servilink.aplicacion.VerificadorGoogle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Doble de Google para las pruebas de integración: cada credencial conocida corresponde a una identidad. */
public class VerificadorGoogleFalso implements VerificadorGoogle {
    public final Map<String, Identidad> credenciales = new ConcurrentHashMap<>();
    private final String clienteId;

    public VerificadorGoogleFalso(String clienteId) { this.clienteId = clienteId == null ? "" : clienteId; }

    @Override public boolean disponible() { return !clienteId.isBlank(); }

    @Override public String clienteId() { return clienteId; }

    @Override
    public Identidad verificar(String credencial) {
        Identidad i = credenciales.get(credencial);
        if (i == null) {
            throw new ErrorNegocio(Codigo.UNAUTHENTICATED, "No se pudo verificar tu cuenta de Google. Intenta de nuevo");
        }
        return i;
    }
}
