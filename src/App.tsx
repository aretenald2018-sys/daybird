import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Bell,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Download,
  Focus,
  MoreHorizontal,
  Pause,
  Play,
  Plus,
  RotateCcw,
  Settings,
  SlidersHorizontal,
  Square,
  TimerReset,
  Trash2,
  Upload
} from 'lucide-react';
import { registerSW } from 'virtual:pwa-register';
import { db, exportBackup, getRecoveryBackupInfo, importBackup, loadSnapshot, resetDatabase, restoreRecoveryBackup, saveRecoveryBackup, type DayBirdSnapshot } from './lib/db';
import {
  DEFAULT_SETTINGS,
  addDays,
  dateKey,
  expandOccurrences,
  formatDate,
  formatDuration,
  formatMinute,
  id,
  parseDateKey,
  remainingSeconds,
  segmentsForDate,
  startOfWeek,
  type AppSettings,
  type Category,
  type FocusMode,
  type FocusSession,
  type OccurrenceOverride,
  type ScheduleOccurrence,
  type ScheduleSeries
} from './lib/domain';
import {
  cancelFocusNotification,
  isNative,
  lightHaptic,
  notificationCapability,
  openExactAlarmSettings,
  requestNotificationPermission,
  scheduleFocusNotification,
  syncScheduleNotifications,
  type NotificationCapability
} from './lib/platform';
import { syncDayBirdWidgets } from './lib/widgets';

type Tab = 'day' | 'week' | 'focus' | 'settings';
type Toast = { id: number; message: string };
const TIME_CELL_MINUTE = 10;
const TIME_GRID_SNAP_MINUTE = 5;
const TIME_MATRIX_COLUMNS = 6;
const TIME_GRID_GUTTER = 64;
const BLOCK_MOVE_LONG_PRESS_MS = 420;
const POINTER_DRAG_THRESHOLD = 7;
const CLICK_SUPPRESS_MS = 700;

const EMPTY_SNAPSHOT: DayBirdSnapshot = {
  categories: [], schedules: [], overrides: [], focusSessions: [], settings: DEFAULT_SETTINGS
};

