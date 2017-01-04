package nodomain.freeyourgadget.gadgetbridge.service.devices.hplus;

/*
* @author João Paulo Barraca &lt;jpbarraca@gmail.com&gt;
*/


import java.util.Calendar;
import java.util.Locale;


class HPlusDataRecordSteps extends HPlusDataRecord{
    public int year;
    public int month;
    public int day;

    public int steps;
    public int distance;

    public int activeTime;
    public int maxHeartRate;
    public int minHeartRate;
    public int calories;

    HPlusDataRecordSteps(byte[] data) {
        super(data);

        year =  (data[10] & 0xFF) * 256 + (data[9] & 0xFF);
        month = data[11] & 0xFF;
        day = data[12] & 0xFF;

        //Recover from bug in firmware where year is corrupted
        if(year < 1900)
            year += 1900;

        if (year < 2000 || month > 12 || day > 31) {
            throw new IllegalArgumentException("Invalid record date "+year+"-"+month+"-"+day);
        }
        steps = (data[2] & 0xFF) * 256 + (data[1] & 0xFF);
        distance = (data[4] & 0xFF) * 256 + (data[3] & 0xFF);
        activeTime = (data[14] & 0xFF) * 256 + (data[13] & 0xFF);
        calories = (data[6] & 0xFF) * 256 + (data[5] & 0xFF);
        calories += (data[8] & 0xFF) * 256 + (data[7] & 0xFF);

        maxHeartRate = data[15] & 0xFF;
        minHeartRate = data[16] & 0xFF;

        Calendar date = Calendar.getInstance();
        date.set(Calendar.YEAR, year);
        date.set(Calendar.MONTH, month - 1);
        date.set(Calendar.DAY_OF_MONTH, day);
        date.set(Calendar.HOUR_OF_DAY, 23);
        date.set(Calendar.MINUTE, 59);
        date.set(Calendar.SECOND, 59);
        date.set(Calendar.MILLISECOND, 999);

        timestamp = (int) (date.getTimeInMillis() / 1000);
    }

    public String toString(){
        return String.format(Locale.US, "%s-%s-%s steps:%d distance:%d minHR:%d maxHR:%d calories:%d activeTime:%d", year, month, day, steps, distance,minHeartRate, maxHeartRate, calories, activeTime);
    }
}