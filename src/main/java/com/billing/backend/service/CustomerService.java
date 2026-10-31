package com.billing.backend.service;

import com.billing.backend.entity.Business;
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
import org.springframework.security.core.Authentication;
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
    private final BusinessService businessService;

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
    public Customer createCustomer(Customer customerData) {
        boolean giveLogin = Boolean.TRUE.equals(customerData.getInviteRecipient());

        String name = trimToEmpty(customerData.getName());
        String email = trimToEmpty(customerData.getEmail()).toLowerCase();
        String phone = normalizePhone(customerData.getPhone());
        String gstin = trimToEmpty(customerData.getGstin()).toUpperCase();
        String pan = trimToEmpty(customerData.getPan()).toUpperCase();

        if (name.isEmpty()) {
            throw new BadRequestException("Customer name is required");
        }
        if (giveLogin && email.isEmpty()) {
            throw new BadRequestException("An email address is needed to give this customer a login");
        }
        if (giveLogin && phone.isEmpty()) {
            throw new BadRequestException("A phone number is needed to give this customer a login");
        }
        if (!email.isEmpty() && !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BadRequestException("Invalid email format");
        }
        if (!phone.isEmpty() && !phone.matches("^[6-9][0-9]{9}$")) {
            throw new BadRequestException("Phone must be a 10-digit Indian mobile number");
        }
        if (!gstin.isEmpty() && !gstin.matches("^[0-3][0-9][A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")) {
            throw new BadRequestException("Invalid GSTIN format");
        }
        if (!pan.isEmpty() && !pan.matches("^[A-Z]{5}[0-9]{4}[A-Z]$")) {
            throw new BadRequestException("Invalid PAN format. Expected: AAAAA9999A");
        }

        if (!email.isEmpty() && customerRepository.existsByEmail(email)) {
            throw new ConflictException("A customer with email \"" + email + "\" already exists.");
        }
        if (!phone.isEmpty() && customerRepository.existsByPhone(phone)) {
            throw new ConflictException("A customer with phone \"" + phone + "\" already exists.");
        }
        if (giveLogin && userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with email \"" + email
                    + "\" already exists. Untick \"Give this customer a login\" to add them as a customer only.");
        }
        if (giveLogin && userRepository.existsByPhone(phone)) {
            throw new ConflictException("An account with phone \"" + phone
                    + "\" already exists. Untick \"Give this customer a login\" to add them as a customer only.");
        }

        // Get the currently logged-in user's id and name from JWT via SecurityContext
        UsernamePasswordAuthenticationToken auth =
                (UsernamePasswordAuthenticationToken) SecurityContextHolder
                        .getContext().getAuthentication();
        String loginUserName = (auth != null && auth.getDetails() != null)
                ? auth.getDetails().toString()
                : "Unknown";

        // The company is one of the creator's own businesses, picked by id
        Business business = null;
        if (StringUtils.hasText(customerData.getBusinessId()) && auth != null) {
            business = businessService.getOwnedBusiness(customerData.getBusinessId(), auth.getName());
        }
        String company = business != null
                ? business.getName()
                : StringUtils.hasText(customerData.getCompany()) ? customerData.getCompany().trim() : name;

        // Place of supply: the customer's state, else the GSTIN's state, else the business's own state
        String stateCode = trimToEmpty(customerData.getStateCode());
        if (stateCode.isEmpty() && !gstin.isEmpty()) {
            stateCode = gstin.substring(0, 2);
        }
        if (stateCode.isEmpty() && business != null && StringUtils.hasText(business.getStateCode())) {
            stateCode = business.getStateCode();
        }
        if (stateCode.isEmpty()) {
            throw new BadRequestException("Please select the customer's state");
        }

        long count = customerRepository.count();
        String customerId = "CUST-" + String.format("%03d", count + 1);
        while (customerRepository.existsById(customerId)) {
            count++;
            customerId = "CUST-" + String.format("%03d", count + 1);
        }

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
                .userid(giveLogin ? userId : "")
                .name(name)
                .email(email)
                .phone(phone)
                .address(trimToEmpty(customerData.getAddress()))
                .company(company)
                .businessId(business != null ? business.getId() : null)
                .gstin(gstin)
                .pan(pan)
                .state(trimToEmpty(customerData.getState()))
                .stateCode(stateCode)
                .totalSpent(BigDecimal.ZERO)
                .billsCount(0)
                .status(Customer.CustomerStatus.ACTIVE)
                .build();
        customerRepository.save(customer);

        if (giveLogin) {
            createCustomerLogin(customer, userId, loginUserName);
        }
        return customer;
    }

    /**
     * Gives an existing customer a login (from "Give this customer a login" on a new bill).
     * Does nothing when they already have one.
     */
    @Transactional
    public void giveLogin(String customerId) {
        Customer customer = getCustomerById(customerId);
        if (StringUtils.hasText(customer.getUserid())) {
            return;
        }
        String email = trimToEmpty(customer.getEmail()).toLowerCase();
        String phone = trimToEmpty(customer.getPhone());
        if (email.isEmpty()) {
            throw new BadRequestException("An email address is needed to give this customer a login");
        }
        if (phone.isEmpty()) {
            throw new BadRequestException("A phone number is needed to give this customer a login");
        }
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with email \"" + email
                    + "\" already exists. Untick \"Give this customer a login\" to issue the bill only.");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new ConflictException("An account with phone \"" + phone
                    + "\" already exists. Untick \"Give this customer a login\" to issue the bill only.");
        }

        long userIdCounter = userRepository.count() + 1;
        String userId = "USR-" + String.format("%02d", userIdCounter);
        while (userRepository.existsById(userId)) {
            userIdCounter++;
            userId = "USR-" + String.format("%02d", userIdCounter);
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String createdBy = (auth != null && auth.getDetails() != null) ? auth.getDetails().toString() : "Unknown";

        customer.setUserid(userId);
        customerRepository.save(customer);
        createCustomerLogin(customer, userId, createdBy);
    }

    /** Creates the customer's own login and emails them a temporary password */
    private void createCustomerLogin(Customer customer, String userId, String createdBy) {
        //      Auto password generating
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();

        StringBuilder passwordBuilder = new StringBuilder();

        for (int i = 0; i < 10; i++) {
            passwordBuilder.append(characters.charAt(random.nextInt(characters.length())));
        }

        String password = passwordBuilder.toString();

        // Usernames are unique: rahul, then rahul1, rahul2, ...
        String baseUsername = customer.getEmail().split("@")[0];
        if (baseUsername.length() > 45) baseUsername = baseUsername.substring(0, 45);
        String username = baseUsername;
        int suffix = 1;
        while (userRepository.findByUsername(username).isPresent()) {
            username = baseUsername + suffix++;
        }

        User newUser = User.builder()
                .id(userId)
                .name(customer.getName())
                .email(customer.getEmail())
                .phone(customer.getPhone())
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .role("Administrator")
                .status(User.UserStatus.ACTIVE)
                .loginAttempts(0)
                .build();

        // Send email
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom("sanishil.cse@gmail.com");
        message.setTo(customer.getEmail());
        message.setSubject("Universal Billing System - Account Created");

        message.setText(
                "Hello " + customer.getName() + ",\n\n" +
                        "Your account has been successfully created in the Universal Billing System.\n\n" +
                        "Created By: " + createdBy + " for " + customer.getCompany() + "\n\n" +
                        "Login Credentials:\n" +
                        "Username: " + customer.getEmail() + "\n" +
                        "Password: " + password + "\n\n" +
                        "Please log in using the above credentials and verify your account details. " +
                        "For security reasons, we strongly recommend changing your password after your first login.\n\n" +
                        "Regards,\n" +
                        "Universal Billing System"
        );

        userRepository.save(newUser);
        mailSender.send(message);
    }

    private String trimToEmpty(String value) {
        return value != null ? value.trim() : "";
    }

    /** Keeps digits only and drops a leading +91 / 91 / 0 */
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) return digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) return digits.substring(1);
        return digits;
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
        if (StringUtils.hasText(updatedData.getBusinessId())) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            Business business = businessService.getOwnedBusiness(updatedData.getBusinessId(), auth.getName());
            existing.setBusinessId(business.getId());
            existing.setCompany(business.getName());
        } else if (StringUtils.hasText(updatedData.getCompany())) {
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
