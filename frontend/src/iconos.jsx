/** Iconos de trazo (24×24) compartidos por el panel lateral, la barra del celular y las tarjetas. */
const ICONOS = {
  nueva: 'M12 5v14M5 12h14',
  solicitar: 'M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0-18 0M12 8v8M8 12h8',
  solicitudes: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  oportunidades: 'M12 21s7-6.2 7-11a7 7 0 0 0-14 0c0 4.8 7 11 7 11zM12 10m-2.5 0a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0-5 0',
  servicios: 'M14.7 6.3a4 4 0 0 0-5.4 5.4L3 18l3 3 6.3-6.3a4 4 0 0 0 5.4-5.4l-2.5 2.5-2.4-.6-.6-2.4z',
  billetera: 'M3 7h18v12H3zM3 11h18M16 15h2',
  zona: 'M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0-18 0M12 12m-4 0a4 4 0 1 0 8 0a4 4 0 1 0-8 0',
  verificacion: 'M12 3l8 3v6c0 4.5-3.2 7.9-8 9-4.8-1.1-8-4.5-8-9V6zM8.5 12l2.5 2.5 4.5-5',
  resumen: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  usuarios: 'M16 19v-1a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v1M9.5 10a3.5 3.5 0 1 0 0-7 3.5 3.5 0 0 0 0 7M21 19v-1a4 4 0 0 0-3-3.9',
  tecnicos: 'M12 3l8 3v6c0 4.5-3.2 7.9-8 9-4.8-1.1-8-4.5-8-9V6zM8.5 12l2.5 2.5 4.5-5',
  parametros: 'M4 6h10M18 6h2M4 12h2M10 12h10M4 18h12M20 18h0M14 4v4M8 10v4M16 16v4',
  auditoria: 'M9 3h6l4 4v14H5V3zM9 12h6M9 16h6',
  cuenta: 'M12 8m-4 0a4 4 0 1 0 8 0a4 4 0 1 0-8 0M4 21c0-4 4-6 8-6s8 2 8 6',
  mas: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
  atras: 'M15 18l-6-6 6-6',
  salir: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  reloj: 'M12 12m-9 0a9 9 0 1 0 18 0a9 9 0 1 0-18 0M12 7v5l3 2',
  llama: 'M12 3c3 4 5 6.5 5 9.5a5 5 0 0 1-10 0C7 9.5 9 7 12 3z',
  rayo: 'M13 2L4 14h7l-1 8 9-12h-7z',
  candado: 'M5 11h14v10H5zM8 11V8a4 4 0 0 1 8 0v3',
  brocha: 'M4 20l4-1 11-11a2.8 2.8 0 0 0-4-4L4 15z',
  subir: 'M12 19V5M5 12l7-7 7 7',
  bajar: 'M12 5v14M5 12l7 7 7-7',
  campana: 'M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.7 21a2 2 0 0 1-3.4 0',
};

export function Icono({ k, t = 20, sw = 2.2 }) {
  return (
    <svg width={t} height={t} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={sw} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={ICONOS[k] || ICONOS.zona} />
    </svg>
  );
}

/** Icono según el oficio; los que no tienen uno propio usan la llave. */
export function claveDeOficio(oficio) {
  const o = (oficio || '').toLowerCase();
  if (o.includes('plom')) return 'llama';
  if (o.includes('elect')) return 'rayo';
  if (o.includes('cerraj')) return 'candado';
  if (o.includes('pint')) return 'brocha';
  return 'servicios';
}
