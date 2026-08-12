const fs = require('fs');
const path = require('path');

function processFile(filePath) {
    let content = fs.readFileSync(filePath, 'utf8');

    // Remove frontmatter
    content = content.replace(/^---\n[\s\S]*?\n---\n/, '');

    // Remove imports
    content = content.replace(/^import\s+.*?from\s+['"].*?['"];?\n/gm, '');

    // Replace specific known tags to readable text
    content = content.replace(/<SettingsPath\s+path="([^"]+)"\s*\/?>/g, '**$1**');
    content = content.replace(/<KeyCap\s+(?:key|letter)="([^"]+)"\s*\/?>/g, '`$1`');
    content = content.replace(/<LinkCard\s+title="([^"]+)"\s+href="([^"]+)"\s*\/?>/g, '[$1]($2)');
    content = content.replace(/<LinkCard\s+title="([^"]+)"\s+description="([^"]+)"\s+href="([^"]+)"\s*\/?>/g, '[$1]($3) - $2');
    
    // Strip layout tags but keep content
    content = content.replace(/<\/?(?:PhoneFrame|Steps|CardGrid|FileTree|Flavor|Since|LayoutExplorer|ThemePreview|GestureDemo|FilterTable|Card)[^>]*>/g, '');

    // Remove completely generic self-closing components we missed
    content = content.replace(/<[A-Z][a-zA-Z0-9]*\s+[^>]*\/>/g, '');

    // Clean up empty lines created by tag removal
    content = content.replace(/\n{3,}/g, '\n\n');

    return content;
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

walkDir(inputDir, (filePath) => {
    if (filePath.endsWith('.mdx') || filePath.endsWith('.md')) {
        const relativePath = path.relative(inputDir, filePath);
        const outPath = path.join(outputDir, relativePath).replace(/\.mdx$/, '.md');
        
        fs.mkdirSync(path.dirname(outPath), { recursive: true });
        
        const converted = processFile(filePath);
        fs.writeFileSync(outPath, converted.trim() + '\n');
    }
});
