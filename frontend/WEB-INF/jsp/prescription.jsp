<%@ page import="java.util.*" %>
<%!
  private String escRx(Object value) {
    String text = String.valueOf(value == null ? "" : value);
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
  }
%>
<%
  Map prescription = (Map) request.getAttribute("prescription");
  List<Map<String,Object>> items = (List<Map<String,Object>>) prescription.get("items");
%>
<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Prescription · MediSlot</title>
<link rel="stylesheet" href="/css/design.css"><script src="/js/theme.js"></script></head><body><button class="theme-toggle print-theme" type="button" data-theme-toggle aria-pressed="false"><span data-theme-icon>◐</span><span data-theme-label>Dark mode</span></button><main class="sheet">
<div class="brand">✦ MEDISLOT · PRESCRIPTION</div><h1>Visit prescription</h1><div class="facts"><div><b>Patient</b><br><%=escRx(prescription.get("patientName"))%></div><div><b>Prescribed by</b><br>Dr. <%=escRx(prescription.get("doctorName"))%></div><div><b>Date</b><br><%=escRx(prescription.get("createdAt"))%></div></div>
<% for(Map<String,Object> item:items){ %><section class="item"><b><%=escRx(item.get("medicineName"))%></b><p><%=escRx(item.get("dosage"))%> · Quantity: <%=escRx(item.get("quantity"))%></p></section><% } %>
<p><b>Additional instructions</b><br><%=escRx(prescription.get("instructions"))%></p><div class="notice">Academic demonstration only. This is not a real prescription or medical advice. Do not use for treatment.</div><div class="actions"><button onclick="window.print()">Print prescription</button></div></main></body></html>
