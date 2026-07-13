import { act, fireEvent, render } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { DayView } from '../App';
import { DEFAULT_CATEGORIES, DEFAULT_SETTINGS, type ScheduleOccurrence } from '../lib/domain';

const baseProps = {
  date: '2026-07-13',
  settings: DEFAULT_SETTINGS,
  categories: DEFAULT_CATEGORIES,
  occurrences: [],
  onDateChange: vi.fn(),
  onEdit: vi.fn(),
  onMove: vi.fn(async () => undefined),
  onToggleDetail: vi.fn(async () => undefined)
};

class TestPointerEvent extends MouseEvent {
  readonly pointerId: number;
  readonly pointerType: string;

  constructor(type: string, init: PointerEventInit = {}) {
    super(type, init);
    this.pointerId = init.pointerId ?? 0;
    this.pointerType = init.pointerType ?? '';
  }
}

beforeAll(() => {
  vi.stubGlobal('PointerEvent', TestPointerEvent);
});

afterAll(() => {
  vi.unstubAllGlobals();
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

function setMatrixBounds(grid: Element) {
  vi.spyOn(grid, 'getBoundingClientRect').mockReturnValue({
    x: 0, y: 0, top: 0, left: 0, right: 360, bottom: 756, width: 360, height: 756,
    toJSON: () => ({})
  } as DOMRect);
}

describe('DayView touch gestures', () => {
  it('shows bullet and checkbox details and toggles a checkbox without opening the editor', () => {
    const onEdit = vi.fn();
    const onToggleDetail = vi.fn(async () => undefined);
    const occurrence: ScheduleOccurrence = {
      key: 'shopping', seriesId: 'shopping', originalDate: '2026-07-13', date: '2026-07-13',
      title: '프로젝트 정리', subtasks: ['ㅡ 참고자료 검토', 'ㅁ 초안 작성', '☑ 공유 완료'], categoryId: 'work',
      startMinute: 18 * 60, durationMinute: 30, reminderMinute: null
    };
    const { container, getByRole } = render(<DayView {...baseProps} occurrences={[occurrence]} onAdd={vi.fn()} onEdit={onEdit} onToggleDetail={onToggleDetail} />);

    expect(container.querySelector('.event-detail-bullet')?.textContent).toBe('• 참고자료 검토');
    fireEvent.click(getByRole('button', { name: '초안 작성 완료' }));
    expect(onToggleDetail).toHaveBeenCalledWith(expect.objectContaining({ key: 'shopping' }), 1);
    expect(getByRole('button', { name: '공유 완료 완료 취소' })).toHaveAttribute('aria-pressed', 'true');
    expect(onEdit).not.toHaveBeenCalled();
  });

  it('stacks overlapping plans in thinner lanes without making the hour row taller', () => {
    const occurrences: ScheduleOccurrence[] = [
      { key: 'first', seriesId: 'first', originalDate: '2026-07-13', date: '2026-07-13', title: '14시 블록', categoryId: 'work', startMinute: 14 * 60, durationMinute: 60, reminderMinute: null },
      { key: 'second', seriesId: 'second', originalDate: '2026-07-13', date: '2026-07-13', title: '14시 05분 블록', categoryId: 'study', startMinute: 14 * 60 + 5, durationMinute: 45, reminderMinute: null }
    ];
    const { container } = render(<DayView {...baseProps} occurrences={occurrences} onAdd={vi.fn()} />);
    const blocks = Array.from(container.querySelectorAll<HTMLElement>('.matrix-event.stacked'));

    expect(blocks).toHaveLength(2);
    expect(blocks.map(block => block.style.height)).toEqual(['24px', '24px']);
    expect(blocks.map(block => block.style.top)).toEqual(['450px', '476px']);
    expect(blocks[1]?.style.left).not.toBe(blocks[0]?.style.left);
  });

  it('creates a new schedule when dragging immediately from an existing stacked block', () => {
    vi.useFakeTimers();
    const onAdd = vi.fn();
    const onEdit = vi.fn();
    const onMove = vi.fn(async () => undefined);
    const occurrences: ScheduleOccurrence[] = [
      { key: 'first', seriesId: 'first', originalDate: '2026-07-13', date: '2026-07-13', title: '14시 블록', categoryId: 'work', startMinute: 14 * 60, durationMinute: 60, reminderMinute: null },
      { key: 'second', seriesId: 'second', originalDate: '2026-07-13', date: '2026-07-13', title: '14시 05분 블록', categoryId: 'study', startMinute: 14 * 60 + 5, durationMinute: 45, reminderMinute: null }
    ];
    const { container } = render(<DayView {...baseProps} occurrences={occurrences} onAdd={onAdd} onEdit={onEdit} onMove={onMove} />);
    const grid = container.querySelector('.timeline-grid');
    const block = container.querySelector<HTMLElement>('[aria-label^="14시 블록,"]');
    expect(grid).not.toBeNull();
    expect(block).not.toBeNull();
    setMatrixBounds(grid!);

    fireEvent.pointerDown(block!, { pointerId: 10, pointerType: 'touch', clientX: 70, clientY: 450, button: 0 });
    fireEvent.pointerMove(block!, { pointerId: 10, pointerType: 'touch', clientX: 95, clientY: 450 });
    fireEvent.pointerUp(block!, { pointerId: 10, pointerType: 'touch', clientX: 95, clientY: 450 });
    fireEvent.click(block!);

    expect(onAdd).toHaveBeenLastCalledWith(840, 15);
    expect(onMove).not.toHaveBeenCalled();
    expect(onEdit).not.toHaveBeenCalled();
  });

  it('moves the selected stacked block only after a long press', () => {
    vi.useFakeTimers();
    const onAdd = vi.fn();
    const onMove = vi.fn(async () => undefined);
    const occurrences: ScheduleOccurrence[] = [
      { key: 'first', seriesId: 'first', originalDate: '2026-07-13', date: '2026-07-13', title: '14시 블록', categoryId: 'work', startMinute: 14 * 60, durationMinute: 60, reminderMinute: null },
      { key: 'second', seriesId: 'second', originalDate: '2026-07-13', date: '2026-07-13', title: '14시 05분 블록', categoryId: 'study', startMinute: 14 * 60 + 5, durationMinute: 45, reminderMinute: null }
    ];
    const { container } = render(<DayView {...baseProps} occurrences={occurrences} onAdd={onAdd} onMove={onMove} />);
    const grid = container.querySelector('.timeline-grid');
    const block = container.querySelector<HTMLElement>('[aria-label^="14시 05분 블록,"]');
    expect(grid).not.toBeNull();
    expect(block).not.toBeNull();
    setMatrixBounds(grid!);

    fireEvent.pointerDown(block!, { pointerId: 11, pointerType: 'touch', clientX: 95, clientY: 476, button: 0 });
    act(() => vi.advanceTimersByTime(450));
    fireEvent.pointerMove(block!, { pointerId: 11, pointerType: 'touch', clientX: 145, clientY: 476 });
    fireEvent.pointerUp(block!, { pointerId: 11, pointerType: 'touch', clientX: 145, clientY: 476 });

    expect(onMove).toHaveBeenCalledTimes(1);
    expect(onMove).toHaveBeenLastCalledWith(expect.objectContaining({ key: 'second' }), 855, 45);
    expect(onAdd).not.toHaveBeenCalled();
  });

  it('creates one 10-minute planning cell without pointer movement', () => {
    const onAdd = vi.fn();
    const { container } = render(<DayView {...baseProps} onAdd={onAdd} />);
    const grid = container.querySelector('.timeline-grid');
    expect(grid).not.toBeNull();
    setMatrixBounds(grid!);

    fireEvent.pointerDown(grid!, { pointerId: 9, pointerType: 'mouse', clientX: 80, clientY: 120, button: 0 });
    fireEvent.pointerUp(grid!, { pointerId: 9, pointerType: 'mouse', clientX: 80, clientY: 120 });

    expect(onAdd).toHaveBeenLastCalledWith(480, 10);
  });

  it('creates a block after a touch long-press and drag', () => {
    vi.useFakeTimers();
    const onAdd = vi.fn();
    const { container } = render(<DayView {...baseProps} onAdd={onAdd} />);
    const grid = container.querySelector('.timeline-grid');
    expect(grid).not.toBeNull();
    expect(container.querySelectorAll('.timeline-cell')).toHaveLength(108);
    setMatrixBounds(grid!);

    fireEvent.pointerDown(grid!, { pointerId: 1, pointerType: 'touch', clientX: 80, clientY: 120, button: 0 });
    act(() => vi.advanceTimersByTime(300));
    fireEvent.pointerMove(grid!, { pointerId: 1, pointerType: 'touch', clientX: 165, clientY: 120 });
    fireEvent.pointerUp(grid!, { pointerId: 1, pointerType: 'touch', clientX: 165, clientY: 120 });

    expect(onAdd).toHaveBeenCalledTimes(1);
    expect(onAdd).toHaveBeenLastCalledWith(480, 30);
  });

  it('continues a cell selection into the next hour row', () => {
    const onAdd = vi.fn();
    const { container } = render(<DayView {...baseProps} onAdd={onAdd} />);
    const grid = container.querySelector('.timeline-grid');
    expect(grid).not.toBeNull();
    setMatrixBounds(grid!);

    fireEvent.pointerDown(grid!, { pointerId: 7, pointerType: 'mouse', clientX: 340, clientY: 120, button: 0 });
    fireEvent.pointerMove(grid!, { pointerId: 7, pointerType: 'mouse', clientX: 160, clientY: 180 });
    fireEvent.pointerUp(grid!, { pointerId: 7, pointerType: 'mouse', clientX: 160, clientY: 180 });

    expect(onAdd).toHaveBeenLastCalledWith(535, 30);
  });

  it('treats an early vertical move as scrolling instead of creating an event', () => {
    vi.useFakeTimers();
    const onAdd = vi.fn();
    const { container } = render(<DayView {...baseProps} onAdd={onAdd} />);
    const grid = container.querySelector('.timeline-grid');
    expect(grid).not.toBeNull();
    setMatrixBounds(grid!);

    fireEvent.pointerDown(grid!, { pointerId: 2, pointerType: 'touch', clientX: 80, clientY: 120, button: 0 });
    fireEvent.pointerMove(grid!, { pointerId: 2, pointerType: 'touch', clientX: 80, clientY: 160 });
    act(() => vi.advanceTimersByTime(300));
    fireEvent.pointerUp(grid!, { pointerId: 2, pointerType: 'touch', clientX: 80, clientY: 160 });

    expect(onAdd).not.toHaveBeenCalled();
  });
});
