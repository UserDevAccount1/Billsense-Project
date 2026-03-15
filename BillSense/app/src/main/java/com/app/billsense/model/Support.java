package com.app.billsense.model;

public class Support {
    String id, ticketNo, userId, userName, concern, status, date;
    boolean isArchived;

    public Support() {
    }

    public Support(String id, String ticketNo, String userId,
                   String userName, String concern, String status,
                   String date, boolean isArchived) {
        this.id = id;
        this.ticketNo = ticketNo;
        this.userId = userId;
        this.userName = userName;
        this.concern = concern;
        this.status = status;
        this.date = date;
        this.isArchived = isArchived;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTicketNo() {
        return ticketNo;
    }

    public void setTicketNo(String ticketNo) {
        this.ticketNo = ticketNo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getConcern() {
        return concern;
    }

    public void setConcern(String concern) {
        this.concern = concern;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public boolean getIsArchived() {
        return isArchived;
    }

    public void setArchived(boolean archived) {
        isArchived = archived;
    }
}
