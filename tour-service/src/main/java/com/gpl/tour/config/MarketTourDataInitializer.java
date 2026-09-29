package com.gpl.tour.config;

import com.gpl.common.lifecycle.CheckpointStatus;
import com.gpl.common.lifecycle.Lifecycle;
import com.gpl.common.lifecycle.TourneeStatus;
import com.gpl.tour.model.Checkpoint;
import com.gpl.tour.model.PickupRequest;
import com.gpl.tour.model.Tour;
import com.gpl.tour.model.TransporterContract;
import com.gpl.tour.repository.CheckpointRepository;
import com.gpl.tour.repository.PickupRequestRepository;
import com.gpl.tour.repository.TourRepository;
import com.gpl.tour.repository.TransporterContractRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Seeds the commercial context of the licensed marketers: the transport
 * contracts that authorise an EXTERNAL tour, the depot pickups that feed them,
 * and one planned tour with its client stops (dev / test / local only).
 *
 * <p><b>Why this seeder exists.</b> {@code TourDataInitializer} only ever
 * exercises the baseline {@code MKT-GPL} / {@code TRP-ABC} pair, and
 * {@code pickup_requests} and {@code transporter_contracts} were seeded empty.
 * Opening a new marketer therefore showed no tour, no contract and no pickup,
 * and creating a tour for it failed: an EXTERNAL tour needs a contract to
 * acknowledge against, and a driver/vehicle that actually exist. This seeder
 * supplies all three, using the ids the other seeders produce
 * ({@code VEH-MKT-SCTM-001}, {@code chauffeur.sctm1}).</p>
 *
 * <p><b>Cross-service references.</b> There is no foreign key between
 * {@code gpl_tour_db}, {@code gpl_fleet_db} and {@code gpl_organization_db}, so
 * every reference below is a stable natural key rather than a UUID: marketer
 * and transporter codes, the deterministic vehicle ids from
 * {@code FleetDeviceDataInitializer}, the {@code personId} from
 * {@code DriverDataInitializer}, and the site codes from
 * {@code MarketSiteDataInitializer}. Checkpoint destinations keep the existing
 * convention of storing the destination site <em>code</em> — the same shape
 * {@code TourDataInitializer} already writes.</p>
 *
 * <p>Idempotent per row: a tour is keyed on its {@code tourCode} within its
 * marketer, a checkpoint on {@code (tourId, sequence)}, a contract on the
 * marketer/transporter pair, and a pickup on the marketer.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local"})
@Order(2)
public class MarketTourDataInitializer implements CommandLineRunner {

    private final TourRepository tourRepository;
    private final CheckpointRepository checkpointRepository;
    private final PickupRequestRepository pickupRequestRepository;
    private final TransporterContractRepository transporterContractRepository;

    /** An EXTERNAL tour: marketer, haulier, vehicle, chauffeur and its stops. */
    private record TourSeed(String tourCode, String marketer, String transporter,
                            String vehicleId, String driverPersonId, String type,
                            double requestedQuantity, List<String> destinationSiteCodes) {
    }

    /** A depot pickup order feeding one marketer. */
    private record PickupSeed(String marketer, String sourceSiteCode,
                              String destinationSiteCode, double requestedQuantity) {
    }

    /**
     * Client delivery points created by {@code MarketSiteDataInitializer}, by
     * site code. Reused across tours so several marketers serve the same city.
     */
    private static final String SITE_IACAM = "SITE-CLT-IACAM-LIV";
    private static final String SITE_CLINIQUE = "SITE-CLT-CLINIQUE-LIV";
    private static final String SITE_SHOTEL = "SITE-CLT-SHOTEL-LIV";
    private static final String SITE_PHARMANORD = "SITE-CLT-PHARMANORD-LIV";

    private static final List<TourSeed> TOURS = List.of(
            new TourSeed("T-SCTM-2026-001", "MKT-SCTM", "TRP-TRANSLOG",
                    "VEH-MKT-SCTM-001", "chauffeur.sctm1", "VRAC", 18.0,
                    List.of(SITE_IACAM, SITE_CLINIQUE)),
            new TourSeed("T-TOTAL-2026-001", "MKT-TOTAL", "TRP-EXPRESSGPL",
                    "VEH-MKT-TOTAL-001", "chauffeur.total1", "VRAC", 20.0,
                    List.of(SITE_SHOTEL, SITE_PHARMANORD)),
            new TourSeed("T-AZA-2026-001", "MKT-AZA", "TRP-TRANSLOG",
                    "VEH-MKT-AZA-001", "chauffeur.aza1", "VRAC", 16.0,
                    List.of(SITE_IACAM, SITE_SHOTEL)),
            new TourSeed("T-CAMGAZ-2026-001", "MKT-CAMGAZ", "TRP-EXPRESSGPL",
                    "VEH-MKT-CAMGAZ-001", "chauffeur.camgaz1", "VRAC", 14.0,
                    List.of(SITE_PHARMANORD, SITE_CLINIQUE)),
            new TourSeed("T-TRADEX-2026-001", "MKT-TRADEX", "TRP-TRANSLOG",
                    "VEH-MKT-TRADEX-001", "chauffeur.tradex1", "VRAC", 12.0,
                    List.of(SITE_PHARMANORD)),
            new TourSeed("T-NEPTUNE-2026-001", "MKT-NEPTUNE", "TRP-EXPRESSGPL",
                    "VEH-MKT-NEPTUNE-001", "chauffeur.neptune1", "VRAC", 10.0,
                    List.of(SITE_CLINIQUE)),
            new TourSeed("T-STARGAS-2026-001", "MKT-STARGAS", "TRP-TRANSLOG",
                    "VEH-MKT-STARGAS-001", "chauffeur.stargas1", "VRAC", 15.0,
                    List.of(SITE_SHOTEL, SITE_IACAM)));

    private static final List<PickupSeed> PICKUPS = List.of(
            new PickupSeed("MKT-SCTM", "SITE-DEP-DLA-PRINCIPAL", "SITE-MKT-SCTM-DEP-YAOUNDE", 20.0),
            new PickupSeed("MKT-TOTAL", "SITE-DEP-DLA-PRINCIPAL", "SITE-MKT-TOTAL-DEP-YAOUNDE", 22.0),
            new PickupSeed("MKT-AZA", "SITE-DEP-YDE-PRINCIPAL", "SITE-MKT-AZA-DEP-DOUALA", 18.0),
            new PickupSeed("MKT-CAMGAZ", "SITE-DEP-DLA-PRINCIPAL", "SITE-MKT-CAMGAZ-DEP-BAFOUSSAM", 16.0),
            new PickupSeed("MKT-TRADEX", "SITE-DEP-DLA-PRINCIPAL", "SITE-MKT-TRADEX-DEP-GAROUA", 14.0),
            new PickupSeed("MKT-NEPTUNE", "SITE-DEP-DLA-PRINCIPAL", "SITE-MKT-NEPTUNE-DEP-LIMBE", 12.0),
            new PickupSeed("MKT-STARGAS", "SITE-DEP-YDE-PRINCIPAL", "SITE-MKT-STARGAS-DEP-DOUALA", 17.0));

    @Override
    @Transactional
    public void run(String... args) {
        int contracts = 0;
        int tours = 0;
        int pickups = 0;

        for (TourSeed t : TOURS) {
            contracts += seedContract(t.marketer(), t.transporter());
            tours += seedTour(t);
        }
        for (PickupSeed p : PICKUPS) {
            pickups += seedPickup(p);
        }

        log.info("===== Contexte commercial ===== {} contrat(s), {} tournée(s), {} enlèvement(s) créé(s) "
                        + "ce démarrage — totaux : {} tournée(s), {} arrêt(s), {} contrat(s), {} enlèvement(s)",
                contracts, tours, pickups,
                tourRepository.count(), checkpointRepository.count(),
                transporterContractRepository.count(), pickupRequestRepository.count());
    }

    /**
     * One live contract per marketer/transporter pair. The pair is the natural
     * key: a marketer may contract several hauliers, but the same pair twice is
     * what would produce a duplicate acknowledgement.
     */
    private int seedContract(String marketer, String transporter) {
        boolean exists = transporterContractRepository.findByMarketerOrganizationId(marketer).stream()
                .anyMatch(c -> transporter.equals(c.getTransporterOrganizationId()));
        if (exists) {
            return 0;
        }

        TransporterContract contract = new TransporterContract();
        contract.setMarketerOrganizationId(marketer);
        contract.setTransporterOrganizationId(transporter);
        contract.setPrimary(true);
        contract.setContractReference("CTR-" + marketer + "-" + transporter);
        contract.setStartedAt(Instant.now().minus(90, ChronoUnit.DAYS));
        contract.setEndedAt(Instant.now().plus(275, ChronoUnit.DAYS));
        contract.setActive(true);
        contract.setCreatedBy("SYSTEM_INIT");
        transporterContractRepository.save(contract);

        log.info("Contrat transporteur créé : {} -> {}", marketer, transporter);
        return 1;
    }

    /** A submitted depot pickup feeding one marketer. */
    private int seedPickup(PickupSeed p) {
        boolean exists = !pickupRequestRepository
                .findByMarketerOrganizationId(p.marketer(), PageRequest.of(0, 1))
                .isEmpty();
        if (exists) {
            return 0;
        }

        PickupRequest pickup = new PickupRequest();
        pickup.setMarketerOrganizationId(p.marketer());
        pickup.setSourceSiteId(p.sourceSiteCode());
        pickup.setDestinationSiteId(p.destinationSiteCode());
        pickup.setRequestedQuantity(p.requestedQuantity());
        // Same vocabulary the service writes on POST /pickups.
        pickup.updateStatus("SUBMITTED", "Demande d'enlèvement vrac soumise");
        pickup.setCreatedBy("SYSTEM_INIT");
        pickupRequestRepository.save(pickup);

        log.info("Enlèvement créé : {} {} -> {}", p.marketer(), p.sourceSiteCode(), p.destinationSiteCode());
        return 1;
    }

    /** The tour and its stops. A tour is keyed on tourCode within its marketer. */
    private int seedTour(TourSeed t) {
        boolean exists = tourRepository.findByMarketerOrganizationId(t.marketer()).stream()
                .anyMatch(x -> t.tourCode().equals(x.getTourCode()));
        if (exists) {
            return 0;
        }

        Tour tour = new Tour();
        tour.setTourCode(t.tourCode());
        tour.setMarketerOrganizationId(t.marketer());
        tour.setExecutionMode("EXTERNAL");
        tour.setTransporterOrganizationId(t.transporter());
        tour.setVehicleId(t.vehicleId());
        // driverId stays null: it is the transport-side assignment slot, while
        // driverPersonId is the PDA login the chauffeur actually signs in with.
        tour.setDriverId(null);
        tour.setDriverPersonId(t.driverPersonId());
        tour.setType(t.type());
        tour.setRequestedQuantity(t.requestedQuantity());
        tour.setLoadedQuantity(null);
        tour.setDeliveredQuantity(null);
        tour.setStartedAt(null);
        tour.setClosedAt(null);
        tour.updateStatus(TourneeStatus.PLANNED.name(), Lifecycle.labelOf(TourneeStatus.PLANNED));
        tour.setCreatedBy("SYSTEM_INIT");

        Tour saved = tourRepository.save(tour);

        Instant now = Instant.now();
        int sequence = 1;
        for (String destination : t.destinationSiteCodes()) {
            addCheckpoint(saved.getId(), destination,
                    sequence, now.plus(sequence * 2L, ChronoUnit.HOURS));
            sequence++;
        }

        log.info("Tournée créée : code={}, marketeur={}, véhicule={}, chauffeur={}, {} arrêt(s)",
                saved.getTourCode(), t.marketer(), t.vehicleId(), t.driverPersonId(),
                t.destinationSiteCodes().size());
        return 1;
    }

    /**
     * One client stop. Keystones on {@code (tourId, sequence)} so a restart
     * cannot violate the UNIQUE constraint on that pair.
     */
    private void addCheckpoint(String tourId, String destinationSiteCode,
                               int sequence, Instant expectedArrival) {
        if (checkpointRepository.findByTourIdAndSequence(tourId, sequence).isPresent()) {
            return;
        }

        Checkpoint cp = Checkpoint.builder()
                .tourId(tourId)
                .clientSiteId(destinationSiteCode)
                .siteId(null)
                .sequence(sequence)
                .expectedArrival(expectedArrival)
                .build();
        cp.updateStatus(CheckpointStatus.PENDING.name(), Lifecycle.labelOf(CheckpointStatus.PENDING));
        cp.setCreatedBy("SYSTEM_INIT");
        checkpointRepository.save(cp);
    }
}
