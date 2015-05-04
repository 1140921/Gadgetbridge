package nodomain.freeyourgadget.gadgetbridge.discovery;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;

import nodomain.freeyourgadget.gadgetbridge.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;

/**
 */
public class DeviceCandidate implements Parcelable {
    private BluetoothDevice device;
    private short rssi;
    private DeviceType deviceType = DeviceType.UNKNOWN;

    public DeviceCandidate(BluetoothDevice device, short rssi) {
        this.device = device;
        this.rssi = rssi;
    }

    private DeviceCandidate(Parcel in) {
        device = in.readParcelable(getClass().getClassLoader());
        rssi = (short) in.readInt();
        deviceType = DeviceType.valueOf(in.readString());

        if (device == null || deviceType == null) {
            throw new IllegalStateException("Unable to read state from Parcel");
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(device, 0);
        dest.writeInt(rssi);
        dest.writeString(deviceType.name());
    }

    public DeviceType getDeviceType() {
        return deviceType;
    }

    public String getMacAddress() {
        return device != null ? device.getAddress() : GBApplication.getContext().getString(R.string._unknown_);
    }

    public String getName() {
        String name = null;
        if (device != null) {
            name = device.getName();
        }
        if (name == null || name.length() == 0) {
            name = GBApplication.getContext().getString(R.string._unknown_);
        }
        return name;
    }

    public short getRssi() {
        return rssi;
    }

    @Override
    public int describeContents() {
        return 0;
    }
}
