package edu.alliance.medislot.dao;

import edu.alliance.medislot.exception.ConflictException;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Locale;

/** JDBC persistence for Phase 3 clinical notes, demo invoices, and aggregate reports. */
public final class Phase3Dao {
    private static final String COMPLETION_CONFLICT = "This visit is no longer available for consultation.";

    /** Saves the clinical record, completes the appointment, releases its claim, and issues one bill atomically. */
    public Map<String, Object> completeVisit(long doctorId, long appointmentId, String note,
                                             String diagnosis, String treatment)
            throws SQLException, ConflictException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                Visit visit = lockVisit(c, appointmentId);
                if (visit == null) throw new NoSuchElementException("Appointment was not found.");
                if (visit.doctorId() != doctorId) throw new SecurityException("Only the assigned doctor may complete this visit.");
                if (!"BOOKED".equals(visit.status())) throw new ConflictException(COMPLETION_CONFLICT);
                if (LocalDateTime.of(visit.date(), visit.time()).isAfter(LocalDateTime.now(AvailabilityDao.ZONE))) {
                    throw new IllegalArgumentException("A future appointment cannot be completed yet.");
                }
                if (visit.fee() == null || visit.fee().signum() < 0) {
                    throw new IllegalArgumentException("A valid consultation fee is not configured for this doctor.");
                }

