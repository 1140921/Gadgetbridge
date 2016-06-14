package nodomain.freeyourgadget.gadgetbridge.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.greenrobot.dao.query.Query;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.DeviceAttributes;
import nodomain.freeyourgadget.gadgetbridge.entities.DeviceAttributesDao;
import nodomain.freeyourgadget.gadgetbridge.entities.DeviceDao;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.entities.UserAttributes;
import nodomain.freeyourgadget.gadgetbridge.entities.UserDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.model.ValidByDate;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;

/**
 * Provides utiliy access to some common entities, so you won't need to use
 * their DAO classes.
 *
 * Maybe this code should actually be in the DAO classes themselves, but then
 * these should be under revision control instead of 100% generated at build time.
 */
public class DBHelper {
    private final Context context;

    public DBHelper(Context context) {
        this.context = context;
    }

    /**
     * Closes the database and returns its name.
     * Important: after calling this, you have to DBHandler#openDb() it again
     * to get it back to work.
     * @param dbHandler
     * @return
     * @throws IllegalStateException
     */
    private String getClosedDBPath(DBHandler dbHandler) throws IllegalStateException {
        SQLiteDatabase db = dbHandler.getDatabase();
        String path = db.getPath();
        dbHandler.closeDb();
        if (db.isOpen()) { // reference counted, so may still be open
            throw new IllegalStateException("Database must be closed");
        }
        return path;
    }

    public File exportDB(DBHandler dbHandler, File toDir) throws IllegalStateException, IOException {
        String dbPath = getClosedDBPath(dbHandler);
        try {
            File sourceFile = new File(dbPath);
            File destFile = new File(toDir, sourceFile.getName());
            if (destFile.exists()) {
                File backup = new File(toDir, destFile.getName() + "_" + getDate());
                destFile.renameTo(backup);
            } else if (!toDir.exists()) {
                if (!toDir.mkdirs()) {
                    throw new IOException("Unable to create directory: " + toDir.getAbsolutePath());
                }
            }

            FileUtils.copyFile(sourceFile, destFile);
            return destFile;
        } finally {
            dbHandler.openDb();
        }
    }

