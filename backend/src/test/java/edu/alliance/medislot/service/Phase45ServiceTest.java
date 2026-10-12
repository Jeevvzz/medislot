package edu.alliance.medislot.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class Phase45ServiceTest {
    private final Phase45Service service = new Phase45Service();

    @Test
    void suggestsSpecialtyWithoutPresentingItAsDiagnosis() {
        Map<String, Object> result = service.suggestSpecialty("skin rash and itching");

        assertEquals("Dermatology", result.get("specialty"));
        assertTrue(String.valueOf(result.get("disclaimer")).contains("not a diagnosis"));
    }

    @Test
    void routesHeartRelatedKeywordsToCardiology() {
        assertEquals("Cardiology", service.suggestSpecialty("heart palpitations").get("specialty"));
    }

    @Test
    void defaultsUnrecognizedKeywordsToGeneralMedicine() {
        assertEquals("General Medicine", service.suggestSpecialty("I need help finding care").get("specialty"));
    }

    @Test
    void localAssistantUsesAuthorizedOverviewAndSafeNavigation() {
        String answer = Phase45Service.localAssistantAnswer("show my appointments",
                Map.of("upcomingAppointments", 2, "pendingTimeRequests", 1,
                        "favoriteDoctors", 3, "pharmacyOrders", 1, "unreadReminders", 2), true);

        assertTrue(answer.contains("2 upcoming appointment(s)"));
        assertTrue(answer.contains("Open Appointments"));
        assertTrue(answer.contains("cannot assess symptoms or diagnose"));
    }

    @Test
    void localAssistantDoesNotIncludeContextWhenNotOptedIn() {
        String answer = Phase45Service.localAssistantAnswer("show my appointments",
                Map.of("upcomingAppointments", 99), false);

        assertFalse(answer.contains("99"));
        assertTrue(answer.contains("Open Appointments"));
    }

    @Test
    void assistantNavigationIsLimitedToTheSignedInRole() {
        assertEquals(List.of("favorites"), Phase45Service.assistantNavigation("show favorites", "PATIENT"));
        assertEquals(Set.of("schedule", "consultations", "doctor-profile"),
                Set.copyOf(Phase45Service.assistantNavigation("help", "DOCTOR")));
        assertEquals(List.of("reports"), Phase45Service.assistantNavigation("help", "ADMIN"));
    }

    @Test
    void assistantContextProjectionExcludesClinicalAndIdentityFields() {
        Map<String, Object> raw = Map.of("date", "2026-10-12", "status", "BOOKED",
                "patientName", "Synthetic Patient", "diagnosis", "private diagnosis", "note", "private note");

        Map<String, Object> safe = Phase45Service.pick(raw, "date", "status");

        assertEquals(Map.of("date", "2026-10-12", "status", "BOOKED"), safe);
        assertFalse(safe.containsKey("diagnosis"));
        assertFalse(safe.containsKey("patientName"));
    }

    @Test
    void localAssistantProvidesNextAppointmentAndReminderNavigation() {
        Map<String, Object> summary = Map.of("upcomingAppointments", 1,
                "pendingTimeRequests", 0, "favoriteDoctors", 0,
                "pharmacyOrders", 0, "unreadReminders", 2,
                "nextAppointment", Map.of("date", "2026-10-13", "time", "09:30"));

        String appointmentAnswer = Phase45Service.localAssistantAnswer("when is my next appointment?", summary, true);
        String reminderAnswer = Phase45Service.localAssistantAnswer("show my reminders", summary, true);

        assertTrue(appointmentAnswer.contains("2026-10-13 at 09:30"));
        assertTrue(reminderAnswer.contains("2 unread reminder(s)"));
        assertTrue(Phase45Service.assistantNavigation("show my reminders", "PATIENT").contains("care-tools"));
    }

    @Test
    void assistantRequiresAnAuthenticatedRoleAndBoundsPromptLength() {
        SecurityException missingActor = assertThrows(SecurityException.class,
            () -> service.assistant("hello", 0, "PATIENT", false));
        SecurityException unknownRole = assertThrows(SecurityException.class,
            () -> service.assistant("hello", 7, "UNKNOWN", false));
        IllegalArgumentException blankQuestion = assertThrows(IllegalArgumentException.class,
            () -> service.assistant(" ", 7, "PATIENT", false));
        IllegalArgumentException longQuestion = assertThrows(IllegalArgumentException.class,
            () -> service.assistant("x".repeat(1201), 7, "PATIENT", false));

        assertEquals("Please sign in to continue.", missingActor.getMessage());
        assertEquals("Please sign in to continue.", unknownRole.getMessage());
        assertTrue(blankQuestion.getMessage().contains("1,200 characters"));
        assertTrue(longQuestion.getMessage().contains("1,200 characters"));
    }
}
