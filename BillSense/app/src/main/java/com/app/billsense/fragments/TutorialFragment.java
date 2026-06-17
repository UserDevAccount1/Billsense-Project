package com.app.billsense.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.app.billsense.R;
import com.app.billsense.adapters.TutorialsAdapter;
import com.app.billsense.databinding.FragmentTutorialBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Tutorials;
import com.app.billsense.utils.FBUtils;
import com.google.firebase.database.DataSnapshot;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class TutorialFragment extends Fragment {
    private FragmentTutorialBinding binding;
    private FBUtils fbUtils;
    private TutorialsAdapter tutorialsAdapter;
    private final ArrayList<Tutorials> tutorialsArrayList = new ArrayList<>();
    private static final SimpleDateFormat DATE_TIME_FORMATTER =
            new SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.US);

    public TutorialFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentTutorialBinding.inflate(inflater, container, false);
        fbUtils = new FBUtils();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getAllTutorials();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void getAllTutorials() {
        binding.tutorialRv.setHasFixedSize(true);
        binding.tutorialRv.setLayoutManager(new LinearLayoutManager(
                requireActivity(), LinearLayoutManager.VERTICAL, false));
        tutorialsAdapter = new TutorialsAdapter(requireContext(), tutorialsArrayList);
        binding.tutorialRv.setAdapter(tutorialsAdapter);
        fbUtils.getAllDataFromPath(fbUtils.TUTORIAL_PATH, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                tutorialsArrayList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()) {
                    Tutorials tutorials = snapshot.getValue(Tutorials.class);
                    if (tutorials == null) continue;
                    tutorialsArrayList.add(tutorials);
                }
                // Sort the list
                Collections.sort(tutorialsArrayList, new Comparator<Tutorials>() {
                    @Override
                    public int compare(Tutorials t1, Tutorials t2) {
                        try {
                            String dateTime1Str = t1.getDate() + " " + t1.getTime();
                            String dateTime2Str = t2.getDate() + " " + t2.getTime();
                            Date dateTime1 = DATE_TIME_FORMATTER.parse(dateTime1Str);
                            Date dateTime2 = DATE_TIME_FORMATTER.parse(dateTime2Str);
                            // For newest first, compare dateTime2 with dateTime1
                            return dateTime2.compareTo(dateTime1);
                        } catch (ParseException e) {
                            Log.e("TutorialFragment", "Error parsing date/time for sorting: " + e.getMessage());
                            return 0;
                        }
                    }
                });

                tutorialsAdapter.notifyDataSetChanged();
            }

            @Override
            public void onDataNotFound() {
                tutorialsArrayList.clear();
                tutorialsAdapter.notifyDataSetChanged();
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                tutorialsArrayList.clear();
                tutorialsAdapter.notifyDataSetChanged();
            }
        });
    }

}