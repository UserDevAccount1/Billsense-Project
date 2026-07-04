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
import com.app.billsense.utils.FBUtils;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

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
        // Pure REST fetch + Gson — the Firebase SDK get() hangs on some devices
        // (same pattern as EvidenceHistoryFragment / SupportActivity).
        fbUtils.fetchJsonFromPath(fbUtils.TRIVIA_PATH, new FBInterface.OnRestJsonFetchCallback() {
            @Override
            public void onJsonFetchSuccess(String jsonBody) {
                if (!isAdded()) return;
                triviaArrayList.clear();
                try {
                    Gson gson = new Gson();
                    JSONObject json = new JSONObject(jsonBody);
                    Iterator<String> keys = json.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object child = json.opt(key);
                        if (!(child instanceof JSONObject)) continue;
                        Trivia trivia = gson.fromJson(child.toString(), Trivia.class);
                        if (trivia == null) continue;
                        triviaArrayList.add(trivia);
                    }
                } catch (Exception e) {
                    Log.e("Trivia Fragment", "Failed to parse trivia JSON: " + e.getMessage());
                }
                sortNewestFirst();
                triviaAdapter.notifyDataSetChanged();
            }

            @Override
            public void onJsonFetchEmpty() {
                if (!isAdded()) return;
                triviaArrayList.clear();
                triviaAdapter.notifyDataSetChanged();
            }

            @Override
            public void onJsonFetchFailed(String errorMessage) {
                if (!isAdded()) return;
                Log.e("Trivia Fragment", "Failed to fetch trivia: " + errorMessage);
                triviaArrayList.clear();
                triviaAdapter.notifyDataSetChanged();
            }
        });
    }

    private void sortNewestFirst() {
        Collections.sort(triviaArrayList, new Comparator<Trivia>() {
            @Override
            public int compare(Trivia t1, Trivia t2) {
                try {
                    LocalDate date1 = LocalDate.parse(t1.getDate(), DATE_FORMATTER);
                    LocalTime time1 = LocalTime.parse(t1.getTime(), TIME_FORMATTER);
                    LocalDateTime dateTime1 = LocalDateTime.of(date1, time1);

                    LocalDate date2 = LocalDate.parse(t2.getDate(), DATE_FORMATTER);
                    LocalTime time2 = LocalTime.parse(t2.getTime(), TIME_FORMATTER);
                    LocalDateTime dateTime2 = LocalDateTime.of(date2, time2);

                    // For newest first, compare dateTime2 with dateTime1
                    return dateTime2.compareTo(dateTime1);
                } catch (Exception e) {
                    Log.e("Trivia Fragment", "Error parsing date/time for sorting: " + e.getMessage());
                    return 0;
                }
            }
        });
    }

}
