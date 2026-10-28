package com.billing.backend.service;

import com.billing.backend.entity.Customer;
import com.billing.backend.entity.User;
import com.billing.backend.exception.BadRequestException;
import com.billing.backend.exception.ConflictException;
import com.billing.backend.exception.ResourceNotFoundException;
import com.billing.backend.repository.CustomerRepository;
import com.billing.backend.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.List;


@Service
@RequiredArgsConstructor
public class CustomerService {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    public List<Customer> getAllCustomers(String search, Customer.CustomerStatus status) {
        boolean hasSearch = StringUtils.hasText(search);
        boolean hasStatus = status != null;

        if (hasSearch && hasStatus) {
            return customerRepository.searchByNameOrEmailAndStatus(search, status);
        } else if (hasSearch) {
            return customerRepository.searchByNameOrEmail(search);
        } else if (hasStatus) {
            return customerRepository.findByStatus(status);
        } else {
            return customerRepository.findAll();
        }
    }

    public Customer getCustomerById(String id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    public Page<Customer> getCustomers(String search, Customer.CustomerStatus status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = (size < 1 || size > 100) ? 10 : size;
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (StringUtils.hasText(search) && status != null) {
            return customerRepository.searchByNameOrEmailAndStatus(search.trim(), status, pageable);
        } else if (StringUtils.hasText(search)) {
            return customerRepository.searchByNameOrEmail(search.trim(), pageable);
        } else if (status != null) {
            return customerRepository.findByStatus(status, pageable);
        }
        return customerRepository.findAll(pageable);
    }

    @Transactional
    public Customer createCustomer(Customer customerData, String email, String phone) {
        if (!StringUtils.hasText(customerData.getName())) {
            throw new BadRequestException("Customer name is required");
        }
        if (!StringUtils.hasText(customerData.getEmail())) {
            throw new BadRequestException("Customer email is required");
        }

        if (!customerData.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BadRequestException("Invalid email format");
        }
        if (customerRepository.existsByEmail(email)) {
            throw new ConflictException(
                    "An account with email \"" + email + "\" already exists.");
        }
        if (StringUtils.hasText(customerData.getGstin())) {
            String gstin = customerData.getGstin().trim();
            if (gstin.length() != 15) {
                throw new BadRequestException("GSTIN must be exactly 15 characters");
            }
            if (!gstin.matches("^[0-3][0-9][A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")) {
                throw new BadRequestException("Invalid GSTIN format");
            }
        }

        if (StringUtils.hasText(customerData.getPan())) {
            String pan = customerData.getPan().trim();
            if (pan.length() != 10) {
                throw new BadRequestException("PAN must be exactly 10 characters");
            }
            if (!pan.matches("^[A-Z]{5}[0-9]{4}[A-Z]$")) {
                throw new BadRequestException("Invalid PAN format. Expected: AAAAA9999A");
            }
        }
        if (customerRepository.existsByPhone(phone)) {
            throw new ConflictException(
                    "An account with phone \"" + phone + "\" already exists.");
        }

        long count = customerRepository.count();
        String customerId = "CUST-" + String.format("%03d", count + 1);

        // Get the currently logged-in user's name from JWT via SecurityContext
        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder
                        .getContext().getAuthentication();
        String loginUserName = (auth != null && auth.getDetails() != null)
                ? auth.getDetails().toString()
                : "Unknown";

        // Generate a unique userId — if it already exists in users table, keep incrementing
        long userIdCounter = count + 1;
        String userId = "USR-" + String.format("%02d", userIdCounter);
        while (userRepository.existsById(userId)) {
            userIdCounter++;
            userId = "USR-" + String.format("%02d", userIdCounter);
        }

//        insert data into customer table
        Customer customer = Customer.builder()
                .id(customerId)
                .userid(userId)
                .name(customerData.getName().trim())
                .email(customerData.getEmail().trim().toLowerCase())
                .phone(StringUtils.hasText(customerData.getPhone())
                        ? customerData.getPhone() : "1234567890")
                .address(StringUtils.hasText(customerData.getAddress())
                        ? customerData.getAddress() : "Bengaluru, Karnataka, India")
                .company(StringUtils.hasText(customerData.getCompany())
                        ? customerData.getCompany() : customerData.getName())
                .gstin(customerData.getGstin() != null
                        ? customerData.getGstin().trim().toUpperCase() : "")
                .pan(customerData.getPan() != null
                        ? customerData.getPan().trim().toUpperCase() : "")
                .state(StringUtils.hasText(customerData.getState())
                        ? customerData.getState() : "Karnataka")
                .stateCode(StringUtils.hasText(customerData.getStateCode())
                        ? customerData.getStateCode() : "29")
                .totalSpent(BigDecimal.ZERO)
                .billsCount(0)
                .status(Customer.CustomerStatus.ACTIVE)
                .build();

// Insert specific data into user table for login
        //      Auto password generating
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();

        StringBuilder passwordBuilder = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            passwordBuilder.append(characters.charAt(random.nextInt(characters.length())));
        }

        String password = passwordBuilder.toString();

        String hashedPassword = passwordEncoder.encode(password);

        User newUser = User.builder()
                .id(userId)
                .name(customerData.getName().trim())
                .email(customerData.getEmail().trim().toLowerCase())
                .phone(StringUtils.hasText(customerData.getPhone()) ? customerData.getPhone() : "1234567890")
                .username(email.split("@")[0])
                .passwordHash(hashedPassword)
                .role("Administrator")
                .status(User.UserStatus.ACTIVE)
                .loginAttempts(0)
                .build();

        // Send email
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom("sanishil.cse@gmail.com");
        message.setTo(email);
        message.setSubject("Universal Billing System - Account Created");


        message.setText(
                "Hello " + customerData.getName().trim() + ",\n\n" +
                        "Your account has been successfully created in the Universal Billing System.\n\n" +
                        "Created By: " + loginUserName + " for " + customer.getCompany().trim() + "\n\n" +
                        "Login Credentials:\n" +
                        "Username: " + email + "\n" +
                        "Password: " + password + "\n\n" +
                        "Please log in using the above credentials and verify your account details. " +
                        "For security reasons, we strongly recommend changing your password after your first login.\n\n" +
                        "Regards,\n" +
                        "Universal Billing System"
        );


        userRepository.save(newUser);
        customerRepository.save(customer);
        mailSender.send(message);
        return customer;
    }

