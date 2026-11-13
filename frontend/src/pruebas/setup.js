import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';
import { detenerEventos } from '../eventos.js';

// jsdom no implementa <dialog>.showModal(); se simula lo mínimo para probar los diálogos.
if (typeof HTMLDialogElement !== 'undefined') {
  HTMLDialogElement.prototype.showModal ??= function showModal() { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close ??= function close() { this.removeAttribute('open'); };
}

/**
 * WebSocket falso y controlable: ninguna prueba abre una conexión de verdad. Cada prueba decide cuándo se abre
 * (abrir), qué manda el servidor (recibir) y cuándo se cae (caer); lo que envió el cliente queda en `enviados`.
 */
export class WebSocketFalso {
  static instancias = [];
  constructor(url) {
    this.url = url; this.readyState = 0; this.enviados = [];
    WebSocketFalso.instancias.push(this);
  }
  send(dato) { this.enviados.push(dato); }
  close() { this.readyState = 3; this.onclose?.(); }
  abrir() { this.readyState = 1; this.onopen?.(); }
  recibir(objeto) { this.onmessage?.({ data: typeof objeto === 'string' ? objeto : JSON.stringify(objeto) }); }
  caer() { this.readyState = 3; this.onclose?.(); }
}
globalThis.WebSocket = WebSocketFalso;

afterEach(() => { detenerEventos(); WebSocketFalso.instancias.length = 0; cleanup(); vi.restoreAllMocks(); try { sessionStorage.clear(); } catch { /* nada */ } window.location.hash = ''; });
