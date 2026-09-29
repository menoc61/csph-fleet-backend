package com.gpl.fleet.config;

import com.gpl.fleet.model.Device;
import com.gpl.fleet.model.Vehicle;
import com.gpl.fleet.model.VehicleTelemetry;
import com.gpl.fleet.repository.DeviceRepository;
import com.gpl.fleet.repository.VehicleRepository;
import com.gpl.fleet.repository.VehicleTelemetryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Seed data for the fleet-device service (dev / test / local profiles only).
 *
 * <p>Creates the rolling stock the national map renders as truck pins:</p>
 * <ul>
 *   <li>one or two vehicles per market participant, with deterministic IDs
 *       (so tour seeders and the PDA can reference them:
 *       {@code VEH-MKT-GPL-001}, {@code VEH-MKT-SCTM-002},
 *       {@code VEH-TRP-TRANSLOG-001}…)</li>
 *   <li>one GPS tracker device per vehicle, positioned on the Douala / Yaoundé
 *       / Bafoussam / Garoua / Limbé corridors used by the seeded tours</li>
 *   <li>A 24h telemetry trail per vehicle (30-min steps) from its depot
 *       toward the first seeded checkpoint city, so
 *       {@code GET /telemetry/vehicles/{id}?start&end} and
 *       {@code .../latest} return drawable corridors</li>
 * </ul>
 *
 * <p>Coordinates mirror the organization-service site seeds
 * (Dépôt Principal Douala 4.0511,9.7679 — Bonabéri 4.0930,9.7400 —
 * Dépôt Principal Yaoundé 3.8612,11.5217 — Mvan 3.8280,11.5520).</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"dev", "test", "local"})
public class FleetDeviceDataInitializer implements CommandLineRunner {

    private final VehicleRepository vehicleRepository;
    private final DeviceRepository deviceRepository;
    private final VehicleTelemetryRepository telemetryRepository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Override
    @Transactional
    public void run(String... args) {
        log.info("===== Initialisation des données de test - Flotte & GPS =====");

        int created = 0;
        for (FleetVehicle f : FLEET) {
            created += seedVehicle(f);
        }

        log.info("===== Initialisation terminée : {} véhicule(s), {} device(s), {} point(s) de télémétrie "
                        + "({} création(s) ce démarrage) =====",
                vehicleRepository.count(), deviceRepository.count(), telemetryRepository.count(), created);
    }

    /**
     * The seeded rolling stock, keyed on a deterministic vehicle id.
     *
     * <p>The baseline {@code VEH-MKT-GPL-00x} / {@code VEH-TRP-ABC-00x} ids are
     * kept verbatim because the tour-service checkpoints reference them and
     * there is no cross-database foreign key to enforce it. Every licensed
     * marketer and transporter created by the organization-service seeder then
     * gets its own pair, so an org-scoped vehicle list is never empty.</p>
     */
    private record FleetVehicle(String vehicleId, String plate, String type, String orgCode,
                                Double maxVolume, Integer maxBottles, String city,
                                double lat, double lng, int battery) {
    }

    private static final List<FleetVehicle> FLEET = List.of(
            // Baseline market (referenced by TourDataInitializer).
            new FleetVehicle("VEH-MKT-GPL-001", "CE-1234-AB", "VRAC", "MKT-GPL", 20.0, null, "Douala", 4.0620, 9.7620, 87),
            new FleetVehicle("VEH-MKT-GPL-002", "CE-5678-CD", "BOUTEILLES50KG", "MKT-GPL", null, 200, "Yaoundé", 3.8510, 11.5300, 74),
            new FleetVehicle("VEH-MKT-GPL-003", "CE-2468-EF", "VRAC", "MKT-GPL", 20.0, null, "Yaoundé", 3.8450, 11.5380, 65),
            new FleetVehicle("VEH-TRP-ABC-001", "LT-9012-GH", "VRAC", "TRP-ABC", 20.0, null, "Douala", 4.0750, 9.7550, 92),
            new FleetVehicle("VEH-TRP-ABC-002", "LT-3456-IJ", "BOUTEILLES50KG", "TRP-ABC", null, 200, "Douala", 4.0480, 9.7020, 58),
            new FleetVehicle("VEH-TRP-ABC-003", "LT-7890-KL", "VRAC", "TRP-ABC", 20.0, null, "Douala", 4.0100, 9.6850, 81),
            // Licensed marketers.
            new FleetVehicle("VEH-MKT-SCTM-001", "CE-1101-SC", "VRAC", "MKT-SCTM", 20.0, null, "Douala", 4.0520, 9.7660, 91),
            new FleetVehicle("VEH-MKT-SCTM-002", "CE-1102-SC", "BOUTEILLES50KG", "MKT-SCTM", null, 200, "Yaoundé", 3.8600, 11.5200, 78),
            new FleetVehicle("VEH-MKT-TOTAL-001", "CE-1201-TE", "VRAC", "MKT-TOTAL", 20.0, null, "Douala", 4.0500, 9.7040, 88),
            new FleetVehicle("VEH-MKT-TOTAL-002", "CE-1202-TE", "BOUTEILLES50KG", "MKT-TOTAL", null, 200, "Yaoundé", 3.8710, 11.5110, 66),
            new FleetVehicle("VEH-MKT-AZA-001", "CE-1301-AZ", "VRAC", "MKT-AZA", 20.0, null, "Yaoundé", 3.8470, 11.5010, 83),
            new FleetVehicle("VEH-MKT-AZA-002", "CE-1302-AZ", "BOUTEILLES50KG", "MKT-AZA", null, 200, "Douala", 4.0610, 9.7490, 71),
            new FleetVehicle("VEH-MKT-CAMGAZ-001", "CE-1401-CG", "VRAC", "MKT-CAMGAZ", 20.0, null, "Douala", 4.0810, 9.7190, 95),
            new FleetVehicle("VEH-MKT-CAMGAZ-002", "CE-1402-CG", "BOUTEILLES50KG", "MKT-CAMGAZ", null, 200, "Bafoussam", 5.4790, 10.4180, 62),
            new FleetVehicle("VEH-MKT-TRADEX-001", "CE-1501-TX", "VRAC", "MKT-TRADEX", 20.0, null, "Douala", 4.0910, 9.7310, 89),
            new FleetVehicle("VEH-MKT-TRADEX-002", "CE-1502-TX", "BOUTEILLES50KG", "MKT-TRADEX", null, 200, "Garoua", 9.3020, 13.3930, 57),
            new FleetVehicle("VEH-MKT-NEPTUNE-001", "CE-1601-NP", "VRAC", "MKT-NEPTUNE", 20.0, null, "Douala", 4.0660, 9.7460, 84),
            new FleetVehicle("VEH-MKT-NEPTUNE-002", "CE-1602-NP", "BOUTEILLES50KG", "MKT-NEPTUNE", null, 200, "Limbé", 4.0190, 9.2140, 69),
            new FleetVehicle("VEH-MKT-STARGAS-001", "CE-1701-SG", "VRAC", "MKT-STARGAS", 20.0, null, "Yaoundé", 3.8560, 11.5110, 79),
            new FleetVehicle("VEH-MKT-STARGAS-002", "CE-1702-SG", "BOUTEILLES50KG", "MKT-STARGAS", null, 200, "Douala", 4.0560, 9.7010, 73),
            // Contracted transporters.
            new FleetVehicle("VEH-TRP-TRANSLOG-001", "LT-2101-TL", "VRAC", "TRP-TRANSLOG", 20.0, null, "Douala", 4.0010, 9.8010, 90),
            new FleetVehicle("VEH-TRP-EXPRESSGPL-001", "LT-2201-EG", "VRAC", "TRP-EXPRESSGPL", 20.0, null, "Douala", 4.0210, 9.6910, 76));

    /**
     * depot -> first-stop corridor per city, so the telemetry trail the map
     * draws ends where the first seeded checkpoint sits.
     */
    private static final java.util.Map<String, double[][]> CORRIDORS = java.util.Map.of(
            "Douala", new double[][]{{4.0511, 9.7679}, {4.0930, 9.7400}},
            "Yaoundé", new double[][]{{3.8612, 11.5217}, {3.8280, 11.5520}},
            "Bafoussam", new double[][]{{5.4781, 10.4176}, {5.4900, 10.4300}},
            "Garoua", new double[][]{{9.3017, 13.3921}, {9.3100, 13.4000}},
            "Limbé", new double[][]{{4.0186, 9.2136}, {4.0300, 9.2300}});

    /**
     * Creates the vehicle, its GPS tracker and a telemetry trail, skipping each
     * part that already exists. Idempotency is per row, not per table: the old
     * {@code count() > 0 -> return} guard meant a single leftover vehicle (from
     * a smoke test) permanently suppressed the entire fleet.
     */
    private int seedVehicle(FleetVehicle f) {
        int created = 0;

        if (vehicleRepository.existsById(f.vehicleId())) {
            // Still make sure the tracker exists — a vehicle can be created by
            // the API without one.
            if (!deviceRepository.existsBySerialNumber(trackerSerial(f.vehicleId()))) {
                createDevice(f);
                created++;
            }
            return created;
        }

        Vehicle vehicle = new Vehicle();
        vehicle.setId(f.vehicleId());
        vehicle.setLicensePlate(f.plate());
        vehicle.setType(f.type());
        // The organization CODE, matching organizations.code and the tour rows.
        vehicle.setOrganizationId(f.orgCode());
        vehicle.setMaxVolume(f.maxVolume());
        vehicle.setMaxBottleCount(f.maxBottles());
        vehicle.setCertificateNumber("CERT-" + f.plate());
        vehicle.setCertificateExpiryAt(Instant.now().plus(365, ChronoUnit.DAYS));
        vehicle.setTareWeight("VRAC".equals(f.type()) ? 8.5 : 6.0);
        vehicle.setActive(true);
        vehicle.setCreatedBy("SYSTEM_INIT");
        vehicleRepository.save(vehicle);

        createDevice(f);
        log.info("Véhicule créé : id={}, plaque={}, type={}, org={}",
                f.vehicleId(), f.plate(), f.type(), f.orgCode());
        return 1;
    }

    /** One GPS tracker per vehicle, plus its 24h trail. */
    private void createDevice(FleetVehicle f) {
        Device device = new Device();
        device.setSerialNumber(trackerSerial(f.vehicleId()));
        device.setDeviceType("GPS_TRACKER");
        device.setFirmwareVersion("1.4.2");
        device.setBatteryLevel(f.battery());
        device.setBatteryCritical(f.battery() < 20);
        device.setLastSync(Instant.now());
        device.setLastLatitude(f.lat());
        device.setLastLongitude(f.lng());
        device.setAssignedToVehicleId(f.vehicleId());
        device.setOrganizationId(f.orgCode());
        device.setCreatedBy("SYSTEM_INIT");
        deviceRepository.save(device);

        seedTelemetryTrail(f.vehicleId(), f.city(), f.battery());
        log.info("Tracker créé : serial={}, véhicule={}, org={}",
                device.getSerialNumber(), f.vehicleId(), f.orgCode());
    }

    /** {@code VEH-MKT-SCTM-001} -> {@code GPS-MKT-SCTM-001}. */
    private String trackerSerial(String vehicleId) {
        return "GPS-" + vehicleId.replaceFirst("^VEH-", "");
    }

    /**
     * 48 points over the last 24h interpolating depot -> first stop, so the map
     * can draw a corridor polyline and a live pin per vehicle. Skipped when the
     * vehicle already has a trail, so a restart does not append a second one.
     */
    private void seedTelemetryTrail(String vehicleId, String city, int batteryLevel) {
        if (telemetryRepository.findFirstByVehicleIdOrderByTimestampDesc(vehicleId).isPresent()) {
            return;
        }
        double[][] corridor = CORRIDORS.getOrDefault(city, CORRIDORS.get("Douala"));
        Instant now = Instant.now();
        for (int i = 0; i < 48; i++) {
            double t = i / 47.0;
            double lat = corridor[0][0] + t * (corridor[1][0] - corridor[0][0]);
            double lng = corridor[0][1] + t * (corridor[1][1] - corridor[0][1]);
            VehicleTelemetry point = new VehicleTelemetry(
                    vehicleId,
                    now.minus(24, ChronoUnit.HOURS).plus(i * 30L, ChronoUnit.MINUTES),
                    geometryFactory.createPoint(new Coordinate(lng, lat)),
                    t < 0.1 || t > 0.9 ? 0.0 : 45.0 + 20.0 * Math.sin(t * Math.PI),
                    t < 0.5 ? 320.0 : 140.0,
                    Math.min(100.0, batteryLevel + t * 5.0));
            telemetryRepository.save(point);
        }
    }
}
