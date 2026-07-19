import { Capacitor, registerPlugin } from '@capacitor/core';

export type DashboardWeights = {
  food: number;
  health: number;
  running: number;
  spending: number;
  wine: number;
};

export type DashboardStatus = {
  connected: boolean;
  paired?: boolean;
  snapshotReady?: boolean;
  ownerUid: string;
  authUid: string;
  revision: number;
  score: number | null;
  lastSuccessEpochMs: number;
  lastError: string;
  weights: Partial<DashboardWeights>;
};

export const DEFAULT_DASHBOARD_WEIGHTS: DashboardWeights = {
  food: 25,
  health: 25,
  running: 20,
  spending: 20,
  wine: 10
};

interface DayBirdDashboardPlugin {
  status(): Promise<DashboardStatus>;
  exchangePairing(options: { code: string }): Promise<DashboardStatus>;
  refresh(): Promise<DashboardStatus>;
  saveWeights(options: { weights: DashboardWeights }): Promise<DashboardStatus>;
  disconnect(): Promise<DashboardStatus>;
}

const Dashboard = registerPlugin<DayBirdDashboardPlugin>('DayBirdDashboard');

function disconnectedStatus(): DashboardStatus {
  return {
    connected: false,
    paired: false,
    snapshotReady: false,
    ownerUid: '',
    authUid: '',
    revision: 0,
    score: null,
    lastSuccessEpochMs: 0,
    lastError: '',
    weights: DEFAULT_DASHBOARD_WEIGHTS
  };
}

export function pairingCodeFromUrl(value: string | undefined | null): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    if (!isPairDeepLinkLocation(url)) return null;
    const code = url.searchParams.get('code')?.trim();
    return code || null;
  } catch {
    return null;
  }
}

export function isPairDeepLinkLocation(
  url: Pick<URL, 'protocol' | 'hostname' | 'pathname'>
): boolean {
  if (url.protocol !== 'daybird:') return false;
  return url.hostname === 'pair' || (url.hostname === '' && url.pathname === '//pair');
}

export async function dashboardStatus(): Promise<DashboardStatus> {
  if (!Capacitor.isNativePlatform()) return disconnectedStatus();
  return Dashboard.status();
}

export async function exchangeDashboardPairing(code: string): Promise<DashboardStatus> {
  return Dashboard.exchangePairing({ code });
}

export async function refreshDashboard(): Promise<DashboardStatus> {
  return Dashboard.refresh();
}

export async function saveDashboardWeights(weights: DashboardWeights): Promise<DashboardStatus> {
  return Dashboard.saveWeights({ weights });
}

export async function disconnectDashboard(): Promise<DashboardStatus> {
  return Dashboard.disconnect();
}

export function resolvedDashboardWeights(status: DashboardStatus | null): DashboardWeights {
  return {
    ...DEFAULT_DASHBOARD_WEIGHTS,
    ...(status?.weights || {})
  };
}
