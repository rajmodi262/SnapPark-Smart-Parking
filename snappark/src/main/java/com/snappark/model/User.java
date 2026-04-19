package com.snappark.model;

import com.snappark.model.enums.Role;

public class User {
    private int id;
    private String name;
    private String username;
    private String passwordHash;
    private Role role;
    private boolean active;

    public User() {}

    public User(int id, String name, String username, String passwordHash, Role role, boolean active) {
        this.id = id;
        this.name = name;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.active = active;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Override
    public String toString() {
        return "User{id=" + id + ", name='" + name + "', role=" + role + "}";
    }
}