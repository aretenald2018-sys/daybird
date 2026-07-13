export type RecurrenceKind = 'none' | 'daily' | 'weekly';
export type FocusMode = 'event' | 'pomodoro';
export type FocusStatus = 'running' | 'paused' | 'completed' | 'cancelled';

export interface Category {
  id: string;
  name: string;
  color: string;
  order: number;
  archived: boolean;
}

export interface RecurrenceRule {
  kind: RecurrenceKind;
  weekdays: number[];
  endDate: string | null;
}

export interface ScheduleSeries {
  id: string;
  title: string;
  categoryId: string;
  startDate: string;
  startMinute: number;
  durationMinute: number;
  recurrence: RecurrenceRule;
  reminderMinute: number | null;
  createdAt: number;
  updatedAt: number;
}

export interface OccurrencePatch {
  title?: string;
  categoryId?: string;
  date?: string;
  startMinute?: number;
  durationMinute?: number;
  reminderMinute?: number | null;
}

export interface OccurrenceOverride {
  id: string;
  seriesId: string;
  occurrenceDate: string;
  action: 'modified' | 'cancelled';
  patch?: OccurrencePatch;
}

export interface ScheduleOccurrence {
  key: string;
  seriesId: string;
  originalDate: string;
  date: string;
  title: string;
  categoryId: string;
  startMinute: number;
  durationMinute: number;
  reminderMinute: number | null;
}

export interface FocusSession {
  id: string;
  mode: FocusMode;
  occurrenceKey: string | null;
  title: string;
  plannedSeconds: number;
  startedAt: number;
  targetEndAt: number;
  pausedAt: number | null;
  pausedRemainingSeconds: number | null;
  status: FocusStatus;
  finishedAt: number | null;
}

export interface AppSettings {
  id: 'app';
  dayStartHour: number;
  dayEndHour: number;
  snapMinute: 5 | 10 | 15 | 30;
  fontScale: 'small' | 'medium' | 'large';
  blockDensity: 'compact' | 'comfortable';
  textAlign: 'left' | 'center';
  pomodoroFocusMinute: number;
  pomodoroBreakMinute: number;
}

export const DEFAULT_SETTINGS: AppSettings = {
  id: 'app',
  dayStartHour: 6,
  dayEndHour: 24,
  snapMinute: 5,
  fontScale: 'medium',
  blockDensity: 'comfortable',
  textAlign: 'left',
  pomodoroFocusMinute: 25,
  pomodoroBreakMinute: 5
};

export const DEFAULT_CATEGORIES: Category[] = [
  { id: 'work', name: '업무', color: '#FF6B64', order: 0, archived: false },
  { id: 'study', name: '학습', color: '#5AA9F8', order: 1, archived: false },
  { id: 'meal', name: '식사', color: '#F7C948', order: 2, archived: false },
  { id: 'exercise', name: '운동', color: '#45C88A', order: 3, archived: false },
  { id: 'rest', name: '휴식', color: '#8D94A0', order: 4, archived: false },
  { id: 'hobby', name: '취미', color: '#A777E3', order: 5, archived: false },
  { id: 'other', name: '기타', color: '#FF75A8', order: 6, archived: false }
];

export function id(prefix: string): string {
  return `${prefix}_${crypto.randomUUID()}`;
}

export function dateKey(date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function parseDateKey(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day, 12, 0, 0, 0);
}

export function addDays(value: string, amount: number): string {
  const date = parseDateKey(value);
  date.setDate(date.getDate() + amount);
  return dateKey(date);
}

export function startOfWeek(value: string): string {
  const date = parseDateKey(value);
  const offset = (date.getDay() + 6) % 7;
  return addDays(value, -offset);
}

export function weekday(value: string): number {
  return parseDateKey(value).getDay();
}

export function formatDate(value: string, options?: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat('ko-KR', options ?? { month: 'long', day: 'numeric', weekday: 'short' }).format(parseDateKey(value));
}

export function formatMinute(minute: number): string {
  const normalized = ((minute % 1440) + 1440) % 1440;
  return `${String(Math.floor(normalized / 60)).padStart(2, '0')}:${String(normalized % 60).padStart(2, '0')}`;
}

export function snapMinute(value: number, step: number): number {
  return Math.max(0, Math.min(1435, Math.round(value / step) * step));
}

function recurrenceMatches(series: ScheduleSeries, candidateDate: string): boolean {
  if (candidateDate < series.startDate) return false;
  if (series.recurrence.endDate && candidateDate > series.recurrence.endDate) return false;
  if (series.recurrence.kind === 'none') return candidateDate === series.startDate;
  if (series.recurrence.kind === 'daily') return true;
  return series.recurrence.weekdays.includes(weekday(candidateDate));
}

