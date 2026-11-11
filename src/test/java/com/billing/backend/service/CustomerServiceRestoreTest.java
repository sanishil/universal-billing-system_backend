package com.billing.backend.service;

import com.billing.backend.entity.Customer;
import com.billing.backend.repository.CustomerRepository;
import com.billing.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Deleting marks a customer INACTIVE; they drop out of the list and re-adding restores the same record */
class CustomerServiceRestoreTest {

    private CustomerRepository customers;
    private UserRepository users;
    private JavaMailSender mail;
    private CustomerService service;

    private final Customer removed = Customer.builder().id("CUST-001").ownerId("USR-01").name("Mistu Das")
            .email("mistu@example.com").phone("9876543210").stateCode("16").billsCount(1)
            .status(Customer.CustomerStatus.INACTIVE).build();
    private final Customer active = Customer.builder().id("CUST-002").ownerId("USR-01").name("Ravi Kumar")
            .email("ravi@example.com").phone("9123456780").stateCode("16")
            .status(Customer.CustomerStatus.ACTIVE).build();

    @BeforeEach
    void setUp() {
        customers = mock(CustomerRepository.class);
        users = mock(UserRepository.class);
        mail = mock(JavaMailSender.class);
        service = new CustomerService(users, customers, mock(PasswordEncoder.class), mail, mock(BusinessService.class));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("USR-01", null, List.of());
        auth.setDetails("Sanishil");
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(customers.findByOwnerIdOrderByCreatedAtDesc("USR-01")).thenReturn(List.of(active, removed));
        when(customers.findFirstByOwnerIdAndEmailIgnoreCase("USR-01", "mistu@example.com")).thenReturn(Optional.of(removed));
        when(customers.findFirstByOwnerIdAndEmailIgnoreCase("USR-01", "ravi@example.com")).thenReturn(Optional.of(active));
        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void activeFilterLeavesOutRemovedCustomers() {
        Page<Customer> page = service.getCustomers("USR-01", null, Customer.CustomerStatus.ACTIVE, 0, 100);
        assertEquals(List.of("CUST-002"), page.getContent().stream().map(Customer::getId).toList());
    }

    @Test
    void lookupTreatsRemovedCustomerAsRestorable() {
        Map<String, Object> result = service.lookupByEmail("USR-01", "mistu@example.com");
        assertEquals("NEW", result.get("status"));
        assertEquals("CUST-001", result.get("removedCustomerId"));

        assertEquals("MINE", service.lookupByEmail("USR-01", "ravi@example.com").get("status"));
    }

    @Test
    void reAddingRemovedCustomerRestoresSameRecord() {
        Customer form = Customer.builder().name("Mistu Das").email("mistu@example.com").phone("9876543210")
                .address("Agartala").stateCode("16").inviteRecipient(false).build();

        Customer saved = service.createCustomer(form);

        assertEquals("CUST-001", saved.getId(), "same id, so the earlier bill stays linked");
        assertEquals(Customer.CustomerStatus.ACTIVE, saved.getStatus());
        assertEquals(1, saved.getBillsCount());
        assertEquals("Agartala", saved.getAddress());
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }
}
