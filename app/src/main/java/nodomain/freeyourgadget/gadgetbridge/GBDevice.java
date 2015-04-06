package nodomain.freeyourgadget.gadgetbridge;

public class GBDevice {
    private final String name;
    private final String address;
    private final Type type;
    private String firmwareVersion = null;
    private State state = State.NOT_CONNECTED;
    private byte freeAppSlot = -1;

    public GBDevice(String address, String name, Type type) {
        this.address = address;
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public String getFirmwareVersion() {
        return firmwareVersion;
    }

    public void setFirmwareVersion(String firmwareVersion) {
        this.firmwareVersion = firmwareVersion;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    String getStateString() {
        switch (state) {
            case NOT_CONNECTED:
                return "not connected"; // TODO: do not hardcode
            case CONNECTING:
                return "connecting";
            case CONNECTED:
                return "connected";
        }
        return "unknown state";
    }

    public String getInfoString() {
        if (firmwareVersion != null) {
            return getStateString() + " (FW: " + firmwareVersion + ")";
        } else {
            return getStateString();
        }
    }

    public Type getType() {
        return type;
    }

    public void setFreeAppSlot(byte freeAppSlot) {
        this.freeAppSlot = freeAppSlot;
    }

    public byte getFreeAppSlot() {
        return freeAppSlot;
    }

    public enum State {
        NOT_CONNECTED,
        CONNECTING,
        CONNECTED
    }

    public enum Type {
        UNKNOWN,
        PEBBLE,
        MIBAND
    }

}
