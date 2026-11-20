import { useEffect, useState } from 'react';
import { api } from './api.js';
import AvisosPush from './AvisosPush.jsx';
import { Icono } from './iconos.jsx';
import { ROLES } from './util.js';
import { ir, usePolling } from './hooks.js';
import Disponibilidad from './Disponibilidad.jsx';

const inicial = (yo) => (yo.nombre || '?').trim().charAt(0).toUpperCase();

/** Barra superior del celular: marca (o flecha de volver) y título a la izquierda; a la derecha, la disponibilidad del técnico o la inicial de la persona. */
export function BarraSuperior({ yo, menu }) {
  const volver = () => { if (window.history.length > 1) window.history.back(); else ir(`#/${menu.base}/${menu.activa}`); };
  return (
    <header className="mov-sup">
      <div className="mov-izq">
        {menu.detalle
          ? <button type="button" className="mov-atras" aria-label="Volver" onClick={volver}><Icono k="atras" t={26} sw={2.6} /></button>
          : <span className="mov-logo"><img src="/favicon.svg" alt="" width="24" height="24" /></span>}
        <h1 className="mov-titulo">{menu.titulo}</h1>
      </div>
      {yo.rol === 'TECNICO' ? <Disponibilidad compacta /> : <span className="mov-avatar" aria-hidden="true">{inicial(yo)}</span>}
    </header>
  );
}

/** Solicitudes del hogar que ya tienen ofertas: el número que aparece sobre "Solicitudes". */
function useConOfertas(rol) {
  const { datos } = usePolling(() => (rol === 'HOGAR' ? api('/service-requests/mine') : Promise.resolve([])), 15000, [rol], (e) => e.tipo === 'solicitud');
  return Array.isArray(datos) ? datos.filter((s) => s.estado === 'E02').length : 0;
}

/** Barra inferior del celular, con la hoja que se abre desde "Más" (o "Cuenta" en el hogar). */
export function BarraInferior({ yo, menu, salir }) {
  const [hoja, setHoja] = useState(false);
  const conOfertas = useConOfertas(yo.rol);
  useEffect(() => {
    const cerrar = () => setHoja(false);
    const tecla = (e) => { if (e.key === 'Escape') setHoja(false); };
    window.addEventListener('hashchange', cerrar);
    window.addEventListener('keydown', tecla);
    return () => { window.removeEventListener('hashchange', cerrar); window.removeEventListener('keydown', tecla); };
  }, []);
  const { fijas, mas, tituloMas, iconoMas, masActiva } = menu.barra;

  return (
    <>
      {hoja && <div className="mov-fondo" onClick={() => setHoja(false)} aria-hidden="true" />}
      {hoja && (
        <div id="mov-hoja" className="mov-hoja" role="dialog" aria-label={tituloMas}>
          <span className="mov-asa" aria-hidden="true" />
          <div className="mov-persona">
            <span className="mov-avatar grande" aria-hidden="true">{inicial(yo)}</span>
            <span className="mov-datos"><span className="mov-nombre">{yo.nombre}</span><span className="mov-rol">{ROLES[yo.rol] || yo.rol}</span></span>
          </div>
          {mas.map(({ k, texto, sub }) => (
            <a key={k} href={`#/${menu.base}/${k}`} className="mov-fila">
              <span className="mov-fila-icono"><Icono k={k} t={22} /></span>
              <span className="mov-fila-texto"><span className="mov-fila-titulo">{texto}</span>{sub && <span className="mov-fila-sub">{sub}</span>}</span>
            </a>
          ))}
          <AvisosPush />
          <button type="button" className="mov-fila peligro" onClick={() => salir()}>
            <span className="mov-fila-icono"><Icono k="salir" t={22} /></span>
            <span className="mov-fila-texto"><span className="mov-fila-titulo">Salir</span></span>
          </button>
        </div>
      )}
      <nav className="mov-nav" aria-label="Principal">
        {fijas.map(({ k, texto, icono }) => (
          <a key={k} href={`#/${menu.base}/${k}`} className="mov-item" aria-current={k === menu.activa && !hoja ? 'page' : undefined}>
            <span className="mov-icono"><Icono k={icono} t={24} />{k === 'solicitudes' && conOfertas > 0 && <span className="mov-globo" aria-label={`${conOfertas} con ofertas`}>{conOfertas}</span>}</span>
            {texto}
          </a>
        ))}
        <button type="button" className="mov-item" aria-expanded={hoja} aria-current={masActiva && !hoja ? 'page' : undefined}
          aria-controls="mov-hoja" aria-haspopup="dialog" onClick={() => setHoja(!hoja)}>
          <span className="mov-icono"><Icono k={iconoMas} t={24} /></span>
          {tituloMas}
        </button>
      </nav>
    </>
  );
}
