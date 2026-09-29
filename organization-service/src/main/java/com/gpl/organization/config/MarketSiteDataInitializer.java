package com.gpl.organization.config;

import com.gpl.common.enums.SiteType;
import com.gpl.organization.model.ClientSite;
import com.gpl.organization.model.Organization;
import com.gpl.organization.model.Site;
import com.gpl.organization.repository.ClientSiteRepository;
import com.gpl.organization.repository.OrganizationRepository;
import com.gpl.organization.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the physical sites of the market participants created by
 * {@link ReferenceDataSeeder} (dev / test / local only).
 *
 * <p>Without this seeder the licensed marketers exist as organizations but own
 * no site at all, so every site-scoped view renders empty and
 * {@code MarketUserDataInitializer} has to leave {@code primarySiteId} null:
 * the web client's marketer detail page reads "Aucun site enregistré", the
 * tour-creation dialog offers no destination, and a pickup request has no
 * source. This is the missing half of the market reference data.</p>
 *
 * <p><b>What is created</b> — for each marketer a seat (OFC), a filling centre
 * (FIL) and a regional storage depot (DEP) in a second city, so the depot
 * network is not all in Douala; for each transporter a vehicle yard (DEP); and
 * for each industrial client a delivery site (CST) plus the {@code client_sites}
 * row that turns it into a tour destination.</p>
 *
 * <p><b>Cross-service references.</b> {@code siteId} values are the codes the
 * tour-service checkpoints and the fleet vehicles reference
 * ({@code MKT-SCTM-DEP-YDE}), not UUIDs — the same convention
 * {@code MarketUserDataInitializer} follows for {@code organizationId}. The
 * {@code orgId} column carries the organization <em>code</em> while
 * {@code organizationId} carries its UUID, mirroring what
 * {@code DataInitializer} writes.</p>
 *
 * <p><b>Idempotent and re-runnable.</b> Each site is keyed on its unique
 * {@code code}, and each client link on its unique {@code site_id}, so a
 * restart creates only what is still missing — it never duplicates and never
 * skips the whole batch because one unrelated row exists (the bug the fleet
 * seeder had). Runs after {@link ReferenceDataSeeder} ({@code @Order(10)}) so
 * the organizations it hangs sites off are already persisted.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local"})
@Order(20)
public class MarketSiteDataInitializer implements CommandLineRunner {

    private final OrganizationRepository organizationRepository;
    private final SiteRepository siteRepository;
    private final ClientSiteRepository clientSiteRepository;


    /** One marketer's footprint: a seat city and a second regional depot city. */
    private record MarketerFootprint(String orgCode, String city, String region,
                                     double lat, double lng,
                                     String depotCity, String depotRegion,
                                     double depotLat, double depotLng) {
    }

    /** A client's single delivery point plus its delivery constraints. */
    private record ClientFootprint(String orgCode, String siteName, String description,
                                   String address, String city, String region,
                                   double lat, double lng,
                                   String deliveryInstructions, boolean requiresAuthorization) {
    }

    private static final List<MarketerFootprint> MARKETERS = List.of(
            new MarketerFootprint("MKT-SCTM", "Douala", "Littoral", 4.0511, 9.7679,
                    "Yaoundé", "Centre", 3.8612, 11.5217),
            new MarketerFootprint("MKT-TOTAL", "Douala", "Littoral", 4.0490, 9.7050,
                    "Yaoundé", "Centre", 3.8700, 11.5100),
            new MarketerFootprint("MKT-AZA", "Yaoundé", "Centre", 3.8480, 11.5021,
                    "Douala", "Littoral", 4.0600, 9.7500),
            new MarketerFootprint("MKT-CAMGAZ", "Douala", "Littoral", 4.0800, 9.7200,
                    "Bafoussam", "Ouest", 5.4781, 10.4176),
            new MarketerFootprint("MKT-TRADEX", "Douala", "Littoral", 4.0900, 9.7300,
                    "Garoua", "Nord", 9.3017, 13.3921),
            new MarketerFootprint("MKT-NEPTUNE", "Douala", "Littoral", 4.0650, 9.7450,
                    "Limbé", "Sud-Ouest", 4.0186, 9.2136),
            new MarketerFootprint("MKT-STARGAS", "Yaoundé", "Centre", 3.8550, 11.5100,
                    "Douala", "Littoral", 4.0550, 9.7000));

