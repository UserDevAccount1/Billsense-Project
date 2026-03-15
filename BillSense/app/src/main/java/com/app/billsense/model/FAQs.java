package com.app.billsense.model;

public class FAQs {
    String id, question, answer, date,
            time;

    public FAQs() {
    }

    public FAQs(String id, String question, String answer,
                String date, String time) {
        this.id = id;
        this.question = question;
        this.answer = answer;
        this.date = date;
        this.time = time;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
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
}
