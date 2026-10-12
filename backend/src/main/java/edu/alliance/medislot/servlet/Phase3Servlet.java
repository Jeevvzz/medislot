package edu.alliance.medislot.servlet;

import com.fasterxml.jackson.databind.JsonNode;
import edu.alliance.medislot.exception.ConflictException;
import edu.alliance.medislot.exception.ValidationException;
import edu.alliance.medislot.service.Phase3Service;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/** Same-origin Phase 3 JSON API; every operation verifies the session role and record owner. */
public final class Phase3Servlet extends JsonServlet {
    private final Phase3Service service = new Phase3Service();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch(response, () -> {
            String path = path(request);
            long actor = actorId(request);
            String role = role(request);
            if ("/history".equals(path)) {
                requireRole(role, "PATIENT");
                send(response, 200, service.patientHistory(actor));
            } else if ("/reports".equals(path)) {
                requireRole(role, "ADMIN");
                send(response, 200, service.reports(request.getParameter("from"), request.getParameter("to")));
            } else {
                sendError(response, 404, "That MediSlot endpoint was not found.", "NOT_FOUND");
            }
        });
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        dispatch(response, () -> {
            String path = path(request);
            long actor = actorId(request);
            String role = role(request);
            if (!"/consultations".equals(path)) {
                sendError(response, 404, "That MediSlot endpoint was not found.", "NOT_FOUND");
                return;
            }
            requireRole(role, "DOCTOR");
            JsonNode input = body(request);
            send(response, 201, service.completeVisit(actor, longField(input, "appointmentId"),
                    textField(input, "note"), textField(input, "diagnosis"), textField(input, "treatment")));
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
            sendError(response, 409, e.getMessage(), "CONFLICT");
        } catch (IllegalArgumentException e) {
            sendError(response, 400, e.getMessage(), "VALIDATION_ERROR");
        } catch (SecurityException e) {
            sendError(response, 403, e.getMessage(), "FORBIDDEN");
        } catch (NoSuchElementException e) {
            sendError(response, 404, e.getMessage(), "NOT_FOUND");
        } catch (IOException e) {
            sendError(response, 400, "Send a valid JSON request.", "INVALID_JSON");
        } catch (SQLException e) {
            sendError(response, 500, "MediSlot could not complete that request. Please try again.", "SERVER_ERROR");
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

    @FunctionalInterface
    private interface Endpoint { void run() throws Exception; }
}