function rgba(hex: string, alpha: number): string {
  const value = hex.replace('#', '');
  const r = Number.parseInt(value.slice(0, 2), 16);
  const g = Number.parseInt(value.slice(2, 4), 16);
  const b = Number.parseInt(value.slice(4, 6), 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function minuteFromTime(value: string): number {
  const [hour, minute] = value.split(':').map(Number);
  return hour * 60 + minute;
}

function timeFromMinute(value: number): string {
  const normalized = Math.max(0, Math.min(1439, value));
  return `${String(Math.floor(normalized / 60)).padStart(2, '0')}:${String(normalized % 60).padStart(2, '0')}`;
}

interface EventEditorState {
  occurrence: ScheduleOccurrence | null;
  draftDate: string;
  draftStart: number;
  draftDuration: number;
}

export default function App() {
  const [snapshot, setSnapshot] = useState<DayBirdSnapshot>(EMPTY_SNAPSHOT);
  const [ready, setReady] = useState(false);
  const [tab, setTab] = useState<Tab>('day');
  const [selectedDate, setSelectedDate] = useState(dateKey());
  const [editor, setEditor] = useState<EventEditorState | null>(null);
  const [toast, setToast] = useState<Toast | null>(null);
  const [installPrompt, setInstallPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [needRefresh, setNeedRefresh] = useState(false);
  const updateServiceWorkerRef = useRef<(reloadPage?: boolean) => Promise<void>>(() => Promise.resolve());

  const refresh = useCallback(async () => {
    const next = await loadSnapshot();
    await saveRecoveryBackup(next);
    await syncDayBirdWidgets(next);
    setSnapshot(next);
    setReady(true);
  }, []);

  const notify = useCallback((message: string) => {
    const item = { id: Date.now(), message };
    setToast(item);
    window.setTimeout(() => setToast(current => current?.id === item.id ? null : current), 2600);
  }, []);

  useEffect(() => {
    void refresh();
    const onInstall = (event: BeforeInstallPromptEvent) => {
      event.preventDefault();
      setInstallPrompt(event);
    };
    window.addEventListener('beforeinstallprompt', onInstall);
    if ('serviceWorker' in navigator) {
      updateServiceWorkerRef.current = registerSW({
        onNeedRefresh: () => setNeedRefresh(true),
        onOfflineReady: () => notify('오프라인에서도 사용할 준비가 됐어요.')
      });
    }
    return () => window.removeEventListener('beforeinstallprompt', onInstall);
  }, [notify, refresh]);

  useEffect(() => {
    if (!ready) return;
    document.documentElement.dataset.fontScale = snapshot.settings.fontScale;
    document.documentElement.dataset.density = snapshot.settings.blockDensity;
  }, [ready, snapshot.settings]);

  useEffect(() => {
    if (!ready || !isNative) return;
    void syncScheduleNotifications(snapshot.schedules, snapshot.overrides);
  }, [ready, snapshot.schedules, snapshot.overrides]);

  const occurrenceRange = useMemo(() => {
    const start = addDays(startOfWeek(selectedDate), -1);
    return expandOccurrences(snapshot.schedules, snapshot.overrides, start, addDays(start, 9));
  }, [selectedDate, snapshot.overrides, snapshot.schedules]);

  const openDate = (value: string) => {
    setSelectedDate(value);
    setTab('day');
  };

  const openEditor = (occurrence: ScheduleOccurrence | null, date = selectedDate, start = 9 * 60, duration = 60) => {
    setEditor({ occurrence, draftDate: date, draftStart: start, draftDuration: duration });
  };

  const quickUpdateOccurrence = async (occurrence: ScheduleOccurrence, startMinute: number, durationMinute: number) => {
    const series = snapshot.schedules.find(item => item.id === occurrence.seriesId);
    if (!series) return;
    if (series.recurrence.kind === 'none') {
      await db.schedules.update(series.id, { startMinute, durationMinute, updatedAt: Date.now() });
      notify('일정 시간을 조정했어요.');
    } else {
      await db.overrides.put({
        id: `${series.id}:${occurrence.originalDate}`,
        seriesId: series.id,
        occurrenceDate: occurrence.originalDate,
        action: 'modified',
        patch: {
          title: occurrence.title,
          subtasks: occurrence.subtasks ?? [],
          categoryId: occurrence.categoryId,
          date: occurrence.date,
          startMinute,
          durationMinute,
          reminderMinute: occurrence.reminderMinute
        }
      });
      notify('이번 반복 일정의 시간만 조정했어요.');
    }
    await refresh();
  };

  if (!ready) {
    return (
      <main className="loading-screen" aria-label="DayBird 불러오는 중">
        <div className="brand-mark" aria-hidden="true"><span /><span /></div>
        <strong>DayBird</strong>
        <div className="loading-bar"><span /></div>
      </main>
    );
  }

  return (
    <div className="app-shell">
      <main className="app-main">
        {tab === 'day' && (
          <DayView
            date={selectedDate}
            settings={snapshot.settings}
            categories={snapshot.categories}
            occurrences={occurrenceRange}
            onDateChange={setSelectedDate}
            onAdd={(start, duration) => openEditor(null, selectedDate, start, duration)}
            onEdit={occurrence => openEditor(occurrence, occurrence.date, occurrence.startMinute, occurrence.durationMinute)}
            onMove={quickUpdateOccurrence}
          />
        )}
        {tab === 'week' && (
          <WeekView
            date={selectedDate}
            settings={snapshot.settings}
            categories={snapshot.categories}
            occurrences={occurrenceRange}
            onDateChange={setSelectedDate}
            onOpenDate={openDate}
          />
        )}
        {tab === 'focus' && (
          <FocusView
            snapshot={snapshot}
            onRefresh={refresh}
            notify={notify}
          />
        )}
        {tab === 'settings' && (
          <SettingsView
            snapshot={snapshot}
            installPrompt={installPrompt}
            onInstalled={() => setInstallPrompt(null)}
            onRefresh={refresh}
            notify={notify}
          />
        )}
      </main>

      <nav className="tab-bar" aria-label="주요 메뉴">
        <TabButton active={tab === 'day'} icon={<Clock3 />} label="오늘" onClick={() => setTab('day')} />
        <TabButton active={tab === 'week'} icon={<CalendarDays />} label="주간" onClick={() => setTab('week')} />
        <TabButton active={tab === 'focus'} icon={<Focus />} label="포커스" onClick={() => setTab('focus')} />
        <TabButton active={tab === 'settings'} icon={<Settings />} label="설정" onClick={() => setTab('settings')} />
      </nav>

      {tab === 'day' && (
        <button className="floating-add" type="button" aria-label="새 일정 추가" onClick={() => openEditor(null)}>
          <Plus />
        </button>
      )}

      {editor && (
        <EventEditor
          state={editor}
          snapshot={snapshot}
          onClose={() => setEditor(null)}
          onSaved={async message => {
            setEditor(null);
            await refresh();
            notify(message);
          }}
        />
      )}

      {toast && <div className="toast" role="status"><Check />{toast.message}</div>}
      {needRefresh && (
        <div className="update-banner" role="status">
          <span>새 버전이 준비됐어요.</span>
          <button type="button" onClick={() => void updateServiceWorkerRef.current(true)}>업데이트</button>
        </div>
      )}
    </div>
  );
}

function TabButton({ active, icon, label, onClick }: { active: boolean; icon: React.ReactNode; label: string; onClick: () => void }) {
  return (
    <button type="button" className={active ? 'active' : ''} aria-current={active ? 'page' : undefined} onClick={onClick}>
      {icon}<span>{label}</span>
    </button>
  );
}

export function DayView({ date, settings, categories, occurrences, onDateChange, onAdd, onEdit, onMove }: {
  date: string;
  settings: AppSettings;
  categories: Category[];
  occurrences: ScheduleOccurrence[];
  onDateChange: (date: string) => void;
  onAdd: (start: number, duration: number) => void;
  onEdit: (occurrence: ScheduleOccurrence) => void;
  onMove: (occurrence: ScheduleOccurrence, startMinute: number, durationMinute: number) => Promise<void>;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const hourHeight = settings.blockDensity === 'compact' ? 34 : 42;
  const startMinute = settings.dayStartHour * 60;
  const endMinute = settings.dayEndHour * 60;
  const daySegments = useMemo(() => segmentsForDate(occurrences, date), [date, occurrences]);
  const rangeStart = Math.floor(Math.min(startMinute, ...daySegments.map(item => item.segmentStart)) / 60) * 60;
  const rangeEnd = Math.ceil(Math.max(endMinute, ...daySegments.map(item => item.segmentEnd)) / 60) * 60;
  const hours = Array.from({ length: Math.ceil((rangeEnd - rangeStart) / 60) + 1 }, (_, index) => Math.floor(rangeStart / 60) + index);
  const timeCells = Array.from({ length: (rangeEnd - rangeStart) / TIME_CELL_MINUTE }, (_, index) => rangeStart + index * TIME_CELL_MINUTE);
  const categoryMap = new Map(categories.map(category => [category.id, category]));
  const dragRef = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    startMinute: number;
    startScrollTop: number;
    state: 'pending' | 'dragging' | 'scrolling';
    longPressTimer: number | null;
  } | null>(null);
  const [dragPreview, setDragPreview] = useState<{ start: number; end: number } | null>(null);
  const dragPreviewRef = useRef<{ start: number; end: number } | null>(null);
  const blockDragRef = useRef<{
    pointerId: number;
    startX: number;
    startY: number;
    startMinute: number;
    anchorMinute: number;
    occurrence: ScheduleOccurrence;
    originalStart: number;
    originalDuration: number;
    mode: 'move' | 'resize-start' | 'resize-end';
    state: 'pending' | 'creating' | 'moving';
    longPressTimer: number | null;
    changed: boolean;
  } | null>(null);
  const suppressClickUntilRef = useRef(0);
  const [blockPreview, setBlockPreview] = useState<{ key: string; start: number; duration: number } | null>(null);
  const blockPreviewRef = useRef<{ key: string; start: number; duration: number } | null>(null);

  const updateDragPreview = (next: { start: number; end: number } | null) => {
    dragPreviewRef.current = next;
    setDragPreview(next);
  };

  const updateBlockPreview = (next: { key: string; start: number; duration: number } | null) => {
    blockPreviewRef.current = next;
    setBlockPreview(next);
  };

  const safelyCapturePointer = (element: HTMLElement, pointerId: number) => {
    try {
      element.setPointerCapture(pointerId);
    } catch {
      // Some embedded WebViews can end the pointer before capture is applied.
    }
  };

  const minuteAtPointer = (clientX: number, clientY: number) => {
    const rect = gridRef.current?.getBoundingClientRect();
    if (!rect) return rangeStart;
    const gridWidth = Math.max(1, rect.width - TIME_GRID_GUTTER);
    const snapColumns = TIME_MATRIX_COLUMNS * (TIME_CELL_MINUTE / TIME_GRID_SNAP_MINUTE);
    const columnWidth = gridWidth / snapColumns;
    const column = Math.max(0, Math.min(snapColumns - 1, Math.floor((clientX - rect.left - TIME_GRID_GUTTER) / columnWidth)));
    const row = Math.max(0, Math.min((rangeEnd - rangeStart) / 60 - 1, Math.floor((clientY - rect.top) / hourHeight)));
    return rangeStart + row * 60 + column * TIME_GRID_SNAP_MINUTE;
  };

  const matrixPieces = (start: number, end: number) => {
    const safeStart = Math.max(rangeStart, Math.min(rangeEnd - TIME_GRID_SNAP_MINUTE, Math.floor(start / TIME_GRID_SNAP_MINUTE) * TIME_GRID_SNAP_MINUTE));
    const safeEnd = Math.max(safeStart + TIME_GRID_SNAP_MINUTE, Math.min(rangeEnd, Math.ceil(end / TIME_GRID_SNAP_MINUTE) * TIME_GRID_SNAP_MINUTE));
    const firstRow = Math.floor((safeStart - rangeStart) / 60);
    const lastRow = Math.floor((safeEnd - TIME_GRID_SNAP_MINUTE - rangeStart) / 60);
    return Array.from({ length: lastRow - firstRow + 1 }, (_, index) => {
      const row = firstRow + index;
      const rowStart = rangeStart + row * 60;
      return {
        row,
        start: Math.max(safeStart, rowStart),
        end: Math.min(safeEnd, rowStart + 60),
        isFirst: row === firstRow,
        isLast: row === lastRow
      };
    });
  };

  const matrixPieceStyle = (piece: ReturnType<typeof matrixPieces>[number], lane = 0, laneCount = 1) => {
    const startRatio = (piece.start - (rangeStart + piece.row * 60)) / 60;
    const widthRatio = (piece.end - piece.start) / 60;
    const laneHeight = (hourHeight - 4) / laneCount;
    return {
      top: `${piece.row * hourHeight + 2 + lane * laneHeight}px`,
      height: `${Math.max(10, laneHeight - 2)}px`,
      left: `calc(${TIME_GRID_GUTTER}px + ${startRatio * 100}% - ${TIME_GRID_GUTTER * startRatio}px)`,
      width: `calc(${widthRatio * 100}% - ${TIME_GRID_GUTTER * widthRatio}px - 3px)`,
      zIndex: 5 + lane
    } as React.CSSProperties;
  };

  const clearTimelineGesture = () => {
    const timer = dragRef.current?.longPressTimer;
    if (timer != null) window.clearTimeout(timer);
    dragRef.current = null;
    updateDragPreview(null);
  };

  const clearBlockGesture = () => {
    const timer = blockDragRef.current?.longPressTimer;
    if (timer != null) window.clearTimeout(timer);
    blockDragRef.current = null;
    updateBlockPreview(null);
    updateDragPreview(null);
  };

  const handlePointerDown = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0 || (event.target as HTMLElement).closest('.event-block')) return;
    const rect = event.currentTarget.getBoundingClientRect();
    if (event.clientX < rect.left + TIME_GRID_GUTTER) return;
    const minute = minuteAtPointer(event.clientX, event.clientY);
    safelyCapturePointer(event.currentTarget, event.pointerId);
    const isTouch = event.pointerType === 'touch';
    const gesture = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      startMinute: minute,
      startScrollTop: scrollRef.current?.scrollTop ?? 0,
      state: isTouch ? 'pending' as const : 'dragging' as const,
      longPressTimer: null as number | null
    };
    dragRef.current = gesture;
    if (isTouch) {
      gesture.longPressTimer = window.setTimeout(() => {
        if (dragRef.current !== gesture || gesture.state !== 'pending') return;
        gesture.state = 'dragging';
        gesture.longPressTimer = null;
        updateDragPreview({ start: minute, end: minute + TIME_CELL_MINUTE });
        void lightHaptic();
      }, 280);
    } else {
      updateDragPreview({ start: minute, end: minute + TIME_CELL_MINUTE });
    }
  };

  const handlePointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    if (!dragRef.current || dragRef.current.pointerId !== event.pointerId) return;
    const gesture = dragRef.current;
    const verticalDelta = event.clientY - gesture.startY;
    const horizontalDelta = event.clientX - gesture.startX;
    if (gesture.state === 'pending' && Math.abs(verticalDelta) > 6 && Math.abs(verticalDelta) >= Math.abs(horizontalDelta)) {
      if (gesture.longPressTimer !== null) window.clearTimeout(gesture.longPressTimer);
      gesture.longPressTimer = null;
      gesture.state = 'scrolling';
    }
    if (gesture.state === 'scrolling') {
      if (scrollRef.current) scrollRef.current.scrollTop = gesture.startScrollTop - verticalDelta;
      return;
    }
    if (gesture.state !== 'dragging') return;
    const current = minuteAtPointer(event.clientX, event.clientY);
    updateDragPreview(current >= gesture.startMinute
      ? { start: gesture.startMinute, end: current + TIME_CELL_MINUTE }
      : { start: current, end: gesture.startMinute + TIME_CELL_MINUTE });
  };

  const handlePointerUp = (event: React.PointerEvent<HTMLDivElement>) => {
    const gesture = dragRef.current;
    if (!gesture || gesture.pointerId !== event.pointerId) return;
    const preview = dragPreviewRef.current;
    const shouldCreate = gesture.state === 'dragging' && preview !== null;
    clearTimelineGesture();
    if (!shouldCreate || !preview) return;
    void lightHaptic();
    onAdd(preview.start, Math.max(TIME_CELL_MINUTE, preview.end - preview.start));
  };

  const startBlockDrag = (event: React.PointerEvent<HTMLElement>, occurrence: ScheduleOccurrence, mode: 'move' | 'resize-start' | 'resize-end') => {
    if (event.button !== 0) return;
    event.stopPropagation();
    safelyCapturePointer(event.currentTarget, event.pointerId);
    const minute = minuteAtPointer(event.clientX, event.clientY);
    const gesture: NonNullable<typeof blockDragRef.current> = {
      pointerId: event.pointerId,
      startX: event.clientX,
      startY: event.clientY,
      startMinute: minute,
      anchorMinute: minute,
      occurrence,
      originalStart: occurrence.startMinute,
      originalDuration: occurrence.durationMinute,
      mode,
      state: 'pending',
      longPressTimer: null as number | null,
      changed: false
    };
    blockDragRef.current = gesture;
    gesture.longPressTimer = window.setTimeout(() => {
      if (blockDragRef.current !== gesture || gesture.state !== 'pending') return;
      gesture.state = 'moving';
      gesture.longPressTimer = null;
      updateBlockPreview({ key: occurrence.key, start: occurrence.startMinute, duration: occurrence.durationMinute });
      void lightHaptic();
    }, BLOCK_MOVE_LONG_PRESS_MS);
  };

  const moveBlockDrag = (event: React.PointerEvent<HTMLElement>) => {
    const drag = blockDragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    const distance = Math.hypot(event.clientX - drag.startX, event.clientY - drag.startY);
    if (drag.state === 'pending') {
      if (distance < POINTER_DRAG_THRESHOLD) return;
      if (drag.longPressTimer !== null) window.clearTimeout(drag.longPressTimer);
      drag.longPressTimer = null;
      drag.state = 'creating';
      const current = minuteAtPointer(event.clientX, event.clientY);
      updateDragPreview(current >= drag.startMinute
        ? { start: drag.startMinute, end: current + TIME_CELL_MINUTE }
        : { start: current, end: drag.startMinute + TIME_CELL_MINUTE });
      return;
    }
    if (drag.state === 'creating') {
      const current = minuteAtPointer(event.clientX, event.clientY);
      updateDragPreview(current >= drag.startMinute
        ? { start: drag.startMinute, end: current + TIME_CELL_MINUTE }
        : { start: current, end: drag.startMinute + TIME_CELL_MINUTE });
      return;
    }
    if (drag.state !== 'moving') return;
    const rawDelta = minuteAtPointer(event.clientX, event.clientY) - drag.anchorMinute;
    const delta = Math.round(rawDelta / TIME_GRID_SNAP_MINUTE) * TIME_GRID_SNAP_MINUTE;
    if (Math.abs(delta) >= TIME_GRID_SNAP_MINUTE) drag.changed = true;
    let nextStart = drag.originalStart;
    let nextDuration = drag.originalDuration;
    if (drag.mode === 'move') {
      nextStart = Math.max(0, Math.min(1440 - TIME_GRID_SNAP_MINUTE, drag.originalStart + delta));
    } else if (drag.mode === 'resize-start') {
      const end = drag.originalStart + drag.originalDuration;
      nextStart = Math.max(0, Math.min(end - TIME_GRID_SNAP_MINUTE, drag.originalStart + delta));
      nextDuration = end - nextStart;
    } else {
      nextDuration = Math.max(TIME_GRID_SNAP_MINUTE, Math.min(1440, drag.originalDuration + delta));
    }
    updateBlockPreview({ key: drag.occurrence.key, start: nextStart, duration: nextDuration });
  };

  const endBlockDrag = (event: React.PointerEvent<HTMLElement>) => {
    const drag = blockDragRef.current;
    if (!drag || drag.pointerId !== event.pointerId) return;
    if (drag.longPressTimer !== null) window.clearTimeout(drag.longPressTimer);
    const selection = dragPreviewRef.current;
    const preview = blockPreviewRef.current;
    blockDragRef.current = null;
    updateDragPreview(null);
    updateBlockPreview(null);
    if (drag.state === 'creating' && selection) {
      suppressClickUntilRef.current = Date.now() + CLICK_SUPPRESS_MS;
      void lightHaptic();
      onAdd(selection.start, Math.max(TIME_CELL_MINUTE, selection.end - selection.start));
      return;
    }
    if (drag.state !== 'moving') return;
    suppressClickUntilRef.current = Date.now() + CLICK_SUPPRESS_MS;
    if (!drag.changed || !preview) return;
    void lightHaptic();
    void onMove(drag.occurrence, preview.start, preview.duration);
  };

  useEffect(() => {
    if (date !== dateKey() || !scrollRef.current) return;
    const now = new Date();
    const minute = now.getHours() * 60 + now.getMinutes();
    const target = Math.max(0, ((minute - rangeStart) / 60) * hourHeight - 220);
    scrollRef.current.scrollTop = target;
  }, [date, hourHeight, rangeStart]);

  useEffect(() => () => {
    const timer = dragRef.current?.longPressTimer;
    if (timer != null) window.clearTimeout(timer);
    const blockTimer = blockDragRef.current?.longPressTimer;
    if (blockTimer != null) window.clearTimeout(blockTimer);
  }, []);

  const isToday = date === dateKey();
  const now = new Date();
  const nowMinute = now.getHours() * 60 + now.getMinutes();

  return (
    <section className="screen day-screen">
      <header className="screen-header day-header">
        <div>
          <p className="eyebrow">{isToday ? '오늘' : formatDate(date, { year: 'numeric', month: 'long' })}</p>
          <h1>{formatDate(date, { month: '2-digit', day: '2-digit' })}<small>{formatDate(date, { weekday: 'short' })}</small></h1>
        </div>
        <div className="header-actions">
          <button className="icon-button" type="button" aria-label="이전 날짜" onClick={() => onDateChange(addDays(date, -1))}><ChevronLeft /></button>
          <button className="today-button" type="button" onClick={() => onDateChange(dateKey())}>오늘</button>
          <button className="icon-button" type="button" aria-label="다음 날짜" onClick={() => onDateChange(addDays(date, 1))}><ChevronRight /></button>
        </div>
      </header>

      <div className="timeline-scroll" ref={scrollRef}>
        <div
          className="timeline-grid"
          style={{
            height: `${((rangeEnd - rangeStart) / 60) * hourHeight}px`,
            '--time-row-height': `${hourHeight}px`
          } as React.CSSProperties}
          ref={gridRef}
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={handlePointerUp}
          onPointerCancel={clearTimelineGesture}
          onContextMenu={event => event.preventDefault()}
        >
          <div className="time-cell-grid" aria-hidden="true">
            {timeCells.map(minute => <span className="timeline-cell" key={minute} />)}
          </div>
          {hours.map(hour => (
            <div className="hour-line" key={hour} style={{ top: `${((hour * 60 - rangeStart) / 60) * hourHeight}px` }}>
              <span>{hour === 24 ? '24:00' : `${String(hour).padStart(2, '0')}:00`}</span>
            </div>
          ))}

          {daySegments.flatMap(segment => {
            const category = categoryMap.get(segment.categoryId) ?? categories.at(-1);
            const preview = blockPreview?.key === segment.key ? blockPreview : null;
            const displayStart = preview?.start ?? segment.segmentStart;
            const displayEnd = preview ? preview.start + preview.duration : segment.segmentEnd;
            return matrixPieces(displayStart, displayEnd).map(piece => (
              <button
                key={`${segment.key}:${piece.row}`}
                type="button"
                className={`event-block matrix-event${piece.isFirst ? ' starts-here' : ''}${piece.isLast ? ' ends-here' : ''}${segment.laneCount > 1 ? ' stacked' : ''}${segment.laneCount > 2 ? ' dense' : ''}`}
                style={{
                  ...matrixPieceStyle(piece, segment.lane, segment.laneCount),
                  '--event-color': category?.color ?? '#8D94A0',
                  '--event-bg': rgba(category?.color ?? '#8D94A0', 0.2),
                  textAlign: settings.textAlign
                } as React.CSSProperties}
                aria-label={`${segment.title}${segment.subtasks?.length ? `, 소항목 ${segment.subtasks.join(', ')}` : ''}, ${formatMinute(segment.startMinute)}부터 ${formatMinute(segment.startMinute + segment.durationMinute)}까지`}
                onPointerDown={event => startBlockDrag(event, segment, 'move')}
                onPointerMove={moveBlockDrag}
                onPointerUp={endBlockDrag}
                onPointerCancel={clearBlockGesture}
                onContextMenu={event => event.preventDefault()}
                onClick={() => {
                  if (Date.now() < suppressClickUntilRef.current) return;
                  onEdit(segment);
                }}
              >
                {piece.isFirst && <span className="resize-handle start" aria-hidden="true" onPointerDown={event => startBlockDrag(event, segment, 'resize-start')} />}
                {piece.isFirst && <>
                  <strong>{segment.title}</strong>
                  {!!segment.subtasks?.length && <span className="event-subtasks">{segment.subtasks.map(item => `• ${item}`).join('  ')}</span>}
                  {segment.laneCount === 1 && (displayEnd - displayStart) >= 20 && <span className="event-time">{formatMinute(displayStart)}–{formatMinute(displayEnd)}</span>}
                </>}
                {piece.isLast && <span className="resize-handle end" aria-hidden="true" onPointerDown={event => startBlockDrag(event, segment, 'resize-end')} />}
              </button>
            ));
          })}

          {dragPreview && (
            matrixPieces(dragPreview.start, dragPreview.end).map(piece => (
              <div className="drag-preview matrix-selection" key={piece.row} style={matrixPieceStyle(piece)}>
                {piece.isFirst && `${formatMinute(dragPreview.start)} – ${formatMinute(dragPreview.end)}`}
              </div>
            ))
          )}

          {isToday && nowMinute >= rangeStart && nowMinute <= rangeEnd && (
            <div className="now-line" style={{ top: `${((nowMinute - rangeStart) / 60) * hourHeight}px` }}><span>{formatMinute(nowMinute)}</span></div>
          )}

        </div>
      </div>
    </section>
  );
}

