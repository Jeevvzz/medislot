package edu.alliance.medislot.service;

import edu.alliance.medislot.dao.AppointmentDao;
import edu.alliance.medislot.dao.AvailabilityDao;
import edu.alliance.medislot.exception.ConflictException;
import edu.alliance.medislot.exception.ValidationException;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Validates Phase 2 inputs before delegating to JDBC DAOs. */
public final class Phase2Service {
    private final AvailabilityDao availability = new AvailabilityDao();
    private final AppointmentDao appointments = new AppointmentDao();

    public Map<String, Object> doctorProfile(long doctorId) throws SQLException, ValidationException {
        requirePositive(doctorId, "Doctor");
        return availability.doctorProfile(doctorId);
    }

    public List<String> slots(long doctorId, String rawDate) throws SQLException, ValidationException {
        requirePositive(doctorId, "Doctor");
        LocalDate date = parseDate(rawDate);
        LocalDate today = LocalDate.now(AvailabilityDao.ZONE);
        if (date.isBefore(today) || date.isAfter(today.plusDays(120))) {
            throw new ValidationException("Choose a date within the next 120 days.");
        }
        return availability.availableSlots(doctorId, date);
    }

    public Map<String, Object> doctorSelfProfile(long doctorId) throws SQLException, ValidationException {
        return doctorProfile(doctorId);
    }

    public void updateDoctorProfile(long doctorId, String bio, String languages)
            throws SQLException, ValidationException {
        if (bio == null || bio.trim().length() > 600) throw new ValidationException("Profile summary must be 600 characters or fewer.");
        if (languages == null || languages.trim().length() > 200) throw new ValidationException("Languages must be 200 characters or fewer.");
        availability.updateDoctorProfile(doctorId, bio.trim(), languages.trim());
    }

    public List<Map<String, Object>> doctorAvailability(long doctorId) throws SQLException {
        return availability.weeklyAvailability(doctorId);
    }

    public void setAvailability(long doctorId, int weekday, String rawStart, String rawEnd)
            throws SQLException, ValidationException, ConflictException {
        if (weekday < 1 || weekday > 7) throw new ValidationException("Choose a weekday from 1 to 7.");
        LocalTime start = parseTime(rawStart);
        LocalTime end = parseTime(rawEnd);
        if (!isHalfHour(start) || !isHalfHour(end) || !end.isAfter(start)) {
            throw new ValidationException("Availability must use 30-minute boundaries and end after it starts.");
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < AvailabilityDao.SLOT_MINUTES || minutes > 12 * 60 || minutes % AvailabilityDao.SLOT_MINUTES != 0) {
            throw new ValidationException("Availability must contain whole 30-minute slots and be no longer than 12 hours.");
        }
        availability.saveWeeklyWindow(doctorId, weekday, start, end);
    }

    public void removeAvailability(long doctorId, int weekday) throws SQLException, ValidationException {
        if (weekday < 1 || weekday > 7) throw new ValidationException("Choose a weekday from 1 to 7.");
        if (!availability.removeWeeklyWindow(doctorId, weekday)) throw new NoSuchElementException("No availability is set for that weekday.");
    }

    public List<Map<String, Object>> favorites(long patientId) throws SQLException {
        return availability.favorites(patientId);
    }

    public void addFavorite(long patientId, long doctorId) throws SQLException, ValidationException, ConflictException {
        requirePositive(doctorId, "Doctor");
        availability.addFavorite(patientId, doctorId);
    }

    public void removeFavorite(long patientId, long doctorId) throws SQLException, ValidationException {
        requirePositive(doctorId, "Doctor");
        availability.removeFavorite(patientId, doctorId);
    }

    public List<Map<String, Object>> appointments(long actorId, String role) throws SQLException {
        return appointments.appointments(actorId, role);
    }

    public Map<String, Object> book(long patientId, long doctorId, String date, String time)
            throws SQLException, ValidationException, ConflictException {
        requirePositive(doctorId, "Doctor");
        LocalDate targetDate = parseDate(date);
        LocalTime targetTime = parseTime(time);
        requireHalfHourSlot(targetTime);
        requireFuture(targetDate, targetTime);
        return appointments.book(patientId, doctorId, targetDate, targetTime);
    }

    public Map<String, Object> reschedule(long patientId, long appointmentId, String date, String time)
            throws SQLException, ValidationException, ConflictException {
        requirePositive(appointmentId, "Appointment");
        LocalDate targetDate = parseDate(date);
        LocalTime targetTime = parseTime(time);
        requireHalfHourSlot(targetTime);
        requireFuture(targetDate, targetTime);
        return appointments.reschedule(patientId, appointmentId, targetDate, targetTime);
    }

    public Map<String, Object> cancel(long patientId, long appointmentId)
            throws SQLException, ValidationException, ConflictException {
        requirePositive(appointmentId, "Appointment");
        return appointments.cancel(patientId, appointmentId);
    }

    public Map<String, Object> createTimeRequest(long actorId, String role, long appointmentId,
                                                  String date, String time)
            throws SQLException, ValidationException, ConflictException {
        requirePositive(appointmentId, "Appointment");
        LocalDate targetDate = parseDate(date);
        LocalTime targetTime = parseTime(time);
        requireHalfHourSlot(targetTime);
        requireFuture(targetDate, targetTime);
        return appointments.createTimeRequest(actorId, role, appointmentId, targetDate, targetTime);
    }

    public List<Map<String, Object>> timeRequests(long actorId, String role) throws SQLException {
        return appointments.timeRequests(actorId, role);
    }

    public Map<String, Object> decideTimeRequest(long actorId, String role, long requestId, String decision)
            throws SQLException, ValidationException, ConflictException {
        requirePositive(requestId, "Time request");
        if (!"accept".equals(decision) && !"decline".equals(decision)) {
            throw new ValidationException("Choose accept or decline.");
        }
        return appointments.decideTimeRequest(actorId, role, requestId, "accept".equals(decision));
    }

    private LocalDate parseDate(String value) throws ValidationException {
        try {
            if (value == null || value.isBlank()) throw new DateTimeParseException("missing", "", 0);
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException("Enter a valid date in YYYY-MM-DD format.");
        }
    }

    private LocalTime parseTime(String value) throws ValidationException {
        try {
            if (value == null || value.isBlank()) throw new DateTimeParseException("missing", "", 0);
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException("Enter a valid time in HH:MM format.");
        }
    }

    private void requireHalfHourSlot(LocalTime time) throws ValidationException {
        if (!isHalfHour(time)) throw new ValidationException("Choose a time on a 30-minute boundary.");
    }

    private boolean isHalfHour(LocalTime time) {
        return time.getSecond() == 0 && time.getNano() == 0 && time.getMinute() % 30 == 0;
    }

    private void requireFuture(LocalDate date, LocalTime time) throws ValidationException {
        if (!date.atTime(time).isAfter(LocalDateTime.now(AvailabilityDao.ZONE))) {
            throw new ValidationException("Choose a future date and time.");
        }
        if (date.isAfter(LocalDate.now(AvailabilityDao.ZONE).plusDays(120))) {
            throw new ValidationException("Choose a date within the next 120 days.");
        }
    }

    private void requirePositive(long id, String kind) throws ValidationException {
        if (id <= 0) throw new ValidationException("Choose a valid " + kind.toLowerCase() + ".");
    }
}
