package com.snappark.service;

import org.mindrot.jbcrypt.BCrypt;

import com.snappark.dao.UserDAO;
import com.snappark.model.User;
import com.snappark.model.enums.Role;

public class AuthService {
    private static AuthService instance;
    private final UserDAO userDAO;
    private User currentUser;

    private AuthService() {
        this.userDAO = new UserDAO();
    }

    public static AuthService getInstance() {
        if (instance == null) {
            instance = new AuthService();
        }
        return instance;
    }

    public User login(String username, String password) {
        User user = userDAO.findByUsername(username);
        if (user == null) return null;
        if (BCrypt.checkpw(password, user.getPasswordHash())) {
            currentUser = user;
            System.out.println("Login successful: " + user.getName() + " (" + user.getRole() + ")");
            return user;
        }
        return null;
    }

    public void logout() {
        System.out.println("User logged out: " + (currentUser != null ? currentUser.getName() : "none"));
        currentUser = null;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean hasPermission(Role requiredRole) {
        if (currentUser == null) return false;
        return switch (requiredRole) {
            case CUSTOMER  -> true;
            case ATTENDANT -> currentUser.getRole() != Role.CUSTOMER;
            case MANAGER   -> currentUser.getRole() == Role.MANAGER || currentUser.getRole() == Role.ADMIN;
            case ADMIN     -> currentUser.getRole() == Role.ADMIN;
        };
    }
}