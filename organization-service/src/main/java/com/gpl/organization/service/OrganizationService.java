package com.gpl.organization.service;

import com.gpl.common.dto.PageResponse;
import com.gpl.common.enums.EntityStatus;
import com.gpl.common.enums.OrganizationTier;
import com.gpl.common.enums.OrganizationType;
import com.gpl.common.exception.DuplicateResourceException;
import com.gpl.organization.dto.*;
import com.gpl.organization.model.Organization;
import com.gpl.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.criteria.Predicate;

@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    /**
     * French vocabulary the web client and the Postman collection use, mapped to
     * the enum. It exists because {@link OrganizationType#fromCode} only knows
     * the stored codes ({@code MKT}), while {@code GET /organizations?type=}
     * receives {@code MARKETEUR}. Without this table the filter is a 200 with
     * zero rows — the failure mode that looks like "the seed data is missing".
     */
    private static final Map<String, OrganizationType> TYPE_ALIASES = Map.ofEntries(
            Map.entry("MARKETEUR", OrganizationType.MARKETER),
            Map.entry("MARQUETEUR", OrganizationType.MARKETER),
            Map.entry("DISTRIBUTEUR", OrganizationType.MARKETER),
            Map.entry("TRANSPORTEUR", OrganizationType.TRANSPORTER),
            Map.entry("DEPOT", OrganizationType.DEPOT),
            Map.entry("REGULATEUR", OrganizationType.REGULATOR),
            Map.entry("CLIENT", OrganizationType.CLIENT),
            Map.entry("INTEGRATEUR", OrganizationType.INTEGRATOR));

    /** Same idea for the tier: the client sends {@code TIER_3}, the column holds {@code T3}. */
    private static final Map<String, OrganizationTier> TIER_ALIASES = Map.ofEntries(
            Map.entry("TIER_1", OrganizationTier.TIER_1_GOVERNANCE),
            Map.entry("TIER1", OrganizationTier.TIER_1_GOVERNANCE),
            Map.entry("TIER_2", OrganizationTier.TIER_2_INFRASTRUCTURE),
            Map.entry("TIER2", OrganizationTier.TIER_2_INFRASTRUCTURE),
            Map.entry("TIER_3", OrganizationTier.TIER_3_OPERATIONS),
            Map.entry("TIER3", OrganizationTier.TIER_3_OPERATIONS),
            Map.entry("TIER_4", OrganizationTier.TIER_4_CONSUMPTION),
            Map.entry("TIER4", OrganizationTier.TIER_4_CONSUMPTION));

    @Transactional
    public OrganizationResponse createOrganization(CreateOrganizationRequest request, String createdBy) {
        if (organizationRepository.existsByCode(request.getCode())) {
            throw new DuplicateResourceException("Organization with code " + request.getCode() + " already exists");
        }

        Organization org = Organization.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .type(request.getType())
                .tier(request.getTier())
                .classStructureId(request.getClassStructureId())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .website(request.getWebsite())
                .isHeadquarters(request.isHeadquarters())
                .build();
        
        org.setCreatedBy(createdBy);

        if (request.getParentOrganizationId() != null) {
            Organization parent = organizationRepository.findById(request.getParentOrganizationId())
                    .orElseThrow(() -> new RuntimeException("Parent organization not found"));
            
            org.setParentOrganizationId(parent.getId());
            org.setHierarchyLevel(parent.getHierarchyLevel() + 1);
            org.setHierarchyPath(parent.getHierarchyPath() + "/" + org.getCode());
            
            parent.setHasChildren(true);
            organizationRepository.save(parent);
        } else {
            org.setHierarchyLevel(0);
            org.setHierarchyPath(org.getCode());
        }

        Organization savedOrg = organizationRepository.save(org);
        return buildOrganizationResponse(savedOrg);
    }

    @Transactional
    public OrganizationResponse updateOrganization(String id, UpdateOrganizationRequest request, String changedBy) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        if (request.getName() != null) org.setName(request.getName());
        if (request.getDescription() != null) org.setDescription(request.getDescription());
        if (request.getContactEmail() != null) org.setContactEmail(request.getContactEmail());
        if (request.getContactPhone() != null) org.setContactPhone(request.getContactPhone());
        if (request.getWebsite() != null) org.setWebsite(request.getWebsite());
        if (request.getLogoUrl() != null) org.setLogoUrl(request.getLogoUrl());
        if (request.getTaxId() != null) org.setTaxId(request.getTaxId());
        if (request.getRegistrationNumber() != null) org.setRegistrationNumber(request.getRegistrationNumber());
        if (request.getCurrency() != null) org.setCurrency(request.getCurrency());
        if (request.getLanguage() != null) org.setLanguage(request.getLanguage());
        if (request.getTimezone() != null) org.setTimezone(request.getTimezone());
        if (request.getIsHeadquarters() != null) org.setHeadquarters(request.getIsHeadquarters());

        org.setChangeby(changedBy);

        Organization updatedOrg = organizationRepository.save(org);
        return buildOrganizationResponse(updatedOrg);
    }

    public OrganizationResponse getOrganization(String id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        return buildOrganizationResponse(org);
    }

    public PageResponse<OrganizationSummaryResponse> listOrganizations(String type, String tier, Boolean isActive, Pageable pageable) {
        Page<Organization> page = organizationRepository.findAll(
                organizationFilter(type, tier, isActive), pageable);

        List<OrganizationSummaryResponse> list = page.getContent().stream()
                .map(this::buildOrganizationSummaryResponse)
                .collect(Collectors.toList());
                
        PageResponse<OrganizationSummaryResponse> res = new PageResponse<>();
        res.setContent(list);
        res.setTotalElements(page.getTotalElements());
        res.setTotalPages(page.getTotalPages());
        return res;
    }

    /**
     * Builds the optional-filter predicate for the listing.
     *
     * <p>Each criterion is added only when the caller actually supplied it. A
     * hand-written {@code :type IS NULL OR ...} query looks equivalent but is
     * not: Hibernate cannot infer the type of a parameter that only ever
     * appears inside a null check, binds it as {@code bytea}, and Postgres
     * rejects the comparison with "function upper(bytea) does not exist".
     * Omitting absent criteria sidesteps that entirely.
     *
     * <p>{@code type} and {@code tier} compare case-insensitively because the
     * web client and hand-written Postman calls send either casing.
     */
    private Specification<Organization> organizationFilter(String type, String tier, Boolean isActive) {
        List<String> types = typeCandidates(type);
        List<String> tiers = tierCandidates(tier);
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (!types.isEmpty()) {
                predicates.add(cb.upper(root.get("type")).in(types));
            }
            if (!tiers.isEmpty()) {
                predicates.add(cb.upper(root.get("tier")).in(tiers));
            }
            if (isActive != null) {
                predicates.add(cb.equal(root.get("isActive"), isActive));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Accepts every spelling the callers actually send — the stored short code
     * ({@code MKT}), the enum constant ({@code MARKETER}), the French long name
     * the web client uses ({@code MARKETEUR}) and the French label
     * ({@code Marqueteur / Distributeur}) — and returns every stored value the
     * filter should match.
     *
     * <p>Returning a <em>set</em> rather than one code is deliberate: rows
     * written before the seeder started translating the vocabulary still hold
     * the long name ({@code MARKETEUR}), and a plain {@code = 'MKT'} would make
     * them invisible to the very filter that is supposed to list them.</p>
     *
     * <p>An unrecognised value is compared verbatim instead of silently
     * matching nothing, so an unknown filter is visibly empty rather than
     * mysteriously empty.</p>
     */
    private List<String> typeCandidates(String type) {
        String key = trimToNull(type);
        if (key == null) {
            return List.of();
        }
        key = key.toUpperCase(Locale.ROOT);

        OrganizationType resolved = TYPE_ALIASES.get(key);
        if (resolved == null) {
            try {
                resolved = OrganizationType.fromCode(key);
            } catch (IllegalArgumentException notACode) {
                for (OrganizationType t : OrganizationType.values()) {
                    if (t.name().equalsIgnoreCase(key)) {
                        resolved = t;
                        break;
                    }
                }
            }
        }
        if (resolved == null) {
            return List.of(key);
        }
        return List.of(resolved.getCode(), resolved.name());
    }

    /**
     * Same contract as {@link #typeCandidates} for the tier: {@code T3},
     * {@code TIER_3}, {@code TIER_3_OPERATIONS} and {@code Operations} all
     * resolve to the same row set.
     */
    private List<String> tierCandidates(String tier) {
        String key = trimToNull(tier);
        if (key == null) {
            return List.of();
        }
        key = key.toUpperCase(Locale.ROOT);

        OrganizationTier resolved = TIER_ALIASES.get(key);
        if (resolved == null) {
            try {
                resolved = OrganizationTier.fromCode(key);
            } catch (IllegalArgumentException notACode) {
                for (OrganizationTier t : OrganizationTier.values()) {
                    if (t.name().equalsIgnoreCase(key)) {
                        resolved = t;
                        break;
                    }
                }
            }
        }
        if (resolved == null) {
            return List.of(key);
        }
        return List.of(resolved.getCode(), resolved.name(), "TIER_" + resolved.getLevel());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public PageResponse<OrganizationSummaryResponse> searchOrganizations(String query, Pageable pageable) {
        Page<Organization> page = organizationRepository.findByNameContainingIgnoreCase(query, pageable);
        List<OrganizationSummaryResponse> list = page.getContent().stream()
                .map(this::buildOrganizationSummaryResponse)
                .collect(Collectors.toList());
                
        PageResponse<OrganizationSummaryResponse> res = new PageResponse<>();
        res.setContent(list);
        res.setTotalElements(page.getTotalElements());
        res.setTotalPages(page.getTotalPages());
        return res;
    }

    public List<OrganizationSummaryResponse> getChildren(String parentId) {
        return organizationRepository.findByParentOrganizationId(parentId).stream()
                .map(this::buildOrganizationSummaryResponse)
                .collect(Collectors.toList());
    }

    public Object getHierarchy(String orgId) {
        // returning simplified for now
        Organization org = organizationRepository.findById(orgId).orElseThrow(() -> new RuntimeException("Not found"));
        return organizationRepository.findByHierarchyPathStartingWith(org.getHierarchyPath());
    }

    @Transactional
    public Organization updateStatus(String id, UpdateStatusRequest request, String changedBy) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organization not found"));

        org.updateStatus(request.getNewStatus(), resolveStatusLabel(request.getNewStatus()));
        org.setChangeby(changedBy);

        return organizationRepository.save(org);
    }

    @Transactional
    public void deleteOrganization(String id) {
        Organization org = organizationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Organization not found"));
        org.updateStatus(EntityStatus.ARCHIVED.getCode(), EntityStatus.ARCHIVED.getDescription());
        organizationRepository.save(org);
    }

    /**
     * Human label for an organization status code, falling back to the raw code
     * so an unknown value stays legible instead of silently reading "Actif".
     */
    private String resolveStatusLabel(String code) {
        try {
            return EntityStatus.fromCode(code).getDescription();
        } catch (IllegalArgumentException unknown) {
            return code;
        }
    }

    private OrganizationResponse buildOrganizationResponse(Organization org) {
        OrganizationResponse res = new OrganizationResponse();
        res.setId(org.getId());
        res.setCode(org.getCode());
        res.setName(org.getName());
        res.setDescription(org.getDescription());
        res.setType(org.getType());
        res.setTypeDescription(org.getTypeDescription());
        res.setTier(org.getTier());
        res.setTierDescription(org.getTierDescription());
        res.setClassStructureId(org.getClassStructureId());
        res.setHierarchyPath(org.getHierarchyPath());
        res.setParentOrganizationId(org.getParentOrganizationId());
        res.setHierarchyLevel(org.getHierarchyLevel());
        res.setLogoUrl(org.getLogoUrl());
        res.setTaxId(org.getTaxId());
        res.setRegistrationNumber(org.getRegistrationNumber());
        res.setContactEmail(org.getContactEmail());
        res.setContactPhone(org.getContactPhone());
        res.setWebsite(org.getWebsite());
        res.setCurrency(org.getCurrency());
        res.setLanguage(org.getLanguage());
        res.setTimezone(org.getTimezone());
        res.setActive(org.isActive());
        res.setLocked(org.isLocked());
        res.setHeadquarters(org.isHeadquarters());
        res.setHasChildren(org.isHasChildren());
        res.setSystemOrg(org.isSystemOrg());
        res.setStatus(org.getStatus());
        res.setModificationsRef("/api/v1/organizations/" + org.getId() + "/modifications");
        return res;
    }

    private OrganizationSummaryResponse buildOrganizationSummaryResponse(Organization org) {
        OrganizationSummaryResponse res = new OrganizationSummaryResponse();
        res.setId(org.getId());
        res.setCode(org.getCode());
        res.setName(org.getName());
        res.setType(org.getType());
        res.setTypeDescription(org.getTypeDescription());
        res.setTier(org.getTier());
        res.setTierDescription(org.getTierDescription());
        res.setStatus(org.getStatus());
        res.setActive(org.isActive());
        res.setHierarchyPath(org.getHierarchyPath());
        res.setHierarchyLevel(org.getHierarchyLevel());
        res.setHasChildren(org.isHasChildren());
        res.setRegistrationNumber(org.getRegistrationNumber());
        res.setTaxId(org.getTaxId());
        res.setContactEmail(org.getContactEmail());
        res.setContactPhone(org.getContactPhone());
        res.setWebsite(org.getWebsite());
        res.setLogoUrl(org.getLogoUrl());
        return res;
    }
}
