import { render } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { WeekView } from '../App';
import { DEFAULT_CATEGORIES, DEFAULT_SETTINGS, type ScheduleOccurrence } from '../lib/domain';

describe('WeekView schedule labels', () => {
  it('always renders short and long schedule names with compact sizing', () => {
    const occurrences: ScheduleOccurrence[] = [
      {
        key: 'shopping', seriesId: 'shopping', originalDate: '2026-07-13', date: '2026-07-13',
        title: '다이소 쇼핑', categoryId: 'personal', startMinute: 18 * 60, durationMinute: 30, reminderMinute: null
      },
      {
        key: 'reading', seriesId: 'reading', originalDate: '2026-07-13', date: '2026-07-13',
        title: '예술사 한 챕터 읽기', categoryId: 'study', startMinute: 19 * 60 + 10, durationMinute: 15, reminderMinute: null
      }
    ];

    const { container, getAllByText } = render(
      <WeekView
        date="2026-07-13"
        settings={DEFAULT_SETTINGS}
        categories={DEFAULT_CATEGORIES}
        occurrences={occurrences}
        onDateChange={vi.fn()}
        onOpenDate={vi.fn()}
      />
    );

    expect(getAllByText('다이소 쇼핑')).toHaveLength(2);
    expect(getAllByText('예술사 한 챕터 읽기')).toHaveLength(2);
    expect(container.querySelector('[aria-label^="다이소 쇼핑,"]')).toHaveClass('compact');
    expect(container.querySelector('[aria-label^="예술사 한 챕터 읽기,"]')).toHaveClass('micro');
    expect(container.querySelectorAll('.week-day-heads button')).toHaveLength(7);
    expect(container.querySelector('.week-agenda-list')).toHaveTextContent('18:00');
  });
});
