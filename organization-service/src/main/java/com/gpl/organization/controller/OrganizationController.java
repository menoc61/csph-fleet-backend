package com.gpl.organization.controller;

import com.gpl.common.dto.ApiResponse;
import com.gpl.common.dto.PageResponse;
import com.gpl.organization.dto.*;
import com.gpl.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import com.gpl.common.security.RequiresPermission;

import java.util.List;

@RestController
@RequestMapping("/api/v1/organizations")
@RequiredArgsConstructor
public class OrganizationController {
    
    private final OrganizationService organizationService;

    @RequiresPermission("ORG_VIEW")
    @GetMapping
    public PageResponse<OrganizationSummaryResponse> listOrganizations(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        if (search != null && !search.isEmpty()) {
            return organizationService.searchOrganizations(search, pageable);
        }
        return organizationService.listOrganizations(type, tier, isActive, pageable);
    }

    @RequiresPermission("ORG_VIEW")
    @GetMapping("/{id}")
    public ApiResponse<OrganizationResponse> getOrganization(@PathVariable String id) {
        return ApiResponse.success(organizationService.getOrganization(id));
    }

    @RequiresPermission("ORG_CREATE")
    @PostMapping
    public ApiResponse<OrganizationResponse> createOrganization(
            @Valid @RequestBody CreateOrganizationRequest request,
            @RequestHeader("X-User-PersonId") String userId) {
        return ApiResponse.success(organizationService.createOrganization(request, userId));
    }

    @RequiresPermission("ORG_UPDATE")
    @PutMapping("/{id}")
    public ApiResponse<OrganizationResponse> updateOrganization(
            @PathVariable String id,
            @Valid @RequestBody UpdateOrganizationRequest request,
            @RequestHeader("X-User-PersonId") String userId) {
        return ApiResponse.success(organizationService.updateOrganization(id, request, userId));
    }

    /**
     * Partial update. Same body and semantics as the PUT above; the distinction
     * exists because the web client's generic CRUD layer
     * ({@code useCrud.updateMut}) issues PATCH for every resource, so without
     * this route editing a marketer or a transporter answered 405.
     *
     * <p>{@code UpdateOrganizationRequest} fields are all nullable and the
     * service only writes the non-null ones, so PATCH is a genuine partial
     * update and cannot blank a field the caller omitted.
     */
    @RequiresPermission("ORG_UPDATE")
    @PatchMapping("/{id}")
    public ApiResponse<OrganizationResponse> patchOrganization(
            @PathVariable String id,
            @RequestBody UpdateOrganizationRequest request,
            @RequestHeader("X-User-PersonId") String userId) {
        return ApiResponse.success(organizationService.updateOrganization(id, request, userId));
    }

    @RequiresPermission("ORG_UPDATE")
    @PatchMapping("/{id}/status")
    public ApiResponse<?> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateStatusRequest request,
            @RequestHeader("X-User-PersonId") String userId) {
        return ApiResponse.success(organizationService.updateStatus(id, request, userId));
    }

    @RequiresPermission("ORG_VIEW")
    @GetMapping("/{id}/children")
    public ApiResponse<List<OrganizationSummaryResponse>> getChildren(@PathVariable String id) {
        return ApiResponse.success(organizationService.getChildren(id));
    }

    @RequiresPermission("ORG_VIEW")
    @GetMapping("/{id}/hierarchy")
    public ApiResponse<Object> getHierarchy(@PathVariable String id) {
        return ApiResponse.success(organizationService.getHierarchy(id));
    }

    @RequiresPermission("ORG_DELETE")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteOrganization(@PathVariable String id) {
        organizationService.deleteOrganization(id);
        return ApiResponse.success(null);
    }
}