                long consultationId;
                String consultSql = "INSERT INTO consultations(appointment_id,patient_id,doctor_id,note,diagnosis,treatment) "
                        + "VALUES(?,?,?,?,?,?)";
                try (PreparedStatement insert = c.prepareStatement(consultSql, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, appointmentId);
                    insert.setLong(2, visit.patientId());
                    insert.setLong(3, visit.doctorId());
                    insert.setString(4, note);
                    insert.setString(5, diagnosis);
                    insert.setString(6, treatment);
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No consultation ID was returned.");
                        consultationId = keys.getLong(1);
                    }
                }

                long billId;
                String billNumber = billNumber(appointmentId);
                String billSql = "INSERT INTO billing_records(consultation_id,appointment_id,patient_id,doctor_id,"
                        + "bill_number,amount,currency,status) VALUES(?,?,?,?,?,?,'INR','ISSUED')";
                try (PreparedStatement insert = c.prepareStatement(billSql, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, consultationId);
                    insert.setLong(2, appointmentId);
                    insert.setLong(3, visit.patientId());
                    insert.setLong(4, visit.doctorId());
                    insert.setString(5, billNumber);
                    insert.setBigDecimal(6, visit.fee());
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No invoice ID was returned.");
                        billId = keys.getLong(1);
                    }
                }

                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE appointments SET status='COMPLETED',updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='BOOKED'")) {
                    update.setLong(1, appointmentId);
                    if (update.executeUpdate() != 1) throw new ConflictException(COMPLETION_CONFLICT);
                }
                try (PreparedStatement release = c.prepareStatement(
                        "DELETE FROM appointment_slot_claims WHERE appointment_id=?")) {
                    release.setLong(1, appointmentId);
                    release.executeUpdate();
                }

                Map<String, Object> result = billById(c, billId);
                result.put("consultationId", consultationId);
                result.put("appointmentId", appointmentId);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException | ConflictException e) {
                c.rollback();
                if (e instanceof SQLException sql && isDuplicate(sql)) {
                    throw new ConflictException("A consultation has already been recorded for this visit.");
                }
                throw e;
            }
        }
    }

    /** Patient-only history; the SQL predicate binds every returned clinical row to the signed-in patient. */
    public List<Map<String, Object>> patientHistory(long patientId) throws SQLException {
        String sql = "SELECT c.id AS consultation_id,c.appointment_id,c.note,c.diagnosis,c.treatment,c.completed_at,"
                + "a.appointment_date,a.appointment_time,b.id AS bill_id,b.bill_number,b.amount,b.currency,b.status AS bill_status,"
                + "b.issued_at,p.name AS patient_name,d.name AS doctor_name,dp.specialty "
                + "FROM consultations c JOIN appointments a ON a.id=c.appointment_id "
                + "JOIN billing_records b ON b.consultation_id=c.id JOIN users p ON p.id=c.patient_id "
                + "JOIN users d ON d.id=c.doctor_id LEFT JOIN doctor_profiles dp ON dp.user_id=d.id "
                + "WHERE c.patient_id=? ORDER BY c.completed_at DESC,c.id DESC";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, patientId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("consultationId", r.getLong("consultation_id"));
                    row.put("appointmentId", r.getLong("appointment_id"));
                    row.put("appointmentDate", r.getDate("appointment_date").toLocalDate().toString());
                    row.put("appointmentTime", r.getTime("appointment_time").toLocalTime().toString());
                    row.put("completedAt", r.getTimestamp("completed_at").toLocalDateTime().toString());
                    row.put("doctorName", r.getString("doctor_name"));
                    row.put("specialty", r.getString("specialty"));
                    row.put("note", r.getString("note"));
                    row.put("diagnosis", r.getString("diagnosis"));
                    row.put("treatment", r.getString("treatment"));
                    row.put("billId", r.getLong("bill_id"));
                    row.put("billNumber", r.getString("bill_number"));
                    row.put("amount", r.getBigDecimal("amount"));
                    row.put("currency", r.getString("currency"));
                    row.put("billStatus", r.getString("bill_status"));
                    row.put("issuedAt", r.getTimestamp("issued_at").toLocalDateTime().toString());
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    /** Returns bill data only when the actor owns the patient account, is the assigned doctor, or is ADMIN. */
    public Map<String, Object> authorizedBill(long actorId, String role, long billId) throws SQLException {
        String sql = billSelect() + " WHERE b.id=? AND ((?='ADMIN') OR (?='PATIENT' AND b.patient_id=?) "
                + "OR (?='DOCTOR' AND b.doctor_id=?))";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, billId);
            p.setString(2, role);
            p.setString(3, role);
            p.setLong(4, actorId);
            p.setString(5, role);
            p.setLong(6, actorId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new NoSuchElementException("Bill was not found.");
                return billView(r);
            }
        }
    }

    /** Aggregate report contains counts and amounts only, never identifiers or clinical text. */
    public Map<String, Object> reports(LocalDate from, LocalDate to) throws SQLException {
        String summarySql = "SELECT COUNT(*) AS appointment_count,"
                + "COALESCE(SUM(CASE WHEN c.id IS NULL THEN 0 ELSE 1 END),0) AS consultation_count,"
                + "COALESCE(SUM(CASE WHEN b.id IS NULL THEN 0 ELSE 1 END),0) AS invoice_count,"
                + "COALESCE(SUM(b.amount),0.00) AS invoice_amount "
                + "FROM appointments a LEFT JOIN consultations c ON c.appointment_id=a.id "
                + "LEFT JOIN billing_records b ON b.consultation_id=c.id WHERE a.appointment_date BETWEEN ? AND ?";
        Map<String, Object> summary = new LinkedHashMap<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(summarySql)) {
            p.setDate(1, Date.valueOf(from));
            p.setDate(2, Date.valueOf(to));
            try (ResultSet r = p.executeQuery()) {
                r.next();
                summary.put("appointments", r.getLong("appointment_count"));
                summary.put("completedConsultations", r.getLong("consultation_count"));
                summary.put("issuedInvoices", r.getLong("invoice_count"));
                summary.put("issuedAmount", r.getBigDecimal("invoice_amount"));
            }
        }

        String specialtySql = "SELECT COALESCE(dp.specialty,'Unspecified') AS specialty,COUNT(*) AS appointment_count,"
                + "COALESCE(SUM(CASE WHEN c.id IS NULL THEN 0 ELSE 1 END),0) AS consultation_count,"
                + "COALESCE(SUM(CASE WHEN b.id IS NULL THEN 0 ELSE 1 END),0) AS invoice_count,"
                + "COALESCE(SUM(b.amount),0.00) AS invoice_amount "
                + "FROM appointments a JOIN doctor_profiles dp ON dp.user_id=a.doctor_id "
                + "LEFT JOIN consultations c ON c.appointment_id=a.id LEFT JOIN billing_records b ON b.consultation_id=c.id "
                + "WHERE a.appointment_date BETWEEN ? AND ? GROUP BY dp.specialty ORDER BY dp.specialty";
        List<Map<String, Object>> bySpecialty = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(specialtySql)) {
            p.setDate(1, Date.valueOf(from));
            p.setDate(2, Date.valueOf(to));
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("specialty", r.getString("specialty"));
                    row.put("appointments", r.getLong("appointment_count"));
                    row.put("completedConsultations", r.getLong("consultation_count"));
                    row.put("issuedInvoices", r.getLong("invoice_count"));
                    row.put("issuedAmount", r.getBigDecimal("invoice_amount"));
                    bySpecialty.add(row);
                }
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("from", from.toString());
        response.put("to", to.toString());
        response.put("summary", summary);
        response.put("bySpecialty", bySpecialty);
        return response;
    }

    private Visit lockVisit(Connection c, long appointmentId) throws SQLException {
        String sql = "SELECT a.id,a.patient_id,a.doctor_id,a.appointment_date,a.appointment_time,a.status,dp.fee "
                + "FROM appointments a JOIN doctor_profiles dp ON dp.user_id=a.doctor_id WHERE a.id=? FOR UPDATE";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, appointmentId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return null;
                return new Visit(r.getLong("id"), r.getLong("patient_id"), r.getLong("doctor_id"),
                        r.getDate("appointment_date").toLocalDate(), r.getTime("appointment_time").toLocalTime(),
                        r.getString("status"), r.getBigDecimal("fee"));
            }
        }
    }

    private Map<String, Object> billById(Connection c, long billId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(billSelect() + " WHERE b.id=?")) {
            p.setLong(1, billId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new SQLException("New invoice could not be read after insertion.");
                return billView(r);
            }
        }
    }

    private String billSelect() {
        return "SELECT b.id AS bill_id,b.bill_number,b.amount,b.currency,b.status AS bill_status,b.issued_at,"
                + "b.patient_id,b.doctor_id,a.id AS appointment_id,a.appointment_date,a.appointment_time,"
                + "p.name AS patient_name,d.name AS doctor_name,dp.specialty "
                + "FROM billing_records b JOIN appointments a ON a.id=b.appointment_id "
                + "JOIN users p ON p.id=b.patient_id JOIN users d ON d.id=b.doctor_id "
                + "LEFT JOIN doctor_profiles dp ON dp.user_id=d.id";
    }

    private Map<String, Object> billView(ResultSet r) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("billId", r.getLong("bill_id"));
        row.put("billNumber", r.getString("bill_number"));
        row.put("amount", r.getBigDecimal("amount"));
        row.put("currency", r.getString("currency"));
        row.put("billStatus", r.getString("bill_status"));
        row.put("issuedAt", r.getTimestamp("issued_at").toLocalDateTime().toString());
        row.put("patientId", r.getLong("patient_id"));
        row.put("doctorId", r.getLong("doctor_id"));
        row.put("appointmentId", r.getLong("appointment_id"));
        row.put("appointmentDate", r.getDate("appointment_date").toLocalDate().toString());
        row.put("appointmentTime", r.getTime("appointment_time").toLocalTime().toString());
        row.put("patientName", r.getString("patient_name"));
        row.put("doctorName", r.getString("doctor_name"));
        row.put("specialty", r.getString("specialty"));
        return row;
    }

    private String billNumber(long appointmentId) {
        return "MS-" + String.format(Locale.ROOT, "%08d", appointmentId);
    }

    private boolean isDuplicate(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if ("23505".equals(current.getSQLState()) || current.getErrorCode() == 1062) return true;
        }
        return false;
    }

    private record Visit(long id, long patientId, long doctorId, LocalDate date, LocalTime time,
                         String status, BigDecimal fee) {}
}
