package com.saurabh.banking.customer;

import java.util.Objects;

public final class Customer {
    private final Long id;
    private final String name;
    private final String email;
    private final String phone;

    public Customer(Long id, String name, String email, String phone) {
        if (id == null || name == null || name.isBlank()) {
            throw new IllegalArgumentException("Customer id and name are required");
        }
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Customer c)) return false;
        return Objects.equals(id, c.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "Customer{id=%d, name='%s'}".formatted(id, name);
    }
}
