package com.android.dialer.app.settings;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.dialer.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecordingExclusionsActivity extends AppCompatActivity {

    private static final int REQ_PICK_CONTACT = 1001;

    private ArrayAdapter<String> adapter;
    private final List<String> excluded = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording_exclusions);

        ListView list = findViewById(R.id.exclusion_list);
        Button add = findViewById(R.id.button_add);
        Button clear = findViewById(R.id.button_clear);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, excluded);
        list.setAdapter(adapter);
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            removeNumber(excluded.get(position));
            return true;
        });

        add.setOnClickListener(v -> pickContact());
        clear.setOnClickListener(v -> clearAll());
        loadNumbers();
    }

    private void pickContact() {
        Intent intent = new Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
        startActivityForResult(intent, REQ_PICK_CONTACT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_CONTACT && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                try (Cursor c = getContentResolver().query(uri,
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER}, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        String number = c.getString(0);
                        if (number != null) {
                            String normalized = PhoneNumberUtils.stripSeparators(number);
                            addNumber(normalized);
                        }
                    }
                }
            }
        }
    }

    private void loadNumbers() {
        Set<String> set = getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                .getStringSet("call_recording_excluded_numbers", new HashSet<>());
        excluded.clear();
        excluded.addAll(set);
        adapter.notifyDataSetChanged();
        updateCount(set.size());
    }

    private void addNumber(String number) {
        Set<String> set = new HashSet<>(getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                .getStringSet("call_recording_excluded_numbers", new HashSet<>()));
        if (set.add(number)) {
            getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                    .edit().putStringSet("call_recording_excluded_numbers", set).apply();
            loadNumbers();
        }
    }

    private void removeNumber(String number) {
        Set<String> set = new HashSet<>(getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                .getStringSet("call_recording_excluded_numbers", new HashSet<>()));
        if (set.remove(number)) {
            getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                    .edit().putStringSet("call_recording_excluded_numbers", set).apply();
            loadNumbers();
        }
    }

    private void clearAll() {
        getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                .edit().putStringSet("call_recording_excluded_numbers", new HashSet<>()).apply();
        loadNumbers();
    }

    private void updateCount(int count) {
        getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                .edit().putInt("call_recording_excluded_count", count).apply();
    }
}