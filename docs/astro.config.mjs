// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import { unified } from '@astrojs/markdown-remark';
import starlightImageZoom from 'starlight-image-zoom';
import starlightLinksValidator from 'starlight-links-validator';

// Link validation is opt-in (`npm run check`) so half-written pages never
// block the dev loop. CI should run `npm run check`.
const plugins = [starlightImageZoom()];
if (process.env.CHECK_LINKS) {
	plugins.push(starlightLinksValidator({ errorOnRelativeLinks: true }));
}

export default defineConfig({
	// TODO: set the final URL before deploying (needed for correct og: tags & sitemap).
	site: 'https://wmkeyboard.pages.dev',
	markdown: {
		// starlight-image-zoom doesn't support Astro 7's Sätteri processor yet:
		// https://github.com/HiDeoo/starlight-image-zoom/issues/63
		processor: unified(),
	},
	integrations: [
		starlight({
			title: 'WM Keyboard',
			description:
				'A modern, privacy-first Android keyboard — offline intelligence, 350+ languages, themes, tools and an addon ecosystem.',
			logo: {
				src: './src/assets/logo-mark.png',
				alt: 'WM Keyboard',
			},
			favicon: '/favicon.png',
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/wasi-master/wmkeyboard' },
			],
			editLink: {
				// TODO: point at the real repo/branch once the docs land.
				baseUrl: 'https://github.com/wasi-master/wmkeyboard/edit/main/docs/',
			},
			lastUpdated: true,
			customCss: [
				'@fontsource-variable/inter',
				'@fontsource-variable/jetbrains-mono',
				'./src/styles/custom.css',
			],
			components: {
				// Room to grow: swap in overrides here (e.g. Hero, Footer) when needed.
			},
			plugins,
			sidebar: [
				{
					label: 'Getting started',
					items: [{ autogenerate: { directory: 'start' } }],
				},
				{
					label: 'Typing',
					items: [{ autogenerate: { directory: 'typing' } }],
				},
				{
					label: 'Languages',
					items: [{ autogenerate: { directory: 'languages' } }],
				},
				{
					label: 'Suggestions & correction',
					items: [{ autogenerate: { directory: 'smart' } }],
				},
				{
					label: 'Emoji & expression',
					items: [{ autogenerate: { directory: 'emoji' } }],
				},
				{
					label: 'Tools',
					items: [{ autogenerate: { directory: 'tools' } }],
				},
				{
					label: 'Themes & appearance',
					items: [{ autogenerate: { directory: 'themes' } }],
				},
				{
					label: 'Addons',
					items: [{ autogenerate: { directory: 'addons' } }],
				},
				{
					label: 'Plugins',
					badge: { text: 'Lua', variant: 'tip' },
					items: [{ autogenerate: { directory: 'plugins' } }],
				},
				{
					label: 'Privacy & security',
					items: [{ autogenerate: { directory: 'privacy' } }],
				},
				{
					label: 'Accessibility',
					items: [{ autogenerate: { directory: 'accessibility' } }],
				},
				{
					label: 'Reference',
					collapsed: true,
					items: [
						{ autogenerate: { directory: 'reference', collapsed: true } },
					],
				},
				{
					label: 'Development',
					collapsed: true,
					items: [{ autogenerate: { directory: 'development', collapsed: true } }],
				},
			],
		}),
	],
});
