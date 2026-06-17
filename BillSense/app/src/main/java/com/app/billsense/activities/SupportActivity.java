package com.app.billsense.activities;

import static com.app.billsense.utils.InputValidator.isValidConcern;
import static com.app.billsense.utils.InputValidator.setInputError;
import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;
import static com.app.billsense.utils.Utils.showToast;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.LayoutInflater;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.billsense.R;
import com.app.billsense.adapters.ConcernAdapter;
import com.app.billsense.databinding.ActivitySupportBinding;
import com.app.billsense.databinding.DialogAddConcernBinding;
import com.app.billsense.fcm.FcmNotificationSender;
import com.app.billsense.interfaces.ConcernInterface;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Support;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.app.billsense.utils.Utils;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import java.util.ArrayList;

public class SupportActivity extends AppCompatActivity implements ConcernInterface {
    private ActivitySupportBinding binding;
    private MaterialToolbar toolbar;
    private FBUtils fbUtils;
    private String userId, userName;
    private ConcernAdapter concernAdapter;
    private final ArrayList<Support> supportArrayList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySupportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fbUtils = new FBUtils();
        userId = PrefManager.getInstance().getUserId();
        userName = PrefManager.getInstance().getUserName();

        toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.customer_support));
        }

        toolbar.setNavigationOnClickListener(view -> {
            finish();
        });

        binding.concernFab.setOnClickListener(view -> {
            showAddConcernDialog();
        });

        binding.historyFab.setOnClickListener(view -> {
            startActivity(new Intent(SupportActivity.this, SupportHistoryActivity.class));
        });

        getConcernByUserId();
        setupSearchListener();

    }

    private void setupSearchListener() {
        binding.searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // Not needed
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (concernAdapter != null) {
                    concernAdapter.getFilter().filter(s);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Not needed
            }
        });
    }

    private void getConcernByUserId() {
        binding.concernRv.setHasFixedSize(true);
        binding.concernRv.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.VERTICAL, false));
        concernAdapter = new ConcernAdapter(this, supportArrayList, this);
        binding.concernRv.setAdapter(concernAdapter);
        showProgressDialog(this);
        fbUtils.fetchJsonFromPath(fbUtils.SUPPORT_PATH, new FBInterface.OnRestJsonFetchCallback() {
            @Override
            public void onJsonFetchSuccess(String json) {
                supportArrayList.clear();
                try {
                    org.json.JSONObject root = new org.json.JSONObject(json);
                    java.util.Iterator<String> keys = root.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        org.json.JSONObject obj = root.getJSONObject(key);
                        String objUserId = obj.optString("userId", "");
                        boolean isArchived = obj.optBoolean("isArchived", false);
                        if (objUserId.equals(userId) && !isArchived) {
                            Support support = new Support();
                            support.setId(key);
                            support.setTicketNo(obj.optString("ticketNo", ""));
                            support.setUserId(obj.optString("userId", ""));
                            support.setUserName(obj.optString("userName", ""));
                            support.setConcern(obj.optString("concern", ""));
                            support.setStatus(obj.optString("status", "pending"));
                            support.setArchived(isArchived);
                            support.setDate(obj.optString("date", ""));
                            supportArrayList.add(support);
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("SupportActivity", "Parse error: " + e.getMessage());
                }
                if (concernAdapter != null) {
                    concernAdapter.updateData(new ArrayList<>(supportArrayList));
                    String currentQuery = binding.searchEditText.getText().toString();
                    if (!currentQuery.isEmpty()) {
                        concernAdapter.getFilter().filter(currentQuery);
                    }
                }
                hideProgressDialog();
            }

            @Override
            public void onJsonFetchEmpty() {
                hideProgressDialog();
                supportArrayList.clear();
                if (concernAdapter != null) {
                    concernAdapter.updateData(new ArrayList<>(supportArrayList));
                }
            }

            @Override
            public void onJsonFetchFailed(String error) {
                hideProgressDialog();
                supportArrayList.clear();
                if (concernAdapter != null) {
                    concernAdapter.updateData(new ArrayList<>(supportArrayList));
                }
                showToast(SupportActivity.this, "Failed to load: " + error);
            }
        });
    }

    private void showAddConcernDialog() {
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this);
        DialogAddConcernBinding concernBinding = DialogAddConcernBinding.inflate(LayoutInflater.from(this));
        dialogBuilder.setView(concernBinding.getRoot());
        AlertDialog alertDialog = dialogBuilder.create();
        alertDialog.setCanceledOnTouchOutside(false);

        TextInputLayout textInputLayout = concernBinding.concernInput;
        EditText inputConcern = textInputLayout.getEditText();

        concernBinding.submitConcern.setOnClickListener(view -> {
            String concern = inputConcern.getText().toString();
            if (!isValidConcern(concern)) {
                setInputError(textInputLayout, "Write valid concern( min 8 characters)");
            } else {
                showProgressDialog(SupportActivity.this);
                fbUtils.saveUserConcernData(fbUtils.SUPPORT_PATH, new FBInterface.OnConcernDataSaveCallBack() {
                            @Override
                            public void onConcernDataSaveSuccess() {
                                alertDialog.dismiss();
                                hideProgressDialog();
                                FcmNotificationSender.get().sendNotificationToTopic(
                                        null, "New Concern", "New concern from " + userName + " has been submitted. ",
                                        userId, "1", "support"
                                );
                                showToast(SupportActivity.this, "Concern submitted successfully");
                            }

                            @Override
                            public void onConcernDataSaveFailure(Exception e) {
                                alertDialog.dismiss();
                                hideProgressDialog();
                                showToast(SupportActivity.this, "Failed to submit concern: " + e.getMessage());
                            }
                        },
                        new Pair<>("ticketNo", Utils.generateTicketNo()),
                        new Pair<>("userId", userId),
                        new Pair<>("userName", userName),
                        new Pair<>("concern", concern),
                        new Pair<>("status", "pending"),
                        new Pair<>("isArchived", false),
                        new Pair<>("date", Utils.getCurrentDateTime())
                );
            }
        });

        alertDialog.show();
    }

    @Override
    public void onDeleteConcern(String id) {
        showDeleteConcernDialog(id);
    }

    private void showDeleteConcernDialog(String id) {
        MaterialAlertDialogBuilder dialogBuilder = new MaterialAlertDialogBuilder(this);
        dialogBuilder.setTitle("Delete Concern");
        dialogBuilder.setMessage("Are you sure you want to delete this concern?");
        dialogBuilder.setPositiveButton("Yes", (dialogInterface, i) -> {
            showProgressDialog(this);
            fbUtils.deleteDataFromPath(fbUtils.SUPPORT_PATH, id, new FBInterface.OnDeleteDataCallBack() {
                @Override
                public void onDeleteDataSuccess() {
                    hideProgressDialog();
                    showToast(SupportActivity.this, "Concern deleted successfully");
                    dialogInterface.dismiss();
                }

                @Override
                public void onDeleteDataFailure(Exception e) {
                    hideProgressDialog();
                    showToast(SupportActivity.this, "Failed to delete concern: " + e.getMessage());
                    dialogInterface.dismiss();
                }
            });
        });
        dialogBuilder.setNegativeButton("No", (dialogInterface, i) -> {
            dialogInterface.dismiss();
        });
        dialogBuilder.show();

    }

    @Override
    public void onChatConcern(Support support) {
        startActivity(new Intent(SupportActivity.this, SupportChatActivity.class)
                .putExtra("id", support.getId())
                .putExtra("ticketNo", support.getTicketNo()));
    }
}