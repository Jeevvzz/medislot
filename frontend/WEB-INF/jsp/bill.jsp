<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>MediSlot demo invoice</title>
  <style>
    :root { color-scheme: light; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: #302a43; background: #f5f2fb; }
    * { box-sizing: border-box; }
    body { margin: 0; padding: 36px 18px; }
    .sheet { max-width: 760px; margin: 0 auto; padding: 42px; background: #fff; border: 1px solid #e5def2; border-radius: 22px; box-shadow: 0 18px 50px rgba(76, 57, 111, .12); }
    .topline { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; border-bottom: 1px solid #ece7f4; padding-bottom: 24px; }
    .brand { color: #7656b3; font-weight: 800; font-size: 1.3rem; letter-spacing: .02em; }
    .kicker { color: #6f6680; font-size: .78rem; font-weight: 700; letter-spacing: .13em; text-transform: uppercase; }
    h1 { margin: 8px 0 4px; font-size: 1.7rem; }
    .demo { margin: 24px 0; padding: 14px 16px; border: 1px solid #e6daf8; border-radius: 12px; background: #f5efff; color: #544270; font-size: .93rem; }
    .facts { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; margin: 24px 0 30px; }
    .fact { padding: 15px; border-radius: 12px; background: #faf8fd; }
    .fact span { display: block; color: #777084; font-size: .8rem; margin-bottom: 5px; }
    .fact strong { overflow-wrap: anywhere; }
    table { width: 100%; border-collapse: collapse; margin-top: 8px; }
    th, td { padding: 15px 8px; text-align: left; border-bottom: 1px solid #ece7f4; }
    th { color: #777084; font-size: .8rem; font-weight: 700; }
    td:last-child, th:last-child { text-align: right; }
    .total { display: flex; justify-content: flex-end; gap: 40px; padding: 20px 8px; font-size: 1.08rem; font-weight: 800; }
    .fine { margin-top: 30px; color: #777084; font-size: .86rem; line-height: 1.55; }
    .actions { max-width: 760px; margin: 18px auto 0; display: flex; justify-content: space-between; gap: 12px; }
    .actions a, .actions button { border: 1px solid #d8cde9; border-radius: 10px; background: #fff; color: #5b4684; padding: 11px 16px; font: inherit; font-weight: 700; text-decoration: none; cursor: pointer; }
    @media (max-width: 560px) { body { padding: 18px 10px; } .sheet { padding: 24px 18px; } .facts { grid-template-columns: 1fr; } .topline { flex-direction: column; } }
    @media print { :root { background: #fff; } body { padding: 0; } .sheet { max-width: none; border: 0; border-radius: 0; box-shadow: none; padding: 16mm; } .actions { display: none; } .demo { print-color-adjust: exact; -webkit-print-color-adjust: exact; } }
  </style>
  <link rel="stylesheet" href="/css/design.css">
  <script src="/js/theme.js"></script>
</head>
<body>
  <main class="sheet" aria-labelledby="invoice-title">
    <div class="topline">
      <div><div class="brand">MediSlot</div><div class="kicker">Hospital appointment demo</div></div>
      <div style="text-align:right"><div class="kicker">Invoice</div><h1 id="invoice-title">Consultation bill</h1></div>
    </div>
    <p class="demo"><strong>Synthetic demo invoice.</strong> No payment is processed. Do not use this page for real patient or financial records.</p>
    <div class="facts">
      <div class="fact"><span>Bill number</span><strong><c:out value="${bill.billNumber}"/></strong></div>
      <div class="fact"><span>Issued</span><strong><c:out value="${bill.issuedAt}"/></strong></div>
      <div class="fact"><span>Patient</span><strong><c:out value="${bill.patientName}"/></strong></div>
      <div class="fact"><span>Visit date and time</span><strong><c:out value="${bill.appointmentDate}"/> · <c:out value="${bill.appointmentTime}"/></strong></div>
      <div class="fact"><span>Doctor</span><strong><c:out value="${bill.doctorName}"/></strong></div>
      <div class="fact"><span>Specialty</span><strong><c:out value="${bill.specialty}"/></strong></div>
    </div>
    <table aria-label="Invoice line items">
      <thead><tr><th>Description</th><th>Qty</th><th>Amount</th></tr></thead>
      <tbody><tr><td>Outpatient consultation</td><td>1</td><td><c:out value="${bill.currency}"/> <c:out value="${bill.amount}"/></td></tr></tbody>
    </table>
    <div class="total"><span>Total</span><span><c:out value="${bill.currency}"/> <c:out value="${bill.amount}"/></span></div>
    <p class="fine">This invoice is generated for the MediSlot university-project demonstration using synthetic data. It is not a payment receipt, tax invoice, insurance claim, or request to pay.</p>
  </main>
  <nav class="actions" aria-label="Invoice actions"><a href="/">Return to MediSlot</a><button class="theme-toggle" type="button" data-theme-toggle aria-pressed="false"><span data-theme-icon>◐</span><span data-theme-label>Dark mode</span></button><button type="button" onclick="window.print()">Print invoice</button></nav>
</body>
</html>
