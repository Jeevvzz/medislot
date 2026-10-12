package edu.alliance.medislot.dao;

import edu.alliance.medislot.exception.ConflictException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

public class UserDao {
    public record Account(long id, String name, String email, String hash, String role, String phone) {}

    private Account read(ResultSet result) throws SQLException {
        return new Account(result.getLong("id"), result.getString("name"), result.getString("email"),
                result.getString("password_hash"), result.getString("role"), result.getString("phone"));
    }

    public Account byEmail(String email) throws SQLException {
        String sql = "SELECT id,name,email,password_hash,role,phone FROM users WHERE LOWER(email)=LOWER(?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? read(result) : null;
            }
        }
    }

    public Account byId(long id) throws SQLException {
        String sql = "SELECT id,name,email,password_hash,role,phone FROM users WHERE id=?";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? read(result) : null;
            }
        }
    }

    public Account create(String name, String email, String hash, String role, String phone)
            throws SQLException, ConflictException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Account account;
                String sql = "INSERT INTO users(name,email,password_hash,role,phone) VALUES(?,?,?,?,?)";
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, name);
                    statement.setString(2, email);
                    statement.setString(3, hash);
                    statement.setString(4, role);
                    statement.setString(5, phone);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No generated user ID was returned.");
                        account = new Account(keys.getLong(1), name, email, hash, role, phone);
                    }
                }
                if ("PATIENT".equals(role)) {
                    try (PreparedStatement profile = connection.prepareStatement(
                            "INSERT INTO patient_profiles(user_id,date_of_birth) VALUES(?,?)")) {
                        profile.setLong(1, account.id());
                        profile.setNull(2, Types.DATE);
                        profile.executeUpdate();
                    }
                }
                connection.commit();
                return account;
            } catch (SQLException e) {
                connection.rollback();
                if (isDuplicateKey(e)) throw new ConflictException("That email is already registered.");
                throw e;
            }
        }
    }

    private boolean isDuplicateKey(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if ("23505".equals(current.getSQLState()) || current.getErrorCode() == 1062) return true;
        }
        return false;
    }
}
