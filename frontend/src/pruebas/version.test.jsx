import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import Version from '../Version.jsx';

function ponerScript(ruta) {
  const s = document.createElement('script');
  s.setAttribute('src', ruta);
  document.head.appendChild(s);
  return s;
}

describe('Version', () => {
  let script;
  beforeEach(() => { script = ponerScript('/assets/index-abc123.js'); });
  afterEach(() => { script.remove(); vi.unstubAllGlobals(); });

  const volver = async () => { await act(async () => { document.dispatchEvent(new Event('visibilitychange')); }); };

  it('ofrece actualizar cuando el sitio publicó un archivo principal distinto', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ text: async () => '<script src="/assets/index-zzz999.js"></script>' })));
    render(<Version />);
    await volver();
    expect(await screen.findByText(/versión nueva/i)).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Actualizar' })).toBeTruthy();
  });

  it('no dice nada si la versión es la misma', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => ({ text: async () => '<script src="/assets/index-abc123.js"></script>' })));
    render(<Version />);
    await volver();
    expect(screen.queryByText(/versión nueva/i)).toBeNull();
  });

  it('pide la página con un parámetro distinto cada vez para que el service worker no responda su copia vieja', async () => {
    const pedir = vi.fn(async () => ({ text: async () => '<script src="/assets/index-abc123.js"></script>' }));
    vi.stubGlobal('fetch', pedir);
    render(<Version />);
    await volver();
    expect(pedir).toHaveBeenCalledTimes(1);
    expect(pedir.mock.calls[0][0]).toMatch(/^\/index\.html\?v=\d+$/);
  });

  it('sin red tampoco molesta', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('sin red'); }));
    render(<Version />);
    await volver();
    expect(screen.queryByText(/versión nueva/i)).toBeNull();
  });
});
