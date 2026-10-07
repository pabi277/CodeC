// W2 browser preflight; dependencies live outside the repo/site.
// WEB_TOOLS contains node_modules/{playwright,@axe-core/playwright,@sparticuz/chromium}.
// BASE_URL must point at the website; OFFLINE_ROOT can point at an extracted review ZIP.
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
const tools = process.env.WEB_TOOLS || '/home/user/.cache/codec-web-tools';
const moduleURL = p => pathToFileURL(path.join(tools, 'node_modules', p)).href;
const { chromium: playwright } = await import(moduleURL('playwright/index.mjs'));
const { default: chromium } = await import(moduleURL('@sparticuz/chromium/build/index.js'));
const { setupLambdaEnvironment } = await import(moduleURL('@sparticuz/chromium/build/helper.js'));
const { inflate } = await import(moduleURL('@sparticuz/chromium/build/lambdafs.js'));
const { default: AxeBuilder } = await import(moduleURL('@axe-core/playwright/dist/index.mjs'));
await inflate(path.join(tools, 'node_modules/@sparticuz/chromium/bin/al2023.tar.br'));
setupLambdaEnvironment('/tmp/al2023/lib');
const browser = await playwright.launch({
  executablePath: await chromium.executablePath(),
  args: chromium.args.filter(arg => arg !== '--single-process'), headless: true,
});
const base = process.env.BASE_URL || 'http://127.0.0.1:8000/';
const site = process.env.OFFLINE_ROOT || fileURLToPath(new URL('../../../website/', import.meta.url));
const resultDir = path.join(tools, 'w2-results');
await fs.mkdir(resultDir, { recursive: true });
const pages = ['index.html', 'install.html', 'start.html'];
const widths = [320, 360, 390, 720, 768, 1110, 1440];
const report = { browser: browser.version(), widths, responsive: [], offline: [], errors: [], externalRequests: [] };
async function overflow(page) {
  return page.evaluate(() => ({ width: innerWidth, scroll: document.documentElement.scrollWidth,
    overflowing: [...document.querySelectorAll('body *')].filter(el => {
      if (!el.checkVisibility() || el.classList.contains('skip-link')) return false;
      // Only deliberate scroll containers may overflow internally (code and tables).
      if (el.closest('.code-block, .table-wrap, .example-code, .terminal-example')) return false;
      const r = el.getBoundingClientRect();
      return r.width > 0 && (r.right > innerWidth + 1 || r.left < -1);
    }).map(el => `${el.tagName}.${el.className}`).slice(0, 12) }));
}
try {
  for (const name of pages) {
    for (const width of widths) {
      const context = await browser.newContext({ viewport: { width, height: 900 }, reducedMotion: 'reduce' });
      const page = await context.newPage();
      page.on('pageerror', error => report.errors.push(`${name}: ${error.message}`));
      page.on('request', req => {
        if (/^https?:/.test(req.url()) && !req.url().startsWith(base)) report.externalRequests.push(req.url());
      });
      const response = await page.goto(base + name);
      assert.equal(response.status(), 200);
      let bounds = await overflow(page);
      assert.ok(bounds.scroll <= width, JSON.stringify({ name, width, bounds }));
      assert.deepEqual(bounds.overflowing, [], JSON.stringify({ name, width, bounds }));
      await page.keyboard.press('Tab');
      assert.equal(await page.locator(':focus').textContent(), 'Skip to content');
      await page.keyboard.press('Enter');
      assert.equal(await page.locator(':focus').getAttribute('id'), 'main');
      assert.equal(await page.evaluate(() => getComputedStyle(document.documentElement).scrollBehavior), 'auto');
      // Open ALL guide disclosures; verify content and keyboard scrolling surfaces too.
      await page.locator('.guide-details').evaluateAll(elements => elements.forEach(el => el.open = true));
      if (width <= 1100) {
        await page.locator('.mobile-menu summary').focus();
        await page.keyboard.press('Enter');
        assert.equal(await page.locator('.mobile-menu').getAttribute('open'), '');
        assert.equal(await page.locator('.mobile-menu nav a:visible').count(), 10);
      }
      bounds = await overflow(page);
      assert.ok(bounds.scroll <= width && !bounds.overflowing.length, JSON.stringify({ name, width, bounds }));
      const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa']).analyze();
      assert.deepEqual(axe.violations, [], JSON.stringify({ name, width, violations: axe.violations }));
      if (width <= 1100) {
        await page.locator('.mobile-menu summary').focus();
        await page.keyboard.press('Space');
        assert.equal(await page.locator('.mobile-menu').getAttribute('open'), null);
      }
      await page.locator('.guide-details').evaluateAll(elements => elements.forEach(el => el.open = false));
      await page.evaluate(() => scrollTo(0, 0));
      if ([360, 1440].includes(width)) await page.screenshot({ path: path.join(resultDir, `${name}-${width}.png`), fullPage: true });
      report.responsive.push({ name, width, overflow: 0, axeViolations: 0, skipLink: 'pass', menu: width <= 1100 ? 'keyboard pass' : 'desktop' });
      await context.close();
    }
  }
  for (const name of pages) {
    const context = await browser.newContext({ viewport: { width: 360, height: 800 }, javaScriptEnabled: false, offline: true });
    const page = await context.newPage();
    let network = [];
    page.on('request', req => { if (/^https?:/.test(req.url())) network.push(req.url()); });
    await page.goto(pathToFileURL(path.join(site, name)).href);
    assert.equal(await page.locator('h1').count(), 1);
    assert.equal(await page.evaluate(() => getComputedStyle(document.body).backgroundColor), 'rgb(11, 16, 14)');
    assert.equal(await page.locator('.wordmark img').first().evaluate(img => img.complete && img.naturalWidth > 0), true);
    await page.locator('.mobile-menu summary').click();
    assert.equal(await page.locator('.mobile-menu nav a:visible').count(), 10);
    const target = name === 'start.html' ? 'install.html' : 'start.html';
    await page.locator(`.mobile-menu nav a[href="${target}"]`).click();
    assert.equal(page.url(), pathToFileURL(path.join(site, target)).href);
    assert.equal(await page.locator('h1').count(), 1);
    // Native disclosures open without author scripts or connectivity.
    const summary = page.locator('.guide-details summary').first();
    await summary.focus();
    await page.keyboard.press('Enter');
    assert.equal(await page.locator('.guide-details').first().getAttribute('open'), '');
    assert.deepEqual(network, []);
    report.offline.push({ name, linkedPage: target, css: 'pass', icon: 'pass', noJSMenuAndDetails: 'pass', networkRequests: 0 });
    await context.close();
  }
  assert.deepEqual(report.errors, []);
  assert.deepEqual(report.externalRequests, []);
  await fs.writeFile(path.join(resultDir, 'browser-report.json'), JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report, null, 2));
} finally { await browser.close(); }
