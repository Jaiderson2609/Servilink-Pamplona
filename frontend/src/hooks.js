import { useCallback, useEffect, useRef, useState } from 'react';
import { RESPALDO_MS, eventosConectados, suscribirEventos } from './eventos.js';

/** Rutas con hash: #/ · #/s/12 (solicitud) · #/t/billetera · #/a/usuarios */
export function parsearRuta(hash) {
  const [, vista, valor] = (hash || '').split('/');
  if (vista === 's' && /^\d+$/.test(valor || '')) return { vista: 's', id: Number(valor) };
  if (vista === 't' || vista === 'a') return { vista, tab: valor || null };
  return { vista: 'inicio' };
}

export function useRuta() {
  const [ruta, setRuta] = useState(() => parsearRuta(window.location.hash));
  useEffect(() => {
    const al = () => setRuta(parsearRuta(window.location.hash));
    window.addEventListener('hashchange', al);
    return () => window.removeEventListener('hashchange', al);
  }, []);
  return ruta;
}
export const ir = (destino) => { window.location.hash = destino; };

/** Espera mínima entre avisos seguidos: varios eventos casi juntos se atienden con una sola consulta. */
const AGRUPAR_MS = 150;

/**
 * Escucha los eventos en tiempo real. `filtro(evento)` decide cuáles interesan; 'reconectado' siempre llega,
 * porque pudo haber cambios mientras el canal estaba caído.
 */
export function useEventos(filtro, alEvento) {
  const filtroRef = useRef(filtro);
  const accionRef = useRef(alEvento);
  filtroRef.current = filtro;
  accionRef.current = alEvento;
  useEffect(() => suscribirEventos((e) => {
    if (e.tipo === 'reconectado' || filtroRef.current(e)) accionRef.current(e);
  }), []);
}

/**
 * Pide los datos ahora y cada `ms`. Solo cambia el estado si la respuesta cambió, para no repintar sin necesidad.
 * Con `escuchar(evento)` además recarga cuando el servidor avisa de un cambio; mientras el canal en tiempo real esté
 * vivo, el sondeo queda como respaldo (una consulta cada RESPALDO_MS) y si se cae vuelve a su ritmo normal.
 * Devuelve { datos, error, cargando, recargar }.
 */
export function usePolling(traer, ms, claves = [], escuchar = null) {
  const [estado, setEstado] = useState({ datos: undefined, error: null, cargando: true });
  const ultimo = useRef('');
  const ultimaCarga = useRef(0);
  const traerRef = useRef(traer);
  const escucharRef = useRef(escuchar);
  traerRef.current = traer;
  escucharRef.current = escuchar;

  const recargar = useCallback(async () => {
    ultimaCarga.current = Date.now();
    try {
      const d = await traerRef.current();
      const firma = JSON.stringify(d);
      if (firma !== ultimo.current) { ultimo.current = firma; setEstado({ datos: d, error: null, cargando: false }); }
      else setEstado((e) => (e.cargando || e.error ? { datos: d, error: null, cargando: false } : e));
    } catch (error) {
      setEstado((e) => ({ ...e, error, cargando: false }));
    }
  }, []);

  useEffect(() => {
    ultimo.current = '';
    setEstado({ datos: undefined, error: null, cargando: true });
    let vivo = true;
    let pendiente = null;
    recargar();
    const t = ms ? setInterval(() => {
      if (!vivo) return;
      const respaldo = escucharRef.current && eventosConectados() && Date.now() - ultimaCarga.current < RESPALDO_MS;
      if (!respaldo) recargar();
    }, ms) : null;
    const baja = escucharRef.current ? suscribirEventos((e) => {
      if (!vivo || pendiente || !(e.tipo === 'reconectado' || escucharRef.current?.(e))) return;
      pendiente = setTimeout(() => { pendiente = null; if (vivo) recargar(); }, AGRUPAR_MS);
    }) : null;
    return () => { vivo = false; if (t) clearInterval(t); if (baja) baja(); if (pendiente) clearTimeout(pendiente); };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, claves);

  return { ...estado, recargar };
}

/** Reloj que se actualiza cada segundo (para las cuentas regresivas). */
export function useAhora(ms = 1000) {
  const [ahora, setAhora] = useState(Date.now());
  useEffect(() => { const t = setInterval(() => setAhora(Date.now()), ms); return () => clearInterval(t); }, [ms]);
  return ahora;
}
