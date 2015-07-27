package nodomain.freeyourgadget.gadgetbridge;

import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.SampleProvider;

public class GBActivitySample implements ActivitySample {
    private final int timestamp;
    private final SampleProvider provider;
    private final short intensity;
    private final short steps;
    private final byte type;

    public GBActivitySample(SampleProvider provider, int timestamp, short intensity, short steps, byte type) {
        this.timestamp = timestamp;
        this.provider = provider;
        this.intensity = intensity;
        this.steps = steps;
        this.type = type;
    }

    @Override
    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public SampleProvider getProvider() {
        return provider;
    }

    @Override
    public short getRawIntensity() {
        return intensity;
    }

    @Override
    public float getIntensity() {
        return getProvider().normalizeIntensity(getRawIntensity());
    }

    @Override
    public short getSteps() {
        return steps;
    }

    @Override
    public byte getRawKind() {
        return type;
    }

    @Override
    public int getKind() {
        return getProvider().normalizeType(getRawKind());
    }
}
