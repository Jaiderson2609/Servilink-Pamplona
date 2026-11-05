import { lazy, Suspense } from 'react';
import { useUI } from './ui.jsx';

const Mapa = lazy(() => import('./Mapa.jsx')); // Leaflet pesa: se descarga solo cuando hay un mapa en pantalla

/** Selector de ubicación: mapa para tocar, coordenadas editables y "Usar mi ubicación". */
export default function Ubicacion({ valor, alCambiar, radioKm }) {
  const { avisar } = useUI();
  const num = (v) => (v === '' || Number.isNaN(Number(v)) ? null : Number(v));

  const usarMiUbicacion = () => {
    if (!navigator.geolocation) return avisar('Tu navegador no permite obtener la ubicación', 'error');
    navigator.geolocation.getCurrentPosition(
      (p) => { alCambiar({ lat: Number(p.coords.latitude.toFixed(6)), lng: Number(p.coords.longitude.toFixed(6)) }); avisar('Ubicación actualizada', 'ok'); },
      () => avisar('No pudimos obtener tu ubicación; toca el mapa o escribe las coordenadas', 'error'),
      { enableHighAccuracy: true, timeout: 8000 });
  };
  const valida = valor.lat != null && valor.lng != null;

  return (
    <div>
      {valida && (
        <Suspense fallback={<div className="mapa cargando">Cargando mapa…</div>}>
          <Mapa punto={valor} radioKm={radioKm} alElegir={(p) => alCambiar({ lat: Number(p.lat.toFixed(6)), lng: Number(p.lng.toFixed(6)) })} />
        </Suspense>
      )}
      <p className="mapa-ayuda">Toca el mapa para mover el punto. Si el mapa no carga, escribe las coordenadas.</p>
      <div className="dos">
        <div className="campo">
          <label htmlFor="u-lat">Latitud</label>
          <input id="u-lat" type="number" step="any" min={-90} max={90} required value={valor.lat ?? ''}
            onChange={(e) => alCambiar({ ...valor, lat: num(e.target.value) })} />
        </div>
        <div className="campo">
          <label htmlFor="u-lng">Longitud</label>
          <input id="u-lng" type="number" step="any" min={-180} max={180} required value={valor.lng ?? ''}
            onChange={(e) => alCambiar({ ...valor, lng: num(e.target.value) })} />
        </div>
      </div>
      <button type="button" className="btn sec chico" onClick={usarMiUbicacion}>Usar mi ubicación</button>
    </div>
  );
}
