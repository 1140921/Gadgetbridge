package nodomain.freeyourgadget.gadgetbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;

public class K9Receiver extends BroadcastReceiver {

    private final String TAG = this.getClass().getSimpleName();
    private final Uri k9Uri = Uri.parse("content://com.fsck.k9.messageprovider/inbox_messages");

    @Override
    public void onReceive(Context context, Intent intent) {
        // get sender and subject from the Intent
        String sender = intent.getStringExtra("com.fsck.k9.intent.extra.FROM");
        String subject = intent.getStringExtra("com.fsck.k9.intent.extra.SUBJECT");

        // get preview from K9 Content Provider, unfortunately this does not come with the Intent
        String[] whereParameters = {intent.getData().toString()};
        String[] messagesProjection = {
                "preview"
        };

        Cursor c = context.getContentResolver().query(k9Uri, null, "uri=?", whereParameters, " LIMIT 1");
        c.moveToFirst();
        String preview = c.getString(c.getColumnIndex("preview"));
        c.close();

        Intent startIntent = new Intent(context, BluetoothCommunicationService.class);
        startIntent.setAction(BluetoothCommunicationService.ACTION_NOTIFICATION_EMAIL);
        startIntent.putExtra("notification_sender", sender);
        startIntent.putExtra("notification_subject", subject);
        startIntent.putExtra("notification_body", preview);

        context.startService(startIntent);
    }
}
