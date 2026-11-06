-- DDS DT-10: los reintentos con la misma Idempotency-Key devuelven el resultado guardado y no repiten la operación.
-- La clave es de cada usuario; una tarea programada borra las que superan las 24 horas.
CREATE TABLE claves_idempotencia (
    usuario_id   BIGINT       NOT NULL REFERENCES usuarios (id),
    clave        VARCHAR(80)  NOT NULL,
    huella       VARCHAR(300) NOT NULL,
    estado       VARCHAR(12)  NOT NULL CHECK (estado IN ('EN_CURSO', 'COMPLETADA')),
    http_estado  INTEGER,
    cuerpo       TEXT,
    tipo         VARCHAR(100),
    creada_en    TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (usuario_id, clave)
);
CREATE INDEX idx_claves_idempotencia_creada ON claves_idempotencia (creada_en);
