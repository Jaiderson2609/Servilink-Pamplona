import { afterEach, describe, expect, it, vi } from 'vitest';
import { MAX_BYTES, prepararFoto } from '../imagenes.js';

const archivo = (bytes, nombre = 'casa.jpg', tipo = 'image/jpeg') => new File([new Uint8Array(bytes)], nombre, { type: tipo });

afterEach(() => { delete globalThis.createImageBitmap; vi.restoreAllMocks(); });

function simularCanvas(tamanosPorCalidad) {
  const dibujado = [];
  vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue({
    fillRect: vi.fn(), drawImage: (...a) => dibujado.push(a), set fillStyle(v) { /* nada */ },
  });
  const calidades = [];
  vi.spyOn(HTMLCanvasElement.prototype, 'toBlob').mockImplementation(function toBlob(cb, tipo, calidad) {
    calidades.push(calidad);
    cb(new Blob([new Uint8Array(tamanosPorCalidad(calidad))], { type: tipo }));
  });
  return { dibujado, calidades };
}

describe('prepararFoto', () => {
  it('una foto que ya cabe en 2 MB no se toca', async () => {
    const f = archivo(1000);
    expect(await prepararFoto(f)).toBe(f);
  });

  it('una foto grande se reduce a JPEG de máximo 1600 px y menos de 2 MB', async () => {
    globalThis.createImageBitmap = vi.fn(async () => ({ width: 4000, height: 3000, close: vi.fn() }));
    const { dibujado } = simularCanvas(() => 500_000);
    const grande = archivo(MAX_BYTES + 500_000, 'IMG_2031.heic', 'image/jpeg');
    const r = await prepararFoto(grande);
    expect(r).not.toBe(grande);
    expect(r.type).toBe('image/jpeg');
    expect(r.name).toBe('IMG_2031.jpg');
    expect(r.size).toBeLessThanOrEqual(MAX_BYTES);
    expect(dibujado[0].slice(3)).toEqual([1600, 1200]); // conserva la proporción 4:3
  });

  it('baja la calidad hasta que cabe', async () => {
    globalThis.createImageBitmap = vi.fn(async () => ({ width: 1000, height: 1000 }));
    const { calidades } = simularCanvas((q) => (q > 0.7 ? MAX_BYTES + 1 : 900_000));
    const r = await prepararFoto(archivo(MAX_BYTES + 10));
    expect(calidades).toEqual([0.85, 0.75, 0.65]);
    expect(r.size).toBe(900_000);
  });

  it('si el navegador no puede reducirla devuelve la original para que se aplique el límite', async () => {
    const grande = archivo(MAX_BYTES + 1);
    expect(await prepararFoto(grande)).toBe(grande); // jsdom no tiene createImageBitmap
    globalThis.createImageBitmap = vi.fn(async () => { throw new Error('formato no soportado'); });
    expect(await prepararFoto(grande)).toBe(grande);
  });

  it('una imagen que no baja de 2 MB ni con la menor calidad se devuelve sin cambios', async () => {
    globalThis.createImageBitmap = vi.fn(async () => ({ width: 100, height: 100 }));
    simularCanvas(() => MAX_BYTES + 1);
    const grande = archivo(MAX_BYTES + 1);
    expect(await prepararFoto(grande)).toBe(grande);
  });
});
