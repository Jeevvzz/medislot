package edu.alliance.medislot.servlet;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.databind.JsonNode;

import edu.alliance.medislot.exception.ValidationException;
import edu.alliance.medislot.service.Phase45Service;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/** Session-protected Phase 4 storefront and Phase 5 assistant, queue, timeline and reminders APIs. */
public final class Phase45Servlet extends JsonServlet {
    private final Phase45Service service = new Phase45Service();

    @Override protected void doGet(HttpServletRequest q,HttpServletResponse r)throws IOException{dispatch(r,()->{String p=path(q);long id=actor(q);String role=role(q);
        if("/catalog".equals(p))send(r,200,service.catalog(q.getParameter("q")));
        else if("/cart".equals(p)){require(role,"PATIENT");send(r,200,service.cart(id));}
        else if("/orders".equals(p)){if(!role.equals("PATIENT")&&!role.equals("ADMIN"))deny();send(r,200,service.orders(id,role));}
        else if("/prescriptions".equals(p)){if(!role.equals("PATIENT")&&!role.equals("DOCTOR")&&!role.equals("ADMIN"))deny();send(r,200,service.prescriptions(id,role));}
        else if("/queue".equals(p)){if(role.equals("DOCTOR"))send(r,200,service.queueForDoctor(id));else{require(role,"PATIENT");if(q.getParameter("id")==null)send(r,200,java.util.List.of());else send(r,200,service.queueToken(number(q.getParameter("id"),"queue token"),id,role));}}
        else if("/reminders".equals(p)){require(role,"PATIENT");send(r,200,service.reminders(id));}
        else if("/timeline".equals(p)){require(role,"PATIENT");send(r,200,service.timeline(id));}
        else error(r,404,"That MediSlot endpoint was not found.");});}

    @Override protected void doPost(HttpServletRequest q,HttpServletResponse r)throws IOException{dispatch(r,()->{String p=path(q);long id=actor(q);String role=role(q);JsonNode b=body(q);
        if("/cart/items".equals(p)){require(role,"PATIENT");service.addToCart(id,longField(b,"medicineId"),intField(b,"quantity"));send(r,201,Map.of("saved",true,"cart",service.cart(id)));}
        else if("/cart/checkout".equals(p)){require(role,"PATIENT");send(r,201,service.checkout(id));}
        else if("/prescriptions".equals(p)){require(role,"DOCTOR");send(r,201,service.prescribe(id,longField(b,"appointmentId"),text(b,"instructions"),b==null?null:b.get("items")));}
        else if(p.matches("/prescriptions/[0-9]+/cart")){require(role,"PATIENT");String[] a=p.split("/");send(r,200,service.addPrescriptionToCart(id,number(a[2],"prescription")));}
        else if("/queue".equals(p)){require(role,"PATIENT");send(r,201,service.queue(id,longField(b,"appointmentId")));}
        else if("/assistant".equals(p)){send(r,200,service.assistant(text(b,"question"),id,role,b!=null&&b.path("includeContext").asBoolean(false)));}
        else if("/specialty-suggestion".equals(p)){send(r,200,service.suggestSpecialty(text(b,"symptoms")));}
        else error(r,404,"That MediSlot endpoint was not found.");});}

    @Override protected void doPut(HttpServletRequest q,HttpServletResponse r)throws IOException{dispatch(r,()->{String p=path(q);long id=actor(q);String role=role(q);JsonNode b=body(q);String[] a=p.split("/");
        if(a.length==4&&"orders".equals(a[1])&&"status".equals(a[3])){require(role,"ADMIN");send(r,200,service.status(number(a[2],"order"),text(b,"status")));}
        else if(a.length==4&&"queue".equals(a[1])&&"status".equals(a[3])){require(role,"DOCTOR");service.updateQueue(id,number(a[2],"queue token"),text(b,"status"));send(r,200,Map.of("saved",true));}
        else if(a.length==4&&"reminders".equals(a[1])&&"read".equals(a[3])){require(role,"PATIENT");service.markReminderRead(id,number(a[2],"reminder"));send(r,200,Map.of("read",true));}
        else error(r,404,"That MediSlot endpoint was not found.");});}
    @Override protected void doDelete(HttpServletRequest q,HttpServletResponse r)throws IOException{dispatch(r,()->{String p=path(q);String role=role(q);require(role,"PATIENT");String[] a=p.split("/");if(a.length==4&&"cart".equals(a[1])&&"items".equals(a[2])){service.removeFromCart(actor(q),number(a[3],"medicine"));send(r,200,Map.of("removed",true));}else error(r,404,"That MediSlot endpoint was not found.");});}

    @Override protected void send(HttpServletResponse r,int code,Object value)throws IOException{r.setHeader("Cache-Control","no-store");super.send(r,code,value);}
    private void dispatch(HttpServletResponse r,Endpoint e)throws IOException{try{e.run();}catch(ValidationException|IllegalArgumentException x){error(r,400,x.getMessage());}catch(SecurityException x){error(r,403,x.getMessage());}catch(NoSuchElementException x){error(r,404,x.getMessage());}catch(IOException x){error(r,400,"Send a valid JSON request.");}catch(SQLException x){getServletContext().log("Phase 4/5 database request failed",x);error(r,500,"MediSlot could not complete that request.");}catch(Exception x){getServletContext().log("Phase 4/5 request failed",x);error(r,500,"MediSlot could not complete that request.");}}
    private long actor(HttpServletRequest q){HttpSession s=q.getSession(false);if(s==null||!(s.getAttribute("userId") instanceof Number n))throw new SecurityException("Please sign in to continue.");return n.longValue();}
    private String role(HttpServletRequest q){Object x=q.getSession(false).getAttribute("role");if(!(x instanceof String))throw new SecurityException("Please sign in to continue.");return(String)x;}
    private String path(HttpServletRequest q){String p=q.getPathInfo();return p==null?"/":p;}
    private void require(String actual,String expected){if(!expected.equals(actual))deny();}
    private void deny(){throw new SecurityException("This role is not allowed to perform that action.");}
    private long number(String s,String kind)throws ValidationException{try{long n=Long.parseLong(s);if(n>0)return n;}catch(NumberFormatException ignored){}throw new ValidationException("Choose a valid "+kind+".");}
    private long longField(JsonNode n,String key)throws ValidationException{if(n==null||!n.path(key).canConvertToLong())throw new ValidationException("Enter a valid "+key+".");return n.path(key).asLong();}
    private int intField(JsonNode n,String key)throws ValidationException{if(n==null||!n.path(key).canConvertToInt())throw new ValidationException("Enter a valid "+key+".");return n.path(key).asInt();}
    private String text(JsonNode n,String key){return n==null||n.path(key).isMissingNode()||n.path(key).isNull()?null:n.path(key).asText();}
    @FunctionalInterface private interface Endpoint{void run()throws Exception;}
}
