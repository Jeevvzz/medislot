package edu.alliance.medislot.dao;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import edu.alliance.medislot.dao.Phase45Dao.PrescriptionLine;
import edu.alliance.medislot.exception.ConflictException;

class Phase45DaoIntegrationTest {
    private static final long PATIENT = 7L;
    private static final long DOCTOR = 1L;
    private static final Phase45Dao dao = new Phase45Dao();

    @BeforeAll
    static void createIsolatedH2Database() throws Exception {
        Path database = Path.of("target", "phase45-tests", "medislot").toAbsolutePath().normalize();
        Files.createDirectories(database.getParent());
        String prefix = database.toString();
        for (String suffix : List.of(".mv.db", ".trace.db", ".lock.db")) {
            Files.deleteIfExists(Path.of(prefix + suffix));
        }
        System.setProperty("medislot.db", "h2");
        System.setProperty("medislot.h2.path", prefix);
        Database.init();
        SeedData.load();
        SeedData.loadPhase2Availability();
        SeedData.loadPhase3DemoHistory();
    }

    @Test
    void catalogCartCheckoutPrescriptionQueueAndPatientRecordsPersist() throws Exception {
        verifyConsultationBillingTransaction();
        List<Map<String, Object>> catalog = dao.catalog("para");
        assertFalse(catalog.isEmpty());
        assertEquals("Paracetamol 500mg", catalog.get(0).get("name"));

        dao.addToCart(PATIENT, 1L, 2);
        assertEquals(2, ((Number) dao.cart(PATIENT).get(0).get("quantity")).intValue());
        Map<String, Object> placed = dao.checkout(PATIENT);
        assertEquals(new BigDecimal("70.00"), placed.get("total"));
        assertEquals("PLACED", placed.get("status"));
        assertEquals(1, ((List<?>) placed.get("items")).size());
        assertEquals("PACKED", dao.setOrderStatus(((Number) placed.get("id")).longValue(), "PACKED").get("status"));
        assertThrows(IllegalArgumentException.class,
                () -> dao.setOrderStatus(((Number) placed.get("id")).longValue(), "PLACED"));
        Map<String, Object> cancelled = dao.setOrderStatus(((Number) placed.get("id")).longValue(), "CANCELLED");
        assertEquals("CANCELLED", cancelled.get("status"));
        assertEquals(20, ((Number) dao.catalog("paracetamol").get(0).get("stock")).intValue());
        assertThrows(IllegalArgumentException.class, () -> dao.addToCart(PATIENT, 1L, 100));

        long completedAppointment = completedAppointment();
        Map<String, Object> rx = dao.createPrescription(DOCTOR, completedAppointment, "Demo only",
                List.of(new PrescriptionLine(1L, null, "One tablet as directed", 1)));
        long prescriptionId = ((Number) rx.get("id")).longValue();
        Map<String, Object> patientPrescription = dao.prescription(prescriptionId, PATIENT, "PATIENT");
        assertEquals(1, ((List<?>) patientPrescription.get("items")).size());
        dao.addPrescriptionToCart(PATIENT, prescriptionId);
        assertEquals(1, dao.cart(PATIENT).size());
        assertThrows(java.util.NoSuchElementException.class,
            () -> dao.prescription(prescriptionId, PATIENT + 1, "PATIENT"));

        dao.generateReminders(PATIENT);
        assertFalse(dao.reminders(PATIENT).isEmpty());
        assertFalse(dao.timeline(PATIENT).isEmpty());

        long todayAppointment = insertTodayAppointment();
        Map<String, Object> token = dao.issueQueueToken(PATIENT, todayAppointment);
        assertTrue(String.valueOf(token.get("token")).startsWith("Q"));
        assertTrue(((Number) token.get("estimatedWaitMinutes")).intValue() >= 0);
        assertEquals(1, dao.queueForDoctor(DOCTOR).size());
        dao.updateQueue(DOCTOR, ((Number) token.get("id")).longValue(), "CALLED");
        assertTrue(dao.queueForDoctor(DOCTOR).isEmpty());
    }

