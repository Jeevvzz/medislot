package edu.alliance.medislot.dao;

import edu.alliance.medislot.util.PasswordUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

/** Inserts the synthetic demo dataset once; restarts never reset user changes. */
public final class SeedData {
    private static final String VERSION = "demo-v1";
    private static final String AVAILABILITY_VERSION = "availability-demo-v1";
    private static final String PHASE3_HISTORY_VERSION = "phase3-history-demo-v1";

    private SeedData() {}

    public static void load() throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (seedMarkerExists(connection, VERSION)) {
                    connection.commit();
                    return;
                }
                if (userCount(connection) != 0) {
                    throw new SQLException("Refusing demo seeding: users exist without the expected seed marker.");
                }
                seed(connection);
                insertSeedMarker(connection, VERSION);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Adds the synthetic weekday schedule only once; never overwrites doctor-managed windows. */
    public static void loadPhase2Availability() throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (seedMarkerExists(connection, AVAILABILITY_VERSION)) {
                    connection.commit();
                    return;
                }
                if (availabilityCount(connection) != 0) {
                    throw new SQLException("Refusing demo availability seeding: schedules exist without the expected seed marker.");
                }
                try (Statement doctors = connection.createStatement();
                     ResultSet result = doctors.executeQuery("SELECT user_id FROM doctor_profiles ORDER BY user_id");
                     PreparedStatement insert = connection.prepareStatement(
                             "INSERT INTO doctor_availability(doctor_id,weekday,start_time,end_time,slot_minutes,active) "
                                     + "VALUES(?,?,?,?,30,TRUE)")) {
                    while (result.next()) {
                        long doctorId = result.getLong(1);
                        for (int weekday = DayOfWeek.MONDAY.getValue(); weekday <= DayOfWeek.FRIDAY.getValue(); weekday++) {
                            insert.setLong(1, doctorId);
                            insert.setInt(2, weekday);
                            insert.setTime(3, Time.valueOf(LocalTime.of(9, 0)));
                            insert.setTime(4, Time.valueOf(LocalTime.of(17, 0)));
                            insert.addBatch();
                        }
                    }
                    insert.executeBatch();
                }
                insertSeedMarker(connection, AVAILABILITY_VERSION);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static boolean seedMarkerExists(Connection connection, String version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT version FROM medislot_seed_history WHERE version=?")) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private static void insertSeedMarker(Connection connection, String version) throws SQLException {
        try (PreparedStatement marker = connection.prepareStatement(
                "INSERT INTO medislot_seed_history(version) VALUES (?)")) {
            marker.setString(1, version);
            marker.executeUpdate();
        }
    }

