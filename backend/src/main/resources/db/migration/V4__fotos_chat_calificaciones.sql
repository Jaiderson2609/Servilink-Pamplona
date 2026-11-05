-- Etapa 4: fotos de la solicitud (RF-022), chat del servicio (RF-033) y calificaciones mutuas (RF-043).
CREATE TABLE fotos_solicitud (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    solicitud_id BIGINT      NOT NULL REFERENCES solicitudes (id),
    subida_por   BIGINT      NOT NULL REFERENCES usuarios (id),
    tipo_mime    VARCHAR(20) NOT NULL CHECK (tipo_mime IN ('image/jpeg', 'image/png', 'image/webp')),
    tamano_bytes INTEGER     NOT NULL CHECK (tamano_bytes BETWEEN 1 AND 2097152),
    contenido    BYTEA       NOT NULL,
    creada_en    TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_fotos_solicitud ON fotos_solicitud (solicitud_id);

CREATE TABLE mensajes (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    solicitud_id BIGINT       NOT NULL REFERENCES solicitudes (id),
    autor_id     BIGINT       NOT NULL REFERENCES usuarios (id),
    texto        VARCHAR(500) NOT NULL CHECK (length(btrim(texto)) > 0),
    creado_en    TIMESTAMPTZ  NOT NULL
);
CREATE INDEX idx_mensajes_solicitud ON mensajes (solicitud_id, id);

-- Cada parte califica a la otra una sola vez por servicio; la unicidad la garantiza la base.
CREATE TABLE calificaciones (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    solicitud_id    BIGINT       NOT NULL REFERENCES solicitudes (id),
    autor_id        BIGINT       NOT NULL REFERENCES usuarios (id),
    destinatario_id BIGINT       NOT NULL REFERENCES usuarios (id),
    estrellas       INTEGER      NOT NULL CHECK (estrellas BETWEEN 1 AND 5),
    comentario      VARCHAR(300),
    creada_en       TIMESTAMPTZ  NOT NULL,
    UNIQUE (solicitud_id, autor_id),
    CHECK (autor_id <> destinatario_id)
);
CREATE INDEX idx_calificaciones_destinatario ON calificaciones (destinatario_id);
