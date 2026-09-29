package com.gpl.organization.config;

import com.gpl.common.enums.OrganizationTier;
import com.gpl.common.enums.OrganizationType;
import com.gpl.organization.model.Organization;
import com.gpl.organization.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the ten administrative regions and the market participants the GPL
 * traceability platform needs to be demonstrable (dev / test / local only).
 *
 * <p>The Cameroon market is a duopoly-plus structure: the state owns the
 * upstream (SNH) and the depot network (SCDP), while distribution is carried by
 * a set of licensed marketers (SCTM, TotalEnergies, AZA, CAMGAZ, TRADEX,
 * Neptune, STARGAS) and a handful of contracted transporters. Seeding those
 * names makes the regulator views, the tour workflow and the reconciliation
 * flux reviewable end to end.
 *
 * <p><b>Type vocabulary.</b> The reference data describes organizations with
 * the long names ({@code MARKETEUR}, {@code DEPOT}, …) that the schema and the
 * web client both speak, but {@code organizations.type} stores the short codes
 * of {@link OrganizationType} ({@code MKT}, {@code DEP}, …). Seeding the long
 * name verbatim would produce rows that no {@code type=MARKETEUR} filter could
 * ever match, which is exactly the bug this seeder is here to prevent. Every
 * type therefore goes through {@link #toTypeCode}, which translates rather
 * than trusting the source vocabulary, and the tier is derived from the type.
 *
 * <p>Idempotent: each row is keyed on its unique {@code code} and skipped when
 * already present, so a restart never duplicates data and never fights the
 * {@code DataInitializer} baseline, which owns {@code CSPH}, {@code DEP-DLA},
 * {@code MKT-GPL}, {@code TRP-ABC} and {@code CLT-IND}.
 *
 * <p>Runs after {@code DataInitializer} ({@code @Order(10)}) so the CSPH
 * headquarters row it hangs children off already exists.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local"})
@Order(10)
public class ReferenceDataSeeder implements CommandLineRunner {

    private final OrganizationRepository organizationRepository;

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;

        // State-owned upstream and depot network.
        created += seedParented("SNH", "SNH — Société Nationale des Hydrocarbures",
                OrganizationType.DEPOT, "État — importation et distribution du brut raffiné",
                "M0100000001A", "M0100000001A", "cspHq", true);
        created += seedParented("SCDP", "SCDP — Société Camerounaise des Dépots Pétroliers",
                OrganizationType.DEPOT, "Réseau national de dépôts et centres emplisseurs",
                "RC/DLA/1975/000123", "M0100001234I", "scdp", true);

        // Licensed marketers (distributeurs).
        for (Marketer m : List.of(
                new Marketer("MKT-SCTM", "SCTM — Société Camerounaise de Transformations Métalliques",
                        "RC/DLA/1990/010456", "M0100123456B"),
                new Marketer("MKT-TOTAL", "TotalEnergies Marketing Cameroun SA",
                        "RC/DLA/1952/000789", "M0100009876C"),
                new Marketer("MKT-AZA", "AZA Afrigaz — Division GPL",
                        "RC/YDE/2005/003211", "M0200154321D"),
                new Marketer("MKT-CAMGAZ", "CAMGAZ SA",
                        "RC/DLA/1988/000234", "M0100032110E"),
                new Marketer("MKT-TRADEX", "TRADEX Cameroun SA",
                        "RC/DLA/2000/007654", "M0100287654F"),
                new Marketer("MKT-NEPTUNE", "Neptune Gaz SARL",
                        "RC/DLA/2019/012345", "M0100987654G"),
                new Marketer("MKT-STARGAS", "STARGAS Cameroun SARL",
                        "RC/DLA/2015/009876", "M0100456789H"))) {
            created += seedParented(m.code(), m.name(), OrganizationType.MARKETER,
                    "Distributeur GPL agréé — tournées VRAC et bouteilles 50 kg",
                    m.registrationNumber(), m.taxId(), "MKT-GPL", false);
        }

        // Contracted transporters.
        for (Marketer t : List.of(
                new Marketer("TRP-TRANSLOG", "Transport Logistique SA",
                        "RC/DLA/2003/005678", "M0100178901J"),
                new Marketer("TRP-EXPRESSGPL", "Express GPL Cameroon SARL",
                        "RC/DLA/2008/008901", "M0100245678K"))) {
            created += seedParented(t.code(), t.name(), OrganizationType.TRANSPORTER,
                    "Transporteur sous contrat — exécute les tournées EXTERNAL",
                    t.registrationNumber(), t.taxId(), "CSPH", false);
        }

        // Industrial / commercial consumption points.
        for (Marketer c : List.of(
                new Marketer("CLT-SHOTEL", "Société Hôtelière du Centre",
                        "RC/YDE/2010/003456", "M0200345678L"),
                new Marketer("CLT-CLINIQUE", "Clinique Baptiste de Douala",
                        "RC/DLA/2007/002345", "M0100398765M"),
                new Marketer("CLT-IACAM", "Industries Alimentaires du Cameroun (IACAM) SA",
                        "RC/DLA/1995/001234", "M0100112233N"),
                new Marketer("CLT-PHARMANORD", "Pharma Nord SARL",
                        "RC/YDE/2012/004567", "M0200445566O"))) {
            created += seedParented(c.code(), c.name(), OrganizationType.CLIENT,
                    "Point de consommation final — revendu par un marketeur",
                    c.registrationNumber(), c.taxId(), "MKT-GPL", false);
        }

        log.info("===== Référentiel organisations ===== {} création(s), {} au total",
                created, organizationRepository.count());
    }

    private record Marketer(String code, String name, String registrationNumber, String taxId) {
    }

    /**
     * Creates one organization under {@code parentCode}, skipping it when the
     * code is already taken. Returns 1 when a row was created, 0 otherwise.
     */
    private int seedParented(String code, String name, OrganizationType type, String description,
                             String registrationNumber, String taxId,
                             String parentCode, boolean isHeadquarters) {
        if (organizationRepository.existsByCode(code)) {
            return 0;
        }

        Organization org = Organization.builder()
                .code(code)
                .name(name)
                .description(description)
                // Store the CODE, not the source's long name — see the class javadoc.
                .type(type.getCode())
                .typeDescription(type.getDescription())
                .tier(tierFor(type).getCode())
                .tierDescription(tierFor(type).getDescription())
                .registrationNumber(registrationNumber)
                .taxId(taxId)
                .isHeadquarters(isHeadquarters)
                .isActive(true)
                .isLocked(false)
                .isSystemOrg(false)
                .currency("XAF")
                .language("FR")
                .timezone("Africa/Douala")
                .build();
        org.setCreatedBy("SYSTEM_INIT");

        // The parent is optional: on a cold volume the baseline seeder may not
        // have produced it yet, and an absent parent must not abort the whole
        // import. The row is then simply a hierarchy root.
        organizationRepository.findByCode(parentCode).ifPresentOrElse(parent -> {
            org.setParentOrganizationId(parent.getId());
            org.setHierarchyLevel(parent.getHierarchyLevel() + 1);
            org.setHierarchyPath(parent.getHierarchyPath() + "/" + code);
            if (!parent.isHasChildren()) {
                parent.setHasChildren(true);
                organizationRepository.save(parent);
            }
        }, () -> {
            org.setHierarchyLevel(0);
            org.setHierarchyPath(code);
            log.warn("Organisation parente {} absente — {} devient une racine de hiérarchie", parentCode, code);
        });

        organizationRepository.save(org);
        return 1;
    }

    /**
     * Market tier from organization type, mirroring the regulator's own
     * visibility model: the state sees everything, depots see their
     * supply chain, marketers and transporters see their operations, and
     * clients see only their deliveries.
     */
    private OrganizationTier tierFor(OrganizationType type) {
        return switch (type) {
            case REGULATOR -> OrganizationTier.TIER_1_GOVERNANCE;
            case DEPOT, INTEGRATOR, PLATFORM_OPERATOR -> OrganizationTier.TIER_2_INFRASTRUCTURE;
            case MARKETER, TRANSPORTER -> OrganizationTier.TIER_3_OPERATIONS;
            case CLIENT -> OrganizationTier.TIER_4_CONSUMPTION;
        };
    }
}
