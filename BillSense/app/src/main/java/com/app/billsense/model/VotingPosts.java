package com.app.billsense.model;

import java.util.Map;

public class VotingPosts {
    private String id, title, description, date, time, mediaType,
            votingQuestion, startDate, startTime, endDate, endTime,
            votingDateTime, status, votingEnabled, downloadImageUrl,
            downloadVideoUrl, userId, userName;
    private String totalVotes, realVotes, fakeVotes, votesResult;
    private Map<String, Boolean> voters; // Add this field

    // Constructors

    public VotingPosts() {
        //Empty constructor needed for Firebase
    }

    public VotingPosts(String id, String title, String description, String date, String time,
                       String mediaType, String votingQuestion, String startDate, String startTime,
                       String endDate, String endTime, String votingDateTime, String status,
                       String votingEnabled, String downloadImageUrl, String downloadVideoUrl,
                       String totalVotes, String realVotes, String fakeVotes, String votesResult,
                       Map<String, Boolean> voters, String userId, String userName) { // Add voters to constructor
        this.id = id;
        this.title = title;
        this.description = description;
        this.date = date;
        this.time = time;
        this.mediaType = mediaType;
        this.votingQuestion = votingQuestion;
        this.startDate = startDate;
        this.startTime = startTime;
        this.endDate = endDate;
        this.endTime = endTime;
        this.votingDateTime = votingDateTime;
        this.status = status;
        this.votingEnabled = votingEnabled;
        this.downloadImageUrl = downloadImageUrl;
        this.downloadVideoUrl = downloadVideoUrl;
        this.totalVotes = totalVotes;
        this.realVotes = realVotes;
        this.fakeVotes = fakeVotes;
        this.votesResult = votesResult;
        this.voters = voters;
        this.userId = userId;
        this.userName = userName;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getMediaType() {
        return mediaType;
    }

    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    public String getVotingQuestion() {
        return votingQuestion;
    }

    public void setVotingQuestion(String votingQuestion) {
        this.votingQuestion = votingQuestion;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getVotingDateTime() {
        return votingDateTime;
    }

    public void setVotingDateTime(String votingDateTime) {
        this.votingDateTime = votingDateTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVotingEnabled() { // Changed to String
        return votingEnabled;
    }

    public void setVotingEnabled(String votingEnabled) { // Changed to String
        this.votingEnabled = votingEnabled;
    }

    public String getDownloadImageUrl() {
        return downloadImageUrl;
    }

    public void setDownloadImageUrl(String downloadImageUrl) {
        this.downloadImageUrl = downloadImageUrl;
    }

    public String getDownloadVideoUrl() {
        return downloadVideoUrl;
    }

    public void setDownloadVideoUrl(String downloadVideoUrl) {
        this.downloadVideoUrl = downloadVideoUrl;
    }

    public String getTotalVotes() {
        return totalVotes;
    }

    public void setTotalVotes(String totalVotes) {
        this.totalVotes = totalVotes;
    }

    public String getRealVotes() {
        return realVotes;
    }

    public void setRealVotes(String realVotes) {
        this.realVotes = realVotes;
    }

    public String getFakeVotes() {
        return fakeVotes;
    }

    public void setFakeVotes(String fakeVotes) {
        this.fakeVotes = fakeVotes;
    }

    public String getVotesResult() {
        return votesResult;
    }

    public void setVotesResult(String votesResult) {
        this.votesResult = votesResult;
    }

    public Map<String, Boolean> getVoters() { //Getter for voters
        return voters;
    }

    public void setVoters(Map<String, Boolean> voters) { //Setter for voters
        this.voters = voters;
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
}