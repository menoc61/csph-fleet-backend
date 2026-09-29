package com.gpl.organization.dto;

import lombok.Data;

@Data
public class OrganizationSummaryResponse {
    private String id;
    private String code;
    private String name;
    private String type;
    private String typeDescription;
    private String tier;
    private String tierDescription;
    private String status;
    private String statusDescription;
    private boolean isActive;
    private String hierarchyPath;
    private int hierarchyLevel;
    private boolean hasChildren;

    /**
     * Legal identifiers. Present on the list projection because the marketer /
     * transporter edit sheets read them straight off the row to prefill the
     * form; without them every edit dialog opened blank and would blank the
     * stored value on save.
     */
    private String registrationNumber;
    private String taxId;
    private String contactEmail;
    private String contactPhone;
    private String website;
    private String logoUrl;
}
