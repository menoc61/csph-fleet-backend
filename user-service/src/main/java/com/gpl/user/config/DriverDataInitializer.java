package com.gpl.user.config;

import com.gpl.common.enums.EntityStatus;
import com.gpl.user.model.Person;
import com.gpl.user.model.Role;
import com.gpl.user.model.UserRoleAssignment;
import com.gpl.user.repository.PersonRepository;
import com.gpl.user.repository.RoleRepository;
import com.gpl.user.repository.UserRoleAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the chauffeurs of the licensed marketers and contracted transporters
 * (dev / test / local only).
 *
 * <p><b>Why this seeder exists.</b> {@code MarketUserDataInitializer} gives each
 * organization a <em>responsable</em> — a MARKETER or TRANSPORTER account. No
 * organization except the two baseline ones has anyone holding {@code DRIVER},
 * and {@code DRIVER} is a role of its own, distinct from {@code LIVREUR}. The
 * tour-creation dialog resolves the chauffeur dropdown by reading the detail
 * projection and keeping only rows whose credentials include {@code DRIVER}, so
 * with this seeder absent every new marketer's chauffeur list is empty and a
 * tour cannot be assigned a driver.</p>
 *
 * <p><b>Login identifiers.</b> {@code personId} follows the
 * {@code chauffeur.<org><n>} convention the baseline
 * {@code UserDataInitializer} established with {@code chauffeur.abc1}. It is
 * the username the PDA types, so it is also what the tour rows store in
 * {@code driver_person_id}.</p>
 *
 * <p><b>Cross-service references.</b> {@code organizationId} and {@code orgId}
 * hold the organization <em>code</em>. {@code primarySiteId} now holds a real
 * site id, because {@code MarketSiteDataInitializer} creates the depot the
 * driver parks at — the reason that field used to be left null is gone.</p>
 *
 * <p>Idempotent, keyed on the unique {@code person_id}, so a restart never
 * duplicates a chauffeur. Runs after {@code UserDataInitializer}
 * ({@code @Order(2)}) so both the role catalogue and the base accounts exist.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local"})
@Order(3)
public class DriverDataInitializer implements CommandLineRunner {

    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;

    private static final String PHONE = "+237 6 99 00 00 00";
    private static final String ROLE_DRIVER = "DRIVER";

    /**
     * One chauffeur. {@code orgCode} is the organization code as stored by
     * organization-service; {@code siteId} is the depot id created by its
     * {@code MarketSiteDataInitializer}.
     */
    private record Driver(String personId, String firstName, String lastName,
                          String orgCode, String city, String siteId) {
    }

    private static final List<Driver> DRIVERS = List.of(
            // Licensed marketers — two chauffeurs each (VRAC + bouteilles).
            new Driver("chauffeur.sctm1", "Éric", "Nkoulou", "MKT-SCTM", "Douala", "MKT-SCTM-DEP-YAOUNDE"),
            new Driver("chauffeur.sctm2", "Régis", "Tchoumi", "MKT-SCTM", "Yaoundé", "MKT-SCTM-DEP-YAOUNDE"),
            new Driver("chauffeur.total1", "Serge", "Kamdem", "MKT-TOTAL", "Douala", "MKT-TOTAL-DEP-YAOUNDE"),
            new Driver("chauffeur.total2", "Bertrand", "Ngo Bell", "MKT-TOTAL", "Yaoundé", "MKT-TOTAL-DEP-YAOUNDE"),
            new Driver("chauffeur.aza1", "Armand", "Mefire", "MKT-AZA", "Yaoundé", "MKT-AZA-DEP-DOUALA"),
            new Driver("chauffeur.aza2", "Landry", "Sop", "MKT-AZA", "Douala", "MKT-AZA-DEP-DOUALA"),
            new Driver("chauffeur.camgaz1", "Blaise", "Djoumessi", "MKT-CAMGAZ", "Douala", "MKT-CAMGAZ-DEP-BAFOUSSAM"),
            new Driver("chauffeur.camgaz2", "Hamidou", "Bello", "MKT-CAMGAZ", "Bafoussam", "MKT-CAMGAZ-DEP-BAFOUSSAM"),
            new Driver("chauffeur.tradex1", "Célestin", "Manga", "MKT-TRADEX", "Douala", "MKT-TRADEX-DEP-GAROUA"),
            new Driver("chauffeur.tradex2", "Oumarou", "Sali", "MKT-TRADEX", "Garoua", "MKT-TRADEX-DEP-GAROUA"),
            new Driver("chauffeur.neptune1", "Franck", "Ewane", "MKT-NEPTUNE", "Douala", "MKT-NEPTUNE-DEP-LIMBE"),
            new Driver("chauffeur.neptune2", "Rodrigue", "Mokake", "MKT-NEPTUNE", "Limbé", "MKT-NEPTUNE-DEP-LIMBE"),
            new Driver("chauffeur.stargas1", "Yannick", "Tiofack", "MKT-STARGAS", "Yaoundé", "MKT-STARGAS-DEP-DOUALA"),
            new Driver("chauffeur.stargas2", "Alain", "Bikoi", "MKT-STARGAS", "Douala", "MKT-STARGAS-DEP-DOUALA"),
            // Contracted transporters — their own long-haul chauffeurs.
            new Driver("chauffeur.translog1", "Mathieu", "Abena", "TRP-TRANSLOG", "Douala", "TRP-TRANSLOG-DEP"),
            new Driver("chauffeur.translog2", "Prosper", "Nyobe", "TRP-TRANSLOG", "Douala", "TRP-TRANSLOG-DEP"),
            new Driver("chauffeur.expressgpl1", "Édouard", "Mvogo", "TRP-EXPRESSGPL", "Douala", "TRP-EXPRESSGPL-DEP"));

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        long uid = 100_000L;
        for (Driver d : DRIVERS) {
            created += seed(d, uid++);
        }

