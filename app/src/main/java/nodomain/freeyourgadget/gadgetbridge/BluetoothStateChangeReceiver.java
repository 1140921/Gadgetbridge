package nodomain.freeyourgadget.gadgetbridge;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class BluetoothStateChangeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();

        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) {
                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
                if (!sharedPrefs.getBoolean("general_autoconnectonbluetooth", false)) {
                    return;
                }
                Intent startIntent = new Intent(context, BluetoothCommunicationService.class);
                startIntent.setAction(BluetoothCommunicationService.ACTION_START);
                context.startService(startIntent);

                Intent connectIntent = new Intent(context, BluetoothCommunicationService.class);
                connectIntent.setAction(BluetoothCommunicationService.ACTION_CONNECT);
                context.startService(connectIntent);
            }
        }
    }
}
