package servilink.nucleo;

import java.time.Instant;

/** Puerto de salida: la hora actual (permite probar la expiración sin esperar). */
public interface Reloj {
    Instant ahora();
}
