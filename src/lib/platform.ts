import { Capacitor } from '@capacitor/core';
import { Haptics, ImpactStyle } from '@capacitor/haptics';
import { LocalNotifications, type LocalNotificationSchema } from '@capacitor/local-notifications';
import { addDays, dateKey, expandOccurrences, occurrenceDateTime, type OccurrenceOverride, type ScheduleSeries } from './domain';

export const isNative = Capacitor.isNativePlatform();

export async function lightHaptic(): Promise<void> {
  if (!isNative) return;
  await Haptics.impact({ style: ImpactStyle.Light }).catch(() => undefined);
}

export interface NotificationCapability {
  native: boolean;
  display: 'granted' | 'denied' | 'prompt' | 'unsupported';
  exact: 'granted' | 'denied' | 'unsupported';
}

export async function notificationCapability(): Promise<NotificationCapability> {
  if (!isNative) return { native: false, display: 'unsupported', exact: 'unsupported' };
  const [display, exact] = await Promise.all([
    LocalNotifications.checkPermissions(),
    LocalNotifications.checkExactNotificationSetting()
  ]);
  return {
    native: true,
    display: display.display === 'prompt-with-rationale' ? 'prompt' : display.display,
    exact: exact.exact_alarm === 'granted' ? 'granted' : 'denied'
  };
}

export async function requestNotificationPermission(): Promise<boolean> {
  if (!isNative) return false;
  const permission = await LocalNotifications.requestPermissions();
  return permission.display === 'granted';
}

export async function openExactAlarmSettings(): Promise<void> {
  if (!isNative) return;
  await LocalNotifications.changeExactNotificationSetting();
}

function notificationId(key: string): number {
  let hash = 2166136261;
  for (let index = 0; index < key.length; index += 1) {
    hash ^= key.charCodeAt(index);
    hash = Math.imul(hash, 16777619);
  }
  return Math.abs(hash | 0) || 1;
}

export async function syncScheduleNotifications(series: ScheduleSeries[], overrides: OccurrenceOverride[]): Promise<void> {
  if (!isNative) return;
  const permission = await LocalNotifications.checkPermissions();
  if (permission.display !== 'granted') return;
  const pending = await LocalNotifications.getPending();
  const scheduleItems = pending.notifications.filter(item => item.extra?.daybirdType === 'schedule');
  if (scheduleItems.length) await LocalNotifications.cancel({ notifications: scheduleItems });

  const start = dateKey();
  const end = addDays(start, 90);
  const occurrences = expandOccurrences(series, overrides, start, end).filter(item => item.reminderMinute !== null);
  const notifications: LocalNotificationSchema[] = [];
  for (const occurrence of occurrences) {
    const at = occurrenceDateTime(occurrence, -(occurrence.reminderMinute ?? 0));
    if (at.getTime() > Date.now()) {
      notifications.push({
        id: notificationId(`schedule:${occurrence.key}`),
        title: occurrence.title,
        body: occurrence.reminderMinute === 0 ? '지금 시작할 시간이에요.' : `${occurrence.reminderMinute}분 후 시작해요.`,
        schedule: { at, allowWhileIdle: true },
        extra: { daybirdType: 'schedule', occurrenceKey: occurrence.key },
        channelId: 'daybird-schedule'
      });
    }
  }

  await LocalNotifications.createChannel({
    id: 'daybird-schedule',
    name: '일정 알림',
    description: '예약한 일정 시작 알림',
    importance: 4,
    visibility: 1
  });
  if (notifications.length) await LocalNotifications.schedule({ notifications });
}

export async function scheduleFocusNotification(sessionId: string, title: string, targetEndAt: number): Promise<void> {
  if (!isNative) return;
  const permission = await LocalNotifications.checkPermissions();
  if (permission.display !== 'granted') return;
  await LocalNotifications.createChannel({
    id: 'daybird-focus',
    name: '집중 타이머',
    description: '진행 중인 집중 세션과 완료 알림',
    importance: 4,
    visibility: 1
  });
  await cancelFocusNotification(sessionId);
  const endsAt = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(new Date(targetEndAt));
  await LocalNotifications.schedule({ notifications: [
    {
      id: notificationId(`focus-live:${sessionId}`),
      title: `집중 중 · ${title}`,
      body: `${endsAt}까지 집중합니다.`,
      ongoing: true,
      autoCancel: false,
      extra: { daybirdType: 'focus-live', sessionId },
      channelId: 'daybird-focus'
    },
    {
      id: notificationId(`focus:${sessionId}`),
      title: '집중 완료',
      body: `${title} 세션을 마쳤어요.`,
      schedule: { at: new Date(targetEndAt), allowWhileIdle: true },
      extra: { daybirdType: 'focus', sessionId },
      channelId: 'daybird-focus'
    }
  ] });
}

export async function cancelFocusNotification(sessionId: string): Promise<void> {
  if (!isNative) return;
  await LocalNotifications.cancel({ notifications: [
    { id: notificationId(`focus:${sessionId}`) },
    { id: notificationId(`focus-live:${sessionId}`) }
  ] });
}
