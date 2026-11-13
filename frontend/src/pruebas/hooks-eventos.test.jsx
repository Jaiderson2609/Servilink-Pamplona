import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, renderHook } from '@testing-library/react';
import { WebSocketFalso } from './setup.js';
import { detenerEventos, iniciarEventos } from '../eventos.js';
import { useEventos, usePolling } from '../hooks.js';

const ultimo = () => WebSocketFalso.instancias.at(-1);
function canalVivo() {
  iniciarEventos('jwt');
  const s = ultimo();
  s.abrir();
  s.recibir({ tipo: 'listo' });
  return s;
}
const avanzar = (ms) => act(async () => { await vi.advanceTimersByTimeAsync(ms); });

beforeEach(() => { vi.useFakeTimers(); });
afterEach(() => { detenerEventos(); vi.useRealTimers(); });

describe('usePolling con eventos en tiempo real', () => {
  const montar = (traer, ms = 6000) =>
    renderHook(() => usePolling(traer, ms, [], (e) => e.tipo === 'solicitud' && e.id === 42));

  it('un evento de su solicitud recarga; el de otra solicitud no', async () => {
    const traer = vi.fn().mockResolvedValue({ n: 1 });
    montar(traer);
    await avanzar(0);
    expect(traer).toHaveBeenCalledTimes(1);
    const s = canalVivo();
    act(() => s.recibir({ tipo: 'solicitud', id: 99 }));
    await avanzar(500);
    expect(traer).toHaveBeenCalledTimes(1);
    act(() => s.recibir({ tipo: 'solicitud', id: 42 }));
    await avanzar(500);
    expect(traer).toHaveBeenCalledTimes(2);
  });

  it('varios eventos seguidos se atienden con una sola consulta', async () => {
    const traer = vi.fn().mockResolvedValue({ n: 1 });
    montar(traer);
    await avanzar(0);
    const s = canalVivo();
    act(() => { s.recibir({ tipo: 'solicitud', id: 42 }); s.recibir({ tipo: 'solicitud', id: 42 }); s.recibir({ tipo: 'solicitud', id: 42 }); });
    await avanzar(500);
    expect(traer).toHaveBeenCalledTimes(2);
  });

  it('con el canal vivo el sondeo baja a una consulta cada 30 segundos', async () => {
    const traer = vi.fn().mockResolvedValue({ n: 1 });
    montar(traer);
    await avanzar(0);
    canalVivo();
    await avanzar(24000); // sin el canal habría sido una consulta cada 6 s (4 más)
    expect(traer).toHaveBeenCalledTimes(1);
    await avanzar(12000);
    expect(traer).toHaveBeenCalledTimes(2);
  });

  it('sin el canal el sondeo sigue a su ritmo normal', async () => {
    const traer = vi.fn().mockResolvedValue({ n: 1 });
    montar(traer);
    await avanzar(0);
    await avanzar(18000);
    expect(traer).toHaveBeenCalledTimes(4); // inicial + a los 6, 12 y 18 s
  });

  it('si el canal se cae vuelve al ritmo normal y al reconectar recarga lo que pudo perderse', async () => {
    const traer = vi.fn().mockResolvedValue({ n: 1 });
    montar(traer);
    await avanzar(0);
    const s = canalVivo();
    s.caer();
    await avanzar(6000);
    expect(traer.mock.calls.length).toBeGreaterThanOrEqual(2); // sondeo normal otra vez
    const antes = traer.mock.calls.length;
    await avanzar(2000); // reconexión
    const nuevo = ultimo();
    nuevo.abrir();
    act(() => nuevo.recibir({ tipo: 'listo' }));
    await avanzar(500);
    expect(traer.mock.calls.length).toBeGreaterThan(antes);
  });

  it('sin función de escucha no se suscribe a nada y se comporta como antes', async () => {
    const traer = vi.fn().mockResolvedValue({ n: 1 });
    renderHook(() => usePolling(traer, 6000, []));
    await avanzar(0);
    const s = canalVivo();
    act(() => s.recibir({ tipo: 'solicitud', id: 42 }));
    await avanzar(500);
    expect(traer).toHaveBeenCalledTimes(1);
    await avanzar(6000);
    expect(traer).toHaveBeenCalledTimes(2);
  });

  it('al desmontar deja de escuchar', async () => {
    const traer = vi.fn().mockResolvedValue({ n: 1 });
    const { unmount } = montar(traer);
    await avanzar(0);
    const s = canalVivo();
    unmount();
    act(() => s.recibir({ tipo: 'solicitud', id: 42 }));
    await avanzar(500);
    expect(traer).toHaveBeenCalledTimes(1);
  });
});

describe('useEventos', () => {
  it('llama a la acción con los eventos que pasan el filtro y siempre con "reconectado"', async () => {
    const accion = vi.fn();
    renderHook(() => useEventos((e) => e.tipo === 'chat' && e.id === 5, accion));
    const s = canalVivo();
    act(() => { s.recibir({ tipo: 'chat', id: 6 }); s.recibir({ tipo: 'chat', id: 5 }); s.recibir({ tipo: 'solicitud', id: 5 }); });
    expect(accion).toHaveBeenCalledTimes(1);
    s.caer();
    await avanzar(1400);
    const nuevo = ultimo();
    nuevo.abrir();
    act(() => nuevo.recibir({ tipo: 'listo' }));
    expect(accion).toHaveBeenCalledTimes(2);
    expect(accion.mock.calls[1][0]).toEqual({ tipo: 'reconectado' });
  });
});
