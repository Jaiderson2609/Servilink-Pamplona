import { useEffect } from 'react';
import { Circle, CircleMarker, MapContainer, TileLayer, useMap, useMapEvents } from 'react-leaflet';

function Clics({ alElegir }) {
  useMapEvents({ click: (e) => alElegir({ lat: e.latlng.lat, lng: e.latlng.lng }) });
  return null;
}
function Centrar({ punto }) {
  const mapa = useMap();
  useEffect(() => { mapa.setView([punto.lat, punto.lng], mapa.getZoom()); }, [punto.lat, punto.lng, mapa]);
  return null;
}

/**
 * Mapa de OpenStreetMap con un punto que se mueve tocando el mapa. Si `radioKm` viene, dibuja el radio de trabajo.
 * La atribución es obligatoria por la licencia de OpenStreetMap.
 */
export default function Mapa({ punto, alElegir, radioKm }) {
  return (
    <div className="mapa">
      <MapContainer center={[punto.lat, punto.lng]} zoom={radioKm && radioKm > 8 ? 10 : 14} scrollWheelZoom={false}
        attributionControl>
        <TileLayer attribution="© colaboradores de OpenStreetMap" url="https://tile.openstreetmap.org/{z}/{x}/{y}.png" maxZoom={19} />
        {radioKm ? <Circle center={[punto.lat, punto.lng]} radius={radioKm * 1000} pathOptions={{ color: '#0f5e57', weight: 2, fillOpacity: 0.08 }} /> : null}
        <CircleMarker center={[punto.lat, punto.lng]} radius={9} pathOptions={{ color: '#fff', weight: 3, fillColor: '#0f5e57', fillOpacity: 1 }} />
        <Clics alElegir={alElegir} />
        <Centrar punto={punto} />
      </MapContainer>
    </div>
  );
}
