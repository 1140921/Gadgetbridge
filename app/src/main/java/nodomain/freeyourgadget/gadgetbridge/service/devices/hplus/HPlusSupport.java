package nodomain.freeyourgadget.gadgetbridge.service.devices.hplus;

/*
* @author João Paulo Barraca &lt;jpbarraca@gmail.com&gt;
*/

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.devices.hplus.HPlusConstants;
import nodomain.freeyourgadget.gadgetbridge.devices.hplus.HPlusCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;
import nodomain.freeyourgadget.gadgetbridge.model.CalendarEventSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CallSpec;
import nodomain.freeyourgadget.gadgetbridge.model.CannedMessagesSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicSpec;
import nodomain.freeyourgadget.gadgetbridge.model.MusicStateSpec;
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec;
import nodomain.freeyourgadget.gadgetbridge.service.btle.AbstractBTLEDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattCharacteristic;
import nodomain.freeyourgadget.gadgetbridge.service.btle.GattService;
import nodomain.freeyourgadget.gadgetbridge.service.btle.TransactionBuilder;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.SetDeviceStateAction;
import nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfoProfile;
import nodomain.freeyourgadget.gadgetbridge.util.GB;


public class HPlusSupport extends AbstractBTLEDeviceSupport {
    private static final Logger LOG = LoggerFactory.getLogger(HPlusSupport.class);

    public BluetoothGattCharacteristic ctrlCharacteristic = null;
    public BluetoothGattCharacteristic measureCharacteristic = null;

    private int[] lastDataStats = null;

