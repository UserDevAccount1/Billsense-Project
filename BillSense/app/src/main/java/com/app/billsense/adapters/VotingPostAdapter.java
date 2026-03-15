package com.app.billsense.adapters;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static com.app.billsense.utils.Utils.getVotingStatusText;
import static com.app.billsense.utils.Utils.showToast;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.MediaController;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.billsense.databinding.ItemVotingPostBinding;
import com.app.billsense.interfaces.VotingPostInterface;
import com.app.billsense.model.VotingPosts;
import com.app.billsense.utils.DialogUtils;
import com.app.billsense.utils.PrefManager;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;

public class VotingPostAdapter extends RecyclerView.Adapter<VotingPostAdapter.ViewHolder> implements Filterable {
    Context context;
    ArrayList<VotingPosts> votingPostsArrayList;
    ArrayList<VotingPosts> votingPostsArrayListFiltered;
    VotingPostInterface votingPostInterface;

    public VotingPostAdapter(Context context, ArrayList<VotingPosts> votingPostsArrayList,
                             VotingPostInterface votingPostInterface) {
        this.context = context;
        this.votingPostsArrayList = new ArrayList<>(votingPostsArrayList);
        this.votingPostsArrayListFiltered = new ArrayList<>(votingPostsArrayList);
        this.votingPostInterface = votingPostInterface;
    }

    @NonNull
    @Override
    public VotingPostAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemVotingPostBinding binding = ItemVotingPostBinding.inflate(LayoutInflater
                .from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull VotingPostAdapter.ViewHolder holder, int position) {
        if (position < votingPostsArrayListFiltered.size()) { // Defensive check
            VotingPosts post = votingPostsArrayListFiltered.get(position); // Get item from the filtered list
            holder.bind(post, context, votingPostInterface);
            Log.e("==VotingPostAdapter", "onBindViewHolder: userid " + post.getUserId() + " for filtered list size " + votingPostsArrayListFiltered.size());
        } else {
            Log.e("==VotingPostAdapter", "onBindViewHolder: Invalid position " + position + " for filtered list size " + votingPostsArrayListFiltered.size());
        }
    }

    @Override
    public void onViewRecycled(@NonNull VotingPostAdapter.ViewHolder holder) {
        holder.clear();
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() {
        return votingPostsArrayListFiltered.size();
    }

    // Method to update the original list and refresh the filtered list
    public void updateData(ArrayList<VotingPosts> newVotingPosts) {
        this.votingPostsArrayList.clear();
        this.votingPostsArrayList.addAll(newVotingPosts);
        this.votingPostsArrayListFiltered.clear();
        this.votingPostsArrayListFiltered.addAll(newVotingPosts); // Reset to show all new data
        Log.d("==VotingPostAdapter", "updateData. All: " + votingPostsArrayList.size() + ", Filtered: " + votingPostsArrayListFiltered.size());
        notifyDataSetChanged();
    }

    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                String charString = "";
                if (constraint != null) {
                    charString = constraint.toString().toLowerCase(Locale.getDefault()).trim();
                }
                Log.d("==VotingPostAdapter", "performFiltering for: '" + charString +
                        "'. Original list size: " + votingPostsArrayList.size());

                ArrayList<VotingPosts> tempListFilteredItems;

                if (charString.isEmpty()) {
                    tempListFilteredItems = new ArrayList<>(votingPostsArrayList); // A new list with all original items
                } else {
                    tempListFilteredItems = new ArrayList<>();
                    for (VotingPosts post : votingPostsArrayList) {
                        if ((post.getTitle() != null && post.getTitle().toLowerCase(Locale.getDefault()).contains(charString)) ||
                                (post.getUserName() != null && post.getUserName().toLowerCase(Locale.getDefault()).contains(charString)) ||
                                (post.getDescription() != null && post.getDescription().toLowerCase(Locale.getDefault()).contains(charString)) ||
                                (post.getVotingQuestion() != null && post.getVotingQuestion().toLowerCase(Locale.getDefault()).contains(charString))) {
                            tempListFilteredItems.add(post);
                        }
                    }
                }

                FilterResults filterResults = new FilterResults();
                filterResults.values = tempListFilteredItems;
                filterResults.count = tempListFilteredItems.size();
                Log.d("==VotingPostAdapter", "performFiltering completed. Found items: "
                        + tempListFilteredItems.size());
                return filterResults;
            }

