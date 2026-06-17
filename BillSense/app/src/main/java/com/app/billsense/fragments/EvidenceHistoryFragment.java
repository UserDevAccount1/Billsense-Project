package com.app.billsense.fragments;

import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.app.billsense.adapters.CasesAdapter;
import com.app.billsense.databinding.FragmentEvidenceHistoryBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Cases;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class EvidenceHistoryFragment extends Fragment {
    private static final String TAG = "EvidenceHistoryFragment";
    private FragmentEvidenceHistoryBinding binding;
    private String userId;
    private FBUtils fbUtils;
    private CasesAdapter casesAdapter;
    private ArrayList<Cases> casesArrayList = new ArrayList<>();

    public EvidenceHistoryFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentEvidenceHistoryBinding.inflate(inflater, container, false);
        fbUtils = new FBUtils();
        userId = PrefManager.getInstance().getUserId();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getAllCasesHistory();
    }

    private void getAllCasesHistory() {
        if (getContext() == null) return;
        showProgressDialog(requireContext());

        binding.caseHistoryRv.setHasFixedSize(true);
        binding.caseHistoryRv.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.VERTICAL, false));
        casesAdapter = new CasesAdapter(requireContext(), casesArrayList);
        binding.caseHistoryRv.setAdapter(casesAdapter);

        // Use pure REST fetch to avoid Firebase SDK hangs
        fbUtils.fetchJsonFromPath(fbUtils.CASES_PATH, new FBInterface.OnRestJsonFetchCallback() {
            @Override
            public void onJsonFetchSuccess(String jsonBody) {
                if (!isAdded()) return;
                try {
                    Gson gson = new Gson();
                    JSONObject json = new JSONObject(jsonBody);
                    Iterator<String> keys = json.keys();
                    casesArrayList.clear();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object child = json.get(key);
                        if (child instanceof JSONObject) {
                            Cases caseItem = gson.fromJson(child.toString(), Cases.class);
                            if (caseItem != null && caseItem.getIsArchived()) {
                                casesArrayList.add(caseItem);
                            }
                        }
                    }
                    casesAdapter.notifyDataSetChanged();
                    updateEmptyState();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse cases JSON: " + e.getMessage());
                    casesArrayList.clear();
                    casesAdapter.notifyDataSetChanged();
                    updateEmptyState();
                }
                hideProgressDialog();
            }

            @Override
            public void onJsonFetchEmpty() {
                if (!isAdded()) return;
                casesArrayList.clear();
                casesAdapter.notifyDataSetChanged();
                updateEmptyState();
                hideProgressDialog();
            }

            @Override
            public void onJsonFetchFailed(String errorMessage) {
                if (!isAdded()) return;
                Log.e(TAG, "Failed to fetch cases: " + errorMessage);
                casesArrayList.clear();
                casesAdapter.notifyDataSetChanged();
                updateEmptyState();
                hideProgressDialog();
            }
        });
    }

    private void updateEmptyState() {
        if (casesArrayList.isEmpty()) {
            binding.noDataTxt.setVisibility(View.VISIBLE);
            binding.caseHistoryRv.setVisibility(View.GONE);
        } else {
            binding.noDataTxt.setVisibility(View.GONE);
            binding.caseHistoryRv.setVisibility(View.VISIBLE);
        }
    }
}
