package edu.alliance.medislot.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import edu.alliance.medislot.exception.ConflictException;
import edu.alliance.medislot.exception.ValidationException;
import edu.alliance.medislot.service.Phase2Service;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Same-origin Phase 2 API. Phase 1 auth and doctor-list mappings remain more specific. */
public final class Phase2Servlet extends JsonServlet {
    private final Phase2Service service = new Phase2Service();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch(response, () -> {
            String path = path(request);
            long actor = actorId(request);
            String role = role(request);
            if (path.equals("/appointments")) {
                send(response, 200, service.appointments(actor, role));
            } else if (path.equals("/time-requests")) {
                send(response, 200, service.timeRequests(actor, role));
            } else if (path.equals("/patient/favorites")) {
                requireRole(role, "PATIENT");
                send(response, 200, service.favorites(actor));
            } else if (path.equals("/doctor/profile")) {
                requireRole(role, "DOCTOR");
                send(response, 200, service.doctorSelfProfile(actor));
            } else if (path.equals("/doctor/availability")) {
                requireRole(role, "DOCTOR");
                send(response, 200, service.doctorAvailability(actor));
            } else {
                String[] parts = parts(path);
                if (parts.length == 3 && "doctors".equals(parts[1])) {
                    send(response, 200, service.doctorProfile(parseId(parts[2], "doctor")));
                } else if (parts.length == 4 && "doctors".equals(parts[1]) && "slots".equals(parts[3])) {
                    send(response, 200, service.slots(parseId(parts[2], "doctor"), request.getParameter("date")));
                } else {
                    sendError(response, 404, "That MediSlot endpoint was not found.", "NOT_FOUND");
                }
            }
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch(response, () -> {
            String path = path(request);
            long actor = actorId(request);
            String role = role(request);
            JsonNode input = body(request);
            if (path.equals("/appointments")) {
                requireRole(role, "PATIENT");
                send(response, 201, service.book(actor, longField(input, "doctorId"),
                        textField(input, "date"), textField(input, "time")));
                return;
            }
            if (path.startsWith("/appointments/")) {
                String[] parts = parts(path);
                if (parts.length == 4 && "reschedule".equals(parts[3])) {
                    requireRole(role, "PATIENT");
                    send(response, 200, service.reschedule(actor, parseId(parts[2], "appointment"),
                            textField(input, "date"), textField(input, "time")));
                    return;
                }
                if (parts.length == 4 && "time-requests".equals(parts[3])) {
                    requireParticipantRole(role);
                    send(response, 201, service.createTimeRequest(actor, role, parseId(parts[2], "appointment"),
                            textField(input, "date"), textField(input, "time")));
                    return;
                }
            }
            if (path.startsWith("/time-requests/")) {
                String[] parts = parts(path);
                if (parts.length == 4 && "decision".equals(parts[3])) {
                    requireParticipantRole(role);
                    send(response, 200, service.decideTimeRequest(actor, role, parseId(parts[2], "time request"),
                            textField(input, "decision")));
                    return;
                }
            }
            if (path.startsWith("/patient/favorites/")) {
                requireRole(role, "PATIENT");
                service.addFavorite(actor, parseId(last(parts(path)), "doctor"));
                send(response, 201, Map.of("saved", true));
                return;
            }
            sendError(response, 404, "That MediSlot endpoint was not found.", "NOT_FOUND");
        });
    }

    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch(response, () -> {
            String path = path(request);
            long actor = actorId(request);
            String role = role(request);
            requireRole(role, "DOCTOR");
            JsonNode input = body(request);
            if (path.equals("/doctor/profile")) {
                service.updateDoctorProfile(actor, textField(input, "bio"), textField(input, "languages"));
                send(response, 200, Map.of("saved", true));
                return;
            }
            String[] parts = parts(path);
            if (parts.length == 4 && "doctor".equals(parts[1]) && "availability".equals(parts[2])) {
                int weekday = parseWeekday(parts[3]);
                service.setAvailability(actor, weekday, textField(input, "startTime"), textField(input, "endTime"));
                send(response, 200, Map.of("saved", true, "weekday", weekday));
                return;
            }
            sendError(response, 404, "That MediSlot endpoint was not found.", "NOT_FOUND");
        });
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch(response, () -> {
            String path = path(request);
            long actor = actorId(request);
            String role = role(request);
            if (path.startsWith("/appointments/")) {
                requireRole(role, "PATIENT");
                String[] parts = parts(path);
                if (parts.length == 3) {
                    send(response, 200, service.cancel(actor, parseId(parts[2], "appointment")));
                    return;
                }
            }
            if (path.startsWith("/patient/favorites/")) {
                requireRole(role, "PATIENT");
                service.removeFavorite(actor, parseId(last(parts(path)), "doctor"));
                send(response, 200, Map.of("removed", true));
                return;
            }
            String[] parts = parts(path);
            if (parts.length == 4 && "doctor".equals(parts[1]) && "availability".equals(parts[2])) {
                requireRole(role, "DOCTOR");
                service.removeAvailability(actor, parseWeekday(parts[3]));
                send(response, 200, Map.of("removed", true));
                return;
            }
            sendError(response, 404, "That MediSlot endpoint was not found.", "NOT_FOUND");
        });
    }

    @Override
    protected void send(HttpServletResponse response, int code, Object value) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        super.send(response, code, value);
    }

    private void dispatch(HttpServletResponse response, Endpoint endpoint) throws IOException {
        try {
            endpoint.run();
        } catch (ValidationException e) {
            sendError(response, 400, e.getMessage(), "VALIDATION_ERROR");
        } catch (ConflictException e) {
            String code = e.getMessage().startsWith("That time was just booked") ? "SLOT_TAKEN" : "CONFLICT";
            sendError(response, 409, e.getMessage(), code);
        } catch (IllegalArgumentException e) {
            sendError(response, 400, e.getMessage(), "VALIDATION_ERROR");
        } catch (SecurityException e) {
            sendError(response, 403, e.getMessage(), "FORBIDDEN");
        } catch (NoSuchElementException e) {
            sendError(response, 404, e.getMessage(), "NOT_FOUND");
        } catch (SQLException e) {
            sendError(response, 500, "MediSlot could not complete that request. Please try again.", "SERVER_ERROR");
        } catch (IOException e) {
            sendError(response, 400, "Send a valid JSON request.", "INVALID_JSON");
        } catch (Exception e) {
            sendError(response, 500, "MediSlot could not complete that request. Please try again.", "SERVER_ERROR");
        }
    }

    private void sendError(HttpServletResponse response, int status, String message, String code) throws IOException {
        send(response, status, Map.of("error", message == null ? "Request failed." : message, "code", code));
    }

    private long actorId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("userId") instanceof Number id)) {
            throw new SecurityException("Please sign in to continue.");
        }
        return id.longValue();
    }

    private String role(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object value = session == null ? null : session.getAttribute("role");
        if (!(value instanceof String role) || role.isBlank()) throw new SecurityException("Please sign in to continue.");
        return role;
    }

    private String path(HttpServletRequest request) {
        String path = request.getPathInfo();
        return path == null || path.isBlank() ? "/" : path;
    }

    private String[] parts(String path) { return path.split("/"); }
    private String last(String[] parts) { return parts[parts.length - 1]; }

    private long parseId(String value, String kind) throws ValidationException {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw new ValidationException("Choose a valid " + kind + ".");
        }
    }

    private int parseWeekday(String value) throws ValidationException {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw new ValidationException("Choose a weekday from 1 to 7."); }
    }

    private long longField(JsonNode input, String name) throws ValidationException {
        JsonNode node = input == null ? null : input.get(name);
        if (node == null || !node.canConvertToLong()) throw new ValidationException("Enter a valid " + name + ".");
        return node.asLong();
    }

    private String textField(JsonNode input, String name) {
        JsonNode node = input == null ? null : input.get(name);
        return node == null || node.isNull() ? null : node.asText();
    }

    private void requireRole(String actual, String expected) {
        if (!expected.equals(actual)) throw new SecurityException(expected.toLowerCase() + " role required.");
    }

    private void requireParticipantRole(String role) {
        if (!"PATIENT".equals(role) && !"DOCTOR".equals(role)) {
            throw new SecurityException("Patient or doctor role required.");
        }
    }

    @FunctionalInterface
    private interface Endpoint { void run() throws Exception; }
}