    public Customer updateCustomer(String id, Customer updatedData) {
        Customer existing = getCustomerById(id);

        if (StringUtils.hasText(updatedData.getName())) {
            existing.setName(updatedData.getName().trim());
        }
        if (StringUtils.hasText(updatedData.getEmail())) {
            if (!updatedData.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                throw new BadRequestException("Invalid email format");
            }
            existing.setEmail(updatedData.getEmail().trim().toLowerCase());
        }
        if (StringUtils.hasText(updatedData.getPhone())) {
            existing.setPhone(updatedData.getPhone().trim());
        }
        if (StringUtils.hasText(updatedData.getAddress())) {
            existing.setAddress(updatedData.getAddress().trim());
        }
        if (StringUtils.hasText(updatedData.getCompany())) {
            existing.setCompany(updatedData.getCompany().trim());
        }
        if (updatedData.getGstin() != null) {
            existing.setGstin(updatedData.getGstin().trim().toUpperCase());
        }
        if (updatedData.getPan() != null) {
            existing.setPan(updatedData.getPan().trim().toUpperCase());
        }
        if (StringUtils.hasText(updatedData.getState())) {
            existing.setState(updatedData.getState().trim());
        }
        if (StringUtils.hasText(updatedData.getStateCode())) {
            existing.setStateCode(updatedData.getStateCode().trim());
        }
        if (updatedData.getStatus() != null) {
            existing.setStatus(updatedData.getStatus());
        }

        return customerRepository.save(existing);
    }

    public void deleteCustomer(String id) {
        Customer customer = getCustomerById(id);
        customer.setStatus(Customer.CustomerStatus.INACTIVE);
        customerRepository.save(customer);
    }

    public void incrementBillCount(String customerId) {
        Customer customer = getCustomerById(customerId);
        customer.setBillsCount(customer.getBillsCount() + 1);
        customerRepository.save(customer);
    }

    public void decrementBillCount(String customerId) {
        Customer customer = getCustomerById(customerId);
        int count = customer.getBillsCount();
        customer.setBillsCount(Math.max(0, count - 1));
        customerRepository.save(customer);
    }

    public void addToTotalSpent(String customerId, BigDecimal amount) {
        Customer customer = getCustomerById(customerId);
        BigDecimal newTotal = customer.getTotalSpent().add(amount);
        customer.setTotalSpent(newTotal);
        customerRepository.save(customer);
    }
}
