import { render } from '@testing-library/react';
import { vi } from 'vitest';
import { SesionProvider } from '../sesion.jsx';
import { UIProvider } from '../ui.jsx';
import { setToken } from '../api.js';

/**
 * Sustituye fetch por una tabla de rutas. Cada clave es "METODO /ruta" (sin /api/v1); el valor es el cuerpo JSON,
 * un número de estado con cuerpo [estado, cuerpo] o una función (init) => lo anterior.
 * Devuelve la lista de llamadas para poder comprobarlas.
 */
export function simularApi(tabla) {
  const llamadas = [];
  globalThis.fetch = vi.fn(async (url, init = {}) => {
    const metodo = init.method || 'GET';
    const ruta = String(url).replace('/api/v1', '');
    llamadas.push({ metodo, ruta, init, cuerpo: init.body && typeof init.body === 'string' ? JSON.parse(init.body) : init.body, cab: init.headers || {} });
    const clave = Object.keys(tabla).find((k) => k === `${metodo} ${ruta}`) ?? Object.keys(tabla).find((k) => `${metodo} ${ruta}`.startsWith(k + '?'));
    if (!clave) return new Response(JSON.stringify({ code: 'NOT_FOUND', message: 'sin simular: ' + metodo + ' ' + ruta, traceId: 't' }), { status: 404 });
    let r = tabla[clave];
    if (typeof r === 'function') r = r(init);
    const tupla = Array.isArray(r) && r.length === 2 && typeof r[0] === 'number'; // [estado, cuerpo]; un arreglo normal es el cuerpo
    const [estado, cuerpo] = tupla ? r : [200, r];
    return new Response(cuerpo === undefined ? '' : JSON.stringify(cuerpo), { status: estado, headers: { 'Content-Type': 'application/json' } });
  });
  return llamadas;
}

export function sesionComo(yo) {
  sessionStorage.setItem('servilink', JSON.stringify({ token: 'tok', yo }));
  setToken('tok');
}

export function pintar(ui) {
  return render(<UIProvider><SesionProvider>{ui}</SesionProvider></UIProvider>);
}
