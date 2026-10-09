
package com.saurabh.banking.repository;

import com.saurabh.banking.customer.Customer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomerRepositoryTest {

    private CustomerRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CustomerRepository();

        // Clean up records left by previous test runs.
        repository.delete(90001L);
        repository.delete(90002L);
        repository.delete(90003L);
    }

    @Test
    void shouldSaveAndFindCustomer() {

        Customer customer = new Customer(
                90001L,
                "Test Customer",
                "test.customer@example.com",
                "9876543210"
        );

        repository.save(customer);

        Customer result = repository.findById(90001L);

        assertNotNull(result);
        assertEquals(90001L, result.getId());
        assertEquals("Test Customer", result.getName());
        assertEquals(
                "test.customer@example.com",
                result.getEmail()
        );
        assertEquals("9876543210", result.getPhone());
    }

    @Test
    void shouldFindAllCustomers() {

        Customer customer = new Customer(
                90002L,
                "Another Customer",
                "another.customer@example.com",
                "9876543211"
        );

        repository.save(customer);

        assertTrue(
                repository.findAll().stream()
                        .anyMatch(existing ->
                                existing.getId().equals(90002L))
        );
    }

    @Test
    void shouldDeleteCustomer() {

        Customer customer = new Customer(
                90003L,
                "Delete Customer",
                "delete.customer@example.com",
                "9876543212"
        );

        repository.save(customer);

        Customer savedCustomer = repository.findById(90003L);
        assertNotNull(savedCustomer);

        repository.delete(90003L);

        assertNull(repository.findById(90003L));
    }
}
