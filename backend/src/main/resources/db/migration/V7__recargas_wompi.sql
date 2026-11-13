-- Recargas con Wompi (RF-038): cada intento de pago es una fila con referencia única.
-- El saldo solo sube cuando llega un evento firmado y aprobado; la fila registra en qué estado quedó.
CREATE TABLE recargas (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tecnico_id        BIGINT      NOT NULL REFERENCES usuarios (id),
    referencia        VARCHAR(40) NOT NULL UNIQUE,
    valor             BIGINT      NOT NULL CHECK (valor > 0),
    estado            VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK (estado IN ('PENDING','APPROVED','FAILED','REVIEW')),
    transaccion_wompi VARCHAR(60) UNIQUE,
    creada_en         TIMESTAMPTZ NOT NULL,
    resuelta_en       TIMESTAMPTZ
);
CREATE INDEX idx_recargas_tecnico ON recargas (tecnico_id, id DESC);
