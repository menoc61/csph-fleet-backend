package com.gpl.subsidy.config;

import com.gpl.common.enums.DeclarationStatus;
import com.gpl.subsidy.model.Declaration;
import com.gpl.subsidy.repository.DeclarationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Seeds one monthly volume declaration per licensed marketer (dev / test /
 * local only), so the subsidy screens and the reconciliation workflow have a
 * period to work on.
 *
 * <p><b>Why this seeder exists.</b> {@code declarations} was never seeded, so
 * {@code GET /declarations} returned an empty page, the subsidy dashboard had
 * nothing to aggregate, and the regulator's approve/reject chain had no subject.
 * A declaration is also the prerequisite the reconciliation step asserts on, so
 * without one the whole downstream subsidy flow is unreachable on fresh data.</p>
 *
 * <p><b>Cross-service references.</b> {@code marketerOrganizationId} and
 * {@code declaringOrganizationId} hold organization <em>codes</em>
 * ({@code MKT-SCTM}), matching the convention the rest of the platform uses;
 * {@code siteId} is the depot code created by organization-service's
 * {@code MarketSiteDataInitializer}, and {@code submittedByPersonId} is a
 * {@code personId} seeded by user-service's {@code MarketUserDataInitializer}.
 * There is no foreign key across those databases, so they are natural keys.</p>
 *
 * <p>Idempotent per (marketer, site): a restart never declares the same volume
 * twice, which matters because a duplicated declaration inflates the subsidy
 * total instead of merely looking untidy.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local"})
@Order(1)
public class DeclarationDataInitializer implements CommandLineRunner {

    private final DeclarationRepository declarationRepository;

    /** One marketer's monthly declaration for one site. */
    private record DeclarationSeed(String marketer, String siteCode, double declaredVolume,
                                   DeclarationStatus status, String submittedByPersonId) {
    }

    /**
     * The seeded declarations. Amounts are in TM (tonnes métriques) — the unit
     * the reconciliation subtracts — and the period is the previous calendar
     * month, so a "declare last month" flow finds it immediately.
     */
    private static final List<DeclarationSeed> DECLARATIONS = List.of(
            new DeclarationSeed("MKT-SCTM", "MKT-SCTM-DEP-YAOUNDE", 1_240.0,
                    DeclarationStatus.SUBMITTED, "resp.sctm"),
            new DeclarationSeed("MKT-TOTAL", "MKT-TOTAL-DEP-YAOUNDE", 1_380.0,
                    DeclarationStatus.SUBMITTED, "resp.total"),
            new DeclarationSeed("MKT-AZA", "MKT-AZA-DEP-DOUALA", 960.0,
                    DeclarationStatus.DRAFT, "resp.aza"),
            new DeclarationSeed("MKT-CAMGAZ", "MKT-CAMGAZ-DEP-BAFOUSSAM", 870.0,
                    DeclarationStatus.SUBMITTED, "resp.camgaz"),
            new DeclarationSeed("MKT-TRADEX", "MKT-TRADEX-DEP-GAROUA", 640.0,
                    DeclarationStatus.DRAFT, "resp.tradex"),
            new DeclarationSeed("MKT-NEPTUNE", "MKT-NEPTUNE-DEP-LIMBE", 520.0,
                    DeclarationStatus.SUBMITTED, "resp.neptune"),
            new DeclarationSeed("MKT-STARGAS", "MKT-STARGAS-DEP-DOUALA", 730.0,
                    DeclarationStatus.DRAFT, "resp.stargas"));

    @Override
    @Transactional
    public void run(String... args) {
        Instant periodStart = Instant.now().minus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS);
        Instant periodEnd = periodStart.plus(30, ChronoUnit.DAYS);

        int created = 0;
        for (DeclarationSeed d : DECLARATIONS) {
            created += seed(d, periodStart, periodEnd);
        }

        log.info("===== Référentiel déclarations ===== {} création(s) ce démarrage, {} déclaration(s) au total "
                        + "(période {} -> {})",
                created, declarationRepository.count(), periodStart, periodEnd);
    }

    /**
     * Creates the declaration when the marketer has none for that site yet.
     * The site is the natural key: one depot, one declared volume per period.
     */
    private int seed(DeclarationSeed d, Instant periodStart, Instant periodEnd) {
        boolean exists = declarationRepository.findByMarketerOrganizationId(d.marketer()).stream()
                .anyMatch(existing -> d.siteCode().equals(existing.getSiteId()));
        if (exists) {
            return 0;
        }

        Declaration declaration = Declaration.builder()
                .marketerOrganizationId(d.marketer())
                .declaringOrganizationId(d.marketer())
                .siteId(d.siteCode())
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .declaredVolume(d.declaredVolume())
                .submittedByPersonId(d.submittedByPersonId())
                .build();
        // Status goes through the lifecycle door, not a setter.
        declaration.updateStatus(d.status().name(), labelOf(d.status()));
        declaration.setCreatedBy("SYSTEM_INIT");
        declarationRepository.save(declaration);

        log.info("Déclaration créée : {} site={} volume={} TM statut={}",
                d.marketer(), d.siteCode(), d.declaredVolume(), d.status());
        return 1;
    }

    /** French label for each declaration status, matching the service's vocabulary. */
    private String labelOf(DeclarationStatus status) {
        return switch (status) {
            case DRAFT -> "Brouillon";
            case SUBMITTED -> "Soumise au régulateur";
            case RECONCILED -> "Réconciliée";
            case DISPUTED -> "Contestée";
        };
    }
}
