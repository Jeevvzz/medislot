package edu.alliance.medislot;

import org.apache.tomcat.util.scan.StandardJarScanFilter;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

/** Runs Jasper's container initializer for the embedded ServletContextHandler JSP servlet. */
public final class EmbeddedJspStarter extends AbstractLifeCycle {
    private final JettyJasperInitializer initializer = new JettyJasperInitializer();
    private final ServletContextHandler context;

    public EmbeddedJspStarter(ServletContextHandler context) {
        this.context = context;
        StandardJarScanner scanner = new StandardJarScanner();
        StandardJarScanFilter filter = new StandardJarScanFilter();
        filter.setTldScan("jakarta.servlet.jsp.jstl-*,medislot-*");
        filter.setTldSkip("apache-*,ecj-*,jetty-*,asm-*,javax.servlet-*,javax.annotation-*,taglibs-standard-spec-*");
        scanner.setJarScanFilter(filter);
        context.setAttribute("org.apache.tomcat.JarScanner", scanner);
    }

    @Override
    protected void doStart() throws Exception {
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(context.getClassLoader());
        try {
            initializer.onStartup(null, context.getServletContext());
            super.doStart();
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }
}
