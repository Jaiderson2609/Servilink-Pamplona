import { Insignia } from './ui.jsx';
import { cop, recortar } from './util.js';
import { ir } from './hooks.js';

export function ItemSolicitud({ s, extra }) {
  const partes = [extra, s.precioAcordadoCop ? `Acordado: ${cop(s.precioAcordadoCop)}` : null].filter(Boolean).join(' · ');
  return (
    <li>
      <button className="item-sol" onClick={() => ir(`#/s/${s.id}`)}>
        <div className="arriba"><strong>{s.oficio || 'Servicio'}</strong><Insignia estado={s.estado} /></div>
        <div className="suave">{recortar(s.descripcion)}</div>
        <div className="pequeno suave">{partes}</div>
      </button>
    </li>
  );
}

export function ListaSolicitudes({ lista, vacio, extra }) {
  if (!lista.length) return <div className="vacio">{vacio}</div>;
  return <ul className="lista-sol">{lista.map((s) => <ItemSolicitud key={s.id} s={s} extra={extra ? extra(s) : null} />)}</ul>;
}