    private static long userCount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM users")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static long availabilityCount(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM doctor_availability")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void seed(Connection connection) throws SQLException {
        String[][] doctors = {
                {"Aarav Mehta", "aarav.doctor@medislot.local", "Cardiology", "12", "900", "AM"},
                {"Diya Nair", "diya.doctor@medislot.local", "Dermatology", "8", "700", "DN"},
                {"Kabir Rao", "kabir.doctor@medislot.local", "Pediatrics", "10", "650", "KR"},
                {"Mira Shah", "mira.doctor@medislot.local", "Orthopedics", "15", "850", "MS"},
                {"Arjun Iyer", "arjun.doctor@medislot.local", "Neurology", "9", "800", "AI"},
                {"Anaya Das", "anaya.doctor@medislot.local", "General Medicine", "6", "500", "AD"}
        };
        long[] doctorIds = new long[doctors.length];
        for (int i = 0; i < doctors.length; i++) {
            String[] doctor = doctors[i];
            doctorIds[i] = insertUser(connection, doctor[0], doctor[1], "DOCTOR", "");
            try (PreparedStatement profile = connection.prepareStatement(
                    "INSERT INTO doctor_profiles(user_id,specialty,experience,fee,rating,avatar,bio,languages) "
                            + "VALUES(?,?,?,?,?,?,?,?)")) {
                profile.setLong(1, doctorIds[i]);
                profile.setString(2, doctor[2]);
                profile.setInt(3, Integer.parseInt(doctor[3]));
                profile.setBigDecimal(4, new BigDecimal(doctor[4]));
                profile.setBigDecimal(5, new BigDecimal("4." + (7 + i % 3)));
                profile.setString(6, doctor[5]);
                profile.setString(7, profileBio(doctor[2]));
                profile.setString(8, "English, Hindi");
                profile.executeUpdate();
            }
        }

        long[] patientIds = new long[10];
        for (int i = 0; i < patientIds.length; i++) {
            String phone = "+91 90000 000" + String.format("%02d", i);
            patientIds[i] = insertUser(connection, "Patient " + (i + 1),
                    "patient" + (i + 1) + "@medislot.local", "PATIENT", phone);
            try (PreparedStatement profile = connection.prepareStatement(
                    "INSERT INTO patient_profiles(user_id,date_of_birth) VALUES(?,?)")) {
                profile.setLong(1, patientIds[i]);
                profile.setDate(2, Date.valueOf(LocalDate.of(1990 + i, 1 + i % 12, 1 + i % 27)));
                profile.executeUpdate();
            }
        }
        insertUser(connection, "MediSlot Admin", "admin@medislot.local", "ADMIN", "");

        String[] medicines = {
                "Paracetamol 500mg", "Ibuprofen 200mg", "Cetirizine 10mg", "Amoxicillin 500mg", "Vitamin C",
                "Omeprazole 20mg", "Metformin 500mg", "Amlodipine 5mg", "Atorvastatin 10mg", "Azithromycin 250mg",
                "ORS Sachets", "Antacid Tablets", "Vitamin D3", "Cough Syrup", "Saline Spray", "Bandage Roll",
                "Antiseptic Cream", "Thermometer", "Calcium Tablets", "Iron Tablets", "Loratadine", "Pantoprazole",
                "Diclofenac Gel", "Eye Drops", "Multivitamin", "Glucose Strips", "Insulin Syringe", "Hand Sanitizer",
                "Surgical Masks", "Hot Water Bag"
        };
        for (int i = 0; i < medicines.length; i++) {
            try (PreparedStatement medicine = connection.prepareStatement(
                    "INSERT INTO medicines(name,category,price,stock) VALUES(?,?,?,?)")) {
                medicine.setString(1, medicines[i]);
                medicine.setString(2, i < 10 ? "Medicine" : i < 20 ? "Care" : "Wellness");
                medicine.setBigDecimal(3, new BigDecimal((35 + i * 8) + ".00"));
                medicine.setInt(4, 20 + i);
                medicine.executeUpdate();
            }
        }

        for (int i = 0; i < 4; i++) {
            try (PreparedStatement appointment = connection.prepareStatement(
                    "INSERT INTO appointments(patient_id,doctor_id,appointment_date,appointment_time,status) VALUES(?,?,?,?,?)")) {
                appointment.setLong(1, patientIds[i]);
                appointment.setLong(2, doctorIds[i]);
                appointment.setDate(3, Date.valueOf(LocalDate.now().plusDays(i + 1)));
                appointment.setTime(4, Time.valueOf(LocalTime.of(9 + i, 0)));
                appointment.setString(5, "BOOKED");
                appointment.executeUpdate();
            }
        }
    }

    private static String profileBio(String specialty) {
        return switch (specialty) {
            case "Cardiology" -> "Thoughtful heart care with time for every question.";
            case "Dermatology" -> "Practical skin-health guidance focused on your comfort.";
            case "Pediatrics" -> "Warm, family-centered care for growing minds and bodies.";
            case "Orthopedics" -> "Clear support for movement, recovery, and everyday strength.";
            case "Neurology" -> "Careful neurological assessment in a calm, attentive setting.";
            default -> "Patient-first general care with a focus on clear next steps.";
        };
    }

    private static long insertUser(Connection connection, String name, String email, String role, String phone)
            throws SQLException {
        String hash = PasswordUtil.hash("medislot123");
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO users(name,email,password_hash,role,phone) VALUES(?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, hash);
            statement.setString(4, role);
            statement.setString(5, phone);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No generated user ID was returned.");
                return keys.getLong(1);
            }
        }
    }

    /** Seeds one clearly synthetic historical visit only if the demo patient has no consultation history. */
    public static void loadPhase3DemoHistory() throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (seedMarkerExists(connection, PHASE3_HISTORY_VERSION)) {
                    connection.commit();
                    return;
                }
                long patientId = userIdByEmail(connection, "patient1@medislot.local");
                long doctorId = userIdByEmail(connection, "aarav.doctor@medislot.local");
                if (patientId > 0 && doctorId > 0 && !hasConsultationHistory(connection, patientId)) {
                    BigDecimal fee = doctorFee(connection, doctorId);
                    if (fee != null && fee.signum() >= 0) {
                        LocalDate firstCandidate = LocalDate.now(AvailabilityDao.ZONE).minusDays(7);
                        for (int offset = 0; offset < 60; offset++) {
                            LocalDate date = firstCandidate.minusDays(offset);
                            if (date.getDayOfWeek().getValue() > DayOfWeek.FRIDAY.getValue()) continue;
                            LocalTime time = LocalTime.of(10, 0);
                            if (doctorSlotOccupied(connection, doctorId, date, time)) continue;
                            long appointmentId = insertSampleAppointment(connection, patientId, doctorId, date, time);
                            long consultationId = insertSampleConsultation(connection, appointmentId, patientId, doctorId);
                            insertSampleBill(connection, consultationId, appointmentId, patientId, doctorId, fee);
                            break;
                        }
                    }
                }
                insertSeedMarker(connection, PHASE3_HISTORY_VERSION);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private static long userIdByEmail(Connection connection, String email) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM users WHERE email=?")) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getLong(1) : 0; }
        }
    }

    private static boolean hasConsultationHistory(Connection connection, long patientId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM consultations WHERE patient_id=? LIMIT 1")) {
            statement.setLong(1, patientId);
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private static BigDecimal doctorFee(Connection connection, long doctorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT fee FROM doctor_profiles WHERE user_id=?")) {
            statement.setLong(1, doctorId);
            try (ResultSet result = statement.executeQuery()) { return result.next() ? result.getBigDecimal(1) : null; }
        }
    }

    private static boolean doctorSlotOccupied(Connection connection, long doctorId, LocalDate date, LocalTime time)
            throws SQLException {
        String sql = "SELECT 1 FROM appointments WHERE doctor_id=? AND appointment_date=? AND appointment_time=? "
                + "AND status NOT IN ('CANCELLED','CANCELED')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, doctorId);
            statement.setDate(2, Date.valueOf(date));
            statement.setTime(3, Time.valueOf(time));
            try (ResultSet result = statement.executeQuery()) { return result.next(); }
        }
    }

    private static long insertSampleAppointment(Connection connection, long patientId, long doctorId,
                                                LocalDate date, LocalTime time) throws SQLException {
        String sql = "INSERT INTO appointments(patient_id,doctor_id,appointment_date,appointment_time,status,"
                + "duration_minutes,updated_at) VALUES(?,?,?,?,'COMPLETED',30,CURRENT_TIMESTAMP)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, patientId);
            statement.setLong(2, doctorId);
            statement.setDate(3, Date.valueOf(date));
            statement.setTime(4, Time.valueOf(time));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No sample appointment ID was returned.");
                return keys.getLong(1);
            }
        }
    }

    private static long insertSampleConsultation(Connection connection, long appointmentId, long patientId, long doctorId)
            throws SQLException {
        String sql = "INSERT INTO consultations(appointment_id,patient_id,doctor_id,note,diagnosis,treatment) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, appointmentId);
            statement.setLong(2, patientId);
            statement.setLong(3, doctorId);
            statement.setString(4, "Synthetic demonstration note only. Do not enter or infer real patient information.");
            statement.setString(5, "Sample wellness review (demo only)");
            statement.setString(6, "Sample follow-up guidance only; no real treatment is prescribed.");
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) throw new SQLException("No sample consultation ID was returned.");
                return keys.getLong(1);
            }
        }
    }

    private static void insertSampleBill(Connection connection, long consultationId, long appointmentId,
                                         long patientId, long doctorId, BigDecimal fee) throws SQLException {
        String sql = "INSERT INTO billing_records(consultation_id,appointment_id,patient_id,doctor_id,bill_number,"
                + "amount,currency,status) VALUES(?,?,?,?,?,?,'INR','ISSUED')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, consultationId);
            statement.setLong(2, appointmentId);
            statement.setLong(3, patientId);
            statement.setLong(4, doctorId);
            statement.setString(5, "MS-" + String.format(Locale.ROOT, "%08d", appointmentId));
            statement.setBigDecimal(6, fee);
            statement.executeUpdate();
        }
    }
}
