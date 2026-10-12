package edu.alliance.medislot.servlet;

import edu.alliance.medislot.service.Phase3Service;
import edu.alliance.medislot.exception.ValidationException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.NoSuchElementException;

/** Validates bill ownership before forwarding to the JSP renderer. */
public final class BillPrintServlet extends HttpServlet {
    private final Phase3Service service = new Phase3Service();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        try {
            HttpSession session = request.getSession(false);
            if (session == null || !(session.getAttribute("userId") instanceof Number actor)
                    || !(session.getAttribute("role") instanceof String role)) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Please sign in to view this bill.");
                return;
            }
            long billId;
            try {
                billId = Long.parseLong(request.getParameter("id"));
            } catch (NumberFormatException | NullPointerException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Choose a valid bill.");
                return;
            }
            Map<String, Object> bill = service.billForPrint(actor.longValue(), role, billId);
            request.setAttribute("bill", bill);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("text/html;charset=UTF-8");
            request.getRequestDispatcher("/WEB-INF/jsp/bill.jsp").forward(request, response);
        } catch (NoSuchElementException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Bill was not found.");
        } catch (SecurityException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "You are not allowed to view this bill.");
        } catch (ValidationException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Choose a valid bill.");
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Choose a valid bill.");
        } catch (SQLException e) {
            getServletContext().log("Bill rendering failed.", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "MediSlot could not open this bill.");
        }
    }
}
