package nodomain.freeyourgadget.gadgetbridge.devices;

import android.net.Uri;

import java.util.ArrayList;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.model.ServiceCommand;
import nodomain.freeyourgadget.gadgetbridge.model.Alarm;

/**
 * Specifies all events that GadgetBridge intends to send to the gadget device.
 * Implementations can decide to ignore events that they do not support.
 * Implementations need to send/encode event to the connected device.
 */
public interface EventHandler {
    void onSMS(String from, String body);

    void onEmail(String from, String subject, String body);

    void onGenericNotification(String title, String details);

    void onSetTime(long ts);

    void onSetAlarms(ArrayList<Alarm> alarms);

    void onSetCallState(String number, String name, ServiceCommand command);

    void onSetMusicInfo(String artist, String album, String track);

    void onFirmwareVersionReq();

    void onBatteryInfoReq();

    void onInstallApp(Uri uri);

    void onAppInfoReq();

    void onAppStart(UUID uuid);

    void onAppDelete(UUID uuid);

    void onPhoneVersion(byte os);

    void onFetchActivityData();

    void onReboot();

    void onFindDevice(boolean start);

    void onScreenshotReq();
}
