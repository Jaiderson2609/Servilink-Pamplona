import { referenciaDeRetorno } from './Billetera.jsx';

export const TABS_HOGAR = [['nueva', 'Nueva solicitud'], ['solicitudes', 'Mis solicitudes']];
export const TABS_TECNICO = [['oportunidades', 'Solicitudes cercanas'], ['servicios', 'Mis servicios'], ['billetera', 'Billetera'], ['zona', 'Zona de trabajo'], ['verificacion', 'Verificación']];
export const TABS_ADMIN = [['resumen', 'Resumen'], ['usuarios', 'Usuarios'], ['solicitudes', 'Solicitudes'], ['tecnicos', 'Verificación'], ['parametros', 'Parámetros'], ['auditoria', 'Auditoría']];

const valida = (tabs, tab, porDefecto) => (tabs.some(([k]) => k === tab) ? tab : porDefecto);

export const pestanaHogar = (tab) => valida(TABS_HOGAR, tab, 'nueva');
// Wompi devuelve al técnico a la raíz con ?recarga=…: se le lleva a la billetera a ver el resultado.
export const pestanaTecnico = (tab) => (referenciaDeRetorno() ? 'billetera' : valida(TABS_TECNICO, tab, 'oportunidades'));
export const pestanaAdmin = (tab) => valida(TABS_ADMIN, tab, 'resumen');

// Barra del celular: las opciones fijas van abajo; el resto cabe en la hoja "Más" (hogar: "Cuenta", que solo trae la salida).
const ETIQUETA_BARRA = { nueva: 'Solicitar', solicitudes: 'Solicitudes', oportunidades: 'Cercanas', servicios: 'Servicios', billetera: 'Billetera', resumen: 'Resumen', usuarios: 'Usuarios', tecnicos: 'Verificar' };
const ICONO_BARRA = { nueva: 'solicitar' };
const SUBTITULO_HOJA = { zona: 'Dónde recibes solicitudes', verificacion: 'Tu identidad', parametros: 'Reglas del sistema', auditoria: 'Quién hizo qué' };
const BARRA = {
  HOGAR: { fijas: ['nueva', 'solicitudes'], mas: [], tituloMas: 'Cuenta', iconoMas: 'cuenta' },
  TECNICO: { fijas: ['oportunidades', 'servicios', 'billetera'], mas: ['zona', 'verificacion'], tituloMas: 'Más', iconoMas: 'mas' },
  ADMIN: { fijas: ['resumen', 'usuarios', 'solicitudes', 'tecnicos'], mas: ['parametros', 'auditoria'], tituloMas: 'Más', iconoMas: 'mas' },
};
const TITULO_PANTALLA = { oportunidades: 'Cercanas' };

/** Opciones del panel lateral según el rol, y cuál está activa en la ruta actual. Incluye lo que necesita la barra del celular. */
export function menuDe(rol, ruta) {
  const enDetalle = ruta.vista === 's';
  const [base, items, activa] = rol === 'HOGAR'
    ? ['h', TABS_HOGAR, enDetalle ? 'solicitudes' : pestanaHogar(ruta.vista === 'h' ? ruta.tab : null)]
    : rol === 'TECNICO'
      ? ['t', TABS_TECNICO, enDetalle ? 'servicios' : pestanaTecnico(ruta.vista === 't' ? ruta.tab : null)]
      : ['a', TABS_ADMIN, enDetalle ? 'solicitudes' : pestanaAdmin(ruta.vista === 'a' ? ruta.tab : null)];
  const nombre = (k) => items.find(([c]) => c === k)[1];
  const b = BARRA[rol] || BARRA.ADMIN;
  return {
    base, items, activa,
    detalle: enDetalle,
    titulo: enDetalle ? 'Solicitud' : TITULO_PANTALLA[activa] || nombre(activa),
    barra: {
      fijas: b.fijas.map((k) => ({ k, texto: ETIQUETA_BARRA[k], icono: ICONO_BARRA[k] || k })),
      mas: b.mas.map((k) => ({ k, texto: nombre(k), sub: SUBTITULO_HOJA[k] })),
      tituloMas: b.tituloMas, iconoMas: b.iconoMas,
      masActiva: !enDetalle && b.mas.includes(activa),
    },
  };
}
