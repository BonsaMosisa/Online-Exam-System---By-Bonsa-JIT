package server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseManager {

    private static final String DB_URL = "jdbc:mysql://localhost:3306/exam_system";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = ""; // Default XAMPP password is empty

    public DatabaseManager() {
        try {
            // Load the JDBC driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Initialize the database if needed
            initializeDatabase();
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC Driver not found: " + e.getMessage());
            throw new RuntimeException("MySQL JDBC Driver not found", e);
        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
            throw new RuntimeException("Database initialization error", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public void closeResources(Connection conn, PreparedStatement stmt, ResultSet rs) {
        try {
            if (rs != null) {
                rs.close();
            }
            if (stmt != null) {
                stmt.close();
            }
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            System.err.println("Error closing database resources: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeDatabase() throws SQLException {
        // This method would create the database and tables if they don't exist
        // For simplicity, we'll assume the database is already set up
        // In a real application, you would include the SQL to create the schema
    }
}
