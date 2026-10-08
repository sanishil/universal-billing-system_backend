package com.billing.backend.service;

import com.billing.backend.entity.Customer;
import com.billing.backend.exception.BadRequestException;
import com.billing.backend.exception.ResourceNotFoundException;
import com.billing.backend.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

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

    public Customer createCustomer(Customer customerData) {
        if (!StringUtils.hasText(customerData.getName())) {
            throw new BadRequestException("Customer name is required");
        }
        if (!StringUtils.hasText(customerData.getEmail())) {
            throw new BadRequestException("Customer email is required");
        }

        if (!customerData.getEmail().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new BadRequestException("Invalid email format");
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

        long count = customerRepository.count();
        String customerId = "CUST-" + String.format("%03d", count + 1);

        Customer customer = Customer.builder()
                .id(customerId)
                .name(customerData.getName().trim())
                .email(customerData.getEmail().trim().toLowerCase())
                .phone(StringUtils.hasText(customerData.getPhone())
                        ? customerData.getPhone() : "+91 98000 00000")
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

        return customerRepository.save(customer);
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
