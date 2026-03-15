package com.app.billsense.fragments;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.app.billsense.R;
import com.app.billsense.adapters.FAQAdapter;
import com.app.billsense.databinding.FragmentFaqBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.FAQs;
import com.app.billsense.model.Trivia;
import com.app.billsense.utils.FBUtils;
import com.google.firebase.database.DataSnapshot;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

@RequiresApi(api = Build.VERSION_CODES.O)
public class FaqFragment extends Fragment {
    private FragmentFaqBinding binding;
    private FBUtils fbUtils;
    private FAQAdapter faqAdapter;
    private final ArrayList<FAQs> faQsArrayList = new ArrayList<>();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public FaqFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentFaqBinding.inflate(inflater, container, false);
        fbUtils = new FBUtils();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getAllFAQs();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void getAllFAQs() {
        binding.faqRv.setHasFixedSize(true);
        binding.faqRv.setLayoutManager(new LinearLayoutManager(
                requireActivity(), LinearLayoutManager.VERTICAL, false));
        faqAdapter = new FAQAdapter(requireContext(), faQsArrayList);
        binding.faqRv.setAdapter(faqAdapter);
        fbUtils.getAllDataFromPathSingle(fbUtils.FAQ_PATH, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                faQsArrayList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    FAQs faQs = snapshot.getValue(FAQs.class);
                    faQsArrayList.add(faQs);
                }
                Collections.sort(faQsArrayList, new Comparator<FAQs>() {
                    @Override
                    public int compare(FAQs t1, FAQs t2) {
                        try {
                            // Ensure getTutorialDate() and getTutorialTime() return the correct String fields
                            java.time.LocalDate date1 = java.time.LocalDate.parse(t1.getDate(), DATE_FORMATTER);
                            LocalTime time1 = LocalTime.parse(t1.getTime(), TIME_FORMATTER);
                            LocalDateTime dateTime1 = LocalDateTime.of(date1, time1);

                            LocalDate date2 = LocalDate.parse(t2.getDate(), DATE_FORMATTER);
                            LocalTime time2 = LocalTime.parse(t2.getTime(), TIME_FORMATTER);
                            LocalDateTime dateTime2 = LocalDateTime.of(date2, time2);

                            // For newest first, compare dateTime2 with dateTime1
                            return dateTime2.compareTo(dateTime1);
                        } catch (DateTimeParseException e) {
                            Log.e("FAQ Fragment", "Error parsing date/time for sorting: " + e.getMessage());
                            return 0;
                        }
                    }
                });

                faqAdapter.notifyDataSetChanged();
            }

            @Override
            public void onDataNotFound() {

            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                faQsArrayList.clear();
                faqAdapter.notifyDataSetChanged();
            }
        });

    }

}