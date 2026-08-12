const fs = require('fs');
const path = require('path');

function processFile(filePath) {
    let content = fs.readFileSync(filePath, 'utf8');

    let title = "";
    let order = 999;
    
    // Extract frontmatter
    const frontmatterMatch = content.match(/^---\n([\s\S]*?)\n---/);
    if (frontmatterMatch) {
        const frontmatter = frontmatterMatch[1];
        const titleMatch = frontmatter.match(/^title:\s*(['"]?)(.*?)\1$/m);
        if (titleMatch) title = titleMatch[2];
        
        const orderMatch = frontmatter.match(/^\s*order:\s*(\d+)/m);
        if (orderMatch) order = parseInt(orderMatch[1], 10);
        
        // Remove frontmatter
        content = content.replace(/^---\n[\s\S]*?\n---(\n|$)/, '');
    }

    // Remove MDX block comments
    content = content.replace(/\{\/\*[\s\S]*?\*\/\}/g, '');
    
    // Replace known variables from index.mdx
    content = content.replace(/\{languages\.length\}/g, '352');
    content = content.replace(/\{wordlists\.length\}/g, '333');

    // Remove imports
    content = content.replace(/^import\s+.*?from\s+['"].*?['"];?\n/gm, '');

    // Replace specific known tags to readable text
    content = content.replace(/<SettingsPath\s+path="([^"]+)"\s*\/?>/g, '**$1**');
    content = content.replace(/<KeyCap\s+(?:key|letter)="([^"]+)"\s*\/?>/g, '`$1`');
    content = content.replace(/<LinkCard\s+title="([^"]+)"\s+href="([^"]+)"\s*\/?>/g, '[$1]($2)');
    content = content.replace(/<LinkCard\s+title="([^"]+)"\s+description="([^"]+)"\s+href="([^"]+)"\s*\/?>/g, '[$1]($3) - $2');
    content = content.replace(/<LinkButton\s+href="([^"]+)"[^>]*>([\s\S]*?)<\/LinkButton>/g, '[$2]($1)');
    
    // Strip layout tags but keep content
    content = content.replace(/<\/?(?:PhoneFrame|Steps|CardGrid|FileTree|Flavor|Since|LayoutExplorer|ThemePreview|GestureDemo|FilterTable|Card|Fragment|FeatureRow)[^>]*>/g, '');

    // Remove completely generic self-closing components we missed
    content = content.replace(/<[A-Z][a-zA-Z0-9]*\s+[^>]*\/>/g, '');

    // Remove leading tabs to prevent indented text from turning into Markdown code blocks
    content = content.replace(/^\t+/gm, '');

    // Clean up empty lines created by tag removal
    content = content.replace(/\n{3,}/g, '\n\n');

    return { content: content.trim() + '\n', title, order };
}

function walkDir(dir, callback) {
    fs.readdirSync(dir).forEach(f => {
        let dirPath = path.join(dir, f);
        let isDirectory = fs.statSync(dirPath).isDirectory();
        isDirectory ? walkDir(dirPath, callback) : callback(path.join(dir, f));
    });
}

const inputDir = process.argv[2];
const outputDir = process.argv[3];

if (!inputDir || !outputDir) {
    console.error("Usage: node convert-mdx.js <input-dir> <output-dir>");
    process.exit(1);
}

fs.mkdirSync(outputDir, { recursive: true });

let pages = [];

walkDir(inputDir, (filePath) => {
    if (filePath.endsWith('.mdx') || filePath.endsWith('.md')) {
        const relativePath = path.relative(inputDir, filePath);
        
        let outPath = relativePath.replace(/\.mdx$/, '.md');
        if (outPath === 'index.md') outPath = 'Home.md';
        
        const fullOutPath = path.join(outputDir, outPath);
        fs.mkdirSync(path.dirname(fullOutPath), { recursive: true });
        
        const { content, title, order } = processFile(filePath);
        fs.writeFileSync(fullOutPath, content);
        
        const parts = relativePath.split(path.sep);
        const category = parts.length > 1 ? parts[0] : 'root';
        const displayTitle = title || path.basename(relativePath, path.extname(relativePath));
        
        // Remove .md extension for linking, and use forward slash for URLs
        let link = outPath.replace(/\.md$/, '').split(path.sep).join('/');
        
        pages.push({
            title: displayTitle,
            order,
            category,
            link
        });
    }
});

// Generate _Sidebar.md
let sidebarContent = `* [Home](Home)\n`;

// Group by category, excluding root
const categories = [...new Set(pages.map(p => p.category))].filter(c => c !== 'root').sort();

for (const cat of categories) {
    const catName = cat.charAt(0).toUpperCase() + cat.slice(1);
    sidebarContent += `* **${catName}**\n`;
    
    const catPages = pages.filter(p => p.category === cat).sort((a, b) => a.order - b.order);
    for (const page of catPages) {
        sidebarContent += `  * [${page.title}](${page.link})\n`;
    }
}

// Add other root pages if any
const rootPages = pages.filter(p => p.category === 'root' && p.link !== 'Home').sort((a, b) => a.order - b.order);
for (const page of rootPages) {
    sidebarContent += `* [${page.title}](${page.link})\n`;
}

fs.writeFileSync(path.join(outputDir, '_Sidebar.md'), sidebarContent);