        log.info("===== Référentiel chauffeurs ===== {} création(s) ce démarrage, {} personne(s) au total",
                created, personRepository.count());
    }

    /**
     * Creates the person, then grants {@code DRIVER}. Returns 1 when the person
     * was created, 0 when it already existed.
     */
    private int seed(Driver d, long uid) {
        if (personRepository.existsByPersonId(d.personId())) {
            return 0;
        }

        Person person = new Person();
        person.setPersonId(d.personId());
        person.setPersonUid(uid);
        // Codes, not UUIDs — see the class javadoc.
        person.setOrganizationId(d.orgCode());
        person.setOrgId(d.orgCode());
        person.setLocationOrg(d.orgCode());
        person.setPrimarySiteId(d.siteId());
        person.setSiteId(d.siteId());
        person.setLocationSite(d.siteId());
        person.setEmail(d.personId() + "@" + localPart(d.orgCode()) + ".cm");
        person.setFirstName(d.firstName());
        person.setLastName(d.lastName());
        person.setDisplayName(d.lastName().toUpperCase() + " " + d.firstName());
        person.setTitle("Chauffeur livreur GPL");
        person.setJobCode("CHF-001");
        person.setJobCodeDescription("Chauffeur livreur GPL");
        person.setPrimaryPhone(PHONE);
        person.setAddressLine1("");
        person.setCity(d.city());
        person.setLanguage("FR");
        person.setDeviceClass(3);
        person.setDeviceClassDescription("Chauffeur / Livreur");

        person.updateStatus(EntityStatus.ACTIVE.getCode(), EntityStatus.ACTIVE.getDescription());
        person.setCreatedBy("SYSTEM_INIT");
        person.setActive(true);
        person.setLocked(false);
        person.setCertified(true);
        person.setAcceptingWfMail(true);
        person.setLocToServReq(false);
        person.setStatusIface(false);
        person.setWfMailElection("NEVER");
        person.setTransEmailElection("NEVER");

        personRepository.save(person);

        assignDriverRole(d);
        log.info("Chauffeur créé : {} — org={}, site={}", d.personId(), d.orgCode(), d.siteId());
        return 1;
    }

    /**
     * Grants {@code DRIVER} scoped to the driver's organization and depot. A
     * missing role is logged and skipped rather than thrown: the person row is
     * still valid, and failing the whole boot over one catalogue gap would be
     * worse than a chauffeur without credentials.
     */
    private void assignDriverRole(Driver d) {
        roleRepository.findByCode(ROLE_DRIVER).ifPresentOrElse(role -> {
            UserRoleAssignment assignment = new UserRoleAssignment();
            assignment.setPersonId(d.personId());
            assignment.setRoleId(role.getId());
            assignment.setOrganizationId(d.orgCode());
            assignment.setSiteId(d.siteId());
            assignment.setPrimary(true);
            assignment.setActive(true);
            // Status goes through the lifecycle door, not a setter:
            // AuditableEntity removes the setters on purpose.
            assignment.updateStatus(EntityStatus.ACTIVE.getCode(), EntityStatus.ACTIVE.getDescription());
            assignment.setCreatedBy("SYSTEM_INIT");
            roleAssignmentRepository.save(assignment);
        }, () -> log.warn("Rôle {} introuvable — {} créé sans droits", ROLE_DRIVER, d.personId()));
    }

    /** {@code MKT-SCTM} -> {@code sctm}, for building a plausible mailbox. */
    private String localPart(String orgCode) {
        return orgCode.substring(orgCode.indexOf('-') + 1).toLowerCase();
    }
}
