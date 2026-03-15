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
import com.google.firebase.database.DataSnapshot;

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
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle(getString(R.string.history));

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
        fbUtils.getAllDataFromPath(fbUtils.SUPPORT_PATH, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                supportArrayList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Support support = snapshot.getValue(Support.class);
                    if (support != null) {
                        if (support.getUserId().equals(userId)) {
                            if (support.getIsArchived()) {
                                supportArrayList.add(support); // Add to master list
                                concernAdapter.notifyDataSetChanged();
                            }
                        }
                    }
                }
                hideProgressDialog();
                binding.concernRv.setVisibility(VISIBLE);
                binding.noDataTxt.setVisibility(GONE);
            }

            @Override
            public void onDataNotFound() {
                hideProgressDialog();
                binding.concernRv.setVisibility(GONE);
                binding.noDataTxt.setVisibility(VISIBLE);
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                hideProgressDialog();
                binding.concernRv.setVisibility(GONE);
                binding.noDataTxt.setVisibility(VISIBLE);
            }
        });
    }
}