import '@testing-library/jest-dom/vitest';
import { afterEach, vi } from 'vitest';
import { cleanup } from '@testing-library/react';

// jsdom no implementa <dialog>.showModal(); se simula lo mínimo para probar los diálogos.
if (typeof HTMLDialogElement !== 'undefined') {
  HTMLDialogElement.prototype.showModal ??= function showModal() { this.setAttribute('open', ''); };
  HTMLDialogElement.prototype.close ??= function close() { this.removeAttribute('open'); };
}

afterEach(() => { cleanup(); vi.restoreAllMocks(); try { sessionStorage.clear(); } catch { /* nada */ } window.location.hash = ''; });
