package com.billing.backend.service;

import com.billing.backend.entity.Customer;
import com.billing.backend.entity.User;
import com.billing.backend.repository.CustomerRepository;
import com.billing.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Adding a customer who is already in the system emails them; brand-new customers without a login get nothing */
class CustomerServiceAddExistingTest {

    private UserRepository users;
    private CustomerRepository customers;
    private JavaMailSender mail;
    private CustomerService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        customers = mock(CustomerRepository.class);
        mail = mock(JavaMailSender.class);
        service = new CustomerService(users, customers, mock(PasswordEncoder.class), mail, mock(BusinessService.class));

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken("USR-01", null, List.of());
        auth.setDetails("Sanishil");
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(users.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(customers.findByEmailIgnoreCaseOrderByCreatedAtAsc(anyString())).thenReturn(List.of());
        when(customers.existsById(anyString())).thenReturn(false);
        when(users.existsById(anyString())).thenReturn(false);
        when(customers.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static Customer form(String email) {
        return Customer.builder().name("Mistu Das").email(email).phone("9876543210")
                .company("Room Rental").stateCode("16").inviteRecipient(false).build();
    }

    @Test
    void emailsCustomerWhoHasTheirOwnAccount() {
        when(users.findByEmailIgnoreCase("mistu@example.com"))
                .thenReturn(Optional.of(User.builder().id("USR-09").name("Mistu Das").email("mistu@example.com").build()));

        Customer saved = service.createCustomer(form("Mistu@Example.com"));

        assertEquals("USR-09", saved.getUserid(), "linked to their existing login");
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertArrayEquals(new String[]{"mistu@example.com"}, sent.getValue().getTo());
        assertTrue(sent.getValue().getSubject().contains("Room Rental"));
        assertTrue(sent.getValue().getText().contains("Sanishil has added you as a customer of Room Rental"));
        assertTrue(sent.getValue().getText().contains("Received bills"));
    }

    @Test
    void emailsCustomerAlreadyAddedByAnotherBusiness() {
        when(customers.findByEmailIgnoreCaseOrderByCreatedAtAsc("mistu@example.com"))
                .thenReturn(List.of(Customer.builder().id("CUST-001").ownerId("USR-05").name("Mistu Das").build()));

        service.createCustomer(form("mistu@example.com"));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(sent.capture());
        assertTrue(sent.getValue().getText().contains("You will receive invoices from Room Rental by email"));
    }

    @Test
    void noEmailForBrandNewCustomerWithoutLogin() {
        service.createCustomer(form("new.person@example.com"));
        verify(mail, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void mailFailureDoesNotStopTheCustomerBeingSaved() {
        when(users.findByEmailIgnoreCase("mistu@example.com"))
                .thenReturn(Optional.of(User.builder().id("USR-09").email("mistu@example.com").build()));
        doThrow(new RuntimeException("SMTP down")).when(mail).send(any(SimpleMailMessage.class));

        Customer saved = assertDoesNotThrow(() -> service.createCustomer(form("mistu@example.com")));
        assertEquals("Mistu Das", saved.getName());
        verify(customers).save(any(Customer.class));
    }
}