    private final GBDeviceEventVersionInfo versionCmd = new GBDeviceEventVersionInfo();
    private HPlusHandlerThread syncHelper;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String s = intent.getAction();
            if (s.equals(DeviceInfoProfile.ACTION_DEVICE_INFO)) {
                handleDeviceInfo((nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo) intent.getParcelableExtra(DeviceInfoProfile.EXTRA_DEVICE_INFO));
            }
        }
    };

    public HPlusSupport() {
        super(LOG);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ACCESS);
        addSupportedService(GattService.UUID_SERVICE_GENERIC_ATTRIBUTE);
        addSupportedService(HPlusConstants.UUID_SERVICE_HP);

        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(getContext());
        IntentFilter intentFilter = new IntentFilter();

        broadcastManager.registerReceiver(mReceiver, intentFilter);

    }

    @Override
    public void dispose() {
        LOG.debug("Dispose");
        LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(getContext());
        broadcastManager.unregisterReceiver(mReceiver);
        super.dispose();

        if(syncHelper != null)
            syncHelper.quit();
    }

    @Override
    protected TransactionBuilder initializeDevice(TransactionBuilder builder) {
        LOG.debug("Initializing");

        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZING, getContext()));

        measureCharacteristic = getCharacteristic(HPlusConstants.UUID_CHARACTERISTIC_MEASURE);
        ctrlCharacteristic = getCharacteristic(HPlusConstants.UUID_CHARACTERISTIC_CONTROL);

        getDevice().setFirmwareVersion("N/A");
        getDevice().setFirmwareVersion2("N/A");

        syncHelper = new HPlusHandlerThread(getDevice(), getContext(), this);

        //Initialize device
        sendUserInfo(builder); //Sync preferences
        setSIT(builder);          //Sync SIT Interval
        setCurrentDate(builder);  // Sync Current Date
        setCurrentTime(builder);  // Sync Current Time

        requestDeviceInfo(builder);

        setInitialized(builder);


        syncHelper.start();

        builder.notify(getCharacteristic(HPlusConstants.UUID_CHARACTERISTIC_MEASURE), true);
        builder.setGattCallback(this);
        builder.notify(measureCharacteristic, true);

        //LOG.debug("Initialization Done");

        return builder;
    }

    private HPlusSupport sendUserInfo(TransactionBuilder builder) {
        builder.write(ctrlCharacteristic, HPlusConstants.CMD_SET_PREF_START);
        builder.write(ctrlCharacteristic, HPlusConstants.CMD_SET_PREF_START1);

        syncPreferences(builder);

        builder.write(ctrlCharacteristic, new byte[]{HPlusConstants.CMD_SET_CONF_END});
        return this;
    }

    private HPlusSupport syncPreferences(TransactionBuilder transaction) {
        LOG.info("Attempting to sync preferences with: " + getDevice().getAddress());

        byte gender = HPlusCoordinator.getUserGender(getDevice().getAddress());
        byte age = HPlusCoordinator.getUserAge(getDevice().getAddress());
        byte bodyHeight = HPlusCoordinator.getUserHeight(getDevice().getAddress());
        byte bodyWeight = HPlusCoordinator.getUserWeight(getDevice().getAddress());
        int goal = HPlusCoordinator.getGoal(getDevice().getAddress());
        byte displayTime = HPlusCoordinator.getScreenTime(getDevice().getAddress());
        byte country = HPlusCoordinator.getCountry(getDevice().getAddress());
        byte social = HPlusCoordinator.getSocial(getDevice().getAddress()); // ??
        byte allDayHeart = HPlusCoordinator.getAllDayHR(getDevice().getAddress());
        byte wrist = HPlusCoordinator.getUserWrist(getDevice().getAddress());
        byte alertTimeHour = 0;
        byte alertTimeMinute = 0;

        if (HPlusCoordinator.getSWAlertTime(getDevice().getAddress())) {
            int t = HPlusCoordinator.getAlertTime(getDevice().getAddress());

            alertTimeHour = (byte) ((t / 256) & 0xff);
            alertTimeMinute = (byte) (t % 256);
        }

        byte unit = HPlusCoordinator.getUnit(getDevice().getAddress());
        byte timemode = HPlusCoordinator.getTimeMode((getDevice().getAddress()));

        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_PREFS,
                gender,
                age,
                bodyHeight,
                bodyWeight,
                0,
                0,
                (byte) ((goal / 256) & 0xff),
                (byte) (goal % 256),
                displayTime,
                country,
                0,
                social,
                allDayHeart,
                wrist,
                0,
                alertTimeHour,
                alertTimeMinute,
                unit,
                timemode
        });

        setAllDayHeart(transaction);

        return this;
    }

    private HPlusSupport setLanguage(TransactionBuilder transaction) {
        LOG.info("Attempting to set language...");

        byte value = HPlusCoordinator.getCountry(getDevice().getAddress());
        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_LANGUAGE,
                value
        });
        return this;
    }


    private HPlusSupport setTimeMode(TransactionBuilder transaction) {
        LOG.info("Attempting to set Time Mode...");

        byte value = HPlusCoordinator.getTimeMode(getDevice().getAddress());
        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_TIMEMODE,
                value
        });
        return this;
    }

    private HPlusSupport setUnit(TransactionBuilder transaction) {
        LOG.info("Attempting to set Units...");


        byte value = HPlusCoordinator.getUnit(getDevice().getAddress());
        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_UNITS,
                value
        });
        return this;
    }

    private HPlusSupport setCurrentDate(TransactionBuilder transaction) {
        LOG.info("Attempting to set Current Date...");

        Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR) - 1900;
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_DATE,
                (byte) ((year / 256) & 0xff),
                (byte) (year % 256),
                (byte) (month + 1),
                (byte) (day)

        });
        return this;
    }

    private HPlusSupport setCurrentTime(TransactionBuilder transaction) {
        LOG.info("Attempting to set Current Time...");

        Calendar c = Calendar.getInstance();

        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_TIME,
                (byte) c.get(Calendar.HOUR_OF_DAY),
                (byte) c.get(Calendar.MINUTE),
                (byte) c.get(Calendar.SECOND)

        });
        return this;
    }


    private HPlusSupport setDayOfWeek(TransactionBuilder transaction) {
        LOG.info("Attempting to set Day Of Week...");

        Calendar c = Calendar.getInstance();

        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_WEEK,
                (byte) c.get(Calendar.DAY_OF_WEEK)
        });
        return this;
    }


    private HPlusSupport setSIT(TransactionBuilder transaction) {
        LOG.info("Attempting to set SIT...");

        int startTime = HPlusCoordinator.getSITStartTime(getDevice().getAddress());
        int endTime = HPlusCoordinator.getSITEndTime(getDevice().getAddress());

        Calendar now = Calendar.getInstance();

        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_SIT_INTERVAL,
                (byte) ((startTime / 256) & 0xff),
                (byte) (startTime % 256),
                (byte) ((endTime / 256) & 0xff),
                (byte) (endTime % 256),
                0,
                0,
                (byte) ((now.get(Calendar.YEAR) / 256) & 0xff),
                (byte) (now.get(Calendar.YEAR) % 256),
                (byte) (now.get(Calendar.MONTH) + 1),
                (byte) (now.get(Calendar.DAY_OF_MONTH)),
                (byte) (now.get(Calendar.HOUR)),
                (byte) (now.get(Calendar.MINUTE)),
                (byte) (now.get(Calendar.SECOND)),
                0,
                0,
                0,
                0

        });
        return this;
    }

    private HPlusSupport setWeight(TransactionBuilder transaction) {
        LOG.info("Attempting to set Weight...");

        byte value = HPlusCoordinator.getUserWeight(getDevice().getAddress());
        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_WEIGHT,
                value

        });
        return this;
    }

    private HPlusSupport setHeight(TransactionBuilder transaction) {
        LOG.info("Attempting to set Height...");

        byte value = HPlusCoordinator.getUserHeight(getDevice().getAddress());
        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_HEIGHT,
                value

        });
        return this;
    }


    private HPlusSupport setAge(TransactionBuilder transaction) {
        LOG.info("Attempting to set Age...");

        byte value = HPlusCoordinator.getUserAge(getDevice().getAddress());
        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_AGE,
                value

        });
        return this;
    }

    private HPlusSupport setGender(TransactionBuilder transaction) {
        LOG.info("Attempting to set Gender...");

        byte value = HPlusCoordinator.getUserGender(getDevice().getAddress());
        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_GENDER,
                value

        });
        return this;
    }


    private HPlusSupport setGoal(TransactionBuilder transaction) {
        LOG.info("Attempting to set Sex...");

        int value = HPlusCoordinator.getGoal(getDevice().getAddress());
        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_GOAL,
                (byte) ((value / 256) & 0xff),
                (byte) (value % 256)

        });
        return this;
    }


    private HPlusSupport setScreenTime(TransactionBuilder transaction) {
        LOG.info("Attempting to set Screentime...");

        byte value = HPlusCoordinator.getScreenTime(getDevice().getAddress());
        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_SCREENTIME,
                value

        });
        return this;
    }

    private HPlusSupport setAllDayHeart(TransactionBuilder transaction) {
        LOG.info("Attempting to set All Day HR...");

        byte value = HPlusCoordinator.getAllDayHR(getDevice().getAddress());
        transaction.write(ctrlCharacteristic, new byte[]{
                HPlusConstants.CMD_SET_ALLDAY_HRM,
                value

        });
        return this;
    }


    private HPlusSupport setAlarm(TransactionBuilder transaction) {
        LOG.info("Attempting to set Alarm...");
        //TODO: Find how to set alarms
        return this;
    }

    private HPlusSupport setBlood(TransactionBuilder transaction) {
        LOG.info("Attempting to set Blood...");
        //TODO: Find what blood means for the band
        return this;
    }


    private HPlusSupport setFindMe(TransactionBuilder transaction) {
        LOG.info("Attempting to set Findme...");
        //TODO: Find how this works
        return this;
    }

    private HPlusSupport requestDeviceInfo(TransactionBuilder builder) {
        LOG.debug("Requesting Device Info!");

        // HPlus devices seem to report some information in an alternative manner
        builder.write(ctrlCharacteristic, new byte[]{HPlusConstants.CMD_GET_DEVICE_ID});
        builder.write(ctrlCharacteristic, new byte[]{HPlusConstants.CMD_GET_VERSION});

        return this;
    }

    private void setInitialized(TransactionBuilder builder) {
        builder.add(new SetDeviceStateAction(getDevice(), GBDevice.State.INITIALIZED, getContext()));
    }


    @Override
    public boolean useAutoConnect() {
        return true;
    }

    @Override
    public void pair() {
        LOG.debug("Pair");
    }

    private void handleDeviceInfo(nodomain.freeyourgadget.gadgetbridge.service.btle.profiles.deviceinfo.DeviceInfo info) {
        LOG.warn("Device info: " + info);
    }

    @Override
    public void onNotification(NotificationSpec notificationSpec) {
        LOG.debug("Got Notification");
        //TODO: Show different notifications acccording to source as Band supports this
        showText(notificationSpec.title, notificationSpec.body);
    }


    @Override
    public void onSetTime() {
        TransactionBuilder builder = new TransactionBuilder("time");

        setCurrentDate(builder);
        setCurrentTime(builder);
    }

    @Override
    public void onSetAlarms(ArrayList<? extends Alarm> alarms) {

    }

    @Override
    public void onSetCallState(CallSpec callSpec) {
        switch (callSpec.command) {
            case CallSpec.CALL_INCOMING: {
                showIncomingCall(callSpec.name, callSpec.number);
                break;
            }
        }

    }

    @Override
    public void onSetCannedMessages(CannedMessagesSpec cannedMessagesSpec) {
        LOG.debug("Canned Messages: " + cannedMessagesSpec);
    }

    @Override
    public void onSetMusicState(MusicStateSpec stateSpec) {

    }

    @Override
    public void onSetMusicInfo(MusicSpec musicSpec) {

    }

    @Override
    public void onEnableRealtimeSteps(boolean enable) {

    }

    @Override
    public void onInstallApp(Uri uri) {

    }

    @Override
    public void onAppInfoReq() {

    }

    @Override
    public void onAppStart(UUID uuid, boolean start) {

    }

    @Override
    public void onAppDelete(UUID uuid) {

    }

    @Override
    public void onAppConfiguration(UUID appUuid, String config) {

    }

    @Override
    public void onAppReorder(UUID[] uuids) {

    }

    @Override
    public void onFetchActivityData() {
        if(syncHelper != null)
            syncHelper.sync();
    }

    @Override
    public void onReboot() {

    }

    @Override
    public void onHeartRateTest() {
        LOG.debug("On HeartRateTest");

        getQueue().clear();

        TransactionBuilder builder = new TransactionBuilder("HeartRateTest");

        builder.write(ctrlCharacteristic, new byte[]{HPlusConstants.CMD_SET_ALLDAY_HRM, 0x0A}); //Set Real Time... ?
        builder.queue(getQueue());
    }

    @Override
    public void onEnableRealtimeHeartRateMeasurement(boolean enable) {
        LOG.debug("Set Real Time HR Measurement: " + enable);

        getQueue().clear();

        TransactionBuilder builder = new TransactionBuilder("realTimeHeartMeasurement");
        byte state;

        if (enable)
            state = HPlusConstants.ARG_HEARTRATE_ALLDAY_ON;
        else
            state = HPlusConstants.ARG_HEARTRATE_ALLDAY_OFF;

        builder.write(ctrlCharacteristic, new byte[]{HPlusConstants.CMD_SET_ALLDAY_HRM, state});
        builder.queue(getQueue());
    }

    @Override
    public void onFindDevice(boolean start) {
        LOG.debug("Find Me");

        try {
            TransactionBuilder builder = performInitialized("findMe");

            byte[] msg = new byte[2];
            msg[0] = HPlusConstants.CMD_SET_FINDME;

            if (start)
                msg[1] = HPlusConstants.ARG_FINDME_ON;
            else
                msg[1] = HPlusConstants.ARG_FINDME_OFF;

            builder.write(ctrlCharacteristic, msg);
            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error toogling Find Me: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
        }

    }

    @Override
    public void onSetConstantVibration(int intensity) {
        LOG.debug("Vibration Trigger");

        getQueue().clear();

        try {
            TransactionBuilder builder = performInitialized("vibration");

            byte[] msg = new byte[15];
            msg[0] = HPlusConstants.CMD_SET_INCOMING_CALL_NUMBER;

            for (int i = 0; i < msg.length - 1; i++)
                msg[i + 1] = (byte) "GadgetBridge".charAt(i);

            builder.write(ctrlCharacteristic, msg);
            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error setting Vibration: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);
        }
    }

    @Override
    public void onScreenshotReq() {

    }

    @Override
    public void onEnableHeartRateSleepSupport(boolean enable) {

    }

    @Override
    public void onAddCalendarEvent(CalendarEventSpec calendarEventSpec) {

    }

    @Override
    public void onDeleteCalendarEvent(byte type, long id) {

    }

    @Override
    public void onSendConfiguration(String config) {
        LOG.debug("Send Configuration: " + config);

    }

    @Override
    public void onTestNewFunction() {
        LOG.debug("Test New Function");
    }


    private void showIncomingCall(String name, String number) {
        LOG.debug("Show Incoming Call");

        try {
            TransactionBuilder builder = performInitialized("incomingCallIcon");

            //Enable call notifications
            builder.write(ctrlCharacteristic, new byte[]{HPlusConstants.CMD_ACTION_INCOMING_CALL, 1});

            //Show Call Icon
            builder.write(ctrlCharacteristic, new byte[]{HPlusConstants.CMD_ACTION_INCOMING_CALL, HPlusConstants.ARG_INCOMING_CALL});

            //builder = performInitialized("incomingCallText");
            builder.queue(getQueue());

            //TODO: Use WaitAction
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            byte[] msg = new byte[13];

            builder = performInitialized("incomingCallNumber");

            //Show call number
            for (int i = 0; i < msg.length; i++)
                msg[i] = ' ';

            for (int i = 0; i < number.length() && i < (msg.length - 1); i++)
                msg[i + 1] = (byte) number.charAt(i);


            msg[0] = HPlusConstants.CMD_SET_INCOMING_CALL_NUMBER;

            builder.write(ctrlCharacteristic, msg);
            builder.queue(getQueue());

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            builder = performInitialized("incomingCallText");

            //Show call name
            //Must call twice, otherwise nothing happens
            for (int i = 0; i < msg.length; i++)
                msg[i] = ' ';

            for (int i = 0; i < name.length() && i < (msg.length - 1); i++)
                msg[i + 1] = (byte) name.charAt(i);

            msg[0] = HPlusConstants.CMD_ACTION_DISPLAY_TEXT_NAME;
            builder.write(ctrlCharacteristic, msg);

            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            msg[0] = HPlusConstants.CMD_ACTION_DISPLAY_TEXT_NAME_CN;
            builder.write(ctrlCharacteristic, msg);

            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error showing incoming call: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);

        }
    }

    private void showText(String message) {

        showText(null, message);
    }

    private void showText(String title, String body) {
        LOG.debug("Show Notification");

        try {
            TransactionBuilder builder = performInitialized("notification");


            byte[] msg = new byte[20];
            for (int i = 0; i < msg.length; i++)
                msg[i] = ' ';

            msg[0] = HPlusConstants.CMD_ACTION_DISPLAY_TEXT;

            String message = "";

            //TODO: Create StringUtils.pad and StringUtils.truncate
            if (title != null) {
                if (title.length() > 17) {
                    message = title.substring(0, 17);
                } else {
                    message = title;
                    for (int i = message.length(); i < 17; i++)
                        message += " ";
                }
            }
            message += body;

            int length = message.length() / 17;

            builder.write(ctrlCharacteristic, new byte[]{HPlusConstants.CMD_ACTION_INCOMING_SOCIAL, (byte) 255});

            int remaining;

            if (message.length() % 17 > 0)
                remaining = length + 1;
            else
                remaining = length;

            msg[1] = (byte) remaining;
            int message_index = 0;
            int i = 3;

            for (int j = 0; j < message.length(); j++) {
                msg[i++] = (byte) message.charAt(j);

                if (i == msg.length) {
                    message_index++;
                    msg[2] = (byte) message_index;
                    builder.write(ctrlCharacteristic, msg);

                    msg = msg.clone();
                    for (i = 3; i < msg.length; i++)
                        msg[i] = ' ';

                    if (message_index < remaining)
                        i = 3;
                    else
                        break;
                }
            }

            msg[2] = (byte) remaining;

            builder.write(ctrlCharacteristic, msg);
            builder.queue(getQueue());
        } catch (IOException e) {
            GB.toast(getContext(), "Error showing device Notification: " + e.getLocalizedMessage(), Toast.LENGTH_LONG, GB.ERROR);

        }
    }

    public boolean isExpectedDevice(BluetoothDevice device) {
        return true;
    }

    public void close() {
    }

    @Override
    public boolean onCharacteristicChanged(BluetoothGatt gatt,
                                           BluetoothGattCharacteristic characteristic) {
        if (super.onCharacteristicChanged(gatt, characteristic)) {
            return true;
        }

        UUID characteristicUUID = characteristic.getUuid();
        byte[] data = characteristic.getValue();
        if (data.length == 0)
            return true;

        switch (data[0]) {
            case HPlusConstants.DATA_VERSION:
                syncHelper.processVersion(data);
            case HPlusConstants.DATA_STATS: {
                syncHelper.processRealtimeStats(data);
                return true;
            }
            case HPlusConstants.DATA_SLEEP: {
                syncHelper.processIncomingSleepData(data);
                return true;
            }
            case HPlusConstants.DATA_STEPS:{
                syncHelper.processStepStats(data);
                return true;
            }
            case HPlusConstants.DATA_DAY_SUMMARY:
            case HPlusConstants.DATA_DAY_SUMMARY_ALT:
                syncHelper.processIncomingDaySlotData(data);
                return true;
            default:
                LOG.info("Unhandled characteristic changed: " + characteristicUUID);

        }
        return false;
    }


}
