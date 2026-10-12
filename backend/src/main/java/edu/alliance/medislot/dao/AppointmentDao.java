package edu.alliance.medislot.dao;

import edu.alliance.medislot.exception.ConflictException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Transactional persistence for Phase 2 appointments and time proposals. */
public final class AppointmentDao {
    private static final String SLOT_CONFLICT = "That time was just booked. Please choose another available time.";
    private final AvailabilityDao availability = new AvailabilityDao();

    public List<Map<String, Object>> appointments(long actorId, String role) throws SQLException {
        if (!"PATIENT".equals(role) && !"DOCTOR".equals(role)) {
            throw new SecurityException("Patient or doctor role required.");
        }
        String ownerColumn = "PATIENT".equals(role) ? "a.patient_id" : "a.doctor_id";
        String sql = "SELECT a.id,a.patient_id,a.doctor_id,a.appointment_date,a.appointment_time,a.status,"
                + "a.duration_minutes,a.updated_at,p.name AS patient_name,d.name AS doctor_name,dp.specialty "
                + "FROM appointments a JOIN users p ON p.id=a.patient_id JOIN users d ON d.id=a.doctor_id "
                + "LEFT JOIN doctor_profiles dp ON dp.user_id=d.id WHERE " + ownerColumn + "=? "
                + "ORDER BY a.appointment_date DESC,a.appointment_time DESC,a.id DESC";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, actorId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) rows.add(appointmentView(r));
            }
        }
        return rows;
    }

    public Map<String, Object> book(long patientId, long doctorId, LocalDate date, LocalTime time)
            throws SQLException, ConflictException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!availability.doctorExists(c, doctorId)) {
                    throw new NoSuchElementException("Doctor profile was not found.");
                }
                if (!availability.slotInsideAvailability(c, doctorId, date, time, true)) {
                    throw new IllegalArgumentException("That time is outside the doctor's listed availability.");
                }
                Long claimed = lockSlotClaim(c, doctorId, date, time);
                if (claimed != null || activeAppointmentAt(c, doctorId, date, time, 0)) {
                    throw new ConflictException(SLOT_CONFLICT);
                }
                long appointmentId;
                String insertSql = "INSERT INTO appointments(patient_id,doctor_id,appointment_date,appointment_time,"
                        + "status,duration_minutes,updated_at) VALUES(?,?,?,?, 'BOOKED',30,CURRENT_TIMESTAMP)";
                try (PreparedStatement insert = c.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, patientId);
                    insert.setLong(2, doctorId);
                    insert.setDate(3, Date.valueOf(date));
                    insert.setTime(4, Time.valueOf(time));
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No appointment ID was returned.");
                        appointmentId = keys.getLong(1);
                    }
                }
                assignSlotClaim(c, doctorId, date, time, appointmentId);
                Map<String, Object> result = appointmentInTransaction(c, appointmentId);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException | ConflictException e) {
                c.rollback();
                if (e instanceof SQLException sql && isDuplicate(sql)) throw new ConflictException(SLOT_CONFLICT);
                throw e;
            }
        }
    }

    public Map<String, Object> reschedule(long patientId, long appointmentId, LocalDate date, LocalTime time)
            throws SQLException, ConflictException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                Appointment current = lockAppointment(c, appointmentId);
                requirePatientAppointment(current, patientId);
                requireActiveFuture(current);
                if (current.date().equals(date) && current.time().equals(time)) {
                    throw new IllegalArgumentException("Choose a different available time.");
                }
                moveAppointment(c, current, date, time);
                Map<String, Object> result = appointmentInTransaction(c, appointmentId);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException | ConflictException e) {
                c.rollback();
                if (e instanceof SQLException sql && isDuplicate(sql)) throw new ConflictException(SLOT_CONFLICT);
                throw e;
            }
        }
    }

    public Map<String, Object> cancel(long patientId, long appointmentId) throws SQLException, ConflictException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                Appointment current = lockAppointment(c, appointmentId);
                requirePatientAppointment(current, patientId);
                requireActiveFuture(current);
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE appointments SET status='CANCELLED',updated_at=CURRENT_TIMESTAMP WHERE id=?")) {
                    update.setLong(1, appointmentId);
                    update.executeUpdate();
                }
                releaseSlotClaim(c, appointmentId);
                Map<String, Object> result = appointmentInTransaction(c, appointmentId);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException | ConflictException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public Map<String, Object> createTimeRequest(long actorId, String role, long appointmentId,
                                                  LocalDate date, LocalTime time)
            throws SQLException, ConflictException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                Appointment appointment = lockAppointment(c, appointmentId);
                requireParticipant(appointment, actorId, role);
                requireActiveFuture(appointment);
                if (appointment.date().equals(date) && appointment.time().equals(time)) {
                    throw new IllegalArgumentException("Choose a different available time.");
                }
                if (!availability.slotInsideAvailability(c, appointment.doctorId(), date, time, true)) {
                    throw new IllegalArgumentException("That time is outside the doctor's listed availability.");
                }
                Long claimed = lockSlotClaim(c, appointment.doctorId(), date, time);
                if (claimed != null || activeAppointmentAt(c, appointment.doctorId(), date, time, appointmentId)) {
                    throw new ConflictException(SLOT_CONFLICT);
                }
                try (PreparedStatement check = c.prepareStatement(
                        "SELECT id FROM appointment_time_requests WHERE appointment_id=? AND status='PENDING'")) {
                    check.setLong(1, appointmentId);
                    try (ResultSet r = check.executeQuery()) {
                        if (r.next()) throw new ConflictException("A time request is already pending for this appointment.");
                    }
                }
                long requestId;
                String sql = "INSERT INTO appointment_time_requests(appointment_id,requested_by_user_id,"
                        + "requested_by_role,proposed_date,proposed_time,status) VALUES(?,?,?,?,?,'PENDING')";
                try (PreparedStatement insert = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, appointmentId);
                    insert.setLong(2, actorId);
                    insert.setString(3, role);
                    insert.setDate(4, Date.valueOf(date));
                    insert.setTime(5, Time.valueOf(time));
                    insert.executeUpdate();
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No time-request ID was returned.");
                        requestId = keys.getLong(1);
                    }
                }
                Map<String, Object> result = timeRequestInTransaction(c, requestId, actorId);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException | ConflictException e) {
                c.rollback();
                if (e instanceof SQLException sql && isDuplicate(sql)) throw new ConflictException(SLOT_CONFLICT);
                throw e;
            }
        }
    }

    public List<Map<String, Object>> timeRequests(long actorId, String role) throws SQLException {
        if (!"PATIENT".equals(role) && !"DOCTOR".equals(role)) {
            throw new SecurityException("Patient or doctor role required.");
        }
        String ownColumn = "PATIENT".equals(role) ? "a.patient_id" : "a.doctor_id";
        String sql = "SELECT tr.id,tr.appointment_id,tr.requested_by_user_id,tr.requested_by_role,"
                + "tr.proposed_date,tr.proposed_time,tr.status,tr.created_at,p.name AS patient_name,"
                + "d.name AS doctor_name,a.appointment_date,a.appointment_time "
                + "FROM appointment_time_requests tr JOIN appointments a ON a.id=tr.appointment_id "
                + "JOIN users p ON p.id=a.patient_id JOIN users d ON d.id=a.doctor_id WHERE " + ownColumn + "=? "
                + "ORDER BY tr.created_at DESC,tr.id DESC";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, actorId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", r.getLong("id"));
                    item.put("appointmentId", r.getLong("appointment_id"));
                    item.put("requestedByUserId", r.getLong("requested_by_user_id"));
                    item.put("requestedByRole", r.getString("requested_by_role"));
                    item.put("proposedDate", r.getDate("proposed_date").toLocalDate().toString());
                    item.put("proposedTime", r.getTime("proposed_time").toLocalTime().toString());
                    item.put("status", r.getString("status"));
                    item.put("patientName", r.getString("patient_name"));
                    item.put("doctorName", r.getString("doctor_name"));
                    item.put("appointmentDate", r.getDate("appointment_date").toLocalDate().toString());
                    item.put("appointmentTime", r.getTime("appointment_time").toLocalTime().toString());
                    item.put("canRespond", "PENDING".equals(r.getString("status")) && r.getLong("requested_by_user_id") != actorId);
                    rows.add(item);
                }
            }
        }
        return rows;
    }

    public Map<String, Object> decideTimeRequest(long actorId, String role, long requestId, boolean accept)
            throws SQLException, ConflictException {
        boolean reportConflictAfterCommit = false;
        Map<String, Object> result = null;
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                long appointmentId = findRequestAppointmentId(c, requestId);
                Appointment appointment = lockAppointment(c, appointmentId);
                TimeRequest request = lockTimeRequest(c, requestId);
                if (request.appointmentId() != appointmentId) throw new SQLException("Time-request relation changed.");
                requireParticipant(appointment, actorId, role);
                if (request.requestedByUserId() == actorId) {
                    throw new SecurityException("Only the other participant can respond to this time request.");
                }
                if (!"PENDING".equals(request.status())) throw new ConflictException("This time request is no longer pending.");
                if (!accept) {
                    updateRequestStatus(c, requestId, "DECLINED");
                    result = timeRequestInTransaction(c, requestId, actorId);
                    c.commit();
                    return result;
                }
                requireActiveFuture(appointment);
                Savepoint beforeMove = c.setSavepoint();
                try {
                    moveAppointment(c, appointment, request.date(), request.time());
                    updateRequestStatus(c, requestId, "ACCEPTED");
                    result = timeRequestInTransaction(c, requestId, actorId);
                } catch (ConflictException conflict) {
                    c.rollback(beforeMove);
                    updateRequestStatus(c, requestId, "CONFLICT");
                    result = null;
                    reportConflictAfterCommit = true;
                }
                c.commit();
            } catch (SQLException | RuntimeException | ConflictException e) {
                c.rollback();
                if (e instanceof SQLException sql && isDuplicate(sql)) throw new ConflictException(SLOT_CONFLICT);
                throw e;
            }
        }
        if (reportConflictAfterCommit) throw new ConflictException(SLOT_CONFLICT);
        return result;
    }

    private void moveAppointment(Connection c, Appointment current, LocalDate date, LocalTime time)
            throws SQLException, ConflictException {
        if (!availability.slotInsideAvailability(c, current.doctorId(), date, time, true)) {
            throw new ConflictException(SLOT_CONFLICT);
        }
        Long claimed = lockSlotClaim(c, current.doctorId(), date, time);
        if ((claimed != null && claimed != current.id())
                || activeAppointmentAt(c, current.doctorId(), date, time, current.id())) {
            throw new ConflictException(SLOT_CONFLICT);
        }
        releaseSlotClaim(c, current.id());
        if (claimed == null) assignSlotClaim(c, current.doctorId(), date, time, current.id());
        else if (claimed != current.id()) assignSlotClaim(c, current.doctorId(), date, time, current.id());
        try (PreparedStatement update = c.prepareStatement(
                "UPDATE appointments SET appointment_date=?,appointment_time=?,duration_minutes=30,"
                        + "updated_at=CURRENT_TIMESTAMP WHERE id=? AND status='BOOKED'")) {
            update.setDate(1, Date.valueOf(date));
            update.setTime(2, Time.valueOf(time));
            update.setLong(3, current.id());
            if (update.executeUpdate() != 1) throw new ConflictException("This appointment is no longer active.");
        }
    }

    private Long lockSlotClaim(Connection c, long doctorId, LocalDate date, LocalTime time) throws SQLException {
        try (PreparedStatement insert = c.prepareStatement(
                "INSERT INTO appointment_slot_claims(doctor_id,appointment_date,appointment_time,appointment_id) "
                        + "VALUES(?,?,?,NULL)")) {
            insert.setLong(1, doctorId);
            insert.setDate(2, Date.valueOf(date));
            insert.setTime(3, Time.valueOf(time));
            try {
                insert.executeUpdate();
            } catch (SQLException e) {
                if (!isDuplicate(e)) throw e;
            }
        }
        try (PreparedStatement select = c.prepareStatement(
                "SELECT appointment_id FROM appointment_slot_claims WHERE doctor_id=? AND appointment_date=? "
                        + "AND appointment_time=? FOR UPDATE")) {
            select.setLong(1, doctorId);
            select.setDate(2, Date.valueOf(date));
            select.setTime(3, Time.valueOf(time));
            try (ResultSet r = select.executeQuery()) {
                if (!r.next()) throw new SQLException("Slot claim row could not be locked.");
                long id = r.getLong("appointment_id");
                return r.wasNull() ? null : id;
            }
        }
    }

    private void assignSlotClaim(Connection c, long doctorId, LocalDate date, LocalTime time, long appointmentId)
            throws SQLException, ConflictException {
        try (PreparedStatement update = c.prepareStatement(
                "UPDATE appointment_slot_claims SET appointment_id=? WHERE doctor_id=? AND appointment_date=? "
                        + "AND appointment_time=? AND appointment_id IS NULL")) {
            update.setLong(1, appointmentId);
            update.setLong(2, doctorId);
            update.setDate(3, Date.valueOf(date));
            update.setTime(4, Time.valueOf(time));
            if (update.executeUpdate() != 1) throw new ConflictException(SLOT_CONFLICT);
        }
    }

    private boolean activeAppointmentAt(Connection c, long doctorId, LocalDate date, LocalTime time,
                                        long exceptAppointmentId) throws SQLException {
        String sql = "SELECT id FROM appointments WHERE doctor_id=? AND appointment_date=? AND appointment_time=? "
                + "AND status NOT IN ('CANCELLED','CANCELED') AND id<>? FOR UPDATE";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, doctorId);
            p.setDate(2, Date.valueOf(date));
            p.setTime(3, Time.valueOf(time));
            p.setLong(4, exceptAppointmentId);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }

    private void releaseSlotClaim(Connection c, long appointmentId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "UPDATE appointment_slot_claims SET appointment_id=NULL WHERE appointment_id=?")) {
            p.setLong(1, appointmentId);
            p.executeUpdate();
        }
    }

    private Appointment lockAppointment(Connection c, long id) throws SQLException {
        String sql = "SELECT id,patient_id,doctor_id,appointment_date,appointment_time,status "
                + "FROM appointments WHERE id=? FOR UPDATE";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new NoSuchElementException("Appointment was not found.");
                return new Appointment(r.getLong("id"), r.getLong("patient_id"), r.getLong("doctor_id"),
                        r.getDate("appointment_date").toLocalDate(), r.getTime("appointment_time").toLocalTime(),
                        r.getString("status"));
            }
        }
    }

    private void requirePatientAppointment(Appointment appointment, long patientId) {
        if (appointment.patientId() != patientId) throw new SecurityException("You can only change your own appointments.");
    }

    private void requireParticipant(Appointment appointment, long actorId, String role) {
        boolean patient = "PATIENT".equals(role) && appointment.patientId() == actorId;
        boolean doctor = "DOCTOR".equals(role) && appointment.doctorId() == actorId;
        if (!patient && !doctor) throw new SecurityException("Only the appointment patient or doctor may access it.");
    }

    private void requireActiveFuture(Appointment appointment) throws ConflictException {
        if (!"BOOKED".equals(appointment.status())) throw new ConflictException("This appointment is no longer active.");
        LocalDateTime start = appointment.date().atTime(appointment.time());
        if (!start.isAfter(LocalDateTime.now(AvailabilityDao.ZONE))) {
            throw new IllegalArgumentException("Only future appointments can be changed.");
        }
    }

    private long findRequestAppointmentId(Connection c, long requestId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT appointment_id FROM appointment_time_requests WHERE id=?")) {
            p.setLong(1, requestId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new NoSuchElementException("Time request was not found.");
                return r.getLong(1);
            }
        }
    }

    private TimeRequest lockTimeRequest(Connection c, long requestId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT id,appointment_id,requested_by_user_id,proposed_date,proposed_time,status "
                        + "FROM appointment_time_requests WHERE id=? FOR UPDATE")) {
            p.setLong(1, requestId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new NoSuchElementException("Time request was not found.");
                return new TimeRequest(r.getLong("id"), r.getLong("appointment_id"),
                        r.getLong("requested_by_user_id"), r.getDate("proposed_date").toLocalDate(),
                        r.getTime("proposed_time").toLocalTime(), r.getString("status"));
            }
        }
    }

    private void updateRequestStatus(Connection c, long requestId, String status) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "UPDATE appointment_time_requests SET status=?,decided_at=CURRENT_TIMESTAMP WHERE id=?")) {
            p.setString(1, status);
            p.setLong(2, requestId);
            p.executeUpdate();
        }
    }

    private Map<String, Object> appointmentInTransaction(Connection c, long id) throws SQLException {
        String sql = "SELECT a.id,a.patient_id,a.doctor_id,a.appointment_date,a.appointment_time,a.status,"
                + "a.duration_minutes,a.updated_at,p.name AS patient_name,d.name AS doctor_name,dp.specialty "
                + "FROM appointments a JOIN users p ON p.id=a.patient_id JOIN users d ON d.id=a.doctor_id "
                + "LEFT JOIN doctor_profiles dp ON dp.user_id=d.id WHERE a.id=?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new NoSuchElementException("Appointment was not found.");
                return appointmentView(r);
            }
        }
    }

    private Map<String, Object> timeRequestInTransaction(Connection c, long id, long actorId) throws SQLException {
        String sql = "SELECT tr.id,tr.appointment_id,tr.requested_by_user_id,tr.requested_by_role,"
                + "tr.proposed_date,tr.proposed_time,tr.status,p.name AS patient_name,d.name AS doctor_name,"
                + "a.appointment_date,a.appointment_time FROM appointment_time_requests tr "
                + "JOIN appointments a ON a.id=tr.appointment_id JOIN users p ON p.id=a.patient_id "
                + "JOIN users d ON d.id=a.doctor_id WHERE tr.id=?";
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, id);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new NoSuchElementException("Time request was not found.");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", r.getLong("id"));
                item.put("appointmentId", r.getLong("appointment_id"));
                item.put("requestedByUserId", r.getLong("requested_by_user_id"));
                item.put("requestedByRole", r.getString("requested_by_role"));
                item.put("proposedDate", r.getDate("proposed_date").toLocalDate().toString());
                item.put("proposedTime", r.getTime("proposed_time").toLocalTime().toString());
                item.put("status", r.getString("status"));
                item.put("patientName", r.getString("patient_name"));
                item.put("doctorName", r.getString("doctor_name"));
                item.put("appointmentDate", r.getDate("appointment_date").toLocalDate().toString());
                item.put("appointmentTime", r.getTime("appointment_time").toLocalTime().toString());
                item.put("canRespond", "PENDING".equals(r.getString("status")) && r.getLong("requested_by_user_id") != actorId);
                return item;
            }
        }
    }

    private Map<String, Object> appointmentView(ResultSet r) throws SQLException {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", r.getLong("id"));
        item.put("patientId", r.getLong("patient_id"));
        item.put("doctorId", r.getLong("doctor_id"));
        item.put("patientName", r.getString("patient_name"));
        item.put("doctorName", r.getString("doctor_name"));
        item.put("specialty", r.getString("specialty"));
        item.put("date", r.getDate("appointment_date").toLocalDate().toString());
        item.put("time", r.getTime("appointment_time").toLocalTime().toString());
        item.put("status", r.getString("status"));
        item.put("durationMinutes", r.getInt("duration_minutes"));
        return item;
    }

    private boolean isDuplicate(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if ("23505".equals(current.getSQLState()) || current.getErrorCode() == 1062) return true;
        }
        return false;
    }

    private record Appointment(long id, long patientId, long doctorId, LocalDate date, LocalTime time, String status) {}
    private record TimeRequest(long id, long appointmentId, long requestedByUserId,
                               LocalDate date, LocalTime time, String status) {}
}
