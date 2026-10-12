package edu.alliance.medislot.dao;

import edu.alliance.medislot.exception.ConflictException;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/** JDBC reads and writes for Phase 2 doctor profiles, availability and favorites. */
public final class AvailabilityDao {
    public static final int SLOT_MINUTES = 30;
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    public Map<String, Object> doctorProfile(long doctorId) throws SQLException {
        String sql = "SELECT u.id,u.name,d.specialty,d.experience,d.fee,d.rating,d.avatar,d.bio,d.languages "
                + "FROM users u JOIN doctor_profiles d ON d.user_id=u.id WHERE u.id=? AND u.role='DOCTOR'";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, doctorId);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) throw new NoSuchElementException("Doctor profile was not found.");
                Map<String, Object> profile = new LinkedHashMap<>();
                profile.put("id", r.getLong("id"));
                profile.put("name", r.getString("name"));
                profile.put("specialty", r.getString("specialty"));
                profile.put("experience", r.getInt("experience"));
                profile.put("fee", r.getBigDecimal("fee"));
                profile.put("rating", r.getBigDecimal("rating"));
                profile.put("avatar", r.getString("avatar"));
                profile.put("bio", r.getString("bio"));
                profile.put("languages", r.getString("languages"));
                return profile;
            }
        }
    }

    public void updateDoctorProfile(long doctorId, String bio, String languages) throws SQLException {
        String sql = "UPDATE doctor_profiles SET bio=?,languages=? WHERE user_id=?";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, bio);
            p.setString(2, languages);
            p.setLong(3, doctorId);
            if (p.executeUpdate() != 1) throw new NoSuchElementException("Doctor profile was not found.");
        }
    }

    public List<Map<String, Object>> weeklyAvailability(long doctorId) throws SQLException {
        String sql = "SELECT weekday,start_time,end_time,slot_minutes,active FROM doctor_availability "
                + "WHERE doctor_id=? ORDER BY weekday";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, doctorId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    int weekday = r.getInt("weekday");
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("weekday", weekday);
                    row.put("day", DayOfWeek.of(weekday).getDisplayName(TextStyle.FULL, Locale.ENGLISH));
                    row.put("startTime", r.getTime("start_time").toLocalTime().toString());
                    row.put("endTime", r.getTime("end_time").toLocalTime().toString());
                    row.put("slotMinutes", r.getInt("slot_minutes"));
                    row.put("active", r.getBoolean("active"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    public void saveWeeklyWindow(long doctorId, int weekday, LocalTime start, LocalTime end)
            throws SQLException, ConflictException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement update = c.prepareStatement(
                        "UPDATE doctor_availability SET start_time=?,end_time=?,slot_minutes=?,active=TRUE "
                                + "WHERE doctor_id=? AND weekday=?")) {
                    update.setTime(1, Time.valueOf(start));
                    update.setTime(2, Time.valueOf(end));
                    update.setInt(3, SLOT_MINUTES);
                    update.setLong(4, doctorId);
                    update.setInt(5, weekday);
                    changed = update.executeUpdate();
                }
                if (changed == 0) {
                    try (PreparedStatement insert = c.prepareStatement(
                            "INSERT INTO doctor_availability(doctor_id,weekday,start_time,end_time,slot_minutes,active) "
                                    + "VALUES(?,?,?,?,?,TRUE)")) {
                        insert.setLong(1, doctorId);
                        insert.setInt(2, weekday);
                        insert.setTime(3, Time.valueOf(start));
                        insert.setTime(4, Time.valueOf(end));
                        insert.setInt(5, SLOT_MINUTES);
                        insert.executeUpdate();
                    }
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                if (isDuplicate(e)) throw new ConflictException("Availability for that weekday changed. Refresh and try again.");
                throw e;
            }
        }
    }

    public boolean removeWeeklyWindow(long doctorId, int weekday) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "DELETE FROM doctor_availability WHERE doctor_id=? AND weekday=?")) {
            p.setLong(1, doctorId);
            p.setInt(2, weekday);
            return p.executeUpdate() > 0;
        }
    }

    public List<String> availableSlots(long doctorId, LocalDate date) throws SQLException {
        int weekday = date.getDayOfWeek().getValue();
        LocalTime start;
        LocalTime end;
        String windowSql = "SELECT start_time,end_time,slot_minutes FROM doctor_availability "
                + "WHERE doctor_id=? AND weekday=? AND active=TRUE";
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(windowSql)) {
            p.setLong(1, doctorId);
            p.setInt(2, weekday);
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return List.of();
                start = r.getTime("start_time").toLocalTime();
                end = r.getTime("end_time").toLocalTime();
            }
            Set<LocalTime> occupied = occupiedTimes(c, doctorId, date);
            List<String> slots = new ArrayList<>();
            LocalTime now = LocalDateTime.now(ZONE).toLocalTime();
            LocalDate today = LocalDate.now(ZONE);
            for (LocalTime slot = start; !slot.plusMinutes(SLOT_MINUTES).isAfter(end); slot = slot.plusMinutes(SLOT_MINUTES)) {
                if ((date.isAfter(today) || (date.equals(today) && slot.isAfter(now))) && !occupied.contains(slot)) {
                    slots.add(slot.toString());
                }
            }
            return slots;
        }
    }

    public boolean slotInsideAvailability(Connection c, long doctorId, LocalDate date, LocalTime time, boolean lock)
            throws SQLException {
        String sql = "SELECT start_time,end_time,slot_minutes FROM doctor_availability "
                + "WHERE doctor_id=? AND weekday=? AND active=TRUE" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, doctorId);
            p.setInt(2, date.getDayOfWeek().getValue());
            try (ResultSet r = p.executeQuery()) {
                if (!r.next()) return false;
                LocalTime start = r.getTime("start_time").toLocalTime();
                LocalTime end = r.getTime("end_time").toLocalTime();
                int duration = r.getInt("slot_minutes");
                long offset = Duration.between(start, time).toMinutes();
                return duration == SLOT_MINUTES && offset >= 0 && offset % duration == 0
                        && !time.plusMinutes(duration).isAfter(end);
            }
        }
    }

    public boolean doctorExists(Connection c, long doctorId) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "SELECT 1 FROM users WHERE id=? AND role='DOCTOR'")) {
            p.setLong(1, doctorId);
            try (ResultSet r = p.executeQuery()) { return r.next(); }
        }
    }

    public List<Map<String, Object>> favorites(long patientId) throws SQLException {
        String sql = "SELECT u.id,u.name,d.specialty,d.experience,d.fee,d.rating,d.avatar,d.bio,d.languages "
                + "FROM favorite_doctors f JOIN users u ON u.id=f.doctor_id "
                + "JOIN doctor_profiles d ON d.user_id=u.id WHERE f.patient_id=? ORDER BY u.name";
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, patientId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", r.getLong("id"));
                    row.put("name", r.getString("name"));
                    row.put("specialty", r.getString("specialty"));
                    row.put("experience", r.getInt("experience"));
                    row.put("fee", r.getBigDecimal("fee"));
                    row.put("rating", r.getBigDecimal("rating"));
                    row.put("avatar", r.getString("avatar"));
                    row.put("bio", r.getString("bio"));
                    row.put("languages", r.getString("languages"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    public void addFavorite(long patientId, long doctorId) throws SQLException, ConflictException {
        try (Connection c = Database.getConnection()) {
            c.setAutoCommit(false);
            try {
                if (!doctorExists(c, doctorId)) throw new NoSuchElementException("Doctor profile was not found.");
                try (PreparedStatement p = c.prepareStatement(
                        "INSERT INTO favorite_doctors(patient_id,doctor_id) VALUES(?,?)")) {
                    p.setLong(1, patientId);
                    p.setLong(2, doctorId);
                    p.executeUpdate();
                } catch (SQLException e) {
                    if (!isDuplicate(e)) throw e;
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                if (e instanceof SQLException sql && isDuplicate(sql)) {
                    throw new ConflictException("That doctor is already in your favorites.");
                }
                throw e;
            }
        }
    }

    public boolean removeFavorite(long patientId, long doctorId) throws SQLException {
        try (Connection c = Database.getConnection(); PreparedStatement p = c.prepareStatement(
                "DELETE FROM favorite_doctors WHERE patient_id=? AND doctor_id=?")) {
            p.setLong(1, patientId);
            p.setLong(2, doctorId);
            return p.executeUpdate() > 0;
        }
    }

    private Set<LocalTime> occupiedTimes(Connection c, long doctorId, LocalDate date) throws SQLException {
        String sql = "SELECT appointment_time FROM appointments WHERE doctor_id=? AND appointment_date=? "
                + "AND status NOT IN ('CANCELLED','CANCELED')";
        Set<LocalTime> times = new HashSet<>();
        try (PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, doctorId);
            p.setDate(2, Date.valueOf(date));
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) times.add(r.getTime(1).toLocalTime());
            }
        }
        return times;
    }

    private boolean isDuplicate(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if ("23505".equals(current.getSQLState()) || current.getErrorCode() == 1062) return true;
        }
        return false;
    }
}
