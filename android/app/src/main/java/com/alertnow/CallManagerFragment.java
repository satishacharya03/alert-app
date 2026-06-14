package com.alertnow;

import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.HashSet;
import java.util.Set;

public class CallManagerFragment extends Fragment {

    private Button btnRequestRole, btnWhitelist, btnBlacklist;
    private EditText etPhoneNumber;
    private LinearLayout layoutWhitelist, layoutBlacklist;
    private SharedPreferences prefs;

    private static final int REQUEST_ID_ROLE = 101;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_call_manager, container, false);

        btnRequestRole = view.findViewById(R.id.btnRequestRole);
        btnWhitelist = view.findViewById(R.id.btnWhitelist);
        btnBlacklist = view.findViewById(R.id.btnBlacklist);
        etPhoneNumber = view.findViewById(R.id.etPhoneNumber);
        layoutWhitelist = view.findViewById(R.id.layoutWhitelist);
        layoutBlacklist = view.findViewById(R.id.layoutBlacklist);

        prefs = requireActivity().getSharedPreferences("call_filter", Context.MODE_PRIVATE);

        btnRequestRole.setOnClickListener(v -> requestCallScreeningRole());
        btnWhitelist.setOnClickListener(v -> addNumber("whitelist"));
        btnBlacklist.setOnClickListener(v -> addNumber("blacklist"));

        refreshLists();

        return view;
    }

    private void requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) requireActivity().getSystemService(Context.ROLE_SERVICE);
            if (roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    Toast.makeText(getContext(), "Already default call screening app", Toast.LENGTH_SHORT).show();
                } else {
                    Intent intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING);
                    startActivityForResult(intent, REQUEST_ID_ROLE);
                }
            }
        } else {
            Toast.makeText(getContext(), "Call screening requires Android 10+", Toast.LENGTH_SHORT).show();
        }
    }

    private void addNumber(String listType) {
        String num = etPhoneNumber.getText().toString().trim();
        if (num.isEmpty()) return;

        Set<String> list = new HashSet<>(prefs.getStringSet(listType, new HashSet<>()));
        list.add(num);
        prefs.edit().putStringSet(listType, list).apply();
        
        // Remove from the other list if it exists there
        String otherListType = listType.equals("whitelist") ? "blacklist" : "whitelist";
        Set<String> otherList = new HashSet<>(prefs.getStringSet(otherListType, new HashSet<>()));
        if(otherList.contains(num)) {
            otherList.remove(num);
            prefs.edit().putStringSet(otherListType, otherList).apply();
        }

        etPhoneNumber.setText("");
        refreshLists();
    }

    private void removeNumber(String listType, String num) {
        Set<String> list = new HashSet<>(prefs.getStringSet(listType, new HashSet<>()));
        list.remove(num);
        prefs.edit().putStringSet(listType, list).apply();
        refreshLists();
    }

    private void refreshLists() {
        layoutWhitelist.removeAllViews();
        layoutBlacklist.removeAllViews();

        Set<String> white = prefs.getStringSet("whitelist", new HashSet<>());
        Set<String> black = prefs.getStringSet("blacklist", new HashSet<>());

        for (String num : white) {
            addNumberView(layoutWhitelist, "whitelist", num);
        }

        for (String num : black) {
            addNumberView(layoutBlacklist, "blacklist", num);
        }
    }

    private void addNumberView(LinearLayout parent, String listType, String num) {
        TextView tv = new TextView(getContext());
        tv.setText(num + "  [Remove]");
        tv.setTextColor(0xFFFFFFFF);
        tv.setPadding(0, 16, 0, 16);
        tv.setTextSize(16f);
        tv.setOnClickListener(v -> removeNumber(listType, num));
        parent.addView(tv);
    }
}
