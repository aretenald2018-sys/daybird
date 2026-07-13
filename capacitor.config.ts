import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.aretenald.daybird',
  appName: 'DayBird',
  webDir: 'dist',
  backgroundColor: '#f2f2f7',
  plugins: {
    LocalNotifications: {
      smallIcon: 'ic_stat_daybird',
      iconColor: '#007AFF'
    }
  },
  android: {
    backgroundColor: '#f2f2f7'
  }
};

export default config;
