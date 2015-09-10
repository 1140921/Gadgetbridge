package nodomain.freeyourgadget.gadgetbridge.activities;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NavUtils;
import android.support.v4.content.LocalBroadcastManager;
import android.view.ContextMenu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.adapter.GBDeviceAppAdapter;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDeviceApp;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;


public class AppManagerActivity extends Activity {
    public static final String ACTION_REFRESH_APPLIST
            = "nodomain.freeyourgadget.gadgetbridge.appmanager.action.refresh_applist";
    private static final Logger LOG = LoggerFactory.getLogger(AppManagerActivity.class);

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ControlCenter.ACTION_QUIT)) {
                finish();
            } else if (action.equals(ACTION_REFRESH_APPLIST)) {
                appList.clear();
                int appCount = intent.getIntExtra("app_count", 0);
                for (Integer i = 0; i < appCount; i++) {
                    String appName = intent.getStringExtra("app_name" + i.toString());
                    String appCreator = intent.getStringExtra("app_creator" + i.toString());
                    UUID uuid = UUID.fromString(intent.getStringExtra("app_uuid" + i.toString()));
                    GBDeviceApp.Type appType = GBDeviceApp.Type.values()[intent.getIntExtra("app_type" + i.toString(), 0)];

                    appList.add(new GBDeviceApp(uuid, appName, appCreator, "", appType));
                }

                if (sharedPrefs.getBoolean("pebble_force_untested", false)) {
                    appList.addAll(getSystemApps());
                }

                mGBDeviceAppAdapter.notifyDataSetChanged();
            }
        }
    };

    SharedPreferences sharedPrefs;

    private final List<GBDeviceApp> appList = new ArrayList<>();
    private GBDeviceAppAdapter mGBDeviceAppAdapter;
    private GBDeviceApp selectedApp = null;

    private List<GBDeviceApp> getSystemApps() {
        List<GBDeviceApp> systemApps = new ArrayList<>();
        systemApps.add(new GBDeviceApp(UUID.fromString("4dab81a6-d2fc-458a-992c-7a1f3b96a970"), "Sports (System)", "Pebble Inc.", "", GBDeviceApp.Type.UNKNOWN));
        systemApps.add(new GBDeviceApp(UUID.fromString("cf1e816a-9db0-4511-bbb8-f60c48ca8fac"), "Golf (System)", "Pebble Inc.", "", GBDeviceApp.Type.UNKNOWN));

        return systemApps;
    }

    private List<GBDeviceApp> getCachedApps() {
        List<GBDeviceApp> cachedAppList = new ArrayList<>();
        try {
            File cachePath = new File(FileUtils.getExternalFilesDir().getPath() + "/pbw-cache");
            File files[] = cachePath.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().endsWith(".pbw")) {
                        UUID uuid = UUID.fromString(file.getName().substring(0, file.getName().length() - 4));
                        cachedAppList.add(new GBDeviceApp(uuid, uuid.toString(), "N/A", "", GBDeviceApp.Type.UNKNOWN));
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return cachedAppList;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        setContentView(R.layout.activity_appmanager);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        ListView appListView = (ListView) findViewById(R.id.appListView);
        mGBDeviceAppAdapter = new GBDeviceAppAdapter(this, appList);
        appListView.setAdapter(this.mGBDeviceAppAdapter);

        appListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView parent, View v, int position, long id) {
                UUID uuid = appList.get(position).getUUID();
                GBApplication.deviceService().onAppStart(uuid);
            }
        });

        registerForContextMenu(appListView);

        appList.addAll(getCachedApps());

        if (sharedPrefs.getBoolean("pebble_force_untested", false)) {
            appList.addAll(getSystemApps());
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(ControlCenter.ACTION_QUIT);
        filter.addAction(ACTION_REFRESH_APPLIST);

        LocalBroadcastManager.getInstance(this).registerReceiver(mReceiver, filter);

        GBApplication.deviceService().onAppInfoReq();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(
                R.menu.appmanager_context, menu);
        AdapterView.AdapterContextMenuInfo acmi = (AdapterView.AdapterContextMenuInfo) menuInfo;
        selectedApp = appList.get(acmi.position);
        menu.setHeaderTitle(selectedApp.getName());
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.appmanager_app_delete:
                if (selectedApp != null) {
                    GBApplication.deviceService().onAppDelete(selectedApp.getUUID());
                }
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        super.onDestroy();
    }
}
