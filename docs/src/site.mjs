/**
 * Site-wide constants shared by `astro.config.mjs` and the components that need
 * them (`Head.astro` in particular). Keeping them here means the canonical URL,
 * title and description can't drift apart between the Starlight config and the
 * SEO tags built from it.
 */

/**
 * Deployed origin. Also what the sitemap and every absolute og: URL is built
 * from, and what the app's About screen opens (`DOCS_URL` in AboutScreens.kt).
 * Change it in both places or the in-app links point at the old host.
 */
export const SITE_URL = 'https://wmkeyboard.pages.dev';

export const SITE_TITLE = 'WM Keyboard';

export const SITE_DESCRIPTION =
	'A modern, privacy-first Android keyboard — offline intelligence, 350+ languages, themes, tools and an addon ecosystem.';

/** Social card. 1200×630, regenerate with `npm run og`. */
export const OG_IMAGE = '/og-card.png';
export const OG_IMAGE_ALT = `${SITE_TITLE}: a privacy-first Android keyboard with 350+ languages, offline intelligence, tools and themes.`;
