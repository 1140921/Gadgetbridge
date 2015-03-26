package nodomain.freeyourgadget.gadgetbridge;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import nodomain.freeyourgadget.gadgetbridge.protocol.GBDeviceCommand;
import nodomain.freeyourgadget.gadgetbridge.protocol.GBDeviceCommandAppInfo;
import nodomain.freeyourgadget.gadgetbridge.protocol.GBDeviceCommandAppManagementResult;
import nodomain.freeyourgadget.gadgetbridge.protocol.GBDeviceCommandCallControl;
import nodomain.freeyourgadget.gadgetbridge.protocol.GBDeviceCommandMusicControl;
import nodomain.freeyourgadget.gadgetbridge.protocol.GBDeviceCommandVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.protocol.PebbleProtocol;

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

    private static final String TAG = "BluetoothCommunicationService";
    private static final int NOTIFICATION_ID = 1;
    private BluetoothAdapter mBtAdapter = null;
    private BluetoothSocket mBtSocket = null;
    private BtSocketIoThread mBtSocketIoThread = null;

    private boolean mStarted = false;

    private GBDevice gbdevice = null;

    private void setReceiversEnableState(boolean enable) {
        final Class[] receiverClasses = {
                PhoneCallReceiver.class,
                SMSReceiver.class,
                K9Receiver.class,
                MusicPlaybackReceiver.class,
                //NotificationListener.class, // disabling this leads to loss of permission to read notifications
        };

        int newState;

        if (enable) {
            newState = PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } else {
            newState = PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        }

        PackageManager pm = getPackageManager();

        for (Class receiverClass : receiverClasses) {
            ComponentName compName = new ComponentName(getApplicationContext(), receiverClass);

            pm.setComponentEnabledSetting(compName, newState, PackageManager.DONT_KILL_APP);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }


    private Notification createNotification(String text) {
        Intent notificationIntent = new Intent(this, ControlCenter.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                notificationIntent, 0);

        return new NotificationCompat.Builder(this)
                .setContentTitle("Gadgetbridge")
                .setTicker(text)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true).build();
    }

    private void updateNotification(String text) {

        Notification notification = createNotification(text);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notification);
    }

    private void evaluateGBCommandBundle(GBDeviceCommand deviceCmd) {
        switch (deviceCmd.commandClass) {
            case MUSIC_CONTROL:
                Log.i(TAG, "Got command for MUSIC_CONTROL");
                GBDeviceCommandMusicControl musicCmd = (GBDeviceCommandMusicControl) deviceCmd;
                Intent musicIntent = new Intent(GBMusicControlReceiver.ACTION_MUSICCONTROL);
                musicIntent.putExtra("command", musicCmd.command.ordinal());
                sendBroadcast(musicIntent);
                break;
            case CALL_CONTROL:
                Log.i(TAG, "Got command for CALL_CONTROL");
                GBDeviceCommandCallControl callCmd = (GBDeviceCommandCallControl) deviceCmd;
                Intent callIntent = new Intent(GBCallControlReceiver.ACTION_CALLCONTROL);
                callIntent.putExtra("command", callCmd.command.ordinal());
                sendBroadcast(callIntent);
                break;
            case VERSION_INFO:
                Log.i(TAG, "Got command for VERSION_INFO");
                if (gbdevice == null) {
                    return;
                }
                GBDeviceCommandVersionInfo infoCmd = (GBDeviceCommandVersionInfo) deviceCmd;
                gbdevice.setFirmwareVersion(infoCmd.fwVersion);
                sendDeviceUpdateIntent();
                break;
            case APP_INFO:
                Log.i(TAG, "Got command for APP_INFO");
                GBDeviceCommandAppInfo appInfoCmd = (GBDeviceCommandAppInfo) deviceCmd;
                Intent appInfoIntent = new Intent(AppManagerActivity.ACTION_REFRESH_APPLIST);
                int appCount = appInfoCmd.apps.length;
                appInfoIntent.putExtra("app_count", appCount);
                for (Integer i = 0; i < appCount; i++) {
                    appInfoIntent.putExtra("app_name" + i.toString(), appInfoCmd.apps[i].getName());
                    appInfoIntent.putExtra("app_creator" + i.toString(), appInfoCmd.apps[i].getCreator());
                    appInfoIntent.putExtra("app_id" + i.toString(), appInfoCmd.apps[i].getId());
                    appInfoIntent.putExtra("app_index" + i.toString(), appInfoCmd.apps[i].getIndex());
                }
                sendBroadcast(appInfoIntent);
                break;
            case APP_MANAGEMENT_RES:
                GBDeviceCommandAppManagementResult appMgmtRes = (GBDeviceCommandAppManagementResult) deviceCmd;
                switch (appMgmtRes.type) {
                    case DELETE:
                        switch (appMgmtRes.result) {
                            case FAILURE:
                                Log.i(TAG, "failure removing app"); // TODO: report to AppManager
                                break;
                            case SUCCESS:
                                // refresh app list
                                mBtSocketIoThread.write(PebbleProtocol.encodeAppInfoReq());
                                break;
                            default:
                                break;
                        }
                        break;
                    default:
                        break;
                }
            default:
                break;
        }
    }

    private void sendDeviceUpdateIntent() {
        Intent deviceUpdateIntent = new Intent(ControlCenter.ACTION_REFRESH_DEVICELIST);
        deviceUpdateIntent.putExtra("device_address", gbdevice.getAddress());
        deviceUpdateIntent.putExtra("device_state", gbdevice.getState().ordinal());
        deviceUpdateIntent.putExtra("firmware_version", gbdevice.getFirmwareVersion());
        sendBroadcast(deviceUpdateIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

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

        if (!action.equals(ACTION_START) && !action.equals(ACTION_CONNECT) && mBtSocket == null) {
            // trying to send notification without valid Blutooth socket
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

                if (btDeviceAddress != null && (mBtSocket == null || !mBtSocket.isConnected())) {
                    // currently only one thread allowed
                    if (mBtSocketIoThread != null) {
                        mBtSocketIoThread.quit();
                        try {
                            mBtSocketIoThread.join();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                    }
                    BluetoothDevice btDevice = mBtAdapter.getRemoteDevice(btDeviceAddress);
                    if (btDevice != null) {
                        gbdevice = new GBDevice(btDeviceAddress, btDevice.getName());
                        gbdevice.setState(GBDevice.State.CONNECTING);
                        sendDeviceUpdateIntent();

                        mBtSocketIoThread = new BtSocketIoThread(btDeviceAddress);
                        mBtSocketIoThread.start();
                    }
                }
            }
        } else if (action.equals(ACTION_NOTIFICATION_GENERIC)) {
            String title = intent.getStringExtra("notification_title");
            String body = intent.getStringExtra("notification_body");
            byte[] msg = PebbleProtocol.encodeSMS(title, body);
            mBtSocketIoThread.write(msg);
        } else if (action.equals(ACTION_NOTIFICATION_SMS)) {
            String sender = intent.getStringExtra("notification_sender");
            String body = intent.getStringExtra("notification_body");
            String senderName = getContactDisplayNameByNumber(sender);
            byte[] msg = PebbleProtocol.encodeSMS(senderName, body);
            mBtSocketIoThread.write(msg);
        } else if (action.equals(ACTION_NOTIFICATION_EMAIL)) {
            String sender = intent.getStringExtra("notification_sender");
            String subject = intent.getStringExtra("notification_subject");
            String body = intent.getStringExtra("notification_body");
            byte[] msg = PebbleProtocol.encodeEmail(sender, subject, body);
            mBtSocketIoThread.write(msg);
        } else if (action.equals(ACTION_CALLSTATE)) {
            GBCommand command = GBCommand.values()[intent.getIntExtra("call_command", 0)]; // UGLY
            String phoneNumber = intent.getStringExtra("call_phonenumber");
            String callerName = null;
            if (phoneNumber != null) {
                callerName = getContactDisplayNameByNumber(phoneNumber);
            }
            byte[] msg = PebbleProtocol.encodeSetCallState(phoneNumber, callerName, command);
            mBtSocketIoThread.write(msg);
        } else if (action.equals(ACTION_SETTIME)) {
            byte[] msg = PebbleProtocol.encodeSetTime(-1);
            mBtSocketIoThread.write(msg);
        } else if (action.equals(ACTION_SETMUSICINFO)) {
            String artist = intent.getStringExtra("music_artist");
            String album = intent.getStringExtra("music_album");
            String track = intent.getStringExtra("music_track");
            byte[] msg = PebbleProtocol.encodeSetMusicInfo(artist, album, track);
            mBtSocketIoThread.write(msg);
        } else if (action.equals(ACTION_REQUEST_VERSIONINFO)) {
            if (gbdevice != null && gbdevice.getFirmwareVersion() == null) {
                byte[] msg = PebbleProtocol.encodeFirmwareVersionReq();
                mBtSocketIoThread.write(msg);
            } else {
                sendDeviceUpdateIntent();
            }
        } else if (action.equals(ACTION_REQUEST_APPINFO)) {
            mBtSocketIoThread.write(PebbleProtocol.encodeAppInfoReq());
        } else if (action.equals(ACTION_DELETEAPP)) {
            int id = intent.getIntExtra("app_id", -1);
            int index = intent.getIntExtra("app_index", -1);
            mBtSocketIoThread.write(PebbleProtocol.encodeAppDelete(id, index));
        } else if (action.equals(ACTION_START)) {
            startForeground(NOTIFICATION_ID, createNotification("Gadgetbridge running"));
            mStarted = true;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        setReceiversEnableState(false); // disable BroadcastReceivers

        if (mBtSocketIoThread != null) {
            try {
                mBtSocketIoThread.quit();
                mBtSocketIoThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(NOTIFICATION_ID); // need to do this because the updated notification wont be cancelled when service stops
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


    private class BtSocketIoThread extends Thread {
        private final String mmBtDeviceAddress;
        private InputStream mmInStream = null;
        private OutputStream mmOutStream = null;
        private boolean mQuit = false;
        private boolean mmIsConnected = false;
        private int mmConnectionAttempts = 0;

        public BtSocketIoThread(String btDeviceAddress) {
            mmBtDeviceAddress = btDeviceAddress;
        }

        private boolean connect(String btDeviceAddress) {
            BluetoothDevice btDevice = mBtAdapter.getRemoteDevice(btDeviceAddress);
            ParcelUuid uuids[] = btDevice.getUuids();
            try {
                mBtSocket = btDevice.createRfcommSocketToServiceRecord(uuids[0].getUuid());
                mBtSocket.connect();
                mmInStream = mBtSocket.getInputStream();
                mmOutStream = mBtSocket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
                mmInStream = null;
                mmOutStream = null;
                mBtSocket = null;
                return false;
            }
            gbdevice.setState(GBDevice.State.CONNECTED);
            sendDeviceUpdateIntent();
            updateNotification("connected to " + btDevice.getName());
            return true;
        }

        public void run() {
            mmIsConnected = connect(mmBtDeviceAddress);
            setReceiversEnableState(mmIsConnected); // enable/disable BroadcastReceivers
            mQuit = !mmIsConnected; // quit if not connected

            byte[] buffer = new byte[8192];
            int bytes;

            while (!mQuit) {
                try {
                    bytes = mmInStream.read(buffer, 0, 4);
                    if (bytes < 4)
                        continue;

                    ByteBuffer buf = ByteBuffer.wrap(buffer);
                    buf.order(ByteOrder.BIG_ENDIAN);
                    short length = buf.getShort();
                    short endpoint = buf.getShort();
                    if (length < 0 || length > 8192) {
                        Log.i(TAG, "invalid length " + length);
                        while (mmInStream.available() > 0) {
                            mmInStream.read(buffer); // read all
                        }
                        continue;
                    }

                    bytes = mmInStream.read(buffer, 4, length);
                    if (bytes < length) {
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        Log.i(TAG, "Read " + bytes + ", expected " + length + " reading remaining " + (length - bytes));
                        int bytes_rest = mmInStream.read(buffer, 4 + bytes, length - bytes);
                        bytes += bytes_rest;
                    }

                    if (length == 1 && endpoint == PebbleProtocol.ENDPOINT_PHONEVERSION) {
                        Log.i(TAG, "Pebble asked for Phone/App Version - repLYING!");
                        write(PebbleProtocol.encodePhoneVersion(PebbleProtocol.PHONEVERSION_REMOTE_OS_ANDROID));
                        write(PebbleProtocol.encodeFirmwareVersionReq());
                    } else if (endpoint != PebbleProtocol.ENDPOINT_DATALOG) {
                        GBDeviceCommand deviceCmd = PebbleProtocol.decodeResponse(buffer);
                        if (deviceCmd == null) {
                            Log.i(TAG, "unhandled message to endpoint " + endpoint + " (" + bytes + " bytes)");
                        } else {
                            evaluateGBCommandBundle(deviceCmd);
                        }
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    if (e.getMessage().contains("socket closed")) { //FIXME: this does not feel right
                        gbdevice.setState(GBDevice.State.CONNECTING);
                        sendDeviceUpdateIntent();
                        updateNotification("connection lost, trying to reconnect");

                        while (mmConnectionAttempts++ < 10) {
                            Log.i(TAG, "Trying to reconnect (attempt " + mmConnectionAttempts + ")");
                            mmIsConnected = connect(mmBtDeviceAddress);
                            if (mmIsConnected)
                                break;
                        }
                        mmConnectionAttempts = 0;
                        if (!mmIsConnected) {
                            mBtSocket = null;
                            setReceiversEnableState(false);
                            Log.i(TAG, "Bluetooth socket closed, will quit IO Thread");
                            mQuit = true;
                        }
                    }
                }
            }
            mmIsConnected = false;
            if (mBtSocket != null) {
                try {
                    mBtSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            mBtSocket = null;
            updateNotification("not connected");
            gbdevice.setState(GBDevice.State.NOT_CONNECTED);
            sendDeviceUpdateIntent();
        }

        synchronized public void write(byte[] bytes) {
            if (mmIsConnected) {
                try {
                    mmOutStream.write(bytes);
                    mmOutStream.flush();
                } catch (IOException e) {
                }
            }
        }

        public void quit() {
            mQuit = true;
            if (mBtSocket != null) {
                try {
                    mBtSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
