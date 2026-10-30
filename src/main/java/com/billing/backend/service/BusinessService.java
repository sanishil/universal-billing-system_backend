package com.billing.backend.service;

import com.billing.backend.entity.Business;
import com.billing.backend.exception.BadRequestException;
import com.billing.backend.exception.ResourceNotFoundException;
import com.billing.backend.repository.BusinessRepository;
import com.billing.backend.util.TokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BusinessService {

    private final BusinessRepository businessRepository;
    private final TokenGenerator tokenGenerator;

    public List<Business> getBusinesses(String userId) {
        return businessRepository.findByUserIdOrderByCreatedAtAsc(userId);
    }

    /** Throws 404 when the business doesn't exist or belongs to another user */
    public Business getOwnedBusiness(String id, String userId) {
        return businessRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Business", id));
    }

    public Business createBusiness(String userId, Business data) {
        validate(data);
        Business business = Business.builder()
                .id("BIZ-" + tokenGenerator.urlSafe(9))
                .userId(userId)
                .name(data.getName().trim())
                .gstin(normalizeGstin(data.getGstin()))
                .address(data.getAddress() != null ? data.getAddress().trim() : "")
                .stateCode(data.getStateCode().trim())
                .build();
        return businessRepository.save(business);
    }

    public Business updateBusiness(String id, String userId, Business data) {
        validate(data);
        Business existing = getOwnedBusiness(id, userId);
        existing.setName(data.getName().trim());
        existing.setGstin(normalizeGstin(data.getGstin()));
        existing.setAddress(data.getAddress() != null ? data.getAddress().trim() : "");
        existing.setStateCode(data.getStateCode().trim());
        return businessRepository.save(existing);
    }

    /** Customers keep their stored company name after the business is removed */
    public void deleteBusiness(String id, String userId) {
        businessRepository.delete(getOwnedBusiness(id, userId));
    }

    private void validate(Business data) {
        if (!StringUtils.hasText(data.getName())) {
            throw new BadRequestException("Business name is required");
        }
        if (!StringUtils.hasText(data.getStateCode())) {
            throw new BadRequestException("Business state is required");
        }
        String gstin = normalizeGstin(data.getGstin());
        if (!gstin.isEmpty()
                && !gstin.matches("^[0-3][0-9][A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$")) {
            throw new BadRequestException("Invalid GSTIN format");
        }
    }

    private String normalizeGstin(String gstin) {
        return gstin != null ? gstin.trim().toUpperCase() : "";
    }
}
