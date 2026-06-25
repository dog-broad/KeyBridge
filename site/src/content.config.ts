import { defineCollection, z } from 'astro:content';
import { glob } from 'astro/loaders';

// Beginner-facing guides. Drop a new Markdown file into src/content/guides/
// and it is validated, listed, routed at /guides/<slug>, and added to the
// sitemap automatically — no code change required.
const guides = defineCollection({
  loader: glob({ pattern: '**/*.md', base: './src/content/guides' }),
  schema: z.object({
    title: z.string(),
    description: z.string(),
    // Lower sorts first in the index; gives a stable reading order.
    order: z.number().default(99),
    audience: z.string().default('New to KeyBridge'),
    minutes: z.number().default(3),
    updated: z.coerce.date().optional(),
  }),
});

// Future collections (changelog, blog, feature docs) register here alongside.
export const collections = { guides };
