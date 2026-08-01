import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import tailwindcss from '@tailwindcss/vite';

// PRD §8: Vite build output feeds qa-forge-bootstrap's static resources, served from '/'.
// The REST API lives under /api/v1/*, so the dev server proxies it to the local backend.
// Test config lives in vitest.config.ts (merged via mergeConfig) rather than a `test` key
// here — vitest bundles its own Vite version, and the two Plugin types aren't structurally
// identical, so a `test` key on this file's defineConfig fails to typecheck.
export default defineConfig({
  plugins: [react(), tailwindcss()],
  base: '/',
  build: {
    outDir: '../qa-forge-bootstrap/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/actuator': 'http://localhost:8080',
    },
  },
});
