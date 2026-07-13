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

export async function loadSnapshot(): Promise<DayBirdSnapshot> {
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

export async function exportBackup(): Promise<string> {
  const data = await loadSnapshot();
  return JSON.stringify({ schemaVersion: 1, appVersion: '0.1.0', exportedAt: Date.now(), data }, null, 2);
}

export async function importBackup(raw: string): Promise<void> {
  const parsed = backupSchema.parse(JSON.parse(raw));
  const current = await exportBackup();
  localStorage.setItem('daybird:last-auto-backup', current);
  const data = parsed.data as unknown as DayBirdSnapshot;
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

export async function resetDatabase(): Promise<void> {
  await db.transaction('rw', db.categories, db.schedules, db.overrides, db.focusSessions, db.settings, async () => {
    await Promise.all([
      db.categories.clear(), db.schedules.clear(), db.overrides.clear(), db.focusSessions.clear(), db.settings.clear()
    ]);
  });
  await ensureSeedData();
}
