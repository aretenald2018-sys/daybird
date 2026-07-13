import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';
import { fileURLToPath } from 'node:url';

export default defineConfig(({ command, mode }) => {
  const isAndroid = mode === 'android';
  const base = command === 'serve' ? '/' : isAndroid ? './' : '/daybird/';

  return {
    base,
    resolve: isAndroid ? {
      alias: {
        'virtual:pwa-register': fileURLToPath(new URL('./src/lib/pwa-stub.ts', import.meta.url))
      }
    } : undefined,
    plugins: [
      react(),
      ...(!isAndroid ? VitePWA({
        registerType: 'prompt',
        includeAssets: ['icons/daybird-192.png', 'icons/daybird-512.png', 'og.png'],
        manifest: {
          name: 'DayBird — 하루를 한눈에',
          short_name: 'DayBird',
          description: '빠른 시간 블록과 7일 버드뷰를 갖춘 로컬 우선 플래너',
          theme_color: '#f2f2f7',
          background_color: '#f2f2f7',
          display: 'standalone',
          orientation: 'portrait-primary',
          start_url: '/daybird/',
          scope: '/daybird/',
          lang: 'ko-KR',
          categories: ['productivity', 'utilities'],
          icons: [
            { src: '/daybird/icons/daybird-192.png', sizes: '192x192', type: 'image/png' },
            { src: '/daybird/icons/daybird-512.png', sizes: '512x512', type: 'image/png' },
            { src: '/daybird/icons/daybird-512-maskable.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' }
          ]
        },
        workbox: {
          navigateFallback: '/daybird/index.html',
          globPatterns: ['**/*.{js,css,html,png,woff2,json}'],
          cleanupOutdatedCaches: true
        }
      }) : [])
    ],
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
      css: true
    }
  };
});
