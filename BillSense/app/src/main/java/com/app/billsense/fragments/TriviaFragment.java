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
import com.app.billsense.adapters.TriviaAdapter;
import com.app.billsense.databinding.FragmentTriviaBinding;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Trivia;
import com.app.billsense.model.Tutorials;
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
public class TriviaFragment extends Fragment {
    private FragmentTriviaBinding binding;
    private FBUtils fbUtils;
    private TriviaAdapter triviaAdapter;
    private final ArrayList<Trivia> triviaArrayList = new ArrayList<>();
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public TriviaFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        binding = FragmentTriviaBinding.inflate(inflater, container, false);
        fbUtils = new FBUtils();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        getAllTrivia();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void getAllTrivia() {
        binding.triviaRv.setHasFixedSize(true);
        binding.triviaRv.setLayoutManager(new LinearLayoutManager(
                requireActivity(), LinearLayoutManager.VERTICAL, false));
        triviaAdapter = new TriviaAdapter(requireContext(), triviaArrayList);
        binding.triviaRv.setAdapter(triviaAdapter);
        fbUtils.getAllDataFromPath(fbUtils.TRIVIA_PATH, new FBInterface.OnFetchDataCallBack() {
            @Override
            public void onFetchDataSuccess(DataSnapshot dataSnapshot) {
                triviaArrayList.clear();
                for (DataSnapshot snapshot : dataSnapshot.getChildren()){
                    Trivia trivia = snapshot.getValue(Trivia.class);
                    triviaArrayList.add(trivia);
                }
                // Sort the list
                Collections.sort(triviaArrayList, new Comparator<Trivia>() {
                    @Override
                    public int compare(Trivia t1, Trivia t2) {
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
                            Log.e("Trivia Fragment", "Error parsing date/time for sorting: " + e.getMessage());
                            return 0;
                        }
                    }
                });

                triviaAdapter.notifyDataSetChanged();
            }

            @Override
            public void onDataNotFound() {
                triviaArrayList.clear();
                triviaAdapter.notifyDataSetChanged();
            }

            @Override
            public void onFetchDataFailed(String errorMessage) {
                triviaArrayList.clear();
                triviaAdapter.notifyDataSetChanged();
            }
        });

    }

}