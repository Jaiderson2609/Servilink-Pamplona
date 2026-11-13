-- Renovación controlada de la sesión (DAD 11.1): el token de acceso dura poco y se renueva con un token opaco,
-- de un solo uso, que el servidor guarda solo como hash. Cada renovación entrega uno nuevo de la misma familia;
-- si se reutiliza uno ya gastado se revoca toda la familia.
CREATE TABLE sesiones_renovacion (
    id             BIGSERIAL PRIMARY KEY,
    usuario_id     BIGINT      NOT NULL REFERENCES usuarios (id) ON DELETE CASCADE,
    familia        VARCHAR(36) NOT NULL,
    token_hash     CHAR(64)    NOT NULL UNIQUE,
    familia_inicio TIMESTAMPTZ NOT NULL,
    creada_en      TIMESTAMPTZ NOT NULL,
    expira_en      TIMESTAMPTZ NOT NULL,
    usada_en       TIMESTAMPTZ
);
CREATE INDEX idx_sesiones_renovacion_familia ON sesiones_renovacion (familia);
CREATE INDEX idx_sesiones_renovacion_usuario ON sesiones_renovacion (usuario_id);
