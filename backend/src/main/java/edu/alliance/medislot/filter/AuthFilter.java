package edu.alliance.medislot.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

public final class AuthFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());

        if (!path.startsWith("/api/") || path.equals("/api/auth/login") || path.equals("/api/auth/register")) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = httpRequest.getSession(false);
        if (session == null || session.getAttribute("userId") == null) {
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Please sign in to continue.");
            return;
        }

        String role = (String) session.getAttribute("role");
        if (path.startsWith("/api/admin/") && !"ADMIN".equals(role)) {
            sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN, "Admin role required.");
            return;
        }
        if (path.startsWith("/api/doctor/") && !"DOCTOR".equals(role) && !"ADMIN".equals(role)) {
            sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN, "Doctor role required.");
            return;
        }
        if (path.startsWith("/api/patient/") && !"PATIENT".equals(role)) {
            sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN, "Patient role required.");
            return;
        }
        boolean phase2Scheduling = path.equals("/api/appointments") || path.startsWith("/api/appointments/")
                || path.equals("/api/time-requests") || path.startsWith("/api/time-requests/");
        if (phase2Scheduling && !"PATIENT".equals(role) && !"DOCTOR".equals(role)) {
            sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN, "Patient or doctor role required.");
            return;
        }
        chain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
