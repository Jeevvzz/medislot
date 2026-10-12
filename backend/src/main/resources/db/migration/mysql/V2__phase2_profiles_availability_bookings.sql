ALTER TABLE doctor_profiles
  ADD COLUMN bio VARCHAR(600) NULL,
  ADD COLUMN languages VARCHAR(200) NULL;

ALTER TABLE appointments
  ADD COLUMN duration_minutes SMALLINT NOT NULL DEFAULT 30,
  ADD COLUMN updated_at TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE doctor_availability (
  id BIGINT NOT NULL AUTO_INCREMENT,
  doctor_id BIGINT NOT NULL,
  weekday SMALLINT NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  slot_minutes SMALLINT NOT NULL DEFAULT 30,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  CONSTRAINT uq_doctor_availability_weekday UNIQUE (doctor_id, weekday),
  CONSTRAINT fk_availability_doctor FOREIGN KEY (doctor_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT chk_availability_weekday CHECK (weekday BETWEEN 1 AND 7)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE favorite_doctors (
  patient_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (patient_id, doctor_id),
  CONSTRAINT fk_favorite_patient FOREIGN KEY (patient_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_favorite_doctor FOREIGN KEY (doctor_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE appointment_slot_claims (
  doctor_id BIGINT NOT NULL,
  appointment_date DATE NOT NULL,
  appointment_time TIME NOT NULL,
  appointment_id BIGINT NULL,
  PRIMARY KEY (doctor_id, appointment_date, appointment_time),
  CONSTRAINT uq_slot_claim_appointment UNIQUE (appointment_id),
  CONSTRAINT fk_slot_claim_doctor FOREIGN KEY (doctor_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_slot_claim_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO appointment_slot_claims (doctor_id, appointment_date, appointment_time, appointment_id)
SELECT doctor_id, appointment_date, appointment_time, id
FROM appointments
WHERE status NOT IN ('CANCELLED', 'CANCELED');

CREATE TABLE appointment_time_requests (
  id BIGINT NOT NULL AUTO_INCREMENT,
  appointment_id BIGINT NOT NULL,
  requested_by_user_id BIGINT NOT NULL,
  requested_by_role VARCHAR(20) NOT NULL,
  proposed_date DATE NOT NULL,
  proposed_time TIME NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_at TIMESTAMP NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_time_request_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
  CONSTRAINT fk_time_request_user FOREIGN KEY (requested_by_user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_time_request_appointment_status (appointment_id, status),
  INDEX idx_time_request_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE doctor_profiles
SET bio = CASE specialty
  WHEN 'Cardiology' THEN 'Thoughtful heart care with time for every question.'
  WHEN 'Dermatology' THEN 'Practical skin-health guidance focused on your comfort.'
  WHEN 'Pediatrics' THEN 'Warm, family-centered care for growing minds and bodies.'
  WHEN 'Orthopedics' THEN 'Clear support for movement, recovery, and everyday strength.'
  WHEN 'Neurology' THEN 'Careful neurological assessment in a calm, attentive setting.'
  ELSE 'Patient-first general care with a focus on clear next steps.'
END,
languages = 'English, Hindi'
WHERE bio IS NULL OR bio = '';
