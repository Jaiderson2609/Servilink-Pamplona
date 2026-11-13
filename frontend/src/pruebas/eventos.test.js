import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { WebSocketFalso } from './setup.js';
import { RESPALDO_MS, detenerEventos, eventosConectados, iniciarEventos, suscribirEstadoEventos, suscribirEventos, urlEventos } from '../eventos.js';

const ultimo = () => WebSocketFalso.instancias.at(-1);
function conectarYAutenticar(token = 'jwt-1') {
  iniciarEventos(token);
  const s = ultimo();
  s.abrir();
  s.recibir({ tipo: 'listo' });
  return s;
}

beforeEach(() => { vi.useFakeTimers(); });
afterEach(() => { detenerEventos(); vi.useRealTimers(); });

describe('canal de eventos en tiempo real', () => {
  it('al abrir se autentica con el primer mensaje y el token no viaja en la URL', () => {
    iniciarEventos('jwt-secreto');
    const s = ultimo();
    s.abrir();
    expect(JSON.parse(s.enviados[0])).toEqual({ tipo: 'auth', token: 'jwt-secreto' });
    expect(s.url).not.toContain('jwt-secreto');
    expect(s.url).toMatch(/^wss?:\/\/.+\/ws\/v1\/eventos$/);
    expect(eventosConectados()).toBe(false); // todavía no responde "listo"
  });

  it('queda conectado al recibir "listo" y avisa del cambio de estado', () => {
    const estados = [];
    suscribirEstadoEventos((v) => estados.push(v));
    conectarYAutenticar();
    expect(eventosConectados()).toBe(true);
    expect(estados).toEqual([true]);
  });

  it('entrega los eventos a quien escucha y se puede dejar de escuchar', () => {
    const vistos = [];
    const baja = suscribirEventos((e) => vistos.push(e));
    const s = conectarYAutenticar();
    s.recibir({ tipo: 'solicitud', id: 42 });
    s.recibir({ tipo: 'abiertas' });
    baja();
    s.recibir({ tipo: 'chat', id: 42 });
    expect(vistos).toEqual([{ tipo: 'solicitud', id: 42 }, { tipo: 'abiertas' }]);
  });

  it('ignora el pong, el JSON roto y los mensajes sin tipo, y un oyente roto no afecta a los demás', () => {
    const vistos = [];
    suscribirEventos(() => { throw new Error('oyente roto'); });
    suscribirEventos((e) => vistos.push(e));
    const s = conectarYAutenticar();
    s.recibir({ tipo: 'pong' });
    s.recibir('esto no es json');
    s.recibir({ sinTipo: true });
    s.recibir({ tipo: 'chat', id: 7 });
    expect(vistos).toEqual([{ tipo: 'chat', id: 7 }]);
  });

  it('manda un latido cada 25 segundos mientras está conectado', () => {
    const s = conectarYAutenticar();
    vi.advanceTimersByTime(25000);
    vi.advanceTimersByTime(25000);
    expect(s.enviados.filter((m) => m === '{"tipo":"ping"}')).toHaveLength(2);
  });

  it('si se cae reconecta con espera creciente y, al volver, emite "reconectado"', () => {
    const vistos = [];
    suscribirEventos((e) => vistos.push(e.tipo));
    const s1 = conectarYAutenticar();
    s1.caer();
    expect(eventosConectados()).toBe(false);
    expect(WebSocketFalso.instancias).toHaveLength(1);
    vi.advanceTimersByTime(1400); // primera espera: 1 s más hasta 30 % de azar
    expect(WebSocketFalso.instancias).toHaveLength(2);
    const s2 = ultimo();
    s2.abrir();
    s2.caer(); // falla de nuevo antes de autenticarse: la espera se duplica
    vi.advanceTimersByTime(1400);
    expect(WebSocketFalso.instancias).toHaveLength(2); // 2 s todavía no pasaron
    vi.advanceTimersByTime(1500);
    expect(WebSocketFalso.instancias).toHaveLength(3);
    const s3 = ultimo();
    s3.abrir();
    s3.recibir({ tipo: 'listo' });
    expect(eventosConectados()).toBe(true);
    expect(vistos).toEqual(['reconectado']);
  });

  it('la primera conexión exitosa no emite "reconectado"', () => {
    const vistos = [];
    suscribirEventos((e) => vistos.push(e.tipo));
    conectarYAutenticar();
    expect(vistos).toEqual([]);
  });

  it('la espera de reconexión no pasa de 30 segundos', () => {
    iniciarEventos('jwt');
    for (let i = 0; i < 8; i++) {
      ultimo().caer();
      vi.advanceTimersByTime(40000);
    }
    const antes = WebSocketFalso.instancias.length;
    ultimo().caer();
    vi.advanceTimersByTime(40000);
    expect(WebSocketFalso.instancias.length).toBe(antes + 1);
  });

  it('al detenerlo cierra la conexión y no vuelve a conectar', () => {
    const s = conectarYAutenticar();
    detenerEventos();
    expect(s.readyState).toBe(3);
    expect(eventosConectados()).toBe(false);
    vi.advanceTimersByTime(120000);
    expect(WebSocketFalso.instancias).toHaveLength(1);
  });

  it('iniciar dos veces con el mismo token no abre una segunda conexión', () => {
    iniciarEventos('igual');
    iniciarEventos('igual');
    expect(WebSocketFalso.instancias).toHaveLength(1);
    iniciarEventos('otro'); // un token nuevo (otra sesión) sí reemplaza la conexión
    expect(WebSocketFalso.instancias).toHaveLength(2);
    expect(WebSocketFalso.instancias[0].readyState).toBe(3);
  });

  it('el respaldo por sondeo es de 30 segundos', () => {
    expect(RESPALDO_MS).toBe(30000);
  });

  it('la URL usa wss con https y ws con http', () => {
    expect(urlEventos()).toBe(`${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/v1/eventos`);
  });
});
