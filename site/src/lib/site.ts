// Single source of truth for site-wide data. Adding a nav link, a download
// target, a feature card, or a step is a one-line edit here — pages and
// components render from these arrays, so the structure scales without
// touching markup.

export const SITE = {
  name: 'KeyBridge',
  tagline: 'Your phone is now your PC keyboard.',
  description:
    'KeyBridge turns your phone into a wireless keyboard, media remote, and shortcut pad for your computer. Scan a QR code to pair, then type and control your PC from across the room. No account, no cloud.',
  origin: 'https://dog-broad.github.io',
  // Keep in sync with `base` in astro.config.mjs.
  basePath: '/KeyBridge',
} as const;

export const LINKS = {
  appRepo: 'https://github.com/dog-broad/KeyBridge',
  hostRepo: 'https://github.com/dog-broad/keybridge-server',
  appRelease: 'https://github.com/dog-broad/KeyBridge/releases/latest',
  hostRelease: 'https://github.com/dog-broad/keybridge-server/releases/latest',
} as const;

export interface NavItem {
  label: string;
  href: string;
  external?: boolean;
}

export const NAV: NavItem[] = [
  { label: 'How it works', href: '#how' },
  { label: 'Why it’s different', href: '#why' },
  { label: 'Guides', href: '/guides' },
  { label: 'GitHub', href: LINKS.appRepo, external: true },
];

export interface Download {
  name: string;
  platform: string;
  blurb: string;
  href: string;
  cta: string;
  source: string;
}

export const DOWNLOADS: Download[] = [
  {
    name: 'KeyBridge app',
    platform: 'Android',
    blurb:
      'The phone client. Scan, pair, and start typing on your PC. Installs from a signed APK.',
    href: LINKS.appRelease,
    cta: 'Download the app',
    source: LINKS.appRepo,
  },
  {
    name: 'KeyBridge Server',
    platform: 'Windows',
    blurb:
      'The desktop host that receives your input and types it. Double-click installer — no terminal needed.',
    href: LINKS.hostRelease,
    cta: 'Get the desktop host',
    source: LINKS.hostRepo,
  },
];

export interface Feature {
  title: string;
  body: string;
}

export const FEATURES: Feature[] = [
  {
    title: 'Confirmed delivery',
    body: 'Input is never fire-and-forget. The app waits for the PC to confirm every chunk before it treats your text as sent — so nothing silently vanishes, even on a flaky network.',
  },
  {
    title: 'Pairing with no shared secret',
    body: 'There is no hardcoded password. The pairing key lives only in the QR code on your screen and never crosses the network. Re-pair anytime by restarting the host.',
  },
  {
    title: 'Per-session encryption',
    body: 'Every connection derives its own key and is encrypted with AES-256-GCM. A message that does not authenticate is dropped, not trusted. No cloud, no account, local network only.',
  },
];

export interface Step {
  n: string;
  title: string;
  body: string;
  image: 'pair' | 'deliver' | 'control';
  alt: string;
}

export const STEPS: Step[] = [
  {
    n: '01',
    title: 'Pair',
    body: 'Run the host on your PC and scan the QR code it shows. A fresh key is generated for the session — nothing types until your phone and PC are paired.',
    image: 'pair',
    alt: 'KeyBridge pairing screen detecting the desktop host',
  },
  {
    n: '02',
    title: 'Send',
    body: 'Type or paste anything. Long text streams in chunks with live progress, and your text only clears once the PC confirms it landed.',
    image: 'deliver',
    alt: 'Sending text with live per-chunk delivery progress',
  },
  {
    n: '03',
    title: 'Control',
    body: 'A full control grid: modifiers, navigation, function keys, media keys, and multi-key hotkeys — all sent as real keystrokes.',
    image: 'control',
    alt: 'Keyboard control grid with modifiers, media, and function keys',
  },
];

const trimmedBase = SITE.basePath.replace(/\/$/, '');

/** Prefix an internal path with the Pages base so links work under /KeyBridge. */
export function withBase(path = ''): string {
  if (/^https?:\/\//.test(path) || path.startsWith('#')) return path;
  const clean = path.replace(/^\//, '');
  return clean ? `${trimmedBase}/${clean}` : `${trimmedBase}/`;
}
