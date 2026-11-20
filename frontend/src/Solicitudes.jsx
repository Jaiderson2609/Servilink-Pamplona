import { useState } from 'react';
import { Insignia } from './ui.jsx';
import { Icono, claveDeOficio } from './iconos.jsx';
import { cop, recortar } from './util.js';
import { ir } from './hooks.js';

/** Minutos que le quedan a una solicitud abierta para recibir ofertas (null si ya venció o no hay plazo). */
const minutosRestantes = (s) => {
  if (!s.expiraEn) return null;
  const r = new Date(s.expiraEn) - Date.now();
  return r > 0 ? Math.ceil(r / 60000) : null;
};

export function ItemSolicitud({ s, extra, oferta }) {
  const partes = [extra, s.precioAcordadoCop ? `Acordado: ${cop(s.precioAcordadoCop)}` : null].filter(Boolean).join(' · ');
  return (
    <li>
      <button className="item-sol" onClick={() => ir(`#/s/${s.id}`)}>
        <span className="item-icono"><Icono k={claveDeOficio(s.oficio)} t={24} /></span>
        <span className="item-cuerpo">
          <strong className="item-titulo">{s.oficio || 'Servicio'}</strong>
          <span className="item-sub">{recortar(s.descripcion)}</span>
          {partes && <span className="item-sub">{partes}</span>}
          {oferta && (
            <span className="item-pie">
              <span className="item-plazo"><Icono k="reloj" t={16} />{minutosRestantes(s) ? `Quedan ${minutosRestantes(s)} min` : 'Plazo terminado'}</span>
              <span className="item-ofertar">Ofertar</span>
            </span>
          )}
        </span>
        {!oferta && <Insignia estado={s.estado} />}
      </button>
    </li>
  );
}

export function ListaSolicitudes({ lista, vacio, extra, oferta = false }) {
  if (!lista.length) return <div className="vacio">{vacio}</div>;
  return <ul className="lista-sol">{lista.map((s) => <ItemSolicitud key={s.id} s={s} extra={extra ? extra(s) : null} oferta={oferta} />)}</ul>;
}

const FILTROS = [
  ['todas', 'Todas', null],
  ['ofertas', 'Con ofertas', ['E02']],
  ['curso', 'En curso', ['E03', 'E04']],
  ['cerradas', 'Cerradas', ['E05', 'E06', 'E07']],
];

/** Lista con los filtros de siempre (Todas, Con ofertas, En curso, Cerradas) como botones redondeados. */
export function ListaFiltrada({ lista, vacio }) {
  const [filtro, setFiltro] = useState('todas');
  const estados = FILTROS.find(([k]) => k === filtro)[2];
  const visibles = estados ? lista.filter((s) => estados.includes(s.estado)) : lista;
  return (
    <>
      <div className="filtros" role="group" aria-label="Filtrar solicitudes">
        {FILTROS.map(([k, texto]) => (
          <button key={k} type="button" className="filtro" aria-pressed={k === filtro} onClick={() => setFiltro(k)}>{texto}</button>
        ))}
      </div>
      <ListaSolicitudes lista={visibles} vacio={lista.length ? 'No tienes solicitudes en esta categoría.' : vacio} />
    </>
  );
}
