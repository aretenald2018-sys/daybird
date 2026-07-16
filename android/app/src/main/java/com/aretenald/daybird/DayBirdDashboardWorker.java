package com.aretenald.daybird;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class DayBirdDashboardWorker extends Worker {
    public DayBirdDashboardWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            DayBirdDashboardSync.refreshBlocking(getApplicationContext());
            return Result.success();
        } catch (Exception error) {
            DayBirdDashboardState.saveError(getApplicationContext(), error);
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
    }
}