function WeekView({ date, settings, categories, occurrences, onDateChange, onOpenDate }: {
  date: string;
  settings: AppSettings;
  categories: Category[];
  occurrences: ScheduleOccurrence[];
  onDateChange: (date: string) => void;
  onOpenDate: (date: string) => void;
}) {
  const weekStart = startOfWeek(date);
  const days = Array.from({ length: 7 }, (_, index) => addDays(weekStart, index));
  const startMinute = settings.dayStartHour * 60;
  const endMinute = settings.dayEndHour * 60;
  const pxPerMinute = 0.55;
  const categoriesMap = new Map(categories.map(item => [item.id, item]));
  const now = new Date();
  const nowMinute = now.getHours() * 60 + now.getMinutes();
  const hours = Array.from({ length: settings.dayEndHour - settings.dayStartHour + 1 }, (_, index) => settings.dayStartHour + index);

  return (
    <section className="screen week-screen">
      <header className="screen-header">
        <div>
          <p className="eyebrow">7일 버드뷰</p>
          <h1>{formatDate(weekStart, { month: 'long' })}<small>{parseDateKey(weekStart).getFullYear()}</small></h1>
        </div>
        <div className="header-actions">
          <button className="icon-button" type="button" aria-label="이전 주" onClick={() => onDateChange(addDays(date, -7))}><ChevronLeft /></button>
          <button className="today-button" type="button" onClick={() => onDateChange(dateKey())}>이번 주</button>
          <button className="icon-button" type="button" aria-label="다음 주" onClick={() => onDateChange(addDays(date, 7))}><ChevronRight /></button>
        </div>
      </header>

      <div className="week-card">
        <div className="week-day-heads">
          <span />
          {days.map((day, index) => (
            <button key={day} type="button" className={`${day === dateKey() ? 'today' : ''} ${index >= 5 ? 'weekend' : ''}`} onClick={() => onOpenDate(day)}>
              <small>{formatDate(day, { weekday: 'narrow' })}</small>
              <strong>{parseDateKey(day).getDate()}</strong>
            </button>
          ))}
        </div>
        <div className="week-scroll">
          <div className="week-grid" style={{ height: `${(endMinute - startMinute) * pxPerMinute}px` }}>
            <div className="week-time-axis">
              {hours.map(hour => <span key={hour} style={{ top: `${(hour * 60 - startMinute) * pxPerMinute}px` }}>{String(hour).padStart(2, '0')}</span>)}
            </div>
            {days.map((day, index) => {
              const segments = segmentsForDate(occurrences, day);
              return (
                <button key={day} type="button" className={`week-column ${index >= 5 ? 'weekend' : ''} ${day === dateKey() ? 'today' : ''}`} onClick={() => onOpenDate(day)}>
                  {hours.map(hour => <i key={hour} style={{ top: `${(hour * 60 - startMinute) * pxPerMinute}px` }} />)}
                  {segments.map(segment => {
                    const category = categoriesMap.get(segment.categoryId);
                    return (
                      <span
                        className="week-event"
                        key={`${segment.key}:${segment.segmentStart}`}
                        style={{
                          top: `${(segment.segmentStart - startMinute) * pxPerMinute}px`,
                          height: `${Math.max(4, (segment.segmentEnd - segment.segmentStart) * pxPerMinute)}px`,
                          background: rgba(category?.color ?? '#8D94A0', 0.75)
                        }}
                        title={`${segment.title} ${formatMinute(segment.startMinute)}`}
                      >
                        {(segment.segmentEnd - segment.segmentStart) >= 45 ? segment.title : ''}
                      </span>
                    );
                  })}
                  {day === dateKey() && nowMinute >= startMinute && nowMinute <= endMinute && <b className="week-now" style={{ top: `${(nowMinute - startMinute) * pxPerMinute}px` }} />}
                </button>
              );
            })}
          </div>
        </div>
      </div>
      <p className="week-hint">날짜나 블록을 누르면 일간 타임라인에서 자세히 편집할 수 있어요.</p>
    </section>
  );
}

