import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = path.resolve(root, process.argv[2] ?? 'android/app/build/outputs/apk/release/app-release.apk');
const outDir = path.join(root, 'dist', 'downloads');
const output = path.join(outDir, 'daybird.apk');
const packageJson = JSON.parse(await fs.readFile(path.join(root, 'package.json'), 'utf8'));
const bytes = await fs.readFile(source);
await fs.mkdir(outDir, { recursive: true });
await fs.writeFile(output, bytes);
await fs.writeFile(path.join(outDir, 'daybird-apk.json'), JSON.stringify({
  versionName: process.env.DAYBIRD_VERSION_NAME || packageJson.version,
  versionCode: Number(process.env.DAYBIRD_VERSION_CODE || 1),
  gitSha: process.env.GITHUB_SHA || 'local',
  builtAt: new Date().toISOString(),
  size: bytes.length,
  sha256: crypto.createHash('sha256').update(bytes).digest('hex')
}, null, 2), 'utf8');
