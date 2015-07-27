package nodomain.freeyourgadget.gadgetbridge;

import android.app.Activity;

import nodomain.freeyourgadget.gadgetbridge.discovery.DeviceCandidate;
import nodomain.freeyourgadget.gadgetbridge.model.SampleProvider;

public interface DeviceCoordinator {
    String EXTRA_DEVICE_MAC_ADDRESS = "nodomain.freeyourgadget.gadgetbridge.discovery.DeviceCandidate.EXTRA_MAC_ADDRESS";

    boolean supports(DeviceCandidate candidate);

    boolean supports(GBDevice device);

    DeviceType getDeviceType();

    Class<? extends Activity> getPairingActivity();

    Class<? extends Activity> getPrimaryActivity();

    SampleProvider getSampleProvider();
}
