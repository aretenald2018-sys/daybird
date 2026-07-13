import { describe, expect, it } from 'vitest';
import { addDays, expandOccurrences, formatScheduleDetailsText, parseScheduleDetails, parseScheduleDetailsText, remainingSeconds, segmentsForDate, snapMinute, toggleScheduleDetail, type FocusSession, type ScheduleSeries } from './domain';

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

  it('keeps completion on one recurring occurrence without completing the next one', () => {
    const daily = { ...base, recurrence: { kind: 'daily' as const, weekdays: [], endDate: '2026-07-14' } };
    const occurrences = expandOccurrences([daily], [{
      id: 'done', seriesId: 'series', occurrenceDate: '2026-07-13', action: 'modified', patch: { completed: true }
    }], '2026-07-13', '2026-07-14');

    expect(occurrences.map(item => item.completed)).toEqual([true, false]);
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

describe('schedule details', () => {
  it('normalizes bullets and checkboxes while keeping legacy text compatible', () => {
    const stored = parseScheduleDetailsText('회의 메모\nㅡ 참고자료\nㅁ 초안 작성\n☑ 공유 완료');
    expect(stored).toEqual(['ㅡ 회의 메모', 'ㅡ 참고자료', 'ㅁ 초안 작성', '☑ 공유 완료']);
    expect(parseScheduleDetails(stored)).toMatchObject([
      { kind: 'bullet', text: '회의 메모', checked: false },
      { kind: 'bullet', text: '참고자료', checked: false },
      { kind: 'checkbox', text: '초안 작성', checked: false },
      { kind: 'checkbox', text: '공유 완료', checked: true }
    ]);
    expect(formatScheduleDetailsText(stored)).toBe('ㅡ 회의 메모\nㅡ 참고자료\nㅁ 초안 작성\n☑ 공유 완료');
  });

  it('toggles only the selected checkbox', () => {
    const details = ['ㅡ 참고자료', 'ㅁ 초안 작성', '☑ 공유 완료'];
    expect(toggleScheduleDetail(details, 1)).toEqual(['ㅡ 참고자료', '☑ 초안 작성', '☑ 공유 완료']);
    expect(toggleScheduleDetail(details, 2)).toEqual(['ㅡ 참고자료', 'ㅁ 초안 작성', 'ㅁ 공유 완료']);
  });
});
