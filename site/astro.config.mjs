import { defineConfig } from 'astro/config';
import sitemap from '@astrojs/sitemap';

// Served as a GitHub Pages project site at dog-broad.github.io/KeyBridge,
// so every internal link and asset must resolve under the /KeyBridge base.
export default defineConfig({
  site: 'https://dog-broad.github.io',
  base: '/KeyBridge',
  trailingSlash: 'ignore',
  integrations: [sitemap()],
});
