package edu.alliance.medislot;

import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;

import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;

import edu.alliance.medislot.dao.Database;
import edu.alliance.medislot.dao.SeedData;
import edu.alliance.medislot.filter.AuthFilter;
import edu.alliance.medislot.servlet.AuthServlet;
import edu.alliance.medislot.servlet.BillPrintServlet;
import edu.alliance.medislot.servlet.DoctorServlet;
import edu.alliance.medislot.servlet.HealthServlet;
import edu.alliance.medislot.servlet.Phase2Servlet;
import edu.alliance.medislot.servlet.Phase3Servlet;
import edu.alliance.medislot.servlet.Phase45Servlet;
import edu.alliance.medislot.servlet.PrescriptionPrintServlet;
import jakarta.servlet.SessionCookieConfig;

public final class App {
    private App() {}

    public static void main(String[] args) throws Exception {
        Database.init();
        SeedData.load();
        SeedData.loadPhase2Availability();
        SeedData.loadPhase3DemoHistory();

        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "3000"));
        Server server = new Server(new InetSocketAddress("0.0.0.0", port));
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        configureSessionCookie(context.getSessionHandler());

        Path frontend = Path.of("frontend").toAbsolutePath().normalize();
        if (!Files.isDirectory(frontend)) frontend = Path.of("../frontend").toAbsolutePath().normalize();
        if (!Files.isDirectory(frontend)) throw new IllegalStateException("Frontend directory is missing from the runtime image.");
        context.setBaseResource(Resource.newResource(frontend.toFile()));
        configureJsp(context, frontend);
        context.addServlet(new ServletHolder(new HealthServlet()), "/_app/health");
        context.addServlet(AuthServlet.class, "/api/auth/*");
        context.addServlet(DoctorServlet.class, "/api/doctors");
        context.addServlet(Phase3Servlet.class, "/api/phase3/*");
        context.addServlet(Phase45Servlet.class, "/api/phase45/*");
        context.addServlet(Phase2Servlet.class, "/api/*");
        context.addFilter(AuthFilter.class, "/api/*", EnumSet.of(jakarta.servlet.DispatcherType.REQUEST));
        context.addServlet(new ServletHolder(new BillPrintServlet()), "/bills/print.jsp");
        context.addServlet(new ServletHolder(new PrescriptionPrintServlet()), "/prescriptions/print.jsp");
        ServletHolder jsp = new ServletHolder("jsp", JettyJspServlet.class);
        jsp.setInitOrder(0);
        jsp.setInitParameter("scratchdir", Path.of(System.getProperty("java.io.tmpdir"), "medislot-jsp").toString());
        jsp.setInitParameter("fork", "false");
        jsp.setInitParameter("xpoweredBy", "false");
        jsp.setInitParameter("keepgenerated", "false");
        jsp.setInitParameter("compilerSourceVM", "1.8");
        jsp.setInitParameter("compilerTargetVM", "1.8");
        context.addServlet(jsp, "*.jsp");
        ServletHolder defaultServlet = new ServletHolder("default", DefaultServlet.class);
        defaultServlet.setInitParameter("resourceBase", frontend.toUri().toASCIIString());
        defaultServlet.setInitParameter("dirAllowed", "false");
        context.addServlet(defaultServlet, "/");

        server.setHandler(context);
        server.start();
        System.out.println("MediSlot ready on port " + port);
        server.join();
    }

    private static void configureSessionCookie(SessionHandler sessions) {
        boolean secure = !"false".equalsIgnoreCase(System.getenv().getOrDefault("MEDISLOT_SECURE_COOKIES", "true"));
        SessionCookieConfig cookie = sessions.getSessionCookieConfig();
        cookie.setName("MediSlotSession");
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setPath("/");
        sessions.setSecureRequestOnly(false);
        sessions.setSameSite(secure ? HttpCookie.SameSite.NONE : HttpCookie.SameSite.LAX);
    }

    private static void configureJsp(ServletContextHandler context, Path frontend) throws Exception {
        Path scratch = Path.of(System.getProperty("java.io.tmpdir"), "medislot-jsp");
        Files.createDirectories(scratch);
        context.setAttribute("javax.servlet.context.tempdir", scratch.toFile());
        URL applicationJar = App.class.getProtectionDomain().getCodeSource().getLocation();
        context.setClassLoader(new URLClassLoader(new URL[]{applicationJar}, App.class.getClassLoader()));
        context.addBean(new EmbeddedJspStarter(context));
        context.setAttribute(InstanceManager.class.getName(), new SimpleInstanceManager());
    }
}
