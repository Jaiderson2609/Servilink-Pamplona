import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': 'http://localhost:8080', '/ws': { target: 'http://localhost:8080', ws: true } } },
  build: { outDir: 'dist', sourcemap: false },
  test: { environment: 'jsdom', globals: true, setupFiles: './src/pruebas/setup.js', css: false },
});
