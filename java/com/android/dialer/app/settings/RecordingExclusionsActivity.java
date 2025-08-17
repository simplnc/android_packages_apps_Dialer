package com.android.dialer.app.settings;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.android.dialer.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RecordingExclusionsActivity extends AppCompatActivity {

    private static final int REQ_PICK_CONTACT = 1001;

    private ExclusionAdapter adapter;
    private final List<ExcludedContact> excluded = new ArrayList<>();
    private EditText searchEditText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording_exclusions);

        ListView list = findViewById(R.id.exclusion_list);
        Button add = findViewById(R.id.button_add);
        Button clear = findViewById(R.id.button_clear);
        searchEditText = findViewById(R.id.search_edit_text);

        adapter = new ExclusionAdapter(excluded);
        list.setAdapter(adapter);
        
        list.setOnItemClickListener((parent, view, position, id) -> {
            ExcludedContact contact = excluded.get(position);
            showEditDialog(contact);
        });
        
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            ExcludedContact contact = excluded.get(position);
            showRemoveDialog(contact);
            return true;
        });

        add.setOnClickListener(v -> pickContact());
        clear.setOnClickListener(v -> showClearAllDialog());
        
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
        
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
                        new String[]{
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                        }, null, null, null)) {
                    if (c != null && c.moveToFirst()) {
                        String number = c.getString(0);
                        String name = c.getString(1);
                        if (number != null) {
                            String normalized = PhoneNumberUtils.stripSeparators(number);
                            addContact(normalized, name);
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
        for (String number : set) {
            String name = getContactName(number);
            excluded.add(new ExcludedContact(number, name));
        }
        adapter.notifyDataSetChanged();
        updateCount(excluded.size());
    }

    private String getContactName(String number) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
            try (Cursor c = getContentResolver().query(uri,
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    return c.getString(0);
                }
            }
        } catch (Exception e) {
            // Ignore lookup errors
        }
        return null;
    }

    private void addContact(String number, String name) {
        Set<String> set = new HashSet<>(getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                .getStringSet("call_recording_excluded_numbers", new HashSet<>()));
        if (set.add(number)) {
            getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                    .edit().putStringSet("call_recording_excluded_numbers", set).apply();
            loadNumbers();
            Toast.makeText(this, R.string.contact_added_to_exclusions, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.contact_already_excluded, Toast.LENGTH_SHORT).show();
        }
    }

    private void removeContact(ExcludedContact contact) {
        Set<String> set = new HashSet<>(getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                .getStringSet("call_recording_excluded_numbers", new HashSet<>()));
        if (set.remove(contact.number)) {
            getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                    .edit().putStringSet("call_recording_excluded_numbers", set).apply();
            loadNumbers();
            Toast.makeText(this, R.string.contact_removed_from_exclusions, Toast.LENGTH_SHORT).show();
        }
    }

    private void clearAll() {
        getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                .edit().putStringSet("call_recording_excluded_numbers", new HashSet<>()).apply();
        loadNumbers();
        Toast.makeText(this, R.string.all_exclusions_cleared, Toast.LENGTH_SHORT).show();
    }

    private void showEditDialog(ExcludedContact contact) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_exclusion, null);
        EditText nameEdit = dialogView.findViewById(R.id.edit_contact_name);
        EditText numberEdit = dialogView.findViewById(R.id.edit_contact_number);
        
        nameEdit.setText(contact.name != null ? contact.name : "");
        numberEdit.setText(contact.number);

        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_exclusion_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String newName = nameEdit.getText().toString().trim();
                    String newNumber = numberEdit.getText().toString().trim();
                    
                    if (newNumber.isEmpty()) {
                        Toast.makeText(this, R.string.number_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    
                    updateContact(contact, newNumber, newName.isEmpty() ? null : newName);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateContact(ExcludedContact oldContact, String newNumber, String newName) {
        Set<String> set = new HashSet<>(getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                .getStringSet("call_recording_excluded_numbers", new HashSet<>()));
        
        if (set.remove(oldContact.number) && set.add(newNumber)) {
            getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                    .edit().putStringSet("call_recording_excluded_numbers", set).apply();
            loadNumbers();
            Toast.makeText(this, R.string.exclusion_updated, Toast.LENGTH_SHORT).show();
        }
    }

    private void showRemoveDialog(ExcludedContact contact) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.remove_exclusion_title)
                .setMessage(getString(R.string.remove_exclusion_message, 
                    contact.name != null ? contact.name : contact.number))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> removeContact(contact))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showClearAllDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.clear_all_exclusions_title)
                .setMessage(R.string.clear_all_exclusions_message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> clearAll())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateCount(int count) {
        getSharedPreferences(getPackageName() + "_preferences", MODE_PRIVATE)
                .edit().putInt("call_recording_excluded_count", count).apply();
    }

    private static class ExcludedContact {
        final String number;
        final String name;

        ExcludedContact(String number, String name) {
            this.number = number;
            this.name = name;
        }

        @Override
        public String toString() {
            return name != null ? name + " (" + number + ")" : number;
        }
    }

    private class ExclusionAdapter extends ArrayAdapter<ExcludedContact> implements Filterable {
        private final List<ExcludedContact> originalList;
        private final List<ExcludedContact> filteredList;

        public ExclusionAdapter(List<ExcludedContact> list) {
            super(RecordingExclusionsActivity.this, 0, list);
            this.originalList = new ArrayList<>(list);
            this.filteredList = new ArrayList<>(list);
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(
                    android.R.layout.simple_list_item_2, parent, false);
            }

            ExcludedContact contact = getItem(position);
            if (contact != null) {
                TextView text1 = convertView.findViewById(android.R.id.text1);
                TextView text2 = convertView.findViewById(android.R.id.text2);
                
                text1.setText(contact.name != null ? contact.name : getString(R.string.unknown_contact));
                text2.setText(contact.number);
            }

            return convertView;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    List<ExcludedContact> filtered = new ArrayList<>();

                    if (constraint == null || constraint.length() == 0) {
                        filtered.addAll(originalList);
                    } else {
                        String filterPattern = constraint.toString().toLowerCase().trim();
                        for (ExcludedContact contact : originalList) {
                            if (contact.name != null && contact.name.toLowerCase().contains(filterPattern) ||
                                contact.number.toLowerCase().contains(filterPattern)) {
                                filtered.add(contact);
                            }
                        }
                    }

                    results.values = filtered;
                    results.count = filtered.size();
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredList.clear();
                    if (results.values != null) {
                        filteredList.addAll((List<ExcludedContact>) results.values);
                    }
                    notifyDataSetChanged();
                }
            };
        }

        @Override
        public int getCount() {
            return filteredList.size();
        }

        @Override
        public ExcludedContact getItem(int position) {
            return filteredList.get(position);
        }
    }
}