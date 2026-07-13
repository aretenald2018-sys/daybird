import { act, fireEvent, render } from '@testing-library/react';
import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { DayView } from '../App';
import { DEFAULT_CATEGORIES, DEFAULT_SETTINGS } from '../lib/domain';

const baseProps = {
  date: '2026-07-13',
  settings: DEFAULT_SETTINGS,
  categories: DEFAULT_CATEGORIES,
  occurrences: [],
  onDateChange: vi.fn(),
  onEdit: vi.fn(),
  onMove: vi.fn(async () => undefined)
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
  it('creates one 10-minute planning cell without pointer movement', () => {
    const onAdd = vi.fn();
    const { container } = render(<DayView {...baseProps} onAdd={onAdd} />);
    const grid = container.querySelector('.timeline-grid');
    expect(grid).not.toBeNull();
    setMatrixBounds(grid!);

    fireEvent.pointerDown(grid!, { pointerId: 9, pointerType: 'mouse', clientX: 90, clientY: 120, button: 0 });
    fireEvent.pointerUp(grid!, { pointerId: 9, pointerType: 'mouse', clientX: 90, clientY: 120 });

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

    fireEvent.pointerDown(grid!, { pointerId: 1, pointerType: 'touch', clientX: 90, clientY: 120, button: 0 });
    act(() => vi.advanceTimersByTime(300));
    fireEvent.pointerMove(grid!, { pointerId: 1, pointerType: 'touch', clientX: 210, clientY: 120 });
    fireEvent.pointerUp(grid!, { pointerId: 1, pointerType: 'touch', clientX: 210, clientY: 120 });

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
    fireEvent.pointerMove(grid!, { pointerId: 7, pointerType: 'mouse', clientX: 160, clientY: 162 });
    fireEvent.pointerUp(grid!, { pointerId: 7, pointerType: 'mouse', clientX: 160, clientY: 162 });

    expect(onAdd).toHaveBeenLastCalledWith(530, 30);
  });

  it('treats an early vertical move as scrolling instead of creating an event', () => {
    vi.useFakeTimers();
    const onAdd = vi.fn();
    const { container } = render(<DayView {...baseProps} onAdd={onAdd} />);
    const grid = container.querySelector('.timeline-grid');
    expect(grid).not.toBeNull();
    setMatrixBounds(grid!);

    fireEvent.pointerDown(grid!, { pointerId: 2, pointerType: 'touch', clientX: 90, clientY: 120, button: 0 });
    fireEvent.pointerMove(grid!, { pointerId: 2, pointerType: 'touch', clientX: 90, clientY: 160 });
    act(() => vi.advanceTimersByTime(300));
    fireEvent.pointerUp(grid!, { pointerId: 2, pointerType: 'touch', clientX: 90, clientY: 160 });

    expect(onAdd).not.toHaveBeenCalled();
  });
});
