/**
 * Generates public/og-card.png — the 1200×630 image every page links as its
 * `og:image` / `twitter:image` (see src/components/Head.astro).
 *
 * Run with `npm run og`. The output is committed, so this only needs re-running
 * when the logo, title or tagline change.
 */

import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

import { SITE_TITLE } from '../src/site.mjs';

const root = new URL('../', import.meta.url);
const logoPath = fileURLToPath(new URL('src/assets/logo-mark.png', root));
const outPath = fileURLToPath(new URL('public/og-card.png', root));

const WIDTH = 1200;
const HEIGHT = 630;
const LOGO = 260;

// Brand stops, same as src/styles/custom.css.
const BLUE = '#4c8df6';
const VIOLET = '#8b5cf6';
const NAVY = '#17182b';

const TAGLINE = 'Privacy-first Android keyboard';
const POINTS = ['350+ languages', 'Offline intelligence', '60+ tools', 'Themes & addons'];

/** SVG is XML — bare `&` in a tagline would blow up the parse. */
const xml = (text) =>
	text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const background = Buffer.from(`
<svg xmlns="http://www.w3.org/2000/svg" width="${WIDTH}" height="${HEIGHT}">
	<defs>
		<linearGradient id="brand" x1="0" y1="0" x2="1" y2="1">
			<stop offset="0%" stop-color="${BLUE}"/>
			<stop offset="100%" stop-color="${VIOLET}"/>
		</linearGradient>
		<radialGradient id="glow" cx="0.5" cy="0.5" r="0.5">
			<stop offset="0%" stop-color="${VIOLET}" stop-opacity="0.55"/>
			<stop offset="100%" stop-color="${VIOLET}" stop-opacity="0"/>
		</radialGradient>
	</defs>

	<rect width="${WIDTH}" height="${HEIGHT}" fill="${NAVY}"/>
	<circle cx="215" cy="250" r="330" fill="url(#glow)"/>
	<rect x="0" y="${HEIGHT - 10}" width="${WIDTH}" height="10" fill="url(#brand)"/>

	<g font-family="Inter, 'Helvetica Neue', Helvetica, Arial, sans-serif">
		<text x="370" y="245" font-size="86" font-weight="700" fill="#ffffff">${xml(SITE_TITLE)}</text>
		<text x="370" y="310" font-size="38" font-weight="500" fill="#c2c4d7">${xml(TAGLINE)}</text>
		<g font-size="27" font-weight="500" fill="#8b8fae">
			${POINTS.map(
				(point, i) =>
					`<text x="${370 + (i % 2) * 300}" y="${400 + Math.floor(i / 2) * 48}">• ${xml(point)}</text>`
			).join('\n\t\t\t')}
		</g>
	</g>
</svg>`);

const logo = await sharp(logoPath).resize(LOGO, LOGO).png().toBuffer();

await sharp(background)
	.composite([{ input: logo, left: 85, top: Math.round((HEIGHT - LOGO) / 2) - 20 }])
	.png()
	.toFile(outPath);

console.log(`wrote ${outPath} (${WIDTH}×${HEIGHT})`);
