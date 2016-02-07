package nodomain.freeyourgadget.gadgetbridge.service.devices.pebble;


import android.database.sqlite.SQLiteDatabase;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Date;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.pebble.HealthSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.impl.GBActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class DatalogHandlerHealth extends DatalogHandler {

    private final int preambleLength = 10;
    private final int packetLength = 99;

    private static final Logger LOG = LoggerFactory.getLogger(DatalogHandlerHealth.class);

    public DatalogHandlerHealth(int tag, PebbleProtocol pebbleProtocol) {
       super(tag, pebbleProtocol);
    }

    @Override
    public String getTagInfo() {
        return "(health)";
    }

    @Override
    public boolean handleMessage(ByteBuffer datalogMessage, int length) {
        LOG.info(GB.hexdump(datalogMessage.array(), preambleLength, length-preambleLength));

        int unknownPacketPreamble, timestamp;
        byte unknownC, recordLength, recordNum;
        short unknownA;
        int beginOfPacketPosition, beginOfSamplesPosition;

        byte steps, orientation; //possibly
        short intensity; // possibly

        if (0 == ((length - preambleLength) % packetLength)) { // one datalog message may contain several packets
            for (int packet = 0; packet < ((length - preambleLength) / packetLength); packet++) {
                beginOfPacketPosition = preambleLength + packet*packetLength;
                datalogMessage.position(beginOfPacketPosition);
                unknownPacketPreamble = datalogMessage.getInt();
                unknownA = datalogMessage.getShort();
                timestamp = datalogMessage.getInt();
                unknownC = datalogMessage.get();
                recordLength = datalogMessage.get();
                recordNum = datalogMessage.get();

                beginOfSamplesPosition = datalogMessage.position();
                DBHandler dbHandler = null;
                try {
                    dbHandler = GBApplication.acquireDB();
                    try (SQLiteDatabase db = dbHandler.getWritableDatabase()) { // explicitly keep the db open while looping over the samples

                        ActivitySample[] samples = new ActivitySample[recordNum];
                        SampleProvider sampleProvider = new HealthSampleProvider();

                        for (int j = 0; j < recordNum; j++) {
                            datalogMessage.position(beginOfSamplesPosition + j*recordLength);
                            steps = datalogMessage.get();
                            orientation = datalogMessage.get();
                            if (j<(recordNum-1)) {
                                //TODO:apparently last minute data do not contain intensity. I guess we are reading it wrong but this approach is our best bet ATM
                                intensity = datalogMessage.getShort();
                            } else {
                                intensity = 0;
                            }
                            samples[j] = new GBActivitySample(
                                    sampleProvider,
                                    timestamp,
                                    intensity,
                                    (short) (steps & 0xff),
                                    (byte) ActivityKind.TYPE_ACTIVITY);
                            timestamp += 60;
                        }

                        dbHandler.addGBActivitySamples(samples);
                    }
                } catch (Exception ex) {
                  LOG.debug(ex.getMessage());
                    return false;//NACK, so that we get the data again
                }finally {
                    if (dbHandler != null) {
                        dbHandler.release();
                    }
                }

            }
        }
        return true;//ACK by default
    }
}