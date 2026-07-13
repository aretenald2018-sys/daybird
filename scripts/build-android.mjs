import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const variant = process.argv[2] === 'release' ? 'Release' : 'Debug';
const env = { ...process.env };
const localSigningPath = path.join(root, '.android-signing', 'credentials.json');

if (variant === 'Release' && fs.existsSync(localSigningPath)) {
  const localSigning = JSON.parse(fs.readFileSync(localSigningPath, 'utf8'));
  env.DAYBIRD_KEYSTORE_PATH ||= path.join(root, '.android-signing', 'daybird-release.keystore');
  env.DAYBIRD_KEYSTORE_PASSWORD ||= localSigning.storePassword;
  env.DAYBIRD_KEY_ALIAS ||= localSigning.keyAlias;
  env.DAYBIRD_KEY_PASSWORD ||= localSigning.keyPassword;
}

if (process.platform === 'win32') {
  env.JAVA_HOME ||= 'C:\\Program Files\\Android\\Android Studio\\jbr';
  env.ANDROID_HOME ||= path.join(env.LOCALAPPDATA || path.join(env.USERPROFILE || '', 'AppData', 'Local'), 'Android', 'Sdk');
  env.ANDROID_SDK_ROOT ||= env.ANDROID_HOME;
}

if (!env.JAVA_HOME || !fs.existsSync(env.JAVA_HOME)) {
  throw new Error('JAVA_HOME을 찾지 못했습니다. Android Studio JBR 또는 JDK 21 경로를 설정해 주세요.');
}
if (!env.ANDROID_HOME || !fs.existsSync(env.ANDROID_HOME)) {
  throw new Error('Android SDK를 찾지 못했습니다. ANDROID_HOME을 설정해 주세요.');
}

function run(command, args) {
  const executable = process.platform === 'win32' ? (env.ComSpec || 'cmd.exe') : command;
  const commandArgs = process.platform === 'win32' ? ['/d', '/c', command, ...args] : args;
  const result = spawnSync(executable, commandArgs, { cwd: root, env, stdio: 'inherit', windowsHide: true });
  if (result.status !== 0) process.exit(result.status ?? 1);
}

run(process.platform === 'win32' ? 'npm.cmd' : 'npm', ['run', 'cap:sync']);
run(process.platform === 'win32' ? 'android\\gradlew.bat' : './android/gradlew', ['-p', 'android', `assemble${variant}`]);
