package nodomain.freeyourgadget.gadgetbridge;

import nodomain.freeyourgadget.gadgetbridge.GBDevice.State;
import nodomain.freeyourgadget.gadgetbridge.miband.MiBandSupport;
import nodomain.freeyourgadget.gadgetbridge.pebble.PebbleIoThread;
import nodomain.freeyourgadget.gadgetbridge.pebble.PebbleSupport;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.Toast;

public class BluetoothCommunicationService extends Service {
    public static final String ACTION_START
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.start";
    public static final String ACTION_CONNECT
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.connect";
    public static final String ACTION_NOTIFICATION_GENERIC
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.notification_generic";
    public static final String ACTION_NOTIFICATION_SMS
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.notification_sms";
    public static final String ACTION_NOTIFICATION_EMAIL
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.notification_email";
    public static final String ACTION_CALLSTATE
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.callstate";
    public static final String ACTION_SETTIME
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.settime";
    public static final String ACTION_SETMUSICINFO
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.setmusicinfo";
    public static final String ACTION_REQUEST_VERSIONINFO
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.request_versioninfo";
    public static final String ACTION_REQUEST_APPINFO
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.request_appinfo";
    public static final String ACTION_DELETEAPP
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.deleteapp";
    public static final String ACTION_INSTALL_PEBBLEAPP
            = "nodomain.freeyourgadget.gadgetbride.bluetoothcommunicationservice.action.install_pebbbleapp";

    private static final String TAG = "CommunicationService";
    private BluetoothAdapter mBtAdapter = null;
    private GBDeviceIoThread mGBDeviceIoThread = null;

    private boolean mStarted = false;

