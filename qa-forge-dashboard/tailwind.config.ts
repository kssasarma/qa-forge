import type { Config } from 'tailwindcss';

// Tailwind 4 primarily configures via the `@theme` block in src/index.css; this file exists
// for the `content` glob PRD §8 lists it for, plus the categorical/status token names the
// dashboard's chart components reference (values are set in index.css to keep light/dark in
// one place — see dataviz skill's reference palette).
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
} satisfies Config;
