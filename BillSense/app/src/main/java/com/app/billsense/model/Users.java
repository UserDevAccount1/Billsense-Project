package com.app.billsense.model;

public class Users {
    String id, name, email, phone, downloadIdUrl, status, verificationCode, password,
            image, fcmToken;

    public Users() {
    }

    public Users(String id, String name, String email, String phone, String downloadIdUrl,
                 String status, String verificationCode, String password, String image, String fcmToken) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.downloadIdUrl = downloadIdUrl;
        this.status = status;
        this.verificationCode = verificationCode;
        this.password = password;
        this.image = image;
        this.fcmToken = fcmToken;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDownloadIdUrl() {
        return downloadIdUrl;
    }

    public void setDownloadIdUrl(String downloadIdUrl) {
        this.downloadIdUrl = downloadIdUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
