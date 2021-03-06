package com.thesoftwarecompany.nfc;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;

import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String CARD_NOS = "CardNos";
    PendingIntent pendingIntent;
    NfcAdapter adapter;
    TextView txtCardContents;

    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        txtCardContents = findViewById(R.id.txtCardContents);
        adapter = NfcAdapter.getDefaultAdapter(getApplicationContext());
        if (adapter == null) {
            Toast.makeText(this, "This device doesn't support NFC", Toast.LENGTH_SHORT).show();
            finish();
        }
        pendingIntent = PendingIntent.getActivity(
                this, 0, new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        assert adapter != null;
        adapter.enableForegroundDispatch(this, pendingIntent, null, null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (adapter != null) {
            adapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        resolveIntent(intent);
    }

    private void resolveIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Tag tag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
            assert tag != null;
            String id = bytesToHexString(tag.getId());
            boolean cardRegistered = searchCard(id);
            txtCardContents.setText(id);
            AlertDialog.Builder ad = new AlertDialog.Builder(this);
            ad.setTitle("Card Details");
            if(cardRegistered){
                ad.setMessage("Card registered: "+id);
                ad.setPositiveButton("OK",(dialog, which) -> dialog.cancel());
            }else{
                ad.setMessage("Card not registered: "+id);
                ad.setNegativeButton("Cancel",(dialog, which) -> dialog.cancel());
                ad.setPositiveButton("Register",(dialog, which) -> {
                    try {
                        registerCard(id);
                        dialog.cancel();
                    } catch (JSONException e) {
                        Toast.makeText(this, "Error occurred", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                });
            }
            ad.show();

//            Log.d(TAG, "ID: " + id);
//            Log.d(TAG, "TAG: " + tag);
//            String data = detectTagData(tag);
//            Log.d(TAG, "resolveIntent: " + data);
//            byte[] payload = data.getBytes();


        }
    }

    private String bytesToHexString(byte[] src) {
        StringBuilder stringBuilder = new StringBuilder("0x");
        if (src == null || src.length <= 0) {
            return null;
        }
        char[] buffer = new char[2];
        for (byte b : src) {
            buffer[0] = Character.forDigit((b >>> 4) & 0x0F, 16);
            buffer[1] = Character.forDigit(b & 0x0F, 16);
            System.out.println(buffer);
            stringBuilder.append(buffer);
        }

        return stringBuilder.toString();
    }


    private String detectTagData(Tag tag) {
        //
        StringBuilder sb = new StringBuilder();
        byte[] id = tag.getId();
        sb.append("ID (hex): ").append(toHex(id)).append('\n');
        sb.append("ID (reversed hex): ").append(toReversedHex(id)).append('\n');
        sb.append("ID (dec): ").append(toDec(id)).append('\n');
        sb.append("ID (reversed dec): ").append(toReversedDec(id)).append('\n');

        String prefix = "android.nfc.tech.";
        sb.append("Technologies: ");
        for (String tech : tag.getTechList()) {
            sb.append(tech.substring(prefix.length()));
            sb.append(", ");
        }

        sb.delete(sb.length() - 2, sb.length());

        for (String tech : tag.getTechList()) {
            if (tech.equals(MifareClassic.class.getName())) {
                sb.append('\n');
                String type = "Unknown";

                try {
                    MifareClassic mifareTag = MifareClassic.get(tag);

                    switch (mifareTag.getType()) {
                        case MifareClassic.TYPE_CLASSIC:
                            type = "Classic";
                            break;
                        case MifareClassic.TYPE_PLUS:
                            type = "Plus";
                            break;
                        case MifareClassic.TYPE_PRO:
                            type = "Pro";
                            break;
                    }
                    sb.append("Mifare Classic type: ");
                    sb.append(type);
                    sb.append('\n');

                    sb.append("Mifare size: ");
                    sb.append(mifareTag.getSize() + " bytes");
                    sb.append('\n');

                    sb.append("Mifare sectors: ");
                    sb.append(mifareTag.getSectorCount());
                    sb.append('\n');

                    sb.append("Mifare blocks: ");
                    sb.append(mifareTag.getBlockCount());
                } catch (Exception e) {
                    sb.append("Mifare classic error: " + e.getMessage());
                }
            }

            if (tech.equals(MifareUltralight.class.getName())) {
                sb.append('\n');
                MifareUltralight mifareUlTag = MifareUltralight.get(tag);
                String type = "Unknown";
                switch (mifareUlTag.getType()) {
                    case MifareUltralight.TYPE_ULTRALIGHT:
                        type = "Ultralight";
                        break;
                    case MifareUltralight.TYPE_ULTRALIGHT_C:
                        type = "Ultralight C";
                        break;
                }
                sb.append("Mifare Ultralight type: ");
                sb.append(type);
            }
        }
        Log.v("test", sb.toString());
        return sb.toString();
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = bytes.length - 1; i >= 0; --i) {
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
            if (i > 0) {
                sb.append(" ");
            }
        }
        return sb.toString();
    }

    private String toReversedHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; ++i) {
            if (i > 0) {
                sb.append(" ");
            }
            int b = bytes[i] & 0xff;
            if (b < 0x10)
                sb.append('0');
            sb.append(Integer.toHexString(b));
        }
        return sb.toString();
    }

    private long toDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = 0; i < bytes.length; ++i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    private long toReversedDec(byte[] bytes) {
        long result = 0;
        long factor = 1;
        for (int i = bytes.length - 1; i >= 0; --i) {
            long value = bytes[i] & 0xffl;
            result += value * factor;
            factor *= 256l;
        }
        return result;
    }

    private void registerCard(String cardNo) throws JSONException {
        SharedPreferences sharedPreferences = getSharedPreferences(TAG, MODE_PRIVATE);
        String jsonString = sharedPreferences.getString(CARD_NOS, "[]");
        JSONArray jsonArray = new JSONArray(jsonString);
        boolean cardFound = searchCard(cardNo);
        if (cardFound) {
            return;
        }
        jsonArray.put(cardNo);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(CARD_NOS, jsonArray.toString());
        editor.apply();
    }

    private boolean searchCard(String cardNo) {
        try {
            SharedPreferences sharedPreferences = getSharedPreferences(TAG, MODE_PRIVATE);
            String jsonString = sharedPreferences.getString(CARD_NOS, "[]");
            JSONArray jsonArray = new JSONArray(jsonString);
            boolean cardNoFound = false;
            for (int i = 0; i < jsonArray.length(); i++) {
                if (jsonArray.get(i).toString().equals(cardNo)) {
                    cardNoFound = true;
                    break;
                }
            }
            return cardNoFound;
        } catch (JSONException e) {
            return false;
        }
    }


}