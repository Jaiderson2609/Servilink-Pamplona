import { lazy, Suspense, useState } from 'react';
import { api } from './api.js';
import { useUI } from './ui.jsx';

const Mapa = lazy(() => import('./Mapa.jsx')); // Leaflet pesa: se descarga solo cuando hay un mapa en pantalla

/** Selector de ubicación: búsqueda de dirección, mapa para tocar, coordenadas editables y "Usar mi ubicación". */
export default function Ubicacion({ valor, alCambiar, radioKm }) {
  const { avisar, intentar } = useUI();
  const [consulta, setConsulta] = useState('');
  const [resultados, setResultados] = useState(null); // null = aún no se busca
  const [buscando, setBuscando] = useState(false);
  const [elegido, setElegido] = useState(null);
  const num = (v) => (v === '' || Number.isNaN(Number(v)) ? null : Number(v));

  const usarMiUbicacion = () => {
    if (!navigator.geolocation) return avisar('Tu navegador no permite obtener la ubicación', 'error');
    navigator.geolocation.getCurrentPosition(
      (p) => { alCambiar({ lat: Number(p.coords.latitude.toFixed(6)), lng: Number(p.coords.longitude.toFixed(6)) }); avisar('Ubicación actualizada', 'ok'); },
      () => avisar('No pudimos obtener tu ubicación; toca el mapa o escribe las coordenadas', 'error'),
      { enableHighAccuracy: true, timeout: 8000 });
  };
  const valida = valor.lat != null && valor.lng != null;

  /** Solo al tocar "Buscar" (o Enter en el campo): la búsqueda no autocompleta mientras se escribe. */
  const buscar = async () => {
    const texto = consulta.trim();
    if (texto.length < 3) return avisar('Escribe al menos 3 caracteres para buscar la dirección', 'error');
    setBuscando(true);
    const r = await intentar(() => api('/geocoding/search?q=' + encodeURIComponent(texto)));
    setBuscando(false);
    if (r) setResultados(r);
  };
  const elegir = (lugar) => {
    const punto = { lat: Number(lugar.lat.toFixed(6)), lng: Number(lugar.lng.toFixed(6)) };
    alCambiar(punto);
    setElegido({ nombre: lugar.nombre, ...punto });
    setResultados(null);
    avisar('Ubicación fijada. Ajusta el punto en el mapa si hace falta', 'ok');
  };
  // El nombre elegido solo se muestra mientras el punto siga donde se fijó (si lo mueven, deja de ser cierto).
  const nombreFijado = elegido && elegido.lat === valor.lat && elegido.lng === valor.lng ? elegido.nombre : null;

  return (
    <div>
      <div className="campo">
        <label htmlFor="u-buscar">Buscar dirección</label>
        <div className="buscar-direccion">
          <input id="u-buscar" type="search" maxLength={200} value={consulta} placeholder="Ej.: Carrera 5 con calle 6, Pamplona"
            onChange={(e) => setConsulta(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); buscar(); } }} /* Enter busca; no envía el formulario */ />
          <button type="button" className="btn sec" disabled={buscando} onClick={buscar}>{buscando ? 'Buscando…' : 'Buscar'}</button>
        </div>
        <div className="ayuda">Búsqueda de direcciones © colaboradores de OpenStreetMap</div>
        {resultados && (
          <div role="status">
            {resultados.length === 0
              ? <p className="mapa-ayuda">No encontramos esa dirección. Prueba con el barrio y la ciudad, o toca el mapa.</p>
              : (
                <>
                  <p className="mapa-ayuda">{resultados.length === 1 ? '1 resultado' : `${resultados.length} resultados`}. Elige el correcto:</p>
                  <ul className="resultados-direccion" aria-label="Resultados de la búsqueda">
                    {resultados.map((l, i) => <li key={i}><button type="button" className="btn sec" onClick={() => elegir(l)}>{l.nombre}</button></li>)}
                  </ul>
                </>
              )}
          </div>
        )}
        {nombreFijado && <p className="mapa-ayuda" role="status">Punto fijado en: {nombreFijado}</p>}
      </div>
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
