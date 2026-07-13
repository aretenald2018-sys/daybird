import { Capacitor, registerPlugin } from '@capacitor/core';
import { addDays, dateKey, expandOccurrences, remainingSeconds } from './domain';
import type { DayBirdSnapshot } from './db';

interface DayBirdWidgetPlugin {
  sync(options: { payload: string }): Promise<void>;
}

const DayBirdWidget = registerPlugin<DayBirdWidgetPlugin>('DayBirdWidget');

export async function syncDayBirdWidgets(snapshot: DayBirdSnapshot): Promise<void> {
  if (!Capacitor.isNativePlatform()) return;
  const today = dateKey();
  const end = addDays(today, 7);
  const categoryColors = new Map(snapshot.categories.map(category => [category.id, category.color]));
  const events = expandOccurrences(snapshot.schedules, snapshot.overrides, today, end).map(occurrence => ({
    date: occurrence.date,
    title: occurrence.title,
    subtasks: occurrence.subtasks ?? [],
    startMinute: occurrence.startMinute,
    durationMinute: occurrence.durationMinute,
    color: categoryColors.get(occurrence.categoryId) ?? '#8D94A0'
  }));
  const active = snapshot.focusSessions.find(session => session.status === 'running' || session.status === 'paused') ?? null;
  const focus = active ? {
    title: active.title,
    status: active.status,
    targetEndAt: active.targetEndAt,
    remainingSeconds: remainingSeconds(active)
  } : null;

  await DayBirdWidget.sync({
    payload: JSON.stringify({ updatedAt: Date.now(), events, focus })
  }).catch(() => undefined);
}
