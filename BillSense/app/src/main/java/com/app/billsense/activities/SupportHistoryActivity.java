package com.app.billsense.activities;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.billsense.R;
import com.app.billsense.adapters.ConcernHistoryAdapter;
import com.app.billsense.databinding.ActivitySupportHistoryBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Support;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;

public class SupportHistoryActivity extends AppCompatActivity {
    private ActivitySupportHistoryBinding binding;
    private MaterialToolbar toolbar;
    private FBUtils fbUtils;
    private String userId, userName;
    private ConcernHistoryAdapter concernAdapter;
    private final ArrayList<Support> supportArrayList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySupportHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fbUtils = new FBUtils();
        userId = PrefManager.getInstance().getUserId();

        toolbar = binding.toolbar;
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.history));
        }

        toolbar.setNavigationOnClickListener(view -> {
            finish();
        });

        getConcernHistory();
    }

    private void getConcernHistory() {
        showProgressDialog(this);
        binding.concernRv.setHasFixedSize(true);
        binding.concernRv.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.VERTICAL, false));
        concernAdapter = new ConcernHistoryAdapter(this, supportArrayList);
        binding.concernRv.setAdapter(concernAdapter);
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
                        if (objUserId.equals(userId) && isArchived) {
                            Support support = new Support();
                            support.setId(key);
                            support.setTicketNo(obj.optString("ticketNo", ""));
                            support.setUserId(obj.optString("userId", ""));
                            support.setUserName(obj.optString("userName", ""));
                            support.setConcern(obj.optString("concern", ""));
                            support.setStatus(obj.optString("status", "pending"));
                            support.setArchived(true);
                            support.setDate(obj.optString("date", ""));
                            supportArrayList.add(support);
                        }
                    }
                } catch (Exception e) {
                    android.util.Log.e("SupportHistory", "Parse error: " + e.getMessage());
                }
                concernAdapter.notifyDataSetChanged();
                hideProgressDialog();
                if (supportArrayList.isEmpty()) {
                    binding.concernRv.setVisibility(GONE);
                    binding.noDataTxt.setVisibility(VISIBLE);
                } else {
                    binding.concernRv.setVisibility(VISIBLE);
                    binding.noDataTxt.setVisibility(GONE);
                }
            }

            @Override
            public void onJsonFetchEmpty() {
                hideProgressDialog();
                binding.concernRv.setVisibility(GONE);
                binding.noDataTxt.setVisibility(VISIBLE);
            }

            @Override
            public void onJsonFetchFailed(String error) {
                hideProgressDialog();
                binding.concernRv.setVisibility(GONE);
                binding.noDataTxt.setVisibility(VISIBLE);
            }
        });
    }
}