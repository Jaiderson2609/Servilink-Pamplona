import { referenciaDeRetorno } from './Billetera.jsx';

export const TABS_HOGAR = [['nueva', 'Nueva solicitud'], ['solicitudes', 'Mis solicitudes']];
export const TABS_TECNICO = [['oportunidades', 'Solicitudes cercanas'], ['servicios', 'Mis servicios'], ['billetera', 'Billetera'], ['zona', 'Zona de trabajo'], ['verificacion', 'Verificación']];
export const TABS_ADMIN = [['resumen', 'Resumen'], ['usuarios', 'Usuarios'], ['solicitudes', 'Solicitudes'], ['tecnicos', 'Verificación'], ['parametros', 'Parámetros'], ['auditoria', 'Auditoría']];

const valida = (tabs, tab, porDefecto) => (tabs.some(([k]) => k === tab) ? tab : porDefecto);

export const pestanaHogar = (tab) => valida(TABS_HOGAR, tab, 'nueva');
// Wompi devuelve al técnico a la raíz con ?recarga=…: se le lleva a la billetera a ver el resultado.
export const pestanaTecnico = (tab) => (referenciaDeRetorno() ? 'billetera' : valida(TABS_TECNICO, tab, 'oportunidades'));
export const pestanaAdmin = (tab) => valida(TABS_ADMIN, tab, 'resumen');

/** Opciones del panel lateral según el rol, y cuál está activa en la ruta actual. */
export function menuDe(rol, ruta) {
  const enDetalle = ruta.vista === 's';
  if (rol === 'HOGAR') return { base: 'h', items: TABS_HOGAR, activa: enDetalle ? 'solicitudes' : pestanaHogar(ruta.vista === 'h' ? ruta.tab : null) };
  if (rol === 'TECNICO') return { base: 't', items: TABS_TECNICO, activa: enDetalle ? 'servicios' : pestanaTecnico(ruta.vista === 't' ? ruta.tab : null) };
  return { base: 'a', items: TABS_ADMIN, activa: enDetalle ? 'solicitudes' : pestanaAdmin(ruta.vista === 'a' ? ruta.tab : null) };
}
