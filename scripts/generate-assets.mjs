import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const iconDir = path.join(root, 'public', 'icons');
await fs.mkdir(iconDir, { recursive: true });
const nativeAssetDir = path.join(root, 'assets');
await fs.mkdir(nativeAssetDir, { recursive: true });

function iconSvg(size, safe = false) {
  const inset = safe ? Math.round(size * 0.18) : Math.round(size * 0.07);
  const box = size - inset * 2;
  const radius = Math.round(box * 0.25);
  const scale = box / 512;
  return `
    <svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stop-color="#1789FF"/>
          <stop offset="1" stop-color="#4EDCC9"/>
        </linearGradient>
        <filter id="shadow" x="-30%" y="-30%" width="160%" height="170%">
          <feDropShadow dx="0" dy="${10 * scale}" stdDeviation="${12 * scale}" flood-color="#005FC8" flood-opacity=".22"/>
        </filter>
      </defs>
      <rect width="${size}" height="${size}" fill="#F2F2F7"/>
      <rect x="${inset}" y="${inset}" width="${box}" height="${box}" rx="${radius}" fill="url(#bg)"/>
      <g transform="translate(${inset} ${inset}) scale(${scale})" filter="url(#shadow)" fill="#fff">
        <path d="M132 102c31-6 58 13 65 45l37 170c5 25-12 49-37 52-23 3-44-12-48-35l-37-171c-7-31 3-55 20-61Z"/>
        <path d="M276 113c61 4 112 54 114 116 2 58-38 105-94 117-22 5-43-9-48-31l-38-175c-5-23 15-40 39-33l27 6Zm-1 63 22 103c23-10 37-32 36-57-1-28-18-53-58-46Z" fill-rule="evenodd"/>
      </g>
    </svg>`;
}

for (const size of [192, 512]) {
  await sharp(Buffer.from(iconSvg(size))).png().toFile(path.join(iconDir, `daybird-${size}.png`));
}
await sharp(Buffer.from(iconSvg(512, true))).png().toFile(path.join(iconDir, 'daybird-512-maskable.png'));
await sharp(Buffer.from(iconSvg(1024, true))).png().toFile(path.join(nativeAssetDir, 'icon-only.png'));

const splash = `
  <svg width="2732" height="2732" viewBox="0 0 2732 2732" xmlns="http://www.w3.org/2000/svg">
    <rect width="2732" height="2732" fill="#F2F2F7"/>
    <g transform="translate(1026 1026) scale(1.328125)">
      <rect width="512" height="512" rx="132" fill="url(#splashBg)"/>
      <defs><linearGradient id="splashBg" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#1789FF"/><stop offset="1" stop-color="#4EDCC9"/></linearGradient></defs>
      <g fill="#fff"><path d="M132 102c31-6 58 13 65 45l37 170c5 25-12 49-37 52-23 3-44-12-48-35l-37-171c-7-31 3-55 20-61Z"/><path d="M276 113c61 4 112 54 114 116 2 58-38 105-94 117-22 5-43-9-48-31l-38-175c-5-23 15-40 39-33l27 6Zm-1 63 22 103c23-10 37-32 36-57-1-28-18-53-58-46Z" fill-rule="evenodd"/></g>
    </g>
  </svg>`;
await sharp(Buffer.from(splash)).png().toFile(path.join(nativeAssetDir, 'splash.png'));

const og = `
  <svg width="1200" height="630" viewBox="0 0 1200 630" xmlns="http://www.w3.org/2000/svg">
    <defs>
      <linearGradient id="g" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#EAF5FF"/><stop offset="1" stop-color="#E7FBF6"/></linearGradient>
      <linearGradient id="b" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#1687FF"/><stop offset="1" stop-color="#4EDCC9"/></linearGradient>
    </defs>
    <rect width="1200" height="630" fill="#F2F2F7"/>
    <circle cx="990" cy="90" r="260" fill="url(#g)"/>
    <circle cx="115" cy="610" r="230" fill="#E9F3FF"/>
    <rect x="92" y="86" width="94" height="94" rx="25" fill="url(#b)"/>
    <path d="M119 108c9-2 17 4 19 13l9 41c1 7-3 13-10 14-6 1-12-3-13-9l-10-42c-1-8 1-15 5-17Zm36 3c17 1 31 15 31 32 1 16-11 29-26 32-6 2-12-2-13-8l-9-43c-2-6 4-11 10-9l7 1Zm0 17 5 27c7-3 11-9 10-15 0-7-5-14-15-12Z" fill="#fff"/>
    <text x="92" y="256" font-family="Arial, sans-serif" font-size="82" font-weight="750" letter-spacing="-3" fill="#1C1C1E">DayBird</text>
    <text x="96" y="323" font-family="Arial, sans-serif" font-size="31" font-weight="600" fill="#4B4B52">하루를 계획하고, 7일을 한눈에.</text>
    <rect x="92" y="382" width="382" height="74" rx="24" fill="#fff"/>
    <rect x="110" y="399" width="7" height="40" rx="4" fill="#5AA9F8"/>
    <text x="135" y="428" font-family="Arial, sans-serif" font-size="24" font-weight="650" fill="#1C1C1E">드래그로 빠른 일정 등록</text>
    <rect x="502" y="382" width="300" height="74" rx="24" fill="#fff"/>
    <rect x="520" y="399" width="7" height="40" rx="4" fill="#45C88A"/>
    <text x="546" y="428" font-family="Arial, sans-serif" font-size="24" font-weight="650" fill="#1C1C1E">집중 타이머</text>
    <g transform="translate(850 215)">
      <rect width="250" height="330" rx="45" fill="#fff" stroke="#fff" stroke-width="8"/>
      <rect x="28" y="42" width="194" height="22" rx="11" fill="#EAF2FA"/>
      <g fill="#DCEEFF"><rect x="28" y="82" width="52" height="204" rx="12"/><rect x="88" y="82" width="52" height="204" rx="12"/><rect x="148" y="82" width="74" height="204" rx="12"/></g>
      <g fill="#73B5FA"><rect x="34" y="111" width="40" height="55" rx="8"/><rect x="94" y="177" width="40" height="77" rx="8"/><rect x="154" y="96" width="62" height="39" rx="8"/><rect x="154" y="214" width="62" height="48" rx="8"/></g>
    </g>
  </svg>`;
await sharp(Buffer.from(og)).png().toFile(path.join(root, 'public', 'og.png'));
