package com.app.billsense.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.app.billsense.R;
import com.app.billsense.databinding.ItemCommentBinding;
import com.app.billsense.interfaces.CommentInterface;
import com.app.billsense.interfaces.FBInterface;
import com.app.billsense.model.Comments;
import com.app.billsense.model.FAQs;
import com.app.billsense.model.Users;
import com.app.billsense.utils.FBUtils;
import com.bumptech.glide.Glide;

import java.util.ArrayList;

import de.hdodenhof.circleimageview.CircleImageView;

public class PostCommentAdapter extends RecyclerView.Adapter<PostCommentAdapter.ViewHolder> {
    Context context;
    ArrayList<Comments> commentsArrayList;
    CommentInterface commentInterface;
    FBUtils fbUtils;

    public PostCommentAdapter(Context context, ArrayList<Comments> commentsArrayList,
                              CommentInterface commentInterface) {
        this.context = context;
        this.commentsArrayList = commentsArrayList;
        this.commentInterface = commentInterface;
        fbUtils = new FBUtils();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCommentBinding binding = ItemCommentBinding.inflate(LayoutInflater
                .from(context), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Comments comments = commentsArrayList.get(position);
        holder.binding.userComment.setText(comments.getComment());
        holder.binding.userName.setText(comments.getUserName());
        holder.binding.likeCount.setText(comments.getLikes());
        holder.binding.likeButton.setOnClickListener(view -> commentInterface.onLikedComment(comments));
        getUserImage(comments.getUserId(), holder.binding.userProfileImage);
    }

    private void getUserImage(String userId, CircleImageView userProfileImage) {
        fbUtils.getUserData(fbUtils.USERS_PATH, userId, new FBInterface.OnGetUserDataCallBack() {
            @Override
            public void onUserDataSuccess(Users user) {
                if (user.getImage() != null) {
                    Glide.with(context)
                            .load(user.getImage())
                            .placeholder(R.drawable.profile_placeholder)
                            .into(userProfileImage);
                }else {
                    Glide.with(context)
                            .load(R.drawable.profile_placeholder)
                            .into(userProfileImage);
                }
            }

            @Override
            public void onUserDataNotExist() {
                Glide.with(context)
                        .load(R.drawable.profile_placeholder)
                        .into(userProfileImage);
            }

            @Override
            public void onGetUserDataFailure(Exception exception) {
                Glide.with(context)
                        .load(R.drawable.profile_placeholder)
                        .into(userProfileImage);
            }
        });
    }

    @Override
    public int getItemCount() {
        return commentsArrayList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemCommentBinding binding;

        public ViewHolder(@NonNull ItemCommentBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