    /** Transporters only need the yard their fleet parks in. */
    private static final List<MarketerFootprint> TRANSPORTERS = List.of(
            new MarketerFootprint("TRP-TRANSLOG", "Douala", "Littoral", 4.0000, 9.8000,
                    "Douala", "Littoral", 4.0000, 9.8000),
            new MarketerFootprint("TRP-EXPRESSGPL", "Douala", "Littoral", 4.0200, 9.6900,
                    "Douala", "Littoral", 4.0200, 9.6900));

    private static final List<ClientFootprint> CLIENTS = List.of(
            new ClientFootprint("CLT-SHOTEL", "Société Hôtelière du Centre — Cuisine Centrale",
                    "Point de consommation GPL, cuisine centrale de l'hôtel",
                    "Avenue Kennedy, Centre-ville, Yaoundé", "Yaoundé", "Centre",
                    3.8667, 11.5167,
                    "Livrer par l'accès de service, 6h-16h. Prévenir le chef de cuisine 1h avant.",
                    false),
            new ClientFootprint("CLT-CLINIQUE", "Clinique Baptiste de Douala — Chaufferie",
                    "Point de consommation GPL, chaufferie et cuisine",
                    "Bonamoussadi, Douala V", "Douala", "Littoral", 4.0900, 9.7500,
                    "Livraison uniquement avant 8h pour ne pas gêner les urgences. Autorisation d'accès requise.",
                    true),
            new ClientFootprint("CLT-IACAM", "IACAM — Usine de Douala-Bassa",
                    "Point de livraison GPL VRAC, usine agroalimentaire",
                    "Zone Industrielle Bassa, Douala", "Douala", "Littoral", 4.0100, 9.6850,
                    "Rendez-vous obligatoire au poste de garde. Pesée à l'entrée et à la sortie.",
                    true),
            new ClientFootprint("CLT-PHARMANORD", "Pharma Nord — Dépôt Garoua",
                    "Point de consommation GPL, dépôt pharmaceutique",
                    "Quartier Poumpoumré, Garoua", "Garoua", "Nord", 9.3017, 13.3921,
                    "Livrer au dépôt arrière. Contrôle de température demandé à la réception.",
                    false));

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;

        for (MarketerFootprint m : MARKETERS) {
            created += seedMarketer(m);
        }
        for (MarketerFootprint t : TRANSPORTERS) {
            created += seedTransporter(t);
        }
        for (ClientFootprint c : CLIENTS) {
            created += seedClientDeliveryPoint(c);
        }