function EventEditor({ state, snapshot, onClose, onSaved }: {
  state: EventEditorState;
  snapshot: DayBirdSnapshot;
  onClose: () => void;
  onSaved: (message: string) => Promise<void>;
}) {
  const occurrence = state.occurrence;
  const series = occurrence ? snapshot.schedules.find(item => item.id === occurrence.seriesId) : null;
  const [title, setTitle] = useState(occurrence?.title ?? '');
  const [subtasksText, setSubtasksText] = useState((occurrence?.subtasks ?? []).join('\n'));
  const [date, setDate] = useState(state.draftDate);
  const [start, setStart] = useState(timeFromMinute(state.draftStart));
  const [end, setEnd] = useState(timeFromMinute((state.draftStart + state.draftDuration) % 1440));
  const [categoryId, setCategoryId] = useState(occurrence?.categoryId ?? snapshot.categories[0]?.id ?? 'other');
  const [repeat, setRepeat] = useState(series?.recurrence.kind ?? 'none');
  const [weekdays, setWeekdays] = useState<number[]>(series?.recurrence.weekdays ?? [parseDateKey(date).getDay()]);
  const [reminder, setReminder] = useState<string>(String(occurrence?.reminderMinute ?? ''));
  const [scope, setScope] = useState<'occurrence' | 'series'>(!series || series.recurrence.kind === 'none' ? 'series' : 'occurrence');
  const [error, setError] = useState('');

  const save = async () => {
    if (!title.trim()) { setError('일정 이름을 입력해 주세요.'); return; }
    const startMinute = minuteFromTime(start);
    const endMinute = minuteFromTime(end);
    const durationMinute = endMinute <= startMinute ? endMinute + 1440 - startMinute : endMinute - startMinute;
    if (durationMinute < 5) { setError('일정은 최소 5분 이상이어야 해요.'); return; }
    const reminderMinute = reminder === '' ? null : Number(reminder);
    const subtasks = subtasksText.split(/\r?\n/).map(item => item.trim().replace(/^[•-]\s*/, '')).filter(Boolean);
    const now = Date.now();

    if (!series) {
      const newSeries: ScheduleSeries = {
        id: id('schedule'), title: title.trim(), subtasks, categoryId, startDate: date, startMinute, durationMinute,
        recurrence: { kind: repeat, weekdays: repeat === 'weekly' ? weekdays : [], endDate: null },
        reminderMinute, createdAt: now, updatedAt: now
      };
      await db.schedules.add(newSeries);
      if (reminderMinute !== null && isNative) await requestNotificationPermission();
      await onSaved('일정을 추가했어요.');
      return;
    }

    if (scope === 'series') {
      await db.schedules.update(series.id, {
        title: title.trim(), subtasks, categoryId, startDate: date, startMinute, durationMinute,
        recurrence: { kind: repeat, weekdays: repeat === 'weekly' ? weekdays : [], endDate: null },
        reminderMinute, updatedAt: now
      });
      await onSaved('반복 일정을 업데이트했어요.');
    } else if (occurrence) {
      const override: OccurrenceOverride = {
        id: `${series.id}:${occurrence.originalDate}`,
        seriesId: series.id,
        occurrenceDate: occurrence.originalDate,
        action: 'modified',
        patch: { title: title.trim(), subtasks, categoryId, date, startMinute, durationMinute, reminderMinute }
      };
      await db.overrides.put(override);
      await onSaved('이번 일정만 업데이트했어요.');
    }
  };

  const remove = async () => {
    if (!series || !occurrence) return;
    if (scope === 'series' || series.recurrence.kind === 'none') {
      await db.transaction('rw', db.schedules, db.overrides, async () => {
        await db.schedules.delete(series.id);
        await db.overrides.where('seriesId').equals(series.id).delete();
      });
      await onSaved('일정을 삭제했어요.');
    } else {
      await db.overrides.put({ id: `${series.id}:${occurrence.originalDate}`, seriesId: series.id, occurrenceDate: occurrence.originalDate, action: 'cancelled' });
      await onSaved('이번 일정만 삭제했어요.');
    }
  };

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={event => event.target === event.currentTarget && onClose()}>
      <section className="sheet event-editor" role="dialog" aria-modal="true" aria-labelledby="event-editor-title">
        <div className="sheet-handle" />
        <header>
          <button type="button" className="text-button" onClick={onClose}>취소</button>
          <h2 id="event-editor-title">{series ? '일정 편집' : '새 일정'}</h2>
          <button type="button" className="text-button primary" onClick={() => void save()}>저장</button>
        </header>

        {series && series.recurrence.kind !== 'none' && (
          <div className="segmented scope-picker">
            <button type="button" className={scope === 'occurrence' ? 'active' : ''} onClick={() => setScope('occurrence')}>이번 일정만</button>
            <button type="button" className={scope === 'series' ? 'active' : ''} onClick={() => setScope('series')}>전체 반복</button>
          </div>
        )}

        <div className="form-card">
          <label className="title-field"><span className="category-dot" style={{ background: snapshot.categories.find(item => item.id === categoryId)?.color }} /><input autoFocus value={title} onInput={event => setTitle(event.currentTarget.value)} placeholder="무엇을 할까요?" /></label>
          <label><span>날짜</span><input type="date" value={date} onInput={event => setDate(event.currentTarget.value)} /></label>
          <label><span>시작</span><input type="time" step="300" value={start} onInput={event => setStart(event.currentTarget.value)} /></label>
          <label><span>종료</span><input type="time" step="300" value={end} onInput={event => setEnd(event.currentTarget.value)} /></label>
        </div>

        <div className="form-card">
          <label className="subtasks-field">
            <span>소항목</span>
            <textarea value={subtasksText} onInput={event => setSubtasksText(event.currentTarget.value)} placeholder={'비닐봉지\n입욕제'} rows={3} />
          </label>
          <p className="field-hint">한 줄에 하나씩 입력하면 일정 블록과 위젯에 불렛으로 표시됩니다.</p>
        </div>

        <div className="form-card">
          <label><span>카테고리</span><select value={categoryId} onChange={event => setCategoryId(event.target.value)}>{snapshot.categories.filter(item => !item.archived).map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
          <label><span>반복</span><select value={repeat} disabled={scope === 'occurrence'} onChange={event => setRepeat(event.target.value as 'none' | 'daily' | 'weekly')}><option value="none">반복 안 함</option><option value="daily">매일</option><option value="weekly">선택 요일</option></select></label>
          {repeat === 'weekly' && scope === 'series' && (
            <div className="weekday-picker" aria-label="반복 요일">
              {['일', '월', '화', '수', '목', '금', '토'].map((label, index) => (
                <button type="button" key={label} className={weekdays.includes(index) ? 'active' : ''} onClick={() => setWeekdays(current => current.includes(index) ? current.filter(day => day !== index) : [...current, index])}>{label}</button>
              ))}
            </div>
          )}
          <label><span>알림</span><select value={reminder} onChange={event => setReminder(event.target.value)}><option value="">없음</option><option value="0">시작 시각</option><option value="5">5분 전</option><option value="10">10분 전</option><option value="15">15분 전</option><option value="30">30분 전</option></select></label>
        </div>

        {error && <p className="form-error">{error}</p>}
        {series && <button type="button" className="danger-button" onClick={() => void remove()}><Trash2 />{scope === 'occurrence' ? '이번 일정 삭제' : '전체 일정 삭제'}</button>}
      </section>
    </div>
  );
}

