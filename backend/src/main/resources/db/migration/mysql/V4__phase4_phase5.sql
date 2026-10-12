CREATE TABLE prescriptions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  appointment_id BIGINT NOT NULL UNIQUE,
  patient_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  instructions VARCHAR(2000),
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_prescription_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
  CONSTRAINT fk_prescription_patient FOREIGN KEY (patient_id) REFERENCES users(id),
  CONSTRAINT fk_prescription_doctor FOREIGN KEY (doctor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE prescription_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  prescription_id BIGINT NOT NULL,
  medicine_id BIGINT NULL,
  medicine_name VARCHAR(120) NOT NULL,
  dosage VARCHAR(200) NOT NULL,
  quantity INT NOT NULL,
  CONSTRAINT chk_prescription_quantity CHECK (quantity BETWEEN 1 AND 100),
  CONSTRAINT fk_prescription_item FOREIGN KEY (prescription_id) REFERENCES prescriptions(id) ON DELETE CASCADE,
  CONSTRAINT fk_prescription_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX idx_prescription_patient ON prescriptions(patient_id,created_at);

CREATE TABLE patient_carts (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL UNIQUE,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_cart_patient FOREIGN KEY (patient_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE cart_items (
  cart_id BIGINT NOT NULL,
  medicine_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  PRIMARY KEY(cart_id,medicine_id),
  CONSTRAINT chk_cart_quantity CHECK (quantity BETWEEN 1 AND 100),
  CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id) REFERENCES patient_carts(id) ON DELETE CASCADE,
  CONSTRAINT fk_cart_item_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE medicine_orders (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  patient_id BIGINT NOT NULL,
  order_number VARCHAR(32) NOT NULL UNIQUE,
  total DECIMAL(10,2) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PLACED',
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_order_patient FOREIGN KEY (patient_id) REFERENCES users(id),
  INDEX idx_orders_patient_date(patient_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE medicine_order_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  order_id BIGINT NOT NULL,
  medicine_id BIGINT NULL,
  medicine_name VARCHAR(120) NOT NULL,
  unit_price DECIMAL(10,2) NOT NULL,
  quantity INT NOT NULL,
  CONSTRAINT chk_order_quantity CHECK (quantity BETWEEN 1 AND 100),
  CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES medicine_orders(id) ON DELETE CASCADE,
  CONSTRAINT fk_order_item_medicine FOREIGN KEY (medicine_id) REFERENCES medicines(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE queue_tokens (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  appointment_id BIGINT NOT NULL UNIQUE,
  patient_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  token VARCHAR(24) NOT NULL UNIQUE,
  status VARCHAR(16) NOT NULL DEFAULT 'WAITING',
  issued_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_queue_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id),
  CONSTRAINT fk_queue_patient FOREIGN KEY (patient_id) REFERENCES users(id),
  CONSTRAINT fk_queue_doctor FOREIGN KEY (doctor_id) REFERENCES users(id),
  INDEX idx_queue_doctor_status(doctor_id,status,issued_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE in_app_reminders (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  appointment_id BIGINT NOT NULL,
  reminder_type VARCHAR(32) NOT NULL,
  title VARCHAR(180) NOT NULL,
  due_at TIMESTAMP NOT NULL,
  read_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uq_reminder_user_appointment UNIQUE(user_id,appointment_id,reminder_type),
  CONSTRAINT fk_reminder_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_reminder_appointment FOREIGN KEY (appointment_id) REFERENCES appointments(id) ON DELETE CASCADE,
  INDEX idx_reminder_user_due(user_id,due_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
