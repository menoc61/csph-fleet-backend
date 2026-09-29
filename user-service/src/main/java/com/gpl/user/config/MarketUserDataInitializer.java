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
 * Seeds the field accounts of the licensed market participants (dev / test /
 * local only), so each organization created by the organization-service's
 * {@code ReferenceDataSeeder} has someone who can actually log in and be
 * scoped to it.
 *
 * <p>One responsable per marketer, one per transporter, one per industrial
 * client: without them the market views render but no non-regulator actor can
 * be exercised, and every org-scoped screen has to be reviewed as CSPH.</p>
 *
 * <p><b>Login identifiers.</b> {@code personId} is the username the PDA and the
 * web client type, so it follows the {@code <role>.<org>} convention the
 * baseline {@code UserDataInitializer} established. The role assignment is
 * written here too: it is what gives the account its effective permissions, so
 * a person row without it could authenticate but act on nothing.</p>
 *
 * <p><b>Cross-service references.</b> {@code organizationId} holds the
 * organization's <em>code</em> ({@code MKT-SCTM}), not its UUID — that is the
 * convention every existing row in this database follows. A UUID here would
 * create a person that exists but is invisible to every org-scoped query.
 * {@code primarySiteId} is deliberately left null: organization-service seeds
 * no sites for these orgs yet, and inventing a site code would produce a
 * dangling reference that silently breaks site-scoped filtering. Sites arrive
 * with the site seeder, which sets them alongside the rows that exist.</p>
 *
 * <p>Idempotent, and runs after {@code UserDataInitializer} ({@code @Order(2)})
 * so the role catalogue is in place.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local"})
@Order(2)
public class MarketUserDataInitializer implements CommandLineRunner {

    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final UserRoleAssignmentRepository roleAssignmentRepository;

    /**
     * One seeded account. {@code orgCode} is the organization code as stored by
     * organization-service. The role is passed to {@link #seed} rather than
     * carried here, so the same account shape serves every actor type.
     */
    private record Account(String personId, String firstName, String lastName, String email,
                           String phone, String orgCode, String title, String jobCode,
                           String city) {
    }

    private static final String PHONE = "+237 6 77 00 00 00";

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;

        // Responsables of the licensed marketers.
        for (Account a : List.of(
                account("resp.sctm", "Hervé", "Mbarga", "MKT-SCTM", "Douala"),
                account("resp.total", "Christine", "Owona", "MKT-TOTAL", "Yaoundé"),
                account("resp.aza", "Serge", "Njoya", "MKT-AZA", "Yaoundé"),
                account("resp.camgaz", "Ibrahim", "Hamadou", "MKT-CAMGAZ", "Douala"),
                account("resp.tradex", "Alain", "Tchoumi", "MKT-TRADEX", "Douala"),
                account("resp.neptune", "Léonie", "Same", "MKT-NEPTUNE", "Yaoundé"),
                account("resp.stargas", "Yves", "Fotso", "MKT-STARGAS", "Bafoussam"))) {
            created += seed(a, "MARKETER");
        }

        // Responsables of the contracted transporters.
        for (Account a : List.of(
                account("resp.translog", "Rodrigue", "Nkolo", "TRP-TRANSLOG", "Douala"),
                account("resp.expressgpl", "Nadège", "Ateba", "TRP-EXPRESSGPL", "Douala"))) {
            created += seed(a, "TRANSPORTER");
        }

        // Responsables approvisionnement of the industrial clients. VIEWER is
        // the read-only role: a consumption point sees its own deliveries and
        // nothing else.
        for (Account a : List.of(
                clientAccount("resp.shotel", "Marthe", "Bilik", "CLT-SHOTEL", "Yaoundé"),
                clientAccount("resp.clinique", "Paul", "Etoga", "CLT-CLINIQUE", "Douala"),
                clientAccount("resp.iacam", "Françoise", "Mbarga", "CLT-IACAM", "Douala"),
                clientAccount("resp.pharmanord", "Jean-Pierre", "Njoya", "CLT-PHARMANORD", "Douala"))) {
            created += seed(a, "VIEWER");
        }