    private GBDevice mGBDevice = null;
    private DeviceSupport mDeviceSupport;

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null) {
            Log.i(TAG, "no intent");
            return START_NOT_STICKY;
        }

        String action = intent.getAction();

        if (action == null) {
            Log.i(TAG, "no action");
            return START_NOT_STICKY;
        }

        if (!mStarted && !action.equals(ACTION_START)) {
            // using the service before issuing ACTION_START
            return START_NOT_STICKY;
        }

        if (mStarted && action.equals(ACTION_START)) {
            // using ACTION_START when the service has already been started
            return START_STICKY;
        }

        if (!action.equals(ACTION_START) && !action.equals(ACTION_CONNECT) && !isConnected()) {
            // trying to send notification without valid Blutooth connection
            return START_STICKY;
        }

        if (action.equals(ACTION_CONNECT)) {
            //Check the system status
            mBtAdapter = BluetoothAdapter.getDefaultAdapter();
            if (mBtAdapter == null) {
                Toast.makeText(this, "Bluetooth is not supported.", Toast.LENGTH_SHORT).show();
            } else if (!mBtAdapter.isEnabled()) {
                Toast.makeText(this, "Bluetooth is disabled.", Toast.LENGTH_SHORT).show();
            } else {
                String btDeviceAddress = intent.getStringExtra("device_address");
                SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this);
                sharedPrefs.edit().putString("last_device_address", btDeviceAddress).commit();

                if (btDeviceAddress != null && !isConnected() && !isConnecting()) {
                    // currently only one thread allowed
                    if (mGBDeviceIoThread != null) {
                        mGBDeviceIoThread.quit();
                        try {
                            mGBDeviceIoThread.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                    }
                    BluetoothDevice btDevice = mBtAdapter.getRemoteDevice(btDeviceAddress);
                    if (btDevice != null) {
                        if (btDevice.getName() == null || btDevice.getName().equals("MI")) { //FIXME: workaround for Miband not being paired
                            mGBDevice = new GBDevice(btDeviceAddress, btDevice.getName(), GBDevice.Type.MIBAND);
                            mDeviceSupport = new MiBandSupport();
                        } else if (btDevice.getName().indexOf("Pebble") == 0) {
                            mGBDevice = new GBDevice(btDeviceAddress, btDevice.getName(), GBDevice.Type.PEBBLE);
                            mDeviceSupport = new PebbleSupport();
                        }
                        if (mDeviceSupport != null) {
                            mDeviceSupport.initialize(mGBDevice, mBtAdapter, this);
                            mDeviceSupport.connect();
                            if (mDeviceSupport instanceof AbstractBTDeviceSupport) {
                                mGBDeviceIoThread = ((AbstractBTDeviceSupport) mDeviceSupport).getDeviceIOThread();
                            }
                        }
                    }
                }
            }
        } else if (action.equals(ACTION_NOTIFICATION_GENERIC)) {
            String title = intent.getStringExtra("notification_title");
            String body = intent.getStringExtra("notification_body");
            mDeviceSupport.onSMS(title, body);
        } else if (action.equals(ACTION_NOTIFICATION_SMS)) {
            String sender = intent.getStringExtra("notification_sender");
            String body = intent.getStringExtra("notification_body");
            String senderName = getContactDisplayNameByNumber(sender);
            mDeviceSupport.onSMS(senderName, body);
        } else if (action.equals(ACTION_NOTIFICATION_EMAIL)) {
            String sender = intent.getStringExtra("notification_sender");
            String subject = intent.getStringExtra("notification_subject");
            String body = intent.getStringExtra("notification_body");
            mDeviceSupport.onEmail(sender, subject, body);
        } else if (action.equals(ACTION_CALLSTATE)) {
            GBCommand command = GBCommand.values()[intent.getIntExtra("call_command", 0)]; // UGLY
            String phoneNumber = intent.getStringExtra("call_phonenumber");
            String callerName = null;
            if (phoneNumber != null) {
                callerName = getContactDisplayNameByNumber(phoneNumber);
            }
            mDeviceSupport.onSetCallState(phoneNumber, callerName, command);
        } else if (action.equals(ACTION_SETTIME)) {
            mDeviceSupport.onSetTime(-1);
        } else if (action.equals(ACTION_SETMUSICINFO)) {
            String artist = intent.getStringExtra("music_artist");
            String album = intent.getStringExtra("music_album");
            String track = intent.getStringExtra("music_track");
            mDeviceSupport.onSetMusicInfo(artist, album, track);
        } else if (action.equals(ACTION_REQUEST_VERSIONINFO)) {
            if (mGBDevice != null && mGBDevice.getFirmwareVersion() == null) {
                mDeviceSupport.onFirmwareVersionReq();
            } else {
                mGBDevice.sendDeviceUpdateIntent(this);
            }
        } else if (action.equals(ACTION_REQUEST_APPINFO)) {
            mDeviceSupport.onAppInfoReq();
        } else if (action.equals(ACTION_DELETEAPP)) {
            int id = intent.getIntExtra("app_id", -1);
            int index = intent.getIntExtra("app_index", -1);
            mDeviceSupport.onAppDelete(id, index);
        } else if (action.equals(ACTION_INSTALL_PEBBLEAPP)) {
            String uriString = intent.getStringExtra("app_uri");
            if (uriString != null) {
                Log.i(TAG, "will try to install app");
                ((PebbleIoThread) mGBDeviceIoThread).installApp(Uri.parse(uriString));
            }
        } else if (action.equals(ACTION_START)) {
            startForeground(GB.NOTIFICATION_ID, GB.createNotification("Gadgetbridge running", this));
            mStarted = true;
        }

        return START_STICKY;
    }

    private boolean isConnected() {
        return mGBDevice != null && mGBDevice.getState() == State.CONNECTED;
    }

    private boolean isConnecting() {
        return mGBDevice != null && mGBDevice.getState() == State.CONNECTING;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        GB.setReceiversEnableState(false, this); // disable BroadcastReceivers

        if (mGBDeviceIoThread != null) {
            try {
                mGBDeviceIoThread.quit();
                mGBDeviceIoThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(GB.NOTIFICATION_ID); // need to do this because the updated notification wont be cancelled when service stops
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    private String getContactDisplayNameByNumber(String number) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
        String name = number;

        if (number == null || number.equals("")) {
            return name;
        }

        ContentResolver contentResolver = getContentResolver();
        Cursor contactLookup = contentResolver.query(uri, null, null, null, null);

        try {
            if (contactLookup != null && contactLookup.getCount() > 0) {
                contactLookup.moveToNext();
                name = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
            }
        } finally {
            if (contactLookup != null) {
                contactLookup.close();
            }
        }

        return name;
    }
}
