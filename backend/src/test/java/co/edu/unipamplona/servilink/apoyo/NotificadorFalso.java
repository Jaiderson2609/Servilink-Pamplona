package co.edu.unipamplona.servilink.apoyo;

import co.edu.unipamplona.servilink.infraestructura.Notificador;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Doble de Firebase: registra los avisos y puede simular que el servicio está caído. */
public class NotificadorFalso implements Notificador {
    public volatile boolean caido;
    public final List<String> enviados = new CopyOnWriteArrayList<>();
    public final List<String> intentos = new CopyOnWriteArrayList<>();

    @Override
    public boolean enviar(String tokenPush, String texto) {
        intentos.add(texto);
        if (caido) {
            return false;
        }
        enviados.add(texto);
        return true;
    }

    public void reiniciar() {
        caido = false;
        enviados.clear();
        intentos.clear();
    }
}
