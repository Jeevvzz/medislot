CREATE TABLE consultations (
  id BIGINT NOT NULL AUTO_INCREMENT,
  appointment_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  note TEXT NOT NULL,
  diagnosis VARCHAR(1200) NOT NULL,
  treatment VARCHAR(2000) NOT NULL,
  completed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uq_consultation_appointment UNIQUE (appointment_id),
  CONSTRAINT fk_consultation_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
  CONSTRAINT fk_consultation_patient FOREIGN KEY (patient_id) REFERENCES users(id),
  CONSTRAINT fk_consultation_doctor FOREIGN KEY (doctor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_consultation_patient_completed ON consultations(patient_id, completed_at);
CREATE INDEX idx_consultation_doctor_completed ON consultations(doctor_id, completed_at);

CREATE TABLE billing_records (
  id BIGINT NOT NULL AUTO_INCREMENT,
  consultation_id BIGINT NOT NULL,
  appointment_id BIGINT NOT NULL,
  patient_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  bill_number VARCHAR(32) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  currency CHAR(3) NOT NULL DEFAULT 'INR',
  status VARCHAR(16) NOT NULL DEFAULT 'ISSUED',
  issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uq_bill_consultation UNIQUE (consultation_id),
  CONSTRAINT uq_bill_appointment UNIQUE (appointment_id),
  CONSTRAINT uq_bill_number UNIQUE (bill_number),
  CONSTRAINT fk_bill_consultation FOREIGN KEY (consultation_id) REFERENCES consultations(id),
  CONSTRAINT fk_bill_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
  CONSTRAINT fk_bill_patient FOREIGN KEY (patient_id) REFERENCES users(id),
  CONSTRAINT fk_bill_doctor FOREIGN KEY (doctor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_bill_patient_issued ON billing_records(patient_id, issued_at);
CREATE INDEX idx_bill_doctor_issued ON billing_records(doctor_id, issued_at);
