import { describe, expect, it } from 'vitest';
import { unirMensajes } from '../util.js';

describe('unirMensajes', () => {
  it('no repite un mensaje que dos consultas trajeron a la vez', () => {
    const a = [{ id: 1, texto: 'hola' }];
    const r = unirMensajes(a, [{ id: 2, texto: 'link' }]);
    expect(unirMensajes(r, [{ id: 2, texto: 'link' }]).map((m) => m.id)).toEqual([1, 2]);
  });
  it('conserva el orden y agrega solo los nuevos', () => {
    const r = unirMensajes([{ id: 1 }, { id: 2 }], [{ id: 2 }, { id: 3 }]);
    expect(r.map((m) => m.id)).toEqual([1, 2, 3]);
  });
});
