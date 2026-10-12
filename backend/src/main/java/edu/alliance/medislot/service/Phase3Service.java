package edu.alliance.medislot.service;

import edu.alliance.medislot.dao.Phase3Dao;
import edu.alliance.medislot.exception.ConflictException;
import edu.alliance.medislot.exception.ValidationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Validation and business rules for consultation, history, billing, and aggregate reporting. */
public final class Phase3Service {
    private final Phase3Dao dao = new Phase3Dao();

    public Map<String, Object> completeVisit(long doctorId, long appointmentId, String note,
                                             String diagnosis, String treatment)
            throws SQLException, ValidationException, ConflictException {
        requirePositive(doctorId, "doctor");
        requirePositive(appointmentId, "appointment");
        return dao.completeVisit(doctorId, appointmentId,
                requiredText(note, "Consultation note", 4000),
                requiredText(diagnosis, "Diagnosis", 1200),
                requiredText(treatment, "Treatment", 2000));
    }

    public List<Map<String, Object>> patientHistory(long patientId) throws SQLException, ValidationException {
        requirePositive(patientId, "patient");
        return dao.patientHistory(patientId);
    }

    public Map<String, Object> billForPrint(long actorId, String role, long billId)
            throws SQLException, ValidationException {
        requirePositive(actorId, "account");
        requirePositive(billId, "bill");
        if (!"PATIENT".equals(role) && !"DOCTOR".equals(role) && !"ADMIN".equals(role)) {
            throw new SecurityException("Patient, doctor, or admin role required.");
        }
        return dao.authorizedBill(actorId, role, billId);
    }

    public Map<String, Object> reports(String rawFrom, String rawTo)
            throws SQLException, ValidationException {
        LocalDate from = parseDate(rawFrom);
        LocalDate to = parseDate(rawTo);
        if (from.isAfter(to)) throw new ValidationException("Start date must be on or before end date.");
        if (ChronoUnit.DAYS.between(from, to) > 365) {
            throw new ValidationException("Choose a report range of 366 days or fewer.");
        }
        return dao.reports(from, to);
    }

    private String requiredText(String raw, String label, int maxLength) throws ValidationException {
        if (raw == null || raw.isBlank()) throw new ValidationException(label + " is required.");
        String value = raw.trim();
        if (value.length() > maxLength) {
            throw new ValidationException(label + " must be " + maxLength + " characters or fewer.");
        }
        return value;
    }

    private LocalDate parseDate(String raw) throws ValidationException {
        try {
            if (raw == null || raw.isBlank()) throw new DateTimeParseException("missing", "", 0);
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new ValidationException("Enter a valid date in YYYY-MM-DD format.");
        }
    }

    private void requirePositive(long id, String kind) throws ValidationException {
        if (id <= 0) throw new ValidationException("Choose a valid " + kind + ".");
    }
}
