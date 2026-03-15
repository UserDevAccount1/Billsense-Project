package com.app.billsense.utils;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.regex.Pattern;

public class InputValidator {

    public static boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        String emailRegex = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*@[A-Za-z0-9-]+(\\.[A-Za-z0-9]+)*(\\.[A-Za-z]{2,})$";
        return Pattern.matches(emailRegex, email);
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.trim().length() >= 6;
    }

    public static boolean isValidName(String name) {
        return !name.isEmpty() && name.matches("[a-zA-Z ]+");
    }

    public static boolean isValidPhone(String phone) {
        return !phone.isEmpty() && phone.matches("^\\d{10}$");
    }

    public static boolean isValidPhoneWithPlus(String phone) {
        return !phone.isEmpty() && phone.matches("^\\+\\d{10,15}$");
    }

    public static boolean isValidConfirmPassword(String password, String confirmPassword) {
        return !confirmPassword.isEmpty() && confirmPassword.equals(password);
    }

    public static boolean isValidTitle(String title) {
        return !title.trim().isEmpty() && title.trim().length() >= 3 &&
                title.matches("[a-zA-Z0-9\\s\\-\\.,!?'\"\\(\\)]+");
    }

    public static boolean isValidDescription(String description) {
        return !description.trim().isEmpty() && description.trim().length() >= 10;
    }

    public static boolean isValidConcern(String description) {
        return !description.trim().isEmpty() && description.trim().length() >= 8;
    }

    public static boolean isValidComment(String comment) {
        return !comment.trim().isEmpty() && comment.trim().length() >= 2;
    }

    public static boolean isValidQuestion(String question) {
        if (question == null || question.trim().isEmpty() || question.trim().length() < 5) {
            return false;
        }
        Pattern pattern = Pattern.compile("^[a-zA-Z0-9\\s\\-\\.,!?'\"\\(\\)]+\\?$");
        return pattern.matcher(question.trim()).matches();
    }

    public static boolean isValidAnswer(String answer) {
        if (answer == null || answer.trim().isEmpty() || answer.trim().length() < 2) {
            return false;
        }
        Pattern pattern = Pattern.compile("[a-zA-Z0-9\\s\\-\\.,!?'\"\\(\\)]+");
        return pattern.matcher(answer.trim()).matches();
    }


    public static void setInputError(TextInputLayout textInputLayout, String errorMessage) {
        textInputLayout.setError(errorMessage);
        textInputLayout.requestFocus();
    }

    public static void setEditTextError(TextInputEditText inputEditText, String error) {
        inputEditText.setError(error);
        inputEditText.requestFocus();
    }

    public static boolean isValidVerificationCode(String verificationCode) {
        // Check if the verification code is not empty, contains only numbers, and has length 5
        return !verificationCode.isEmpty() && verificationCode.matches("[0-9]+") && verificationCode.length() == 5;
    }

    public static boolean isValidDate(String caseDate) {
        return !caseDate.isEmpty() && caseDate.matches("\\d{2}/\\d{2}/\\d{4}");
    }

    public static boolean isValidTime(String caseTime) {
        if (caseTime == null || caseTime.trim().isEmpty()) {
            return false;
        }
        return caseTime.trim().matches("\\d{2}:\\d{2} (?i)(am|pm)");
    }

    public static boolean isValidAddress(String address) {
        return !address.isEmpty();
    }

    public static boolean isValidLatLng(Double latitude, Double longitude) {
        return latitude != null && longitude != null && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
    }

    public static boolean isValidMessage(String message) {
        return !message.isEmpty();
    }

}
