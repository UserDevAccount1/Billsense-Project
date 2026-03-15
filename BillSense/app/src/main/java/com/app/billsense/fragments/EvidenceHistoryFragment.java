package com.app.billsense.fragments;

import static com.app.billsense.utils.ProgressDialogUtil.hideProgressDialog;
import static com.app.billsense.utils.ProgressDialogUtil.showProgressDialog;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.app.billsense.R;
import com.app.billsense.adapters.CasesAdapter;
import com.app.billsense.databinding.FragmentEvidenceHistoryBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Cases;
import com.app.billsense.utils.FBUtils;
import com.app.billsense.utils.PrefManager;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.database.DataSnapshot;

import java.util.ArrayList;

public class EvidenceHistoryFragment extends Fragment {
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
        // Inflate the layout for this fragment
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
        showProgressDialog(requireContext());
        binding.caseHistoryRv.setHasFixedSize(true);
        binding.caseHistoryRv.setLayoutManager(new LinearLayoutManager(
                requireContext(), LinearLayoutManager.VERTICAL, false));
        casesAdapter = new CasesAdapter(requireContext(), casesArrayList);
        binding.caseHistoryRv.setAdapter(casesAdapter);
        fbUtils.getAllDataFromPath(fbUtils.CASES_PATH, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    Cases cases = snapshot.getValue(Cases.class);
                    assert cases != null;
                    if (cases.getIsArchived()) {
                        casesArrayList.add(cases);
                    }
                    casesAdapter.notifyDataSetChanged();
                }
                if (casesArrayList.isEmpty()){
                    binding.noDataTxt.setVisibility(View.VISIBLE);
                    binding.caseHistoryRv.setVisibility(View.GONE);
                }else {
                    binding.noDataTxt.setVisibility(View.GONE);
                    binding.caseHistoryRv.setVisibility(View.VISIBLE);
                }
                hideProgressDialog();
            }

            @Override
            public void onDataNotFound() {
                casesArrayList.clear();
                casesAdapter.notifyDataSetChanged();
                binding.noDataTxt.setVisibility(View.VISIBLE);
                binding.caseHistoryRv.setVisibility(View.GONE);
                hideProgressDialog();
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                casesArrayList.clear();
                casesAdapter.notifyDataSetChanged();
                binding.noDataTxt.setVisibility(View.VISIBLE);
                binding.caseHistoryRv.setVisibility(View.GONE);
                hideProgressDialog();
            }
        });
    }
}