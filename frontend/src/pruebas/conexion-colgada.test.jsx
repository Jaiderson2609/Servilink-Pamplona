import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { api } from '../api.js';
import { usePolling } from '../hooks.js';

const avanzar = (ms) => act(async () => { await vi.advanceTimersByTimeAsync(ms); });

beforeEach(() => { vi.useFakeTimers(); });
afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); });

describe('conexión colgada', () => {
  it('una petición que nunca responde termina con error de conexión a los 20 s', async () => {
    vi.stubGlobal('fetch', vi.fn((_u, o) => new Promise((_ok, no) => o.signal.addEventListener('abort', () => no(new Error('abortada'))))));
    let error;
    const p = api('/x').catch((e) => { error = e; });
    await avanzar(20001);
    await p;
    expect(error.code).toBe('NETWORK');
  });

  it('al volver a la pantalla se actualiza al momento', async () => {
    const traer = vi.fn().mockResolvedValue({ n: 1 });
    renderHook(() => usePolling(traer, 60000));
    await avanzar(0);
    expect(traer).toHaveBeenCalledTimes(1);
    act(() => { document.dispatchEvent(new Event('visibilitychange')); });
    await avanzar(0);
    expect(traer).toHaveBeenCalledTimes(2);
  });

  it('un fallo pasajero no reemplaza los datos que ya se veían', async () => {
    const traer = vi.fn().mockResolvedValueOnce({ n: 1 }).mockRejectedValueOnce({ message: 'x' });
    const { result } = renderHook(() => usePolling(traer, 1000));
    await avanzar(0);
    await avanzar(1000);
    expect(result.current.datos).toEqual({ n: 1 });
    expect(result.current.error).toBeNull();
  });
});
