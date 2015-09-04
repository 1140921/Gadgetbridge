package nodomain.freeyourgadget.gadgetbridge.externalevents;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.GregorianCalendar;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;


public class TimeChangeReceiver extends BroadcastReceiver {

    private static final Logger LOG = LoggerFactory.getLogger(TimeChangeReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String action = intent.getAction();

        if (sharedPrefs.getBoolean("datetime_synconconnect", true) && (action.equals(Intent.ACTION_TIME_CHANGED) || action.equals(Intent.ACTION_TIMEZONE_CHANGED))) {
            Date newTime = GregorianCalendar.getInstance().getTime();
            LOG.info("Time or Timezone changed, syncing with device: " + DateTimeUtils.formatDate(newTime) + " (" + newTime.toGMTString() + "), " + intent.getAction());
            GBApplication.deviceService().onSetTime();
        }
    }
}