package com.app.billsense.activities;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.app.billsense.adapters.SearchResultsAdapter;
import com.app.billsense.databinding.ActivitySearchBinding;
import com.app.billsense.model.UnifiedSearchResult;
import com.app.billsense.utils.FBUtils;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchActivity extends AppCompatActivity {

    private ActivitySearchBinding binding;
    private SearchResultsAdapter resultsAdapter;
    private final List<UnifiedSearchResult> allResults = new ArrayList<>();

    private FirebaseDatabase firebaseDatabase;
    private ExecutorService executorService; // For background tasks
    private android.os.Handler mainThreadHandler; // For UI updates

    private FBUtils fbUtils; // Instance of FBUtils to access paths

    // Define your Firebase paths USING FBUtils constants
    private Map<String, String> pathsToSearch;

    public static void start(Context context) {
        Intent intent = new Intent(context, SearchActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize FBUtils
        fbUtils = new FBUtils();

        // Initialize pathsToSearch using constants from FBUtils
        // The key of the map is the "Display Name" you want for the search result type,
        // and the value is the actual Firebase path from FBUtils.
        pathsToSearch = new HashMap<String, String>() {{
            put("Tutorials", fbUtils.TUTORIAL_PATH);
            put("Trivia", fbUtils.TRIVIA_PATH);
            put("FAQs", fbUtils.FAQ_PATH);
            put("Cases", fbUtils.CASES_PATH);
            put("Detections", fbUtils.DETECTIONS_PATH);
            put("Voting Posts", fbUtils.VOTING_POST);
        }};

        setSupportActionBar(binding.toolbarSearch);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        firebaseDatabase = FirebaseDatabase.getInstance();
        executorService = Executors.newFixedThreadPool(pathsToSearch.size());
        mainThreadHandler = new Handler(Looper.getMainLooper());

        setupRecyclerView();
        setupSearchView();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void setupRecyclerView() {
        resultsAdapter = new SearchResultsAdapter(result -> {
            Intent intent = new Intent(this, DetailsActivity.class); // Assuming DetailsActivity exists
            intent.putExtra("ITEM_ID", result.getId());
            intent.putExtra("ITEM_TYPE", result.getType());
            // Optionally, you can pass the Firebase path if needed in DetailsActivity
            // intent.putExtra("FIREBASE_PATH", result.getFirebasePath());
            startActivity(intent);

        });
        binding.recyclerViewSearchResults.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerViewSearchResults.setAdapter(resultsAdapter);
    }

    private void setupSearchView() {
        binding.searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                if (query != null && !query.trim().isEmpty()) {
                    if (query.length() > 2) {
                        performSearch(query);
                    } else {
                        Toast.makeText(SearchActivity.this, "Please enter a longer search term", Toast.LENGTH_SHORT).show();
                    }
                }
                binding.searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                if (newText == null || newText.isEmpty()) {
                    clearSearchResults();
                }
                // Optional: Implement live search with debouncing
                return true;
            }
        });
        binding.searchView.requestFocus();
    }

    private void clearSearchResults() {
        allResults.clear();
        resultsAdapter.submitList(new ArrayList<>()); // Submit a new empty list
        binding.textViewNoResults.setVisibility(View.GONE);
    }

    private void performSearch(String query) {
        binding.progressBarSearch.setVisibility(View.VISIBLE);
        binding.textViewNoResults.setVisibility(View.GONE);
        allResults.clear();
        resultsAdapter.submitList(new ArrayList<>()); // Clear previous UI

        String lowerCaseQuery = query.toLowerCase(Locale.ROOT).trim();
        List<UnifiedSearchResult> aggregatedResults = new ArrayList<>();
        AtomicInteger tasksCompleted = new AtomicInteger(0); // To track completion of async tasks

        for (Map.Entry<String, String> entry : pathsToSearch.entrySet()) {
            String typeDisplayName = entry.getKey();
            String firebasePath = entry.getValue();

            executorService.execute(() -> {
                fetchAndFilterData(firebasePath, typeDisplayName, lowerCaseQuery, new SearchCallback() {
                    @Override
                    public void onResultsFetched(List<UnifiedSearchResult> results) {
                        synchronized (aggregatedResults) {
                            aggregatedResults.addAll(results);
                        }
                        checkIfAllTasksCompleted(tasksCompleted, aggregatedResults);
                    }

                    @Override
                    public void onError(DatabaseError error) {
                        Log.e("SearchActivity", "Error fetching from " + firebasePath, error.toException());
                        checkIfAllTasksCompleted(tasksCompleted, aggregatedResults); // Still count as completed
                    }
                });
            });
        }
    }

    private void checkIfAllTasksCompleted(AtomicInteger tasksCompleted, List<UnifiedSearchResult> aggregatedResults) {
        if (tasksCompleted.incrementAndGet() == pathsToSearch.size()) {
            mainThreadHandler.post(() -> {
                binding.progressBarSearch.setVisibility(View.GONE);
                if (aggregatedResults.isEmpty()) {
                    binding.textViewNoResults.setVisibility(View.VISIBLE);
                } else {
                    allResults.addAll(aggregatedResults);
                    // Consider sorting results here
                    resultsAdapter.submitList(new ArrayList<>(allResults)); // Submit a new copy of the list
                }
            });
        }
    }

    interface SearchCallback {
        void onResultsFetched(List<UnifiedSearchResult> results);
        void onError(DatabaseError error);
    }

    // Inside SearchActivity.java

    private void fetchAndFilterData(
            String path, // This is the Firebase path from FBUtils (e.g., fbUtils.TUTORIAL_PATH)
            String typeDisplayName, // This is the key from pathsToSearch (e.g., "Tutorials")
            String lowerCaseQuery,
            SearchCallback callback
    ) {
        DatabaseReference databaseReference = firebaseDatabase.getReference(path);
        List<UnifiedSearchResult> results = new ArrayList<>();

        // Using addListenerForSingleValueEvent as it's a one-time read for search
        databaseReference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Log.d("SearchActivity", "No data found at path: " + path);
                    callback.onResultsFetched(results); // Send back empty list
                    return;
                }

                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    try {
                        String id = itemSnapshot.getKey() != null ? itemSnapshot.getKey() : "";
                        String titleToSearch = "";
                        String contentToSearch = ""; // This will hold description, answer, content, etc.

                        // Determine which fields to use based on the type
                        switch (typeDisplayName) {
                            case "Tutorials":
                                // From com.app.billsense.model.Tutorials.java
                                // Fields: title, description
                                titleToSearch = itemSnapshot.child("title").getValue(String.class);
                                contentToSearch = itemSnapshot.child("description").getValue(String.class);
                                break;
                            case "Trivia":
                                // From com.app.billsense.model.Trivia.java
                                // Fields: title (could be the question), description (could be the answer or more info)
                                titleToSearch = itemSnapshot.child("title").getValue(String.class);
                                contentToSearch = itemSnapshot.child("description").getValue(String.class);
                                break;
                            case "FAQs":
                                // From com.app.billsense.model.FAQs.java
                                // Fields: question, answer
                                titleToSearch = itemSnapshot.child("question").getValue(String.class);
                                contentToSearch = itemSnapshot.child("answer").getValue(String.class);
                                break;
                            case "Cases":
                                // From com.app.billsense.model.Cases.java
                                // Fields: title, description
                                titleToSearch = itemSnapshot.child("title").getValue(String.class);
                                contentToSearch = itemSnapshot.child("description").getValue(String.class);
                                break;
                            case "Detections":
                                // From com.app.billsense.model.Detections.java
                                // Fields: type (could be a title), content
                                titleToSearch = itemSnapshot.child("type").getValue(String.class); // Or another relevant field if 'type' isn't suitable as a title
                                contentToSearch = itemSnapshot.child("content").getValue(String.class);
                                break;
                            case "Voting Posts":
                                // Assuming Voting Posts also have a "title" and "description" or similar
                                // Adjust these field names if they are different for Voting Posts
                                titleToSearch = itemSnapshot.child("title").getValue(String.class);
                                contentToSearch = itemSnapshot.child("description").getValue(String.class);
                                break;
                            default:
                                Log.w("SearchActivity", "Unknown typeDisplayName: " + typeDisplayName);
                                continue; // Skip this item if type is unknown
                        }

                        // Null checks for safety
                        if (titleToSearch == null) titleToSearch = "";
                        if (contentToSearch == null) contentToSearch = "";

                        // Perform the search check
                        boolean titleMatches = !titleToSearch.isEmpty() && titleToSearch.toLowerCase(Locale.ROOT).contains(lowerCaseQuery);
                        boolean contentMatches = !contentToSearch.isEmpty() && contentToSearch.toLowerCase(Locale.ROOT).contains(lowerCaseQuery);

                        if (titleMatches || contentMatches) {
                            String displayTitle = titleToSearch; // The title to show in search results
                            String snippet = contentToSearch.length() > 150 ? contentToSearch.substring(0, 150) + "..." : contentToSearch;

                            // For FAQs, the question is more like a title
                            if ("FAQs".equals(typeDisplayName)) {
                                displayTitle = titleToSearch; // which is actually the 'question'
                            }
                            // For Detections, if 'type' was used as titleToSearch, it might be better to use 'content' for snippet directly or combine.
                            if ("Detections".equals(typeDisplayName) && displayTitle.isEmpty() && !contentToSearch.isEmpty()) {
                                // If title was empty (e.g. 'type' field was missing) but content exists
                                displayTitle = contentToSearch.length() > 50 ? contentToSearch.substring(0, 50) + "..." : contentToSearch;
                            }


                            // Construct the UnifiedSearchResult object
                            switch (typeDisplayName) {
                                case "Tutorials":
                                    results.add(new UnifiedSearchResult.TutorialResult(id, displayTitle, snippet, path, null));
                                    break;
                                case "FAQs":
                                    results.add(new UnifiedSearchResult.FaqResult(id, displayTitle, snippet, path));
                                    break;
                                case "Voting Posts":
                                    results.add(new UnifiedSearchResult.VotingPostResult(id, displayTitle, snippet, path, null));
                                    break;
                                case "Trivia":
                                    results.add(new UnifiedSearchResult.TriviaResult(id, displayTitle, snippet, path));
                                    break;
                                case "Cases":
                                    results.add(new UnifiedSearchResult.CaseResult(id, displayTitle, snippet, path));
                                    break;
                                case "Detections":
                                    results.add(new UnifiedSearchResult.DetectionResult(id, displayTitle, snippet, path));
                                    break;
                            }
                        }
                    } catch (Exception e) {
                        // Catching generic Exception for any parsing errors or NullPointerExceptions
                        Log.e("SearchActivity", "Error parsing item in " + path + " with ID " + itemSnapshot.getKey() + ": " + e.getMessage(), e);
                    }
                }
                callback.onResultsFetched(results);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("SearchActivity", "Firebase read failed for " + path + ": " + error.getMessage(), error.toException());
                callback.onError(error); // Pass the error to the callback
            }
        });
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow(); // Attempt to stop all actively executing tasks
        }
    }
}
