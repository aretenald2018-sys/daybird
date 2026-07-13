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
  it('uses an accessible saturated blue treatment for a completed schedule', () => {
    const occurrence: ScheduleOccurrence = {
      key: 'done', seriesId: 'done', originalDate: '2026-07-13', date: '2026-07-13',
      title: '완료한 일정', categoryId: 'work', startMinute: 18 * 60, durationMinute: 30, reminderMinute: null, completed: true
    };
    const { container, getByRole } = render(<DayView {...baseProps} occurrences={[occurrence]} onAdd={vi.fn()} />);
    const block = getByRole('button', { name: /완료한 일정, 완료됨/ });

    expect(block).toHaveClass('completed');
    expect((block as HTMLElement).style.getPropertyValue('--event-bg')).toBe('#0A63D8');
    expect(container.querySelector('.event-block.completed strong')?.textContent).toBe('완료한 일정');
  });

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
    expect(blocks.map(block => block.style.height)).toEqual(['20px', '20px']);
    expect(blocks.map(block => block.style.top)).toEqual(['386px', '408px']);
    expect(blocks[1]?.style.left).not.toBe(blocks[0]?.style.left);
  });

  it('cancels an early horizontal block drag instead of creating or moving a schedule', () => {
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

    fireEvent.pointerDown(block!, { pointerId: 10, pointerType: 'touch', clientX: 70, clientY: 388, button: 0 });
    fireEvent.pointerMove(block!, { pointerId: 10, pointerType: 'touch', clientX: 95, clientY: 388 });
    fireEvent.pointerUp(block!, { pointerId: 10, pointerType: 'touch', clientX: 95, clientY: 388 });
    fireEvent.click(block!);

    expect(onAdd).not.toHaveBeenCalled();
    expect(onMove).not.toHaveBeenCalled();
    expect(onEdit).not.toHaveBeenCalled();
  });

  it('scrolls the timeline when a block gesture moves vertically before the long press', () => {
    vi.useFakeTimers();
    const onAdd = vi.fn();
    const onEdit = vi.fn();
    const onMove = vi.fn(async () => undefined);
    const occurrence: ScheduleOccurrence = {
      key: 'scroll', seriesId: 'scroll', originalDate: '2026-07-13', date: '2026-07-13',
      title: '스크롤 테스트', categoryId: 'work', startMinute: 14 * 60, durationMinute: 60, reminderMinute: null
    };
    const { container } = render(<DayView {...baseProps} occurrences={[occurrence]} onAdd={onAdd} onEdit={onEdit} onMove={onMove} />);
    const grid = container.querySelector('.timeline-grid');
    const scroller = container.querySelector<HTMLElement>('.timeline-scroll');
    const block = container.querySelector<HTMLElement>('[aria-label^="스크롤 테스트,"]');
    expect(grid).not.toBeNull();
    expect(scroller).not.toBeNull();
    expect(block).not.toBeNull();
    setMatrixBounds(grid!);
    const initialScrollTop = scroller?.scrollTop ?? 0;

    fireEvent.pointerDown(block!, { pointerId: 14, pointerType: 'touch', clientX: 95, clientY: 390, button: 0 });
    fireEvent.pointerMove(block!, { pointerId: 14, pointerType: 'touch', clientX: 95, clientY: 340 });
    act(() => vi.advanceTimersByTime(450));
    fireEvent.pointerUp(block!, { pointerId: 14, pointerType: 'touch', clientX: 95, clientY: 340 });
    fireEvent.click(block!);

    expect(scroller?.scrollTop).toBeCloseTo(initialScrollTop + 50);
    expect(onAdd).not.toHaveBeenCalled();
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

    fireEvent.pointerDown(block!, { pointerId: 11, pointerType: 'touch', clientX: 95, clientY: 410, button: 0 });
    act(() => vi.advanceTimersByTime(450));
    fireEvent.pointerMove(block!, { pointerId: 11, pointerType: 'touch', clientX: 145, clientY: 410 });
    fireEvent.pointerUp(block!, { pointerId: 11, pointerType: 'touch', clientX: 145, clientY: 410 });

    expect(onMove).toHaveBeenCalledTimes(1);
    expect(onMove).toHaveBeenLastCalledWith(expect.objectContaining({ key: 'second' }), 855, 45);
    expect(onAdd).not.toHaveBeenCalled();
  });

  it('creates a standard 30-minute event by tapping an empty time', () => {
    const onAdd = vi.fn();
    const { container } = render(<DayView {...baseProps} onAdd={onAdd} />);
    const grid = container.querySelector('.timeline-grid');
    expect(grid).not.toBeNull();
    setMatrixBounds(grid!);

    fireEvent.pointerDown(grid!, { pointerId: 9, pointerType: 'mouse', clientX: 80, clientY: 120, button: 0 });
    fireEvent.pointerUp(grid!, { pointerId: 9, pointerType: 'mouse', clientX: 80, clientY: 120 });

    expect(onAdd).toHaveBeenLastCalledWith(480, 30);
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
    act(() => vi.advanceTimersByTime(400));
    fireEvent.pointerMove(grid!, { pointerId: 1, pointerType: 'touch', clientX: 165, clientY: 120 });
    fireEvent.pointerUp(grid!, { pointerId: 1, pointerType: 'touch', clientX: 165, clientY: 120 });

    expect(onAdd).toHaveBeenCalledTimes(1);
    expect(onAdd).toHaveBeenLastCalledWith(480, 30);
  });

  it('reveals resize handles after a long press and resizes with a direct handle drag', () => {
    vi.useFakeTimers();
    const onMove = vi.fn(async () => undefined);
    const occurrence: ScheduleOccurrence = {
      key: 'resize', seriesId: 'resize', originalDate: '2026-07-13', date: '2026-07-13',
      title: '길이 조절', categoryId: 'work', startMinute: 14 * 60 + 5, durationMinute: 45, reminderMinute: null
    };
    const { container } = render(<DayView {...baseProps} occurrences={[occurrence]} onAdd={vi.fn()} onMove={onMove} />);
    const grid = container.querySelector('.timeline-grid');
    const block = container.querySelector<HTMLElement>('[aria-label^="길이 조절,"]');
    expect(grid).not.toBeNull();
    expect(block).not.toBeNull();
    setMatrixBounds(grid!);

    fireEvent.pointerDown(block!, { pointerId: 12, pointerType: 'touch', clientX: 95, clientY: 390, button: 0 });
    act(() => vi.advanceTimersByTime(450));
    fireEvent.pointerUp(block!, { pointerId: 12, pointerType: 'touch', clientX: 95, clientY: 390 });
    const endHandle = container.querySelector<HTMLElement>('.resize-handle.end');
    expect(endHandle).not.toBeNull();

    fireEvent.pointerDown(endHandle!, { pointerId: 13, pointerType: 'touch', clientX: 310, clientY: 390, button: 0 });
    fireEvent.pointerMove(endHandle!, { pointerId: 13, pointerType: 'touch', clientX: 340, clientY: 390 });
    fireEvent.pointerUp(endHandle!, { pointerId: 13, pointerType: 'touch', clientX: 340, clientY: 390 });

    expect(onMove).toHaveBeenLastCalledWith(expect.objectContaining({ key: 'resize' }), 845, 55);
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