    private void verifyConsultationBillingTransaction() throws Exception {
        long appointmentId = insertPastAppointment();
        Phase3Dao phase3 = new Phase3Dao();
        assertThrows(SecurityException.class,
                () -> phase3.completeVisit(DOCTOR + 1, appointmentId, "note", "diagnosis", "treatment"));
        Map<String, Object> bill = phase3.completeVisit(DOCTOR, appointmentId,
                "Synthetic integration-test note", "Synthetic test diagnosis", "Test-only plan");
        assertEquals("ISSUED", bill.get("billStatus"));
        assertEquals("INR", bill.get("currency"));
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("SELECT a.status,(SELECT COUNT(*) FROM appointment_slot_claims sc WHERE sc.appointment_id=a.id) claims FROM appointments a WHERE a.id=?")) {
            p.setLong(1, appointmentId);
            try (ResultSet r = p.executeQuery()) {
                assertTrue(r.next());
                assertEquals("COMPLETED", r.getString("status"));
                assertEquals(0, r.getInt("claims"));
            }
        }
        assertThrows(ConflictException.class,
                () -> phase3.completeVisit(DOCTOR, appointmentId, "duplicate", "duplicate", "duplicate"));
        assertTrue(phase3.patientHistory(PATIENT).stream()
                .anyMatch(row -> "Synthetic test diagnosis".equals(row.get("diagnosis"))));
    }

    private long insertPastAppointment() throws Exception {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("INSERT INTO appointments(patient_id,doctor_id,appointment_date,appointment_time,status) VALUES(?,?,?,?, 'BOOKED')", Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, PATIENT);
            p.setLong(2, DOCTOR);
            p.setDate(3, Date.valueOf(LocalDate.now().minusDays(2)));
            p.setTime(4, java.sql.Time.valueOf(LocalTime.of(10, 0)));
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                if (!r.next()) throw new AssertionError("Could not create test visit.");
                long id = r.getLong(1);
                try (PreparedStatement claim = c.prepareStatement("INSERT INTO appointment_slot_claims(doctor_id,appointment_date,appointment_time,appointment_id) VALUES(?,?,?,?)")) {
                    claim.setLong(1, DOCTOR);
                    claim.setDate(2, Date.valueOf(LocalDate.now().minusDays(2)));
                    claim.setTime(3, java.sql.Time.valueOf(LocalTime.of(10, 0)));
                    claim.setLong(4, id);
                    claim.executeUpdate();
                }
                return id;
            }
        }
    }

    private long completedAppointment() throws Exception {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("SELECT a.id FROM appointments a JOIN consultations x ON x.appointment_id=a.id WHERE a.patient_id=? AND a.doctor_id=? AND a.status='COMPLETED' ORDER BY a.id FETCH FIRST 1 ROW ONLY")) {
            p.setLong(1, PATIENT);
            p.setLong(2, DOCTOR);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new AssertionError("Seeded synthetic completed appointment missing.");
                return r.getLong(1);
            }
        }
    }

    private long insertTodayAppointment() throws Exception {
        try (Connection c = Database.getConnection();
             PreparedStatement p = c.prepareStatement("INSERT INTO appointments(patient_id,doctor_id,appointment_date,appointment_time,status) VALUES(?,?,?,?, 'BOOKED')", Statement.RETURN_GENERATED_KEYS)) {
            p.setLong(1, PATIENT);
            p.setLong(2, DOCTOR);
            p.setDate(3, Date.valueOf(LocalDate.now()));
            p.setTime(4, java.sql.Time.valueOf(LocalTime.of(23, 0)));
            p.executeUpdate();
            try (ResultSet r = p.getGeneratedKeys()) {
                if (!r.next()) throw new AssertionError("Could not create test appointment.");
                return r.getLong(1);
            }
        }
    }
}
