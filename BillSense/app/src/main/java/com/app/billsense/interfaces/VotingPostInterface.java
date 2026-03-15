package com.app.billsense.interfaces;

import com.app.billsense.model.VotingPosts;

public interface VotingPostInterface {
    void onPostReal(VotingPosts post);
    void onPostFake(VotingPosts post);
    void onPostComments(VotingPosts post);
    void onEditPost(VotingPosts post);
}
