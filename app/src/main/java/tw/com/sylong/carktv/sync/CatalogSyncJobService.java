package tw.com.sylong.carktv.sync;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;

public class CatalogSyncJobService extends JobService {
    private static final int JOB_ID = 770801;

    public static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        ComponentName component = new ComponentName(context, CatalogSyncJobService.class);
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(6 * 60 * 60 * 1000L);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setRequiresBatteryNotLow(false);
        }
        scheduler.schedule(builder.build());
    }

    public static void runSoon(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            return;
        }
        ComponentName component = new ComponentName(context, CatalogSyncJobService.class);
        JobInfo info = new JobInfo.Builder(JOB_ID + 1, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(1000L)
                .build();
        scheduler.schedule(info);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Thread thread = new Thread(() -> {
            try {
                new CatalogUpdater(this).update(false, null);
            } catch (Exception ignored) {
                // The UI exposes the next manual sync result; background sync stays quiet.
            } finally {
                jobFinished(params, false);
            }
        }, "catalog-job-sync");
        thread.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}

