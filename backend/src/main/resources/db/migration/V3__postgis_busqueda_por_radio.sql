-- Etapa 3: búsqueda por radio con PostGIS (RF-018, RF-019). Requiere la extensión PostGIS en el servidor.
CREATE EXTENSION IF NOT EXISTS postgis;

-- Punto geográfico de cada solicitud, derivado de lat/lng para que no pueda desincronizarse.
ALTER TABLE solicitudes ADD COLUMN punto geography(Point, 4326)
    GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED;
CREATE INDEX idx_solicitudes_punto ON solicitudes USING GIST (punto);

-- Zona de trabajo del técnico: su ubicación base y hasta dónde está dispuesto a desplazarse.
CREATE TABLE ubicaciones_tecnico (
    tecnico_id    BIGINT PRIMARY KEY REFERENCES usuarios (id),
    lat           DOUBLE PRECISION NOT NULL CHECK (lat BETWEEN -90 AND 90),
    lng           DOUBLE PRECISION NOT NULL CHECK (lng BETWEEN -180 AND 180),
    radio_km      INTEGER NOT NULL DEFAULT 5 CHECK (radio_km BETWEEN 1 AND 50),
    actualizada_en TIMESTAMPTZ NOT NULL,
    punto         geography(Point, 4326)
        GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(lng, lat), 4326)::geography) STORED
);
CREATE INDEX idx_ubicaciones_punto ON ubicaciones_tecnico USING GIST (punto);
