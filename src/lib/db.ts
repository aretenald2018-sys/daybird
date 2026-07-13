import Dexie, { type Table } from 'dexie';
import { z } from 'zod';
import {
  DEFAULT_CATEGORIES,
  DEFAULT_SETTINGS,
  type AppSettings,
  type Category,
  type FocusSession,
  type OccurrenceOverride,
  type ScheduleSeries
} from './domain';

class DayBirdDatabase extends Dexie {
  categories!: Table<Category, string>;
  schedules!: Table<ScheduleSeries, string>;
  overrides!: Table<OccurrenceOverride, string>;
  focusSessions!: Table<FocusSession, string>;
  settings!: Table<AppSettings, string>;

  constructor() {
    super('daybird');
    this.version(1).stores({
      categories: 'id, order, archived',
      schedules: 'id, startDate, categoryId, updatedAt',
      overrides: 'id, [seriesId+occurrenceDate], seriesId, occurrenceDate',
      focusSessions: 'id, status, startedAt, occurrenceKey',
      settings: 'id'
    });
  }
}

export const db = new DayBirdDatabase();
const RECOVERY_BACKUP_KEY = 'daybird:recovery-v1';
const LAST_IMPORT_BACKUP_KEY = 'daybird:last-before-import';

export async function ensureSeedData(): Promise<void> {
  await db.transaction('rw', db.categories, db.settings, async () => {
    if ((await db.categories.count()) === 0) await db.categories.bulkAdd(DEFAULT_CATEGORIES);
    if (!(await db.settings.get('app'))) await db.settings.add(DEFAULT_SETTINGS);
  });
}

export interface DayBirdSnapshot {
  categories: Category[];
  schedules: ScheduleSeries[];
  overrides: OccurrenceOverride[];
  focusSessions: FocusSession[];
  settings: AppSettings;
}

const backupSchema = z.object({
  schemaVersion: z.literal(1),
  appVersion: z.string(),
  exportedAt: z.number(),
  data: z.object({
    categories: z.array(z.any()),
    schedules: z.array(z.any()),
    overrides: z.array(z.any()),
    focusSessions: z.array(z.any()),
    settings: z.any()
  })
});

type ParsedBackup = z.infer<typeof backupSchema>;

function readBackup(key: string): ParsedBackup | null {
  try {
    const raw = localStorage.getItem(key);
    return raw ? backupSchema.parse(JSON.parse(raw)) : null;
  } catch {
    return null;
  }
}

function serializeBackup(data: DayBirdSnapshot): string {
  return JSON.stringify({ schemaVersion: 1, appVersion: '0.1.0', exportedAt: Date.now(), data }, null, 2);
}

async function databaseIsEmpty(): Promise<boolean> {
  const counts = await Promise.all([db.categories.count(), db.schedules.count(), db.overrides.count(), db.focusSessions.count(), db.settings.count()]);
  return counts.every(count => count === 0);
}

async function replaceSnapshot(data: DayBirdSnapshot): Promise<void> {
  await db.transaction('rw', db.categories, db.schedules, db.overrides, db.focusSessions, db.settings, async () => {
    await Promise.all([
      db.categories.clear(),
      db.schedules.clear(),
      db.overrides.clear(),
      db.focusSessions.clear(),
      db.settings.clear()
    ]);
    await db.categories.bulkAdd(data.categories);
    await db.schedules.bulkAdd(data.schedules);
    await db.overrides.bulkAdd(data.overrides);
    await db.focusSessions.bulkAdd(data.focusSessions);
    await db.settings.add(data.settings);
  });
}

export async function loadSnapshot(): Promise<DayBirdSnapshot> {
  if (await databaseIsEmpty()) {
    const recovery = readBackup(RECOVERY_BACKUP_KEY);
    if (recovery) await replaceSnapshot(recovery.data as unknown as DayBirdSnapshot);
  }
  await ensureSeedData();
  const [categories, schedules, overrides, focusSessions, settings] = await Promise.all([
    db.categories.orderBy('order').toArray(),
    db.schedules.toArray(),
    db.overrides.toArray(),
    db.focusSessions.orderBy('startedAt').reverse().toArray(),
    db.settings.get('app')
  ]);
  return { categories, schedules, overrides, focusSessions, settings: settings ?? DEFAULT_SETTINGS };
}

export async function saveRecoveryBackup(data?: DayBirdSnapshot): Promise<void> {
  try {
    localStorage.setItem(RECOVERY_BACKUP_KEY, serializeBackup(data ?? await loadSnapshot()));
  } catch {
    // Browser storage can be disabled or full; IndexedDB remains the primary store.
  }
}

export function getRecoveryBackupInfo(): { exportedAt: number; scheduleCount: number } | null {
  const recovery = readBackup(RECOVERY_BACKUP_KEY);
  return recovery ? { exportedAt: recovery.exportedAt, scheduleCount: recovery.data.schedules.length } : null;
}

export async function restoreRecoveryBackup(): Promise<boolean> {
  const recovery = readBackup(RECOVERY_BACKUP_KEY);
  if (!recovery) return false;
  await replaceSnapshot(recovery.data as unknown as DayBirdSnapshot);
  return true;
}

export async function exportBackup(): Promise<string> {
  const data = await loadSnapshot();
  return serializeBackup(data);
}

export async function importBackup(raw: string): Promise<void> {
  const parsed = backupSchema.parse(JSON.parse(raw));
  const current = await exportBackup();
  try { localStorage.setItem(LAST_IMPORT_BACKUP_KEY, current); } catch { /* Keep importing even if localStorage is unavailable. */ }
  const data = parsed.data as unknown as DayBirdSnapshot;
  await replaceSnapshot(data);
  await saveRecoveryBackup(data);
}

export async function resetDatabase(): Promise<void> {
  await db.transaction('rw', db.categories, db.schedules, db.overrides, db.focusSessions, db.settings, async () => {
    await Promise.all([
      db.categories.clear(), db.schedules.clear(), db.overrides.clear(), db.focusSessions.clear(), db.settings.clear()
    ]);
  });
  try { localStorage.removeItem(RECOVERY_BACKUP_KEY); } catch { /* The database reset should still succeed. */ }
  await ensureSeedData();
}