            @SuppressWarnings("unchecked")
            @Override
            protected void publishResults(CharSequence constraint, FilterResults results) {
                Log.d("==VotingPostAdapter", "publishResults for: '" + constraint +
                        "'. Results count: " + results.count);

                if (results.values instanceof ArrayList) {
                    votingPostsArrayListFiltered = (ArrayList<VotingPosts>) results.values;
                } else {
                    votingPostsArrayListFiltered = new ArrayList<>();
                    if (results.values != null) {
                        try {
                            votingPostsArrayListFiltered.addAll((ArrayList<VotingPosts>) results.values);
                        } catch (ClassCastException e) {
                            Log.e("==VotingPostAdapter", "publishResults: Could not cast results.values to ArrayList<VotingPosts>", e);
                        }
                    }
                }
                Log.d("==VotingPostAdapter", "publishResults updated. Filtered size: " +
                        votingPostsArrayListFiltered.size());
                notifyDataSetChanged();
            }
        };
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemVotingPostBinding binding;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private Runnable updateRunnable;

        public ViewHolder(ItemVotingPostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        @SuppressLint("SetTextI18n")
        public void bind(VotingPosts post, Context context, VotingPostInterface votingPostInterface) {
            // Set the basic data here (non-updating)
            binding.postTitle.setText(post.getTitle());
            binding.postDate.setText(post.getDate());
            binding.postDesc.setText(post.getDescription());

            // Media type handling (no changes here)
            if (post.getMediaType().equals("image")) {
                binding.postImg.setVisibility(VISIBLE);
                binding.postVid.setVisibility(GONE);
                binding.playVid.setVisibility(GONE);
                if (post.getDownloadImageUrl() != null) {
                    Glide.with(context).load(post.getDownloadImageUrl())
                            .into(binding.postImg);
                }
            } else if (post.getMediaType().equals("video")) {
                binding.postImg.setVisibility(GONE);
                binding.postVid.setVisibility(VISIBLE);
                binding.playVid.setVisibility(VISIBLE);
                if (post.getDownloadVideoUrl() != null) {
                    binding.postVid.setVideoPath(post.getDownloadVideoUrl());
                    MediaController mediaController = new MediaController(context);
                    mediaController.setPadding(0, 0, 0, 0);
                    FrameLayout.LayoutParams controllerLayoutParams = new FrameLayout.LayoutParams(
                            200,
                            80
                    );
                    controllerLayoutParams.gravity = Gravity.CENTER_HORIZONTAL;
                    mediaController.setLayoutParams(controllerLayoutParams);
                    binding.postVid.setMediaController(mediaController);
                    mediaController.setAnchorView(binding.postVid);
                    binding.playVid.setOnClickListener(view -> {
                        if (!binding.postVid.isPlaying()) {
                            binding.postVid.start();
                            binding.playVid.setVisibility(GONE);
                        }
                    });
                }
            } else {
                binding.postImg.setVisibility(GONE);
                binding.postVid.setVisibility(GONE);
                binding.playVid.setVisibility(GONE);
            }

            // Voting enabled handling (no changes here)
            if (post.getVotingEnabled().equals("true")) {
                binding.votingLlMain.setVisibility(VISIBLE);
            } else {
                binding.votingLlMain.setVisibility(GONE);
            }

            // Other data (no changes here)
            if (post.getVotingQuestion() != null) {
                binding.votingQuestion.setText(post.getVotingQuestion());
            }
            int realVotes = Integer.parseInt(post.getRealVotes() == null || post.getRealVotes().isEmpty() ? "0" : post.getRealVotes());
            int fakeVotes = Integer.parseInt(post.getFakeVotes() == null || post.getFakeVotes().isEmpty() ? "0" : post.getFakeVotes());

            int totalVotes = realVotes + fakeVotes;
            String realVotePercentageStr = "0.00";
            String fakeVotePercentageStr = "0.00";

            if (totalVotes > 0) {
                double realVotePercentage = (double) realVotes / totalVotes * 100;
                double fakeVotePercentage = (double) fakeVotes / totalVotes * 100;
                realVotePercentageStr = String.format(Locale.getDefault(), "%.2f", realVotePercentage); //Note: no % sign here
                fakeVotePercentageStr = String.format(Locale.getDefault(), "%.2f", fakeVotePercentage); //Note: no % sign here
            }

            binding.realVoteResult.setText(realVotes + " votes for real (" + realVotePercentageStr + "%)");
            binding.fakeVoteResult.setText(fakeVotes + " votes for fake (" + fakeVotePercentageStr + "%)");
            if (post.getVotesResult() != null){
                binding.votingResult.setText(post.getVotesResult());
            }

            // Set onClick listeners (no changes here)
            String votingStatus = getVotingStatusText(post.getVotingDateTime());

            boolean isVotingActive = votingStatus.startsWith("Voting ends in:");

            binding.realBtn.setOnClickListener(view -> {
                if (isVotingActive) {
                    votingPostInterface.onPostReal(post);
                } else {
                    showToast(context, "Voting is not currently active");
                }
            });

            binding.fakeBtn.setOnClickListener(view -> {
                if (isVotingActive) {
                    votingPostInterface.onPostFake(post);
                } else {
                    showToast(context, "Voting is not currently active");
                }
            });
            binding.viewComment.setOnClickListener(view -> votingPostInterface.onPostComments(post));

            if (Objects.equals(post.getUserId(), PrefManager.getInstance().getUserId())){
                binding.editPost.setVisibility(VISIBLE);
            }else {
                binding.editPost.setVisibility(GONE);
            }

            binding.editPost.setOnClickListener(view -> {
                votingPostInterface.onEditPost(post);
            });

            binding.postImg.setOnClickListener(view -> {
                if (post.getDownloadImageUrl() != null) {
                    DialogUtils.displayFullImageDialog(context, post.getTitle(), post.getDownloadImageUrl(), null);
                }
            });

            binding.postVid.setOnClickListener(view -> {
                if (post.getDownloadVideoUrl() != null) {
                    DialogUtils.displayFullVideoDialog(context, post.getTitle(), post.getDownloadVideoUrl(), null);
                }
            });

            //*** NEW:  Start updating the voting duration ***
            clear(); //Clear any existing update when rebinding
            if (post.getVotingDateTime() != null) {
                startVotingUpdates(post.getVotingDateTime());
            }
        }

        private void startVotingUpdates(String dateTimeRange) {
            updateRunnable = new Runnable() {
                @Override
                public void run() {
                    String statusText = getVotingStatusText(dateTimeRange);
                    binding.votingDur.setText(statusText);
                    handler.postDelayed(this, 60000); // Update every 60 seconds (1 minute)
                    Log.d("==voting dur", statusText); //To check time is decreasing
                }
            };
            handler.post(updateRunnable);
        }

        public void clear() {
            if (updateRunnable != null) {
                handler.removeCallbacks(updateRunnable);
                updateRunnable = null;
            }
        }
    }
}