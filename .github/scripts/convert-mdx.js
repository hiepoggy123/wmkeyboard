const fs = require('fs');
const path = require('path');

const inputDir = process.argv[2];
const outputDir = process.argv[3];

if (!inputDir || !outputDir) {
    console.error('Usage: node convert-mdx.js <input-dir> <output-dir>');
    process.exit(1);
}

if (!fs.existsSync(outputDir)) {
    fs.mkdirSync(outputDir, { recursive: true });
}

const repo = process.env.GITHUB_REPOSITORY || 'wasi-master/wmkeyboard';
const wikiBase = `/${repo}/wiki/`;

// Pass 1: Gather all files
let allFiles = [];
let titleCounts = {};

function walkDir(dir, callback) {
    fs.readdirSync(dir).forEach(f => {
        let dirPath = path.join(dir, f);
        let isDirectory = fs.statSync(dirPath).isDirectory();
        isDirectory ? walkDir(dirPath, callback) : callback(dirPath);
    });
}

walkDir(inputDir, (filePath) => {
    if (filePath.endsWith('.mdx') || filePath.endsWith('.md')) {
        let content = fs.readFileSync(filePath, 'utf8');
        let title = '';
        let order = 999;
        
        if (content.startsWith('---')) {
            const endOfFrontmatter = content.indexOf('---', 3);
            if (endOfFrontmatter !== -1) {
                const frontmatter = content.substring(3, endOfFrontmatter);
                const titleMatch = frontmatter.match(/title:\s*['"]?(.*?)['"]?\n/);
                if (titleMatch) title = titleMatch[1];
                const orderMatch = frontmatter.match(/sidebar:\s*\n\s*order:\s*(\d+)/);
                if (orderMatch) order = parseInt(orderMatch[1], 10);
                content = content.substring(endOfFrontmatter + 3).trim();
            }
        }

        const relativePath = path.relative(inputDir, filePath);
        if (!title) {
            title = path.basename(relativePath, path.extname(relativePath));
        }

        // Keep track of counts for deduplication
        if (relativePath !== 'index.mdx' && relativePath !== 'index.md') {
            titleCounts[title] = (titleCounts[title] || 0) + 1;
        }

        const parts = relativePath.split(path.sep);
        const category = parts.length > 1 ? parts[0] : 'root';

        allFiles.push({
            filePath,
            relativePath,
            category,
            originalTitle: title,
            order,
            content
        });
    }
});

// Parse astro.config.mjs to get the correct category labels
let categoryLabels = {};
let categoryOrder = [];
try {
    const astroConfigPath = path.join(inputDir, '../../../astro.config.mjs');
    const astroConfig = fs.readFileSync(astroConfigPath, 'utf8');
    const blocks = astroConfig.split(/label:\s*'/).slice(1);
    for (const block of blocks) {
        const label = block.substring(0, block.indexOf("'"));
        const dirMatch = block.substring(0, 200).match(/directory:\s*'([^']+)'/);
        if (dirMatch) {
            categoryOrder.push(dirMatch[1]);
            categoryLabels[dirMatch[1]] = label;
        }
    }
} catch (e) {
    console.error("Could not parse astro.config.mjs for sidebar order", e);
}

// Pass 2: Calculate filenames and build link map
let linkMap = {}; // Maps original route (e.g. '/start/tour/') to new wiki link

for (let file of allFiles) {
    let displayTitle = file.originalTitle;
    let outFileName;
    
    if (file.relativePath === 'index.mdx' || file.relativePath === 'index.md') {
        outFileName = 'Home';
        displayTitle = 'Home';
    } else {
        if (titleCounts[file.originalTitle] > 1) {
            const catLabel = categoryLabels[file.category] || (file.category.charAt(0).toUpperCase() + file.category.slice(1));
            displayTitle = `${catLabel} - ${file.originalTitle}`;
        }
        
        // Convert to filename:
        // 1. Replace standard hyphens with Unicode hyphens to preserve them in GitHub Wiki
        let name = displayTitle.replace(/-/g, '‐');
        // 2. Replace spaces with standard hyphens (GitHub Wiki will turn them back to spaces)
        name = name.replace(/ /g, '-');
        // 3. Remove invalid filename characters
        name = name.replace(/[^\w\‐\-&,'+()]/g, ''); // keep alphanumeric, Unicode hyphen, standard hyphen, ampersand, comma, apostrophe, plus, parens
        outFileName = name;
    }
    
    file.outFileName = outFileName + '.md';
    file.displayTitle = displayTitle;
    file.absoluteLink = `${wikiBase}${outFileName}`;
    
    // Calculate the original internal link this file was known as
    let originalRoute = file.relativePath.replace(/\.mdx?$/, '');
    if (originalRoute === 'index') {
        originalRoute = '';
    } else if (originalRoute.endsWith('/index')) {
        originalRoute = originalRoute.slice(0, -6);
    }
    linkMap['/' + originalRoute] = file.absoluteLink;
    linkMap['/' + originalRoute + '/'] = file.absoluteLink;
}

// Pass 3: Process content and write files
let pages = [];

for (let file of allFiles) {
    let content = file.content;

    // Remove MDX block comments
    content = content.replace(/\{\/\*[\s\S]*?\*\/\}/g, '');
    
    // Replace known variables from index.mdx
    content = content.replace(/\{languages\.length\}/g, '352');
    content = content.replace(/\{wordlists\.length\}/g, '333');

    // Remove imports
    content = content.replace(/^import\s+.*?from\s+['"].*?['"];?\n/gm, '');

    function getMappedLink(href) {
        if (href.startsWith('http')) return href;
        if (href.startsWith('mailto:')) return href;
        if (href.startsWith('#')) return href;
        let hashIndex = href.indexOf('#');
        let hash = hashIndex !== -1 ? href.substring(hashIndex) : '';
        let clean = hashIndex !== -1 ? href.substring(0, hashIndex) : href;
        let mapped = linkMap[clean];
        if (mapped) return mapped + hash;
        // Fallback if not found in map
        let fallback = clean.replace(/^\//, '').replace(/\/$/, '');
        if (fallback === '') fallback = 'Home';
        return `${wikiBase}${fallback}${hash}`;
    }

    // For all Astro block components (e.g. FeatureRow, PhoneFrame, Fragment, CardGrid) AND HTML layout divs,
    // strip the tags but preserve and unindent their body content so it doesn't render as a code block.
    let previousContent;
    do {
        previousContent = content;
        content = content.replace(/^[ \t]*<([A-Z][a-zA-Z0-9]*|div)[^>]*>\s*([\s\S]*?)\s*<\/\1>[ \t]*\n?/gm, (match, tag, body) => {
            // Leave tags that we have specific replacements for
            if (['Card', 'LinkCard', 'LinkButton', 'SettingsPath', 'KeyCap'].includes(tag)) {
                return match;
            }
            return `${body.replace(/^[ \t]+/gm, '')}\n`;
        });
    } while (content !== previousContent);

    // Convert <Card> to headings
    content = content.replace(/<Card\s+title="([^"]+)"[^>]*>\s*([\s\S]*?)\s*<\/Card>/gm, (match, title, body) => `### ${title}\n\n${body.replace(/^[ \t]+/gm, '')}\n`);

    // Replace specific known tags to readable text
    content = content.replace(/<SettingsPath\s+path="([^"]+)"\s*\/?>/g, '**$1**');
    content = content.replace(/<KeyCap\s+(?:key|letter)="([^"]+)"\s*\/?>/g, '`$1`');
    content = content.replace(/<LinkCard\s+title="([^"]+)"\s+href="([^"]+)"\s*\/?>/gm, (match, title, href) => `- [**${title}**](${getMappedLink(href)})`);
    content = content.replace(/<LinkCard\s+title="([^"]+)"\s+description="([^"]+)"\s+href="([^"]+)"\s*\/?>/gm, (match, title, desc, href) => `- [**${title}**](${getMappedLink(href)})\n  ${desc}`);
    content = content.replace(/<LinkButton\s+href="([^"]+)"[^>]*>([\s\S]*?)<\/LinkButton>/gm, (match, href, text) => `[**${text}**](${getMappedLink(href)})`);

    // Remove any remaining self-closing or inline Astro components
    content = content.replace(/<\/?([A-Z][a-zA-Z0-9]*)[^>]*>/g, '');

    // Strip HTML paragraph wrappers that prevent markdown rendering inside them
    content = content.replace(/<p(?:\s+[^>]*)?>/g, '');
    content = content.replace(/<\/p>/g, '\n');
    
    // Rewrite image links
    content = content.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, (match, alt, href) => {
        let newHref = href;
        if (href.startsWith('@assets/')) {
            newHref = href.replace('@assets/', 'assets/');
        } else if (href.includes('assets/')) {
            newHref = href.substring(href.indexOf('assets/'));
        }
        return `![${alt}](${newHref})`;
    });

    // Rewrite standard root-relative markdown links to wiki absolute paths
    content = content.replace(/(?<!!)\[([^\]]+)\]\(\/([^)]+)\)/g, (match, text, href) => {
        if (href.startsWith(`${repo}/wiki/`)) return match; // prevent double prefixing
        return `[${text}](${getMappedLink('/' + href)})`;
    });

    // Rewrite root-relative HTML links
    content = content.replace(/href="\/([^"]+)"/g, (match, href) => `href="${getMappedLink('/' + href)}"`);

    // Parse admonitions into <details>
    content = content.replace(/^:::(\w+)(?:\[(.*?)\])?\s*\n([\s\S]*?)\n^:::/gm, (match, type, title, body) => {
        const summaryTitle = title ? title : (type.charAt(0).toUpperCase() + type.slice(1));
        return `<details>\n<summary><b>${summaryTitle}</b></summary>\n\n${body.trim()}\n\n</details>`;
    });

    // Replace h2 tags with markdown headings
    content = content.replace(/<h2[^>]*>([\s\S]*?)<\/h2>/g, '## $1');

    const fullOutPath = path.join(outputDir, file.outFileName);
    fs.writeFileSync(fullOutPath, content);
    
    pages.push({
        title: file.displayTitle,
        order: file.order,
        category: file.category,
        link: file.absoluteLink
    });
}

// Generate _Sidebar.md
let sidebarContent = `* [Home](${wikiBase}Home)\n`;

let categories = [...new Set(pages.map(p => p.category))].filter(c => c !== 'root');

categories.sort((a, b) => {
    const indexA = categoryOrder.indexOf(a);
    const indexB = categoryOrder.indexOf(b);
    if (indexA !== -1 && indexB !== -1) return indexA - indexB;
    if (indexA !== -1) return -1;
    if (indexB !== -1) return 1;
    return a.localeCompare(b);
});

for (const cat of categories) {
    const catName = categoryLabels[cat] || (cat.charAt(0).toUpperCase() + cat.slice(1));
    sidebarContent += `* **${catName}**\n`;
    
    const catPages = pages.filter(p => p.category === cat).sort((a, b) => a.order - b.order);
    for (const page of catPages) {
        sidebarContent += `  * [${page.title}](${page.link})\n`;
    }
}

const rootPages = pages.filter(p => p.category === 'root' && !p.link.endsWith('/Home')).sort((a, b) => a.order - b.order);
for (const page of rootPages) {
    sidebarContent += `* [${page.title}](${page.link})\n`;
}

fs.writeFileSync(path.join(outputDir, '_Sidebar.md'), sidebarContent);

// Copy assets directory if it exists
const assetsSrcDir = path.join(inputDir, '../../assets');
const assetsDestDir = path.join(outputDir, 'assets');
if (fs.existsSync(assetsSrcDir)) {
    fs.cpSync(assetsSrcDir, assetsDestDir, { recursive: true });
}
