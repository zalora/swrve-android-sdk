package com.swrve.sdk;

import android.content.Intent;
import androidx.core.app.SwrveJobIntentService;

public class SwrvePushServiceDefaultJobIntentService extends SwrveJobIntentService {

    @Override
    protected void onHandleWork(Intent intent) {
        try {
            new SwrvePushServiceManager(this).processMessage(intent.getExtras());
        } catch (Exception ex) {
            SwrveLogger.e("SwrvePushServiceDefaultJobIntentService exception (intent: %s): ", ex, intent);
        }
    }
}