function FocusView({ snapshot, onRefresh, notify }: { snapshot: DayBirdSnapshot; onRefresh: () => Promise<void>; notify: (message: string) => void }) {
  const [mode, setMode] = useState<FocusMode>('pomodoro');
  const [durationMinute, setDurationMinute] = useState(snapshot.settings.pomodoroFocusMinute);
  const [selectedOccurrenceKey, setSelectedOccurrenceKey] = useState('');
  const [now, setNow] = useState(Date.now());
  const active = snapshot.focusSessions.find(item => item.status === 'running' || item.status === 'paused') ?? null;
  const todayOccurrences = useMemo(() => expandOccurrences(snapshot.schedules, snapshot.overrides, dateKey(), dateKey()), [snapshot.overrides, snapshot.schedules]);
  const remaining = active ? remainingSeconds(active, now) : durationMinute * 60;

  useEffect(() => {
    if (!active || active.status !== 'running') return;
    setNow(Date.now());
    const timer = window.setInterval(() => setNow(Date.now()), 500);
    return () => window.clearInterval(timer);
  }, [active]);

  useEffect(() => {
    if (!active || active.status !== 'running' || remaining > 0) return;
    void (async () => {
      await db.focusSessions.update(active.id, { status: 'completed', finishedAt: Date.now() });
      await cancelFocusNotification(active.id);
      await lightHaptic();
      notify(`${active.title} 집중을 마쳤어요.`);
      await onRefresh();
    })();
  }, [active, notify, onRefresh, remaining]);

  const startSession = async () => {
    let title = mode === 'pomodoro' ? `${durationMinute}분 포모도로` : '일정 집중';
    let occurrenceKey: string | null = null;
    let plannedSeconds = durationMinute * 60;
    if (mode === 'event') {
      const occurrence = todayOccurrences.find(item => item.key === selectedOccurrenceKey);
      if (!occurrence) { notify('집중할 일정을 먼저 선택해 주세요.'); return; }
      title = occurrence.title;
      occurrenceKey = occurrence.key;
      plannedSeconds = occurrence.durationMinute * 60;
    }
    const startedAt = Date.now();
    const session: FocusSession = {
      id: id('focus'), mode, occurrenceKey, title, plannedSeconds, startedAt,
      targetEndAt: startedAt + plannedSeconds * 1000, pausedAt: null, pausedRemainingSeconds: null,
      status: 'running', finishedAt: null
    };
    await db.focusSessions.add(session);
    if (isNative) {
      const granted = await requestNotificationPermission();
      if (granted) await scheduleFocusNotification(session.id, title, session.targetEndAt);
    }
    await onRefresh();
  };

  const pause = async () => {
    if (!active) return;
    const left = remainingSeconds(active);
    await cancelFocusNotification(active.id);
    await db.focusSessions.update(active.id, { status: 'paused', pausedAt: Date.now(), pausedRemainingSeconds: left });
    await onRefresh();
  };

  const resume = async () => {
    if (!active) return;
    const targetEndAt = Date.now() + (active.pausedRemainingSeconds ?? 0) * 1000;
    await db.focusSessions.update(active.id, { status: 'running', targetEndAt, pausedAt: null, pausedRemainingSeconds: null });
    await scheduleFocusNotification(active.id, active.title, targetEndAt);
    await onRefresh();
  };

  const cancel = async () => {
    if (!active) return;
    await cancelFocusNotification(active.id);
    await db.focusSessions.update(active.id, { status: 'cancelled', finishedAt: Date.now() });
    await onRefresh();
    notify('집중 세션을 종료했어요.');
  };

  const progress = active ? 1 - remaining / active.plannedSeconds : 0;
  const circumference = 2 * Math.PI * 112;

  return (
    <section className="screen focus-screen">
      <header className="screen-header">
        <div><p className="eyebrow">방해 없이</p><h1>포커스<small>한 번에 하나씩</small></h1></div>
        <button className="icon-button" type="button" aria-label="집중 기록"><MoreHorizontal /></button>
      </header>

      {!active && (
        <div className="segmented focus-mode">
          <button type="button" className={mode === 'pomodoro' ? 'active' : ''} onClick={() => setMode('pomodoro')}>포모도로</button>
          <button type="button" className={mode === 'event' ? 'active' : ''} onClick={() => setMode('event')}>일정 기반</button>
        </div>
      )}

      <div className="focus-orb">
        <svg viewBox="0 0 260 260" aria-hidden="true">
          <circle cx="130" cy="130" r="112" className="orb-track" />
          <circle cx="130" cy="130" r="112" className="orb-progress" style={{ strokeDasharray: circumference, strokeDashoffset: circumference * (1 - progress) }} />
        </svg>
        <div>
          <span>{active?.status === 'paused' ? '잠시 멈춤' : active ? active.title : mode === 'pomodoro' ? '집중 시간' : '일정 집중'}</span>
          <strong>{formatDuration(remaining)}</strong>
          <small>{active ? (active.mode === 'event' ? '일정과 연결됨' : '포모도로 세션') : '준비되면 시작하세요'}</small>
        </div>
      </div>

      {!active && mode === 'pomodoro' && (
        <div className="focus-options">
          <div className="preset-row">
            {[25, 50, 90].map(value => <button type="button" key={value} className={durationMinute === value ? 'active' : ''} onClick={() => setDurationMinute(value)}>{value}분</button>)}
          </div>
          <label className="range-label"><span>사용자 지정</span><strong>{durationMinute}분</strong><input type="range" min="5" max="120" step="5" value={durationMinute} onChange={event => setDurationMinute(Number(event.target.value))} /></label>
        </div>
      )}

      {!active && mode === 'event' && (
        <div className="event-focus-list">
          {todayOccurrences.length === 0 ? <p>오늘 등록된 일정이 없어요.</p> : todayOccurrences.map(item => (
            <button type="button" key={item.key} className={selectedOccurrenceKey === item.key ? 'selected' : ''} onClick={() => setSelectedOccurrenceKey(item.key)}>
              <span>{formatMinute(item.startMinute)}</span><strong>{item.title}</strong><small>{item.durationMinute}분</small>
            </button>
          ))}
        </div>
      )}

      <div className="focus-controls">
        {active ? (
          <>
            <button type="button" className="secondary-control" aria-label="집중 종료" onClick={() => void cancel()}><Square /></button>
            <button type="button" className="primary-control" aria-label={active.status === 'paused' ? '집중 재개' : '집중 일시정지'} onClick={() => void (active.status === 'paused' ? resume() : pause())}>{active.status === 'paused' ? <Play /> : <Pause />}</button>
          </>
        ) : <button type="button" className="start-focus" onClick={() => void startSession()}><Play />집중 시작</button>}
      </div>

      <div className="recent-focus">
        <h2>최근 기록</h2>
        {snapshot.focusSessions.filter(item => item.status === 'completed').slice(0, 3).map(item => (
          <div key={item.id}><span className="focus-check"><Check /></span><p><strong>{item.title}</strong><small>{new Intl.DateTimeFormat('ko-KR', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(item.startedAt)}</small></p><b>{Math.round(item.plannedSeconds / 60)}분</b></div>
        ))}
        {!snapshot.focusSessions.some(item => item.status === 'completed') && <p className="empty-copy">첫 집중을 마치면 여기에 기록돼요.</p>}
      </div>
    </section>
  );
}

function SettingsView({ snapshot, installPrompt, onInstalled, onRefresh, notify }: {
  snapshot: DayBirdSnapshot;
  installPrompt: BeforeInstallPromptEvent | null;
  onInstalled: () => void;
  onRefresh: () => Promise<void>;
  notify: (message: string) => void;
}) {
  const [capability, setCapability] = useState<NotificationCapability | null>(null);
  const [recoveryInfo, setRecoveryInfo] = useState(() => getRecoveryBackupInfo());
  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => { void notificationCapability().then(setCapability); }, []);

  const updateSettings = async (patch: Partial<AppSettings>) => {
    await db.settings.update('app', patch);
    await onRefresh();
  };

  const updateCategory = async (category: Category, patch: Partial<Category>) => {
    await db.categories.update(category.id, patch);
    await onRefresh();
  };

  const addCategory = async () => {
    await db.categories.add({ id: id('category'), name: '새 카테고리', color: '#5AA9F8', order: snapshot.categories.length, archived: false });
    await onRefresh();
  };

  const downloadBackup = async () => {
    const raw = await exportBackup();
    const url = URL.createObjectURL(new Blob([raw], { type: 'application/json' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `daybird-backup-${dateKey()}.json`;
    link.click();
    URL.revokeObjectURL(url);
    notify('백업 파일을 만들었어요.');
  };

  const restoreBackup = async (file: File) => {
    try {
      await importBackup(await file.text());
      await onRefresh();
      setRecoveryInfo(getRecoveryBackupInfo());
      notify('백업을 복원했어요.');
    } catch {
      notify('DayBird 백업 파일을 확인해 주세요.');
    }
  };

  const restoreRecovery = async () => {
    if (!recoveryInfo) {
      notify('이 기기에서 찾을 수 있는 복구 사본이 없어요.');
      return;
    }
    if (!window.confirm('마지막 자동 복구 사본으로 현재 데이터를 덮어쓸까요?')) return;
    if (!await restoreRecoveryBackup()) {
      notify('복구 사본을 읽지 못했어요.');
      return;
    }
    await onRefresh();
    setRecoveryInfo(getRecoveryBackupInfo());
    notify('마지막 자동 복구 사본으로 되돌렸어요.');
  };

  const recoveryDescription = recoveryInfo
    ? `${new Intl.DateTimeFormat('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(recoveryInfo.exportedAt))} · 일정 ${recoveryInfo.scheduleCount}개`
    : '이 기기에 복구 사본이 없어요';

  const install = async () => {
    if (!installPrompt) { notify('브라우저 메뉴에서 홈 화면에 추가할 수 있어요.'); return; }
    await installPrompt.prompt();
    await installPrompt.userChoice;
    onInstalled();
  };

  return (
    <section className="screen settings-screen">
      <header className="screen-header"><div><p className="eyebrow">나에게 맞게</p><h1>설정<small>보이는 방식부터 알림까지</small></h1></div><SlidersHorizontal /></header>

      <h2 className="settings-title">타임라인</h2>
      <div className="settings-card">
        <label><span>시작 시간</span><select value={snapshot.settings.dayStartHour} onChange={event => void updateSettings({ dayStartHour: Number(event.target.value) })}>{Array.from({ length: 13 }, (_, value) => <option key={value} value={value}>{String(value).padStart(2, '0')}:00</option>)}</select></label>
        <label><span>종료 시간</span><select value={snapshot.settings.dayEndHour} onChange={event => void updateSettings({ dayEndHour: Number(event.target.value) })}>{Array.from({ length: 13 }, (_, index) => index + 12).map(value => <option key={value} value={value}>{value}:00</option>)}</select></label>
        <label><span>일정 이동 스냅</span><select value={snapshot.settings.snapMinute} onChange={event => void updateSettings({ snapMinute: Number(event.target.value) as AppSettings['snapMinute'] })}>{[5, 10, 15, 30].map(value => <option key={value} value={value}>{value}분</option>)}</select></label>
        <label><span>블록 크기</span><select value={snapshot.settings.blockDensity} onChange={event => void updateSettings({ blockDensity: event.target.value as AppSettings['blockDensity'] })}><option value="comfortable">여유롭게</option><option value="compact">작게</option></select></label>
        <label><span>글자 크기</span><select value={snapshot.settings.fontScale} onChange={event => void updateSettings({ fontScale: event.target.value as AppSettings['fontScale'] })}><option value="small">작게</option><option value="medium">보통</option><option value="large">크게</option></select></label>
        <label><span>텍스트 위치</span><select value={snapshot.settings.textAlign} onChange={event => void updateSettings({ textAlign: event.target.value as AppSettings['textAlign'] })}><option value="left">왼쪽</option><option value="center">가운데</option></select></label>
      </div>

      <div className="settings-heading-row"><h2 className="settings-title">카테고리</h2><button type="button" onClick={() => void addCategory()}><Plus />추가</button></div>
      <div className="settings-card category-settings">
        {snapshot.categories.filter(item => !item.archived).map(category => (
          <div key={category.id}>
            <input aria-label={`${category.name} 색상`} type="color" value={category.color} onChange={event => void updateCategory(category, { color: event.target.value })} />
            <input aria-label="카테고리 이름" value={category.name} onChange={event => void updateCategory(category, { name: event.target.value })} />
            {snapshot.categories.length > 1 && <button type="button" aria-label={`${category.name} 보관`} onClick={() => void updateCategory(category, { archived: true })}><Trash2 /></button>}
          </div>
        ))}
      </div>

      <h2 className="settings-title">알림과 앱</h2>
      <div className="settings-card action-settings">
        <button type="button" onClick={async () => { await requestNotificationPermission(); setCapability(await notificationCapability()); }}><Bell /><span><strong>알림 권한</strong><small>{capability?.display === 'granted' ? '허용됨' : isNative ? '확인 필요' : 'APK에서 사용 가능'}</small></span><ChevronRight /></button>
        {isNative && <button type="button" onClick={() => void openExactAlarmSettings()}><TimerReset /><span><strong>정시 알람</strong><small>{capability?.exact === 'granted' ? '정확한 시각에 울림' : '시스템 설정에서 허용 필요'}</small></span><ChevronRight /></button>}
        {!isNative && <button type="button" onClick={() => void install()}><Download /><span><strong>DayBird 설치</strong><small>PWA를 홈 화면 앱처럼 사용</small></span><ChevronRight /></button>}
        {!isNative && <a href={`${import.meta.env.BASE_URL}downloads/daybird.apk`} download><Download /><span><strong>Android APK</strong><small>서명된 최신 버전 다운로드</small></span><ChevronRight /></a>}
      </div>

      <h2 className="settings-title">내 데이터</h2>
      <div className="settings-card action-settings">
        <button type="button" onClick={() => void downloadBackup()}><Download /><span><strong>백업 내보내기</strong><small>일정과 설정을 JSON으로 저장</small></span><ChevronRight /></button>
        <button type="button" onClick={() => fileRef.current?.click()}><Upload /><span><strong>백업 가져오기</strong><small>현재 데이터는 자동으로 임시 백업</small></span><ChevronRight /></button>
        <input ref={fileRef} hidden type="file" accept="application/json" onChange={event => { const file = event.target.files?.[0]; if (file) void restoreBackup(file); }} />
        <button type="button" onClick={() => void restoreRecovery()}><RotateCcw /><span><strong>자동 복구 사본 되돌리기</strong><small>{recoveryDescription}</small></span><ChevronRight /></button>
        <button type="button" className="destructive-row" onClick={async () => { if (!window.confirm('모든 일정과 기록을 지울까요?')) return; await resetDatabase(); await onRefresh(); setRecoveryInfo(getRecoveryBackupInfo()); notify('DayBird를 초기화했어요.'); }}><RotateCcw /><span><strong>모든 데이터 초기화</strong><small>이 작업은 되돌릴 수 없어요</small></span><ChevronRight /></button>
      </div>
      <p className="data-storage-note">일정은 이 기기의 앱 저장소에 보관됩니다. PWA와 APK는 저장소가 서로 달라, 옮길 때는 백업 파일을 내보내고 가져와 주세요.</p>

      <footer className="settings-footer"><div className="mini-brand"><span /><span /></div><strong>DayBird</strong><span>Version 0.1.0 · Local first</span></footer>
    </section>
  );
}
