package com.app.billsense.interfaces;

import com.app.billsense.model.Support;

public interface ConcernInterface {
    void onDeleteConcern(String id);
    void onChatConcern(Support support);
}
