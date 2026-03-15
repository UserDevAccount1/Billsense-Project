package com.app.billsense.model;

public abstract class UnifiedSearchResult {
    private String id;
    private String title;
    private String snippet;
    private String type;
    private String originalPath;

    public UnifiedSearchResult(String id, String title, String snippet, String type, String originalPath) {
        this.id = id;
        this.title = title;
        this.snippet = snippet;
        this.type = type;
        this.originalPath = originalPath;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSnippet() { return snippet; }
    public String getType() { return type; }
    public String getOriginalPath() { return originalPath; }

    // Inner classes for specific types

    public static class TutorialResult extends UnifiedSearchResult {
        private String author; // Example extra field

        public TutorialResult(String id, String title, String snippet, String originalPath, String author) {
            super(id, title, snippet, "Tutorials", originalPath);
            this.author = author;
        }
        public String getAuthor() { return author; }
    }

    public static class FaqResult extends UnifiedSearchResult {
        public FaqResult(String id, String title, String snippet, String originalPath) {
            super(id, title, snippet, "FAQs", originalPath);
        }
    }

    public static class VotingPostResult extends UnifiedSearchResult {
        private Long endDate; // Example extra field

        public VotingPostResult(String id, String title, String snippet, String originalPath, Long endDate) {
            super(id, title, snippet, "Voting Posts", originalPath);
            this.endDate = endDate;
        }
        public Long getEndDate() { return endDate; }
    }

    public static class TriviaResult extends UnifiedSearchResult {
        public TriviaResult(String id, String title, String snippet, String originalPath) {
            super(id, title, snippet, "Trivia", originalPath);
        }
    }

    public static class CaseResult extends UnifiedSearchResult {
        public CaseResult(String id, String title, String snippet, String originalPath) {
            super(id, title, snippet, "Cases", originalPath);
        }
    }

    public static class DetectionResult extends UnifiedSearchResult {
        public DetectionResult(String id, String title, String snippet, String originalPath) {
            super(id, title, snippet, "Detections", originalPath);
        }
    }
}
