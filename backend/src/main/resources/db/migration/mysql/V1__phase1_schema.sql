CREATE TABLE users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(100) NOT NULL,
  email VARCHAR(180) NOT NULL,
  password_hash VARCHAR(300) NOT NULL,
  role VARCHAR(20) NOT NULL,
  phone VARCHAR(30),
  PRIMARY KEY (id),
  CONSTRAINT uq_users_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE doctor_profiles (
  user_id BIGINT NOT NULL,
  specialty VARCHAR(100),
  experience INT,
  fee DECIMAL(10,2),
  rating DECIMAL(2,1),
  avatar VARCHAR(12),
  PRIMARY KEY (user_id),
  CONSTRAINT fk_doctor_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE patient_profiles (
  user_id BIGINT NOT NULL,
  date_of_birth DATE NULL,
  PRIMARY KEY (user_id),
  CONSTRAINT fk_patient_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE medicines (
  id BIGINT NOT NULL AUTO_INCREMENT,
  name VARCHAR(120) NOT NULL,
  category VARCHAR(80),
  price DECIMAL(10,2),
  stock INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE appointments (
  id BIGINT NOT NULL AUTO_INCREMENT,
  patient_id BIGINT NOT NULL,
  doctor_id BIGINT NOT NULL,
  appointment_date DATE NOT NULL,
  appointment_time TIME NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'BOOKED',
  PRIMARY KEY (id),
  CONSTRAINT fk_appointment_patient FOREIGN KEY (patient_id) REFERENCES users(id),
  CONSTRAINT fk_appointment_doctor FOREIGN KEY (doctor_id) REFERENCES users(id),
  INDEX idx_appointments_patient_date (patient_id, appointment_date),
  INDEX idx_appointments_doctor_date (doctor_id, appointment_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE medislot_seed_history (
  version VARCHAR(40) NOT NULL,
  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
