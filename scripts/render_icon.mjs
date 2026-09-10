#!/usr/bin/env node
/**
 * Phase 38.1 — render the CodeC launcher rasters + store art from the
 * MASTER MARK (../docs/icon/codec-mark.svg). Build-time tooling only:
 * the outputs are COMMITTED, CI never runs this, and the SVG is the only
 * hand-edited file.
 *
 *   Usage:   cd scripts && npm install && node render_icon.mjs
 *   Verify:  run it twice — git diff must be EMPTY (byte-reproducible).
 *
 * Outputs (all PNG, all overwritten in place):
 *   ../app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/
 *        ic_launcher.png        48/72/96/144/192 px, rounded-square tile
 *        ic_launcher_round.png  same sizes, circular tile baked
 *   ../docs/icon/codec-512.png  512×512 GitHub Release / README art
 *
 * NOT generated here: ic_stat_codec (a 24 dp VECTOR drawable — density
 * independent, tinted by the system; a raster would be strictly worse),
 * and the adaptive XML layers (hand-written vectors above).
 *
 * The legacy template .webp files must be deleted in the same commit as
 * the first run of this script (a stale ic_launcher.webp next to a new
 * ic_launcher.png is a duplicate-resource build failure).
 *
 * sharp is BSD-3-Clause; pinned in package.json. PNG output is
 * deterministic for a given sharp build (no timestamps/metadata chunks
 * are written), which is what makes the committed set provably
 * generated.
 */
'use strict';

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '..');
const MARK_SVG = path.join(REPO, 'docs', 'icon', 'codec-mark.svg');
const RES = path.join(REPO, 'app', 'src', 'main', 'res');

// Brand colours — kept in one place, mirrored in the drawable XMLs.
const SURFACE = '#101418';
const ACCENT = '#3DDC84';

// Legacy launcher densities (px): API 24-25 devices have no adaptive
// layer support, so they need real rasters.
const DENSITIES = [
  ['mdpi', 48],
  ['hdpi', 72],
  ['xhdpi', 96],
  ['xxhdpi', 144],
  ['xxxhdpi', 192],
];

// The tile corner radius (units of the 108 viewport) for the square
// icon and the store art. Round/circle shape for ic_launcher_round.
const CORNER_RADIUS = 18;
// The glyph is drawn at ~37% of the canvas for the ADAPTIVE icon (66 dp
// safe zone); the baked tiles have no launcher mask, so the glyph can
// sit bigger and still breathe.
const GLYPH_SCALE = 1.35;

/** Extract every `<path d="…">` from the master SVG (our own file). */
function readGlyphPaths() {
  const svg = fs.readFileSync(MARK_SVG, 'utf8');
  const paths = [];
  const re = /<path\b[^>]*\bd="([^"]+)"/g;
  let m;
  while ((m = re.exec(svg)) !== null) paths.push(m[1].trim());
  if (paths.length < 2) {
    throw new Error(`expected >=2 paths in ${MARK_SVG}, found ${paths.length}`);
  }
  return paths;
}

function glyphGroup(paths, scale) {
  const cx = 54;
  return `<g fill="${ACCENT}" transform="translate(${cx} ${cx}) scale(${scale}) translate(${-cx} ${-cx})">`
    + paths.map((d) => `<path d="${d}"/>`).join('')
    + `</g>`;
}

/** Square (rounded-corner) tile — the legacy `ic_launcher`. */
function squareSvg(paths, size) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 108 108">`
    + `<rect x="0" y="0" width="108" height="108" rx="${CORNER_RADIUS}" fill="${SURFACE}"/>`
    + glyphGroup(paths, GLYPH_SCALE)
    + `</svg>`;
}

/** Circular tile — the legacy `ic_launcher_round`. */
function roundSvg(paths, size) {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 108 108">`
    + `<circle cx="54" cy="54" r="54" fill="${SURFACE}"/>`
    + glyphGroup(paths, GLYPH_SCALE)
    + `</svg>`;
}

async function render(svgBody, outPath, size) {
  const png = sharp(Buffer.from(svgBody), { density: 96 })
    .resize(size, size, { kernel: 'lanczos3' })
    .png({ compressionLevel: 9 });
  await png.toFile(outPath);
  console.log(`wrote ${path.relative(REPO, outPath)} (${size}x${size})`);
}

async function main() {
  const paths = readGlyphPaths();
  console.log(`glyph paths from ${path.relative(REPO, MARK_SVG)}: ${paths.length}`);

  for (const [density, size] of DENSITIES) {
    const dir = path.join(RES, `mipmap-${density}`);
    fs.mkdirSync(dir, { recursive: true });
    await render(squareSvg(paths, size), path.join(dir, 'ic_launcher.png'), size);
    await render(roundSvg(paths, size), path.join(dir, 'ic_launcher_round.png'), size);
  }

  const iconDir = path.join(REPO, 'docs', 'icon');
  fs.mkdirSync(iconDir, { recursive: true });
  await render(squareSvg(paths, 512), path.join(iconDir, 'codec-512.png'), 512);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