        log.info("===== Référentiel sites ===== {} création(s), {} site(s), {} site(s) client",
                created, siteRepository.count(), clientSiteRepository.count());
    }

    /**
     * Seat + filling centre + regional depot for one marketer. Every site is
     * skipped individually when its code already exists, so a database that
     * already holds the seat still gains the depot on the next boot.
     */
    private int seedMarketer(MarketerFootprint m) {
        Organization org = organizationRepository.findByCode(m.orgCode()).orElse(null);
        if (org == null) {
            log.warn("Marketeur {} absent — sites ignorés", m.orgCode());
            return 0;
        }
        int created = 0;

        created += saveSite(buildSite(org, siteCode(m.orgCode(), "SIEGE"), m.orgCode() + "-OFC",
                org.getName() + " — Siège", "Siège administratif et commercial du distributeur",
                SiteType.OFFICE, "Centre-ville, " + m.city(), m.city(), m.region(),
                m.lat(), m.lng(), null, null));

        created += saveSite(buildSite(org, siteCode(m.orgCode(), "FIL"), m.orgCode() + "-FIL",
                org.getName() + " — Centre emplisseur " + m.city(),
                "Centre emplisseur de bouteilles GPL 50 kg",
                SiteType.FILLING_CENTER, "Zone industrielle, " + m.city(), m.city(), m.region(),
                m.lat() + 0.01, m.lng() + 0.01, 120.0, 6));

        created += saveSite(buildSite(org,
                siteCode(m.orgCode(), "DEP-" + slugCity(m.depotCity())),
                m.orgCode() + "-DEP-" + slugCity(m.depotCity()),
                org.getName() + " — Dépôt " + m.depotCity(),
                "Dépôt régional de stockage et d'expédition GPL",
                SiteType.DEPOT, "Zone logistique, " + m.depotCity(),
                m.depotCity(), m.depotRegion(),
                m.depotLat(), m.depotLng(), 250.0, 4));

        return created;
    }

    /** A transporter's vehicle yard — the site its fleet and tours start from. */
    private int seedTransporter(MarketerFootprint t) {
        Organization org = organizationRepository.findByCode(t.orgCode()).orElse(null);
        if (org == null) {
            log.warn("Transporteur {} absent — sites ignorés", t.orgCode());
            return 0;
        }
        return saveSite(buildSite(org, siteCode(t.orgCode(), "DEP"), t.orgCode() + "-DEP",
                org.getName() + " — Parc " + t.city(),
                "Parc et atelier du transporteur sous contrat",
                SiteType.DEPOT, "Zone logistique, " + t.city(), t.city(), t.region(),
                t.lat(), t.lng(), null, 12));
    }

    /**
     * A client delivery point: the physical site (CST) <em>and</em> the
     * {@code client_sites} row that makes it a tour destination. Having a site
     * without the link is what leaves a checkpoint with no delivery address.
     */
    private int seedClientDeliveryPoint(ClientFootprint c) {
        Organization org = organizationRepository.findByCode(c.orgCode()).orElse(null);
        if (org == null) {
            log.warn("Client {} absent — site de livraison ignoré", c.orgCode());
            return 0;
        }

        String code = siteCode(c.orgCode(), "LIV");
        int created = 0;

        Site site = siteRepository.findByCode(code).orElse(null);
        if (site == null) {
            site = saveAndReturn(buildSite(org, code, c.orgCode() + "-LIV",
                    c.siteName(), c.description(), SiteType.CLIENT_SITE,
                    c.address(), c.city(), c.region(), c.lat(), c.lng(), null, 2));
            created = 1;
        }

        if (!clientSiteRepository.existsBySiteId(site.getId())) {
            ClientSite link = ClientSite.builder()
                    .site(site)
                    .clientOrganization(org)
                    .deliveryInstructions(c.deliveryInstructions())
                    .requiresAuthorization(c.requiresAuthorization())
                    .build();
            link.setCreatedBy("SYSTEM_INIT");
            clientSiteRepository.save(link);
            log.info("Site client rattaché : org={}, site={}", org.getCode(), site.getCode());
        }

        return created;
    }

    private Site buildSite(Organization org, String code, String siteId, String name,
                           String description, SiteType type, String address,
                           String city, String region, double latitude, double longitude,
                           Double storageCapacityTons, Integer maxVehicleBays) {
        return Site.builder()
                .code(code)
                .siteId(siteId)
                .name(name)
                .description(description)
                // `Site.organizationId` is insertable=false, so the FK column is
                // written from the ASSOCIATION, not from the id string. Setting
                // only the id (as DataInitializer does) leaves organization_id
                // NULL and the site invisible to every org-scoped lookup.
                .organization(org)
                .organizationId(org.getId())
                // The CODE, not the UUID — see the class javadoc.
                .orgId(org.getCode())
                .type(type.getCode())
                .typeDescription(type.getDescription())
                .addressLine1(address)
                .city(city)
                .region(region)
                .country("CM")
                .latitude(latitude)
                .longitude(longitude)
                .geofenceRadiusMeters(250)
                .hasStorageCapacity(storageCapacityTons != null)
                .storageCapacityTons(storageCapacityTons)
                .maxVehicleBays(maxVehicleBays)
                .operatingHoursStart("06:00")
                .operatingHoursEnd("18:00")
                .isDefault(false)
                .isActive(true)
                .isLocked(false)
                .disabled(false)
                .isOperational(true)
                .build();
    }

    /** Creates the row when its code is still free; returns 1 when it did. */
    private int saveSite(Site site) {
        if (siteRepository.existsByCode(site.getCode())) {
            return 0;
        }
        saveAndReturn(site);
        return 1;
    }

    private Site saveAndReturn(Site site) {
        site.setCreatedBy("SYSTEM_INIT");
        Site saved = siteRepository.save(site);
        log.info("Site créé : code={}, type={}, org={}, ville={}",
                saved.getCode(), saved.getType(), saved.getOrgId(), saved.getCity());
        return saved;
    }

    /** {@code SITE-MKT-SCTM-SIEGE} — the unique natural key of a seeded site. */
    private String siteCode(String orgCode, String suffix) {
        return "SITE-" + orgCode + "-" + suffix;
    }

    /** {@code Yaoundé} -> {@code YAOUNDE}, so the city is usable inside a code. */
    private String slugCity(String city) {
        return java.text.Normalizer.normalize(city, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase()
                .replaceAll("[^A-Z0-9]", "");
    }
}
