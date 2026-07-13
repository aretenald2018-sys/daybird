import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const signingDir = path.join(root, '.android-signing');
const keystorePath = path.join(signingDir, 'daybird-release.keystore');
const credentialsPath = path.join(signingDir, 'credentials.json');
const repo = 'aretenald2018-sys/daybird';
const javaHome = process.env.JAVA_HOME || (process.platform === 'win32' ? 'C:\\Program Files\\Android\\Android Studio\\jbr' : '');
const keytool = path.join(javaHome, 'bin', process.platform === 'win32' ? 'keytool.exe' : 'keytool');

fs.mkdirSync(signingDir, { recursive: true });

let credentials;
if (fs.existsSync(credentialsPath) && fs.existsSync(keystorePath)) {
  credentials = JSON.parse(fs.readFileSync(credentialsPath, 'utf8'));
} else {
  const password = crypto.randomBytes(30).toString('base64url');
  credentials = { keyAlias: 'daybird-release', storePassword: password, keyPassword: password };
  const env = { ...process.env, DAYBIRD_GENERATED_KEY_PASSWORD: password };
  const result = spawnSync(keytool, [
    '-J-Dfile.encoding=UTF-8',
    '-J-Duser.language=en',
    '-J-Duser.country=US',
    '-genkeypair',
    '-v',
    '-keystore', keystorePath,
    '-storetype', 'PKCS12',
    '-storepass:env', 'DAYBIRD_GENERATED_KEY_PASSWORD',
    '-keypass:env', 'DAYBIRD_GENERATED_KEY_PASSWORD',
    '-alias', credentials.keyAlias,
    '-keyalg', 'RSA',
    '-keysize', '3072',
    '-validity', '10000',
    '-dname', 'CN=com.aretenald.daybird, O=DayBird, C=KR'
  ], { cwd: root, env, stdio: 'inherit', windowsHide: true });
  if (result.status !== 0) process.exit(result.status ?? 1);
  fs.writeFileSync(credentialsPath, `${JSON.stringify(credentials, null, 2)}\n`, 'utf8');
}

const secrets = {
  ANDROID_KEYSTORE_BASE64: fs.readFileSync(keystorePath).toString('base64'),
  ANDROID_KEYSTORE_PASSWORD: credentials.storePassword,
  ANDROID_KEY_ALIAS: credentials.keyAlias,
  ANDROID_KEY_PASSWORD: credentials.keyPassword
};

for (const [name, value] of Object.entries(secrets)) {
  const result = spawnSync('gh', ['secret', 'set', name, '--repo', repo], {
    cwd: root,
    input: value,
    encoding: 'utf8',
    stdio: ['pipe', 'inherit', 'inherit'],
    windowsHide: true
  });
  if (result.status !== 0) process.exit(result.status ?? 1);
}

console.log(`Signing key is ready for ${repo}. Back up ${signingDir} securely.`);