    private String getDate() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }

    public void importDB(DBHandler dbHandler, File fromFile) throws IllegalStateException, IOException {
        String dbPath = getClosedDBPath(dbHandler);
        try {
            File toFile = new File(dbPath);
            FileUtils.copyFile(fromFile, toFile);
        } finally {
            dbHandler.openDb();
        }
    }

    public void validateDB(SQLiteOpenHelper dbHandler) throws IOException {
        try (SQLiteDatabase db = dbHandler.getReadableDatabase()) {
            if (!db.isDatabaseIntegrityOk()) {
                throw new IOException("Database integrity is not OK");
            }
        }
    }

    public static void dropTable(String tableName, SQLiteDatabase db) {
        String statement = "DROP TABLE IF EXISTS '" + tableName + "'";
        db.execSQL(statement);
    }

    public static boolean existsColumn(String tableName, String columnName, SQLiteDatabase db) {
        try (Cursor res = db.rawQuery("PRAGMA table_info('" + tableName + "')", null)) {
            int index = res.getColumnIndex("name");
            if (index < 1) {
                return false; // something's really wrong
            }
            while (res.moveToNext()) {
                String cn = res.getString(index);
                if (columnName.equals(cn)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * WITHOUT ROWID is only available with sqlite 3.8.2, which is available
     * with Lollipop and later.
     *
     * @return the "WITHOUT ROWID" string or an empty string for pre-Lollipop devices
     */
    public static String getWithoutRowId() {
        if (GBApplication.isRunningLollipopOrLater()) {
            return " WITHOUT ROWID;";
        }
        return "";
    }

    public static User getUser(DaoSession session) {
        ActivityUser prefsUser = new ActivityUser();
        UserDao userDao = session.getUserDao();
        Query<User> query = userDao.queryBuilder().where(UserDao.Properties.Name.eq(prefsUser.getName())).build();
        List<User> users = query.list();
        User user;
        if (users.isEmpty()) {
            user = createUser(prefsUser, session);
        } else {
            user = users.get(0); // TODO: multiple users support?
        }
        ensureUserAttributes(user, prefsUser, session);

        return user;
    }

    private static User createUser(ActivityUser prefsUser, DaoSession session) {
        User user = new User();
        user.setName(prefsUser.getName());
        user.setBirthday(prefsUser.getUserBirthday());
        user.setGender(prefsUser.getGender());
        session.getUserDao().insert(user);

        return user;
    }

    private static void ensureUserAttributes(User user, ActivityUser prefsUser, DaoSession session) {
        List<UserAttributes> userAttributes = user.getUserAttributesList();
        if (hasUpToDateUserAttributes(userAttributes, prefsUser)) {
            return;
        }
        UserAttributes attributes = new UserAttributes();
        attributes.setValidFromUTC(DateTimeUtils.todayUTC());
        attributes.setHeightCM(prefsUser.getHeightCm());
        attributes.setWeightKG(prefsUser.getWeightKg());
        attributes.setUserId(user.getId());
        session.getUserAttributesDao().insert(attributes);

        userAttributes.add(attributes);
    }

    private static boolean hasUpToDateUserAttributes(List<UserAttributes> userAttributes, ActivityUser prefsUser) {
        for (UserAttributes attr : userAttributes) {
            if (!isValidNow(attr)) {
                return false;
            }
            if (isEqual(attr, prefsUser)) {
                return true;
            }
        }
        return false;
    }

    // TODO: move this into db queries?
    private static boolean isValidNow(ValidByDate element) {
        Calendar cal = DateTimeUtils.getCalendarUTC();
        Date nowUTC = cal.getTime();
        return isValid(element, nowUTC);
    }

    private static boolean isValid(ValidByDate element, Date nowUTC) {
        Date validFromUTC = element.getValidFromUTC();
        Date validToUTC = element.getValidToUTC();
        if (nowUTC.before(validFromUTC)) {
            return false;
        }
        if (validToUTC != null && nowUTC.after(validToUTC)) {
            return false;
        }
        return true;
    }

    private static boolean isEqual(UserAttributes attr, ActivityUser prefsUser) {
        if (prefsUser.getHeightCm() != attr.getHeightCM()) {
            return false;
        }
        if (prefsUser.getWeightKg() != attr.getWeightKG()) {
            return false;
        }
        if (prefsUser.getSleepDuration() != attr.getSleepGoalHPD()) {
            return false;
        }
        if (prefsUser.getStepsGoal() != attr.getStepsGoalSPD()) {
            return false;
        }
        return true;
    }

    private static boolean isEqual(DeviceAttributes attr, GBDevice gbDevice) {
        if (!isEqual(attr.getFirmwareVersion1(), gbDevice.getFirmwareVersion())) {
            return false;
        }
        if (!isEqual(attr.getFirmwareVersion2(), gbDevice.getFirmwareVersion2())) {
            return false;
        }
        return true;
    }

    private static boolean isEqual(String s1, String s2) {
        if (s1 == s2) {
            return true;
        }
        if (s1 != null) {
            return s1.equals(s2);
        }
        return false;
    }

    public static Device getDevice(GBDevice gbDevice, DaoSession session) {
        DeviceDao deviceDao = session.getDeviceDao();
        Query<Device> query = deviceDao.queryBuilder().where(DeviceDao.Properties.Identifier.eq(gbDevice.getAddress())).build();
        List<Device> devices = query.list();
        Device device;
        if (devices.isEmpty()) {
            device = createDevice(session, gbDevice);
        } else {
            device = devices.get(0);
        }
        ensureDeviceAttributes(device, gbDevice, session);

        return device;
    }

    private static Device createDevice(DaoSession session, GBDevice gbDevice) {
        Device device = new Device();
        device.setIdentifier(gbDevice.getAddress());
        device.setName(gbDevice.getName());
        DeviceCoordinator coordinator = DeviceHelper.getInstance().getCoordinator(gbDevice);
        device.setManufacturer(coordinator.getManufacturer());
        session.getDeviceDao().insert(device);

        return device;
    }

    private static void ensureDeviceAttributes(Device device, GBDevice gbDevice, DaoSession session) {
        List<DeviceAttributes> deviceAttributes = device.getDeviceAttributesList();
        if (hasUpToDateDeviceAttributes(deviceAttributes, gbDevice)) {
            return;
        }
        DeviceAttributes attributes = new DeviceAttributes();

        attributes.setDeviceId(device.getId());
        attributes.setValidFromUTC(DateTimeUtils.todayUTC());
        attributes.setFirmwareVersion1(gbDevice.getFirmwareVersion());
        attributes.setFirmwareVersion2(gbDevice.getFirmwareVersion2());
        DeviceAttributesDao attributesDao = session.getDeviceAttributesDao();
        attributesDao.insert(attributes);

        deviceAttributes.add(attributes);
    }

    private static boolean hasUpToDateDeviceAttributes(List<DeviceAttributes> deviceAttributes, GBDevice gbDevice) {
        for (DeviceAttributes attr : deviceAttributes) {
            if (!isValidNow(attr)) {
                return false;
            }
            if (isEqual(attr, gbDevice)) {
                return true;
            }
        }
        return false;
    }
}
