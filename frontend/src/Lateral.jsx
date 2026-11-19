import { useEffect, useState } from 'react';

const ICONOS = {
  nueva: 'M12 5v14M5 12h14',
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
};

function Icono({ k }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={ICONOS[k] || ICONOS.zona} />
    </svg>
  );
}

export function Marca({ claro }) {
  return (
    <div className={'lat-marca' + (claro ? ' claro' : '')}>
      <span className="lat-logo"><img src="/favicon.svg" alt="" width="26" height="26" /></span>
      <span>ServiLink<br />Pamplona</span>
    </div>
  );
}

/** Panel lateral de navegación: opciones del rol, tarjeta de la persona y salida. En pantallas angostas es un menú que se abre desde la izquierda. */
export default function Lateral({ yo, base, items, activa, salir }) {
  const [abierto, setAbierto] = useState(false);
  useEffect(() => {
    const cerrar = () => setAbierto(false);
    const tecla = (e) => { if (e.key === 'Escape') setAbierto(false); };
    window.addEventListener('hashchange', cerrar);
    window.addEventListener('keydown', tecla);
    return () => { window.removeEventListener('hashchange', cerrar); window.removeEventListener('keydown', tecla); };
  }, []);
  return (
    <>
      <div className="lat-barra">
        <button type="button" className="lat-menu" aria-label={abierto ? 'Cerrar menú' : 'Abrir menú'} aria-expanded={abierto} aria-controls="lat-panel" onClick={() => setAbierto(!abierto)}>
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" aria-hidden="true"><path d="M4 7h16M4 12h16M4 17h16" /></svg>
        </button>
        <Marca claro />
      </div>
      {abierto && <div className="lat-fondo" onClick={() => setAbierto(false)} aria-hidden="true" />}
      <aside id="lat-panel" className={'lateral' + (abierto ? ' abierto' : '')}>
        <div className="lat-arriba">
          <Marca claro />
          <nav className="lat-nav" aria-label="Principal">
            {items.map(([k, t]) => (
              <a key={k} href={`#/${base}/${k}`} className="lat-item" aria-current={k === activa ? 'page' : undefined}>
                <Icono k={k} />{t}
              </a>
            ))}
          </nav>
        </div>
        <div className="lat-yo">
          <div className="lat-persona">
            <span className="lat-avatar" aria-hidden="true">{(yo.nombre || '?').trim().charAt(0).toUpperCase()}</span>
            <span className="lat-datos">
              <span className="lat-nombre">{yo.nombre}</span>
              <span className="lat-rol">{yo.rol.toLowerCase()}</span>
            </span>
          </div>
          <button type="button" className="lat-salir" onClick={() => salir()}>Salir</button>
        </div>
      </aside>
    </>
  );
}
