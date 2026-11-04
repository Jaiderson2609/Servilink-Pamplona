package co.edu.unipamplona.servilink.apoyo;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj que las pruebas controlan para recorrer los bordes de las ventanas sin esperar. */
public class RelojMutable extends Clock {
    private volatile Instant ahora;

    public RelojMutable(Instant inicio) { this.ahora = inicio; }

    public void fijar(Instant instante) { this.ahora = instante; }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zona) { return this; }
    @Override public Instant instant() { return ahora; }
}