export function expandOccurrences(
  seriesList: ScheduleSeries[],
  overrides: OccurrenceOverride[],
  rangeStart: string,
  rangeEnd: string
): ScheduleOccurrence[] {
  const overrideMap = new Map(overrides.map(item => [`${item.seriesId}:${item.occurrenceDate}`, item]));
  const output: ScheduleOccurrence[] = [];
  let cursor = rangeStart;

  while (cursor <= rangeEnd) {
    for (const series of seriesList) {
      if (!recurrenceMatches(series, cursor)) continue;
      const override = overrideMap.get(`${series.id}:${cursor}`);
      if (override?.action === 'cancelled') continue;
      const patch = override?.patch ?? {};
      output.push({
        key: `${series.id}:${cursor}`,
        seriesId: series.id,
        originalDate: cursor,
        date: patch.date ?? cursor,
        title: patch.title ?? series.title,
        categoryId: patch.categoryId ?? series.categoryId,
        startMinute: patch.startMinute ?? series.startMinute,
        durationMinute: patch.durationMinute ?? series.durationMinute,
        reminderMinute: patch.reminderMinute !== undefined ? patch.reminderMinute : series.reminderMinute
      });
    }
    cursor = addDays(cursor, 1);
  }

  for (const override of overrides) {
    if (override.action !== 'modified' || !override.patch?.date) continue;
    if (override.occurrenceDate >= rangeStart && override.occurrenceDate <= rangeEnd) continue;
    const series = seriesList.find(item => item.id === override.seriesId);
    if (!series || override.patch.date < rangeStart || override.patch.date > rangeEnd) continue;
    output.push({
      key: `${series.id}:${override.occurrenceDate}`,
      seriesId: series.id,
      originalDate: override.occurrenceDate,
      date: override.patch.date,
      title: override.patch.title ?? series.title,
      categoryId: override.patch.categoryId ?? series.categoryId,
      startMinute: override.patch.startMinute ?? series.startMinute,
      durationMinute: override.patch.durationMinute ?? series.durationMinute,
      reminderMinute: override.patch.reminderMinute !== undefined ? override.patch.reminderMinute : series.reminderMinute
    });
  }

  return output.sort((a, b) => a.date.localeCompare(b.date) || a.startMinute - b.startMinute || a.title.localeCompare(b.title));
}

export interface DaySegment extends ScheduleOccurrence {
  segmentStart: number;
  segmentEnd: number;
  lane: number;
  laneCount: number;
}

export function segmentsForDate(occurrences: ScheduleOccurrence[], targetDate: string): DaySegment[] {
  const segments: DaySegment[] = [];
  for (const occurrence of occurrences) {
    const end = occurrence.startMinute + occurrence.durationMinute;
    if (occurrence.date === targetDate) {
      segments.push({ ...occurrence, segmentStart: occurrence.startMinute, segmentEnd: Math.min(1440, end), lane: 0, laneCount: 1 });
    }
    if (end > 1440 && addDays(occurrence.date, 1) === targetDate) {
      segments.push({ ...occurrence, date: targetDate, segmentStart: 0, segmentEnd: end - 1440, lane: 0, laneCount: 1 });
    }
  }

  const sorted = segments.sort((a, b) => a.segmentStart - b.segmentStart || a.segmentEnd - b.segmentEnd);
  const active: DaySegment[] = [];
  for (const segment of sorted) {
    for (let index = active.length - 1; index >= 0; index -= 1) {
      if (active[index].segmentEnd <= segment.segmentStart) active.splice(index, 1);
    }
    const used = new Set(active.map(item => item.lane));
    let lane = 0;
    while (used.has(lane)) lane += 1;
    segment.lane = lane;
    active.push(segment);
    const count = Math.max(...active.map(item => item.lane)) + 1;
    for (const item of active) item.laneCount = Math.max(item.laneCount, count);
  }
  return sorted;
}

export function occurrenceDateTime(occurrence: ScheduleOccurrence, minuteOffset = 0): Date {
  const date = parseDateKey(occurrence.date);
  date.setHours(0, occurrence.startMinute + minuteOffset, 0, 0);
  return date;
}

export function remainingSeconds(session: FocusSession, now = Date.now()): number {
  if (session.status === 'paused') return Math.max(0, session.pausedRemainingSeconds ?? 0);
  if (session.status !== 'running') return 0;
  return Math.max(0, Math.ceil((session.targetEndAt - now) / 1000));
}

export function formatDuration(totalSeconds: number): string {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  const rest = seconds % 60;
  return hours > 0
    ? `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
    : `${String(minutes).padStart(2, '0')}:${String(rest).padStart(2, '0')}`;
}