        log.info("===== Utilisateurs marché ===== {} création(s), {} au total",
                created, personRepository.count());
    }

    private Account account(String personId, String firstName, String lastName,
                            String orgCode, String city) {
        return new Account(personId, firstName, lastName,
                personId + "@" + localPart(orgCode) + ".cm",
                PHONE, orgCode, "Responsable Distribution", "GES-STK-001", city);
    }

    private Account clientAccount(String personId, String firstName, String lastName,
                                  String orgCode, String city) {
        return new Account(personId, firstName, lastName,
                personId + "@" + localPart(orgCode) + ".cm",
                PHONE, orgCode, "Responsable Approvisionnement", "RES-APP-001", city);
    }

    /** {@code MKT-SCTM} -> {@code sctm}, for building a plausible mailbox. */
    private String localPart(String orgCode) {
        return orgCode.substring(orgCode.indexOf('-') + 1).toLowerCase();
    }

    /**
     * Creates the person, then grants {@code roleCode} as its primary
     * assignment. Returns 1 when the person was created, 0 when it already
     * existed.
     */
    private int seed(Account a, String roleCode) {
        if (personRepository.existsByPersonId(a.personId())) {
            return 0;
        }

        Person person = new Person();
        person.setPersonId(a.personId());
        person.setPersonUid(System.currentTimeMillis() % 100000);
        // Code, not UUID — see the class javadoc.
        person.setOrganizationId(a.orgCode());
        person.setOrgId(a.orgCode());
        person.setLocationOrg(a.orgCode());
        person.setEmail(a.email());
        person.setFirstName(a.firstName());
        person.setLastName(a.lastName());
        person.setDisplayName(a.lastName().toUpperCase() + " " + a.firstName());
        person.setTitle(a.title());
        person.setJobCode(a.jobCode());
        person.setJobCodeDescription(a.title());
        person.setPrimaryPhone(a.phone());
        person.setAddressLine1("");
        person.setCity(a.city());
        person.setLanguage("FR");
        person.setDeviceClass(2);
        person.setDeviceClassDescription("Gestionnaire / Superviseur");

        person.updateStatus(EntityStatus.ACTIVE.getCode(), EntityStatus.ACTIVE.getDescription());
        person.setCreatedBy("SYSTEM_INIT");
        person.setActive(true);
        person.setLocked(false);
        person.setCertified(false);
        person.setAcceptingWfMail(true);
        person.setLocToServReq(false);
        person.setStatusIface(false);
        person.setWfMailElection("PROCESS");
        person.setTransEmailElection("NEVER");

        personRepository.save(person);

        assignPrimaryRole(a, roleCode);
        return 1;
    }

    /**
     * Grants the role. A missing role is logged and skipped rather than thrown:
     * the person row is still valid, and failing the whole boot over one
     * catalogue gap would be worse than an account without rights.
     */
    private void assignPrimaryRole(Account a, String roleCode) {
        roleRepository.findByCode(roleCode).ifPresentOrElse(role -> {
            UserRoleAssignment assignment = new UserRoleAssignment();
            assignment.setPersonId(a.personId());
            assignment.setRoleId(role.getId());
            assignment.setOrganizationId(a.orgCode());
            assignment.setPrimary(true);
            assignment.setActive(true);
            // Status goes through the lifecycle door, not a setter:
            // AuditableEntity removes the setters on purpose.
            assignment.updateStatus(EntityStatus.ACTIVE.getCode(), EntityStatus.ACTIVE.getDescription());
            assignment.setCreatedBy("SYSTEM_INIT");
            roleAssignmentRepository.save(assignment);
            log.info("Utilisateur {} créé — org={}, rôle={}", a.personId(), a.orgCode(), roleCode);
        }, () -> log.warn("Rôle {} introuvable — {} créé sans droits", roleCode, a.personId()));
    }
}
