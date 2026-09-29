package com.gpl.organization.repository;

import com.gpl.organization.model.Organization;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationRepository extends JpaRepository<Organization, String>, JpaSpecificationExecutor<Organization> {
    Optional<Organization> findByCode(String code);
    List<Organization> findByType(String type);
    List<Organization> findByTier(String tier);
    List<Organization> findByParentOrganizationId(String parentOrganizationId);
    List<Organization> findByHierarchyPathStartingWith(String hierarchyPathPrefix);
    List<Organization> findByIsActiveTrue();
    Page<Organization> findByNameContainingIgnoreCase(String name, Pageable pageable);
    boolean existsByCode(String code);
}
