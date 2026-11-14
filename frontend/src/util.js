export const PAMPLONA = { lat: 7.3772, lng: -72.6473 };

export const ESTADOS = {
  E01: ['Abierta', 'abierta'], E02: ['Con ofertas', 'ofertas'], E03: ['Técnico asignado', 'asignada'],
  E04: ['En ejecución', 'ejecucion'], E05: ['Finalizado', 'final'], E06: ['Expirada', 'expirada'], E07: ['Cancelada', 'cancelada'],
};
export const PASOS = [['E01', 'Publicada'], ['E02', 'Ofertas'], ['E03', 'Asignado'], ['E04', 'En ejecución'], ['E05', 'Finalizado']];
export const TIPOS_MOV = { RECARGA: 'Recarga', RETENCION: 'Comisión retenida', LIBERACION: 'Comisión liberada', COMISION: 'Comisión cobrada' };
export const ACCIONES = { SUSPENDER_USUARIO: 'Suspendió una cuenta', REACTIVAR_USUARIO: 'Reactivó una cuenta', CANCELAR_SOLICITUD: 'Canceló una solicitud', APROBAR_TECNICO: 'Aprobó la verificación de un técnico', RECHAZAR_TECNICO: 'Rechazó la verificación de un técnico' };
export const ACTIVAS = ['E01', 'E02', 'E03', 'E04'];

export const cop = (n) => '$ ' + Number(n ?? 0).toLocaleString('es-CO');
export const fecha = (x) => (x ? new Date(x).toLocaleString('es-CO', { dateStyle: 'medium', timeStyle: 'short' }) : '');
export const hora = (x) => new Date(x).toLocaleTimeString('es-CO', { hour: '2-digit', minute: '2-digit' });
export const metros = (m) => (m == null ? '' : m < 1000 ? `${m} m` : `${(m / 1000).toFixed(1)} km`);
export const recortar = (t, n = 110) => (t.length > n ? t.slice(0, n) + '…' : t);
export const nombrePila = (n) => (n || '').split(' ')[0];
export const estrellasTexto = (prom, n) => (prom == null ? 'Sin calificaciones' : `★ ${prom.toFixed(1)} (${n})`);

/** m:ss que falta hasta `expiraEn`, o null si ya venció. */
export function cuentaAtras(expiraEn, ahora = Date.now()) {
  const r = new Date(expiraEn) - ahora;
  if (!(r > 0)) return null;
  return `${Math.floor(r / 60000)}:${String(Math.floor((r % 60000) / 1000)).padStart(2, '0')}`;
}

/** Clave nueva para Idempotency-Key: 32 caracteres hexadecimales. */
export const nuevaLlave = () => (globalThis.crypto?.randomUUID?.() ?? `${Date.now()}${Math.random()}`.replace(/\D/g, '')).replace(/-/g, '').padEnd(16, '0');

/** Lleva al usuario a otra dirección (el pago en Wompi). Aparte para poder sustituirla en las pruebas. */
export const irA = (url) => window.location.assign(url);
