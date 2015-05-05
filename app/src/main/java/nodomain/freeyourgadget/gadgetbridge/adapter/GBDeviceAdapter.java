package nodomain.freeyourgadget.gadgetbridge.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.R;

public class GBDeviceAdapter extends ArrayAdapter<GBDevice> {

    private final Context context;

    public GBDeviceAdapter(Context context, List<GBDevice> deviceList) {
        super(context, 0, deviceList);

        this.context = context;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        GBDevice device = getItem(position);

        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            view = inflater.inflate(R.layout.device_item, parent, false);
        }
        TextView deviceStatusLabel = (TextView) view.findViewById(R.id.device_status);
        TextView deviceNameLabel = (TextView) view.findViewById(R.id.device_name);
        TextView deviceInfoLabel = (TextView) view.findViewById(R.id.device_info);
        ImageView deviceImageView = (ImageView) view.findViewById(R.id.device_image);

        deviceStatusLabel.setText(device.getStateString());
        deviceNameLabel.setText(device.getName());
        deviceInfoLabel.setText(device.getInfoString());

        switch (device.getType()) {
            case PEBBLE:
                deviceImageView.setImageResource(R.drawable.ic_device_pebble);
                break;
            case MIBAND:
                deviceImageView.setImageResource(R.drawable.ic_device_miband);
                break;
            default:
                deviceImageView.setImageResource(R.drawable.ic_launcher);
        }

        return view;
    }
}
