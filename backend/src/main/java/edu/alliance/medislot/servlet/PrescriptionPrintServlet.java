package edu.alliance.medislot.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.NoSuchElementException;

import edu.alliance.medislot.exception.ValidationException;
import edu.alliance.medislot.service.Phase45Service;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Checks patient/assigned-doctor/admin authorization before rendering the printable prescription. */
public final class PrescriptionPrintServlet extends HttpServlet {
    private final Phase45Service service=new Phase45Service();
    @Override protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException,ServletException{
        r.setHeader("Cache-Control","no-store");r.setHeader("X-Content-Type-Options","nosniff");
        try{HttpSession s=q.getSession(false);if(s==null||!(s.getAttribute("userId") instanceof Number id)||!(s.getAttribute("role") instanceof String role)){r.sendError(401,"Please sign in to view this prescription.");return;}long rx;try{rx=Long.parseLong(q.getParameter("id"));}catch(Exception e){r.sendError(400,"Choose a valid prescription.");return;}Map<String,Object> value=service.prescription(rx,id.longValue(),role);q.setAttribute("prescription",value);r.setCharacterEncoding("UTF-8");r.setContentType("text/html;charset=UTF-8");q.getRequestDispatcher("/WEB-INF/jsp/prescription.jsp").forward(q,r);
        }catch(NoSuchElementException e){r.sendError(404,"Prescription was not found.");}catch(SecurityException e){r.sendError(403,"You are not allowed to view this prescription.");}catch(ValidationException e){r.sendError(400,"Choose a valid prescription.");}catch(SQLException e){getServletContext().log("Prescription rendering failed",e);r.sendError(500,"MediSlot could not open this prescription.");}}
}
