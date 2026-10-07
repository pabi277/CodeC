import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
const root=fileURLToPath(new URL('../../../', import.meta.url)).replace(/\/$/,'');
const tools=process.env.WEB_TOOLS || '/home/user/.cache/codec-web-tools';
const {default: sharp}=await import(pathToFileURL(path.join(tools,'node_modules/sharp/lib/index.js')).href);
const svg=`<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="630"><rect width="1200" height="630" fill="#0b100e"/><rect x="36" y="36" width="1128" height="558" rx="32" fill="#101713" stroke="#28392e" stroke-width="2"/><rect x="78" y="85" width="6" height="460" rx="3" fill="#3ddc84"/><g font-family="DejaVu Sans, sans-serif"><text x="120" y="156" font-size="76" font-weight="bold" fill="#edf2ed">CodeC</text><text x="124" y="227" font-size="23" font-weight="bold" fill="#3ddc84">C PROGRAMMING ON ANDROID</text><text x="120" y="330" font-size="57" font-weight="bold" fill="#edf2ed">Write. Compile. Run.</text><text x="124" y="402" font-size="27" fill="#a4b0a6">Offline C compiler. Editor. Terminal. Projects.</text><text x="124" y="520" font-size="25" fill="#8ce9b3">Open source / Built for your phone</text></g></svg>`;
const icon=await sharp(root+'/docs/brand/icon/codec-512.png').resize(164,164).png().toBuffer();
await sharp(Buffer.from(svg)).composite([{input:icon,left:938,top:84}]).png().toFile(root+'/website/assets/images/social-card.png');
