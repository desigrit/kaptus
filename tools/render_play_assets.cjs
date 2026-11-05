// Run with Playwright installed, or point KAPTUS_PLAYWRIGHT_MODULE to its package.
const fs = require('node:fs/promises');
const path = require('node:path');
const { chromium } = require(process.env.KAPTUS_PLAYWRIGHT_MODULE || 'playwright');

(async () => {
  const root = path.resolve(__dirname, '..');
  const output = path.join(root, 'docs/play-store/graphics');
  await fs.mkdir(output, { recursive: true });
  const icon = (await fs.readFile(path.join(root, 'docs/brand/kaptus-icon.svg'), 'utf8'))
    .replace('<circle cx="54" cy="54" r="36" fill="#176B70"/>',
      '<rect x="18" y="18" width="72" height="72" fill="#176B70"/>');
  if (icon.includes('<circle')) throw new Error('Check the brand SVG background before rendering.');
  const feature = await fs.readFile(path.join(root, 'docs/play-store/feature-graphic.svg'), 'utf8');
  const browser = await chromium.launch({
    headless: true,
    ...(process.env.KAPTUS_CHROME_PATH ? { executablePath: process.env.KAPTUS_CHROME_PATH } : {})
  });
  try {
    const page = await browser.newPage({ deviceScaleFactor: 1 });
    for (const [name, svg, width, height, alpha] of [
      ['icon.png', icon, 512, 512, true],
      ['feature-graphic.png', feature, 1024, 500, false]
    ]) {
      await page.setViewportSize({ width, height });
      await page.setContent(`<html><head><style>html,body{margin:0;padding:0;background:transparent}svg{display:block}</style></head><body>${svg}</body></html>`);
      await page.evaluate(() => document.fonts.ready);
      if (alpha) {
        const png = await page.evaluate(async () => {
          const svg = document.querySelector('svg');
          const source = URL.createObjectURL(new Blob([new XMLSerializer().serializeToString(svg)], { type: 'image/svg+xml' }));
          try {
            const image = new Image();
            image.src = source;
            await image.decode();
            const canvas = document.createElement('canvas');
            canvas.width = 512;
            canvas.height = 512;
            canvas.getContext('2d', { alpha: true }).drawImage(image, 0, 0);
            return canvas.toDataURL('image/png').split(',')[1];
          } finally { URL.revokeObjectURL(source); }
        });
        await fs.writeFile(path.join(output, name), Buffer.from(png, 'base64'));
      } else {
        await page.screenshot({ path: path.join(output, name), omitBackground: false });
      }
      console.log(`${name}: ${width} x ${height}`);
    }
    // Export opaque 24-bit copies for Play without changing screenshot content.
    const screenshots = path.join(output, 'phone-screenshots');
    await fs.mkdir(screenshots, { recursive: true });
    for (const [name, source] of [
      ['01-captions.png', 'kaptus-player-portrait.png'],
      ['02-landscape.png', 'kaptus-player-landscape.png'],
      ['03-settings.png', 'kaptus-settings.png']
    ]) {
      const png = await fs.readFile(path.join(root, 'docs/images', source));
      const width = png.readUInt32BE(16), height = png.readUInt32BE(20);
      await page.setViewportSize({ width, height });
      await page.setContent(`<html><head><style>html,body{margin:0;background:#000}img{display:block}</style></head><body><img width="${width}" height="${height}" src="data:image/png;base64,${png.toString('base64')}"></body></html>`);
      await page.locator('img').evaluate(image => image.decode());
      await page.screenshot({ path: path.join(screenshots, name), omitBackground: false });
      console.log(`${name}: ${width} x ${height}`);
    }
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
