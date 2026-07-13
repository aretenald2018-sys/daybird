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

describe('DayView touch gestures', () => {
  it('creates a block after a touch long-press and drag', () => {
    vi.useFakeTimers();
    const onAdd = vi.fn();
    const { container } = render(<DayView {...baseProps} onAdd={onAdd} />);
    const grid = container.querySelector('.timeline-grid');
    expect(grid).not.toBeNull();

    fireEvent.pointerDown(grid!, { pointerId: 1, pointerType: 'touch', clientY: 120, button: 0 });
    act(() => vi.advanceTimersByTime(300));
    fireEvent.pointerMove(grid!, { pointerId: 1, pointerType: 'touch', clientY: 180 });
    fireEvent.pointerUp(grid!, { pointerId: 1, pointerType: 'touch', clientY: 180 });

    expect(onAdd).toHaveBeenCalledTimes(1);
  });

  it('treats an early vertical move as scrolling instead of creating an event', () => {
    vi.useFakeTimers();
    const onAdd = vi.fn();
    const { container } = render(<DayView {...baseProps} onAdd={onAdd} />);
    const grid = container.querySelector('.timeline-grid');
    expect(grid).not.toBeNull();

    fireEvent.pointerDown(grid!, { pointerId: 2, pointerType: 'touch', clientY: 120, button: 0 });
    fireEvent.pointerMove(grid!, { pointerId: 2, pointerType: 'touch', clientY: 160 });
    act(() => vi.advanceTimersByTime(300));
    fireEvent.pointerUp(grid!, { pointerId: 2, pointerType: 'touch', clientY: 160 });

    expect(onAdd).not.toHaveBeenCalled();
  });
});
