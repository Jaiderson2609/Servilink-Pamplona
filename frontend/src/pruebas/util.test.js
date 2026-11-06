import { describe, expect, it } from 'vitest';
import { cop, cuentaAtras, estrellasTexto, metros, nombrePila, nuevaLlave, recortar } from '../util.js';
import { parsearRuta } from '../hooks.js';

describe('formato', () => {
  it('muestra pesos colombianos con separador de miles', () => {
    expect(cop(50000)).toBe('$ 50.000');
    expect(cop(null)).toBe('$ 0');
  });
  it('muestra distancias en metros o kilómetros', () => {
    expect(metros(850)).toBe('850 m');
    expect(metros(1500)).toBe('1.5 km');
    expect(metros(null)).toBe('');
  });
  it('recorta textos largos y toma el nombre de pila', () => {
    expect(recortar('a'.repeat(120), 110)).toHaveLength(111);
    expect(recortar('corto')).toBe('corto');
    expect(nombrePila('Ana María Pérez')).toBe('Ana');
  });
  it('resume la reputación', () => {
    expect(estrellasTexto(null, 0)).toBe('Sin calificaciones');
    expect(estrellasTexto(4.5, 2)).toBe('★ 4.5 (2)');
  });
});

describe('cuenta regresiva', () => {
  const ahora = Date.parse('2026-11-01T10:00:00Z');
  it('cuenta minutos y segundos', () => {
    expect(cuentaAtras('2026-11-01T10:04:05Z', ahora)).toBe('4:05');
  });
  it('termina exactamente en el instante de expirar', () => {
    expect(cuentaAtras('2026-11-01T10:00:00Z', ahora)).toBeNull();
    expect(cuentaAtras('2026-11-01T09:59:00Z', ahora)).toBeNull();
  });
});

describe('rutas con hash', () => {
  it('reconoce solicitudes, pestañas y el inicio', () => {
    expect(parsearRuta('#/s/12')).toEqual({ vista: 's', id: 12 });
    expect(parsearRuta('#/t/billetera')).toEqual({ vista: 't', tab: 'billetera' });
    expect(parsearRuta('#/a/usuarios')).toEqual({ vista: 'a', tab: 'usuarios' });
    expect(parsearRuta('#/')).toEqual({ vista: 'inicio' });
    expect(parsearRuta('#/s/abc')).toEqual({ vista: 'inicio' });
    expect(parsearRuta('')).toEqual({ vista: 'inicio' });
  });
});

describe('Idempotency-Key', () => {
  it('genera claves válidas para el servidor (8 a 80 caracteres, sin símbolos raros)', () => {
    const k = nuevaLlave();
    expect(k).toMatch(/^[A-Za-z0-9_-]{8,80}$/);
    expect(nuevaLlave()).not.toBe(k);
  });
});
