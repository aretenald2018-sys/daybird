import { describe, expect, it } from 'vitest';
import { addDays, expandOccurrences, remainingSeconds, segmentsForDate, snapMinute, type FocusSession, type ScheduleSeries } from './domain';

const base: ScheduleSeries = {
  id: 'series', title: '주간 리뷰', categoryId: 'work', startDate: '2026-07-13', startMinute: 8 * 60 + 25,
  subtasks: ['비닐봉지', '입욕제'],
  durationMinute: 75, recurrence: { kind: 'none', weekdays: [], endDate: null }, reminderMinute: null,
  createdAt: 1, updatedAt: 1
};

describe('schedule occurrence expansion', () => {
  it('expands selected weekdays and applies a single occurrence override', () => {
    const weekly = { ...base, recurrence: { kind: 'weekly' as const, weekdays: [1, 3], endDate: null } };
    const occurrences = expandOccurrences([weekly], [{
      id: 'override', seriesId: 'series', occurrenceDate: '2026-07-15', action: 'modified',
      patch: { title: '수요일 리뷰', startMinute: 600 }
    }], '2026-07-13', '2026-07-19');
    expect(occurrences).toHaveLength(2);
    expect(occurrences.map(item => item.title)).toEqual(['주간 리뷰', '수요일 리뷰']);
    expect(occurrences[0].subtasks).toEqual(['비닐봉지', '입욕제']);
    expect(occurrences[1].startMinute).toBe(600);
  });

  it('cancels only one recurring occurrence', () => {
    const daily = { ...base, recurrence: { kind: 'daily' as const, weekdays: [], endDate: '2026-07-15' } };
    const occurrences = expandOccurrences([daily], [{ id: 'cancel', seriesId: 'series', occurrenceDate: '2026-07-14', action: 'cancelled' }], '2026-07-13', '2026-07-15');
    expect(occurrences.map(item => item.date)).toEqual(['2026-07-13', '2026-07-15']);
  });

  it('renders cross-midnight schedules on both dates', () => {
    const overnight = { ...base, startMinute: 23 * 60, durationMinute: 120 };
    const occurrence = expandOccurrences([overnight], [], '2026-07-13', '2026-07-13');
    expect(segmentsForDate(occurrence, '2026-07-13')[0]).toMatchObject({ segmentStart: 1380, segmentEnd: 1440 });
    expect(segmentsForDate(occurrence, addDays('2026-07-13', 1))[0]).toMatchObject({ segmentStart: 0, segmentEnd: 60 });
  });
});

describe('time utilities', () => {
  it('snaps and clamps drag minutes', () => {
    expect(snapMinute(506, 5)).toBe(505);
    expect(snapMinute(-40, 15)).toBe(0);
  });

  it('restores focus remaining time from target timestamp', () => {
    const session: FocusSession = {
      id: 'focus', mode: 'pomodoro', occurrenceKey: null, title: '집중', plannedSeconds: 1500,
      startedAt: 1_000, targetEndAt: 101_000, pausedAt: null, pausedRemainingSeconds: null,
      status: 'running', finishedAt: null
    };
    expect(remainingSeconds(session, 41_000)).toBe(60);
    expect(remainingSeconds({ ...session, status: 'paused', pausedRemainingSeconds: 42 }, 99_000)).toBe(42);
  });
});
