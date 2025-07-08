package client;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import shared.RemoteExamService;

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Label statusLabel;

    private RemoteExamService examService;

    // Student client only handles student logins
    private final boolean isTeacher = false;

    public void setExamService(RemoteExamService examService) {
        this.examService = examService;
    }

    @FXML
    private void handleLogin(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both username and password");
            return;
        }

        try {
            boolean authenticated = examService.authenticateUser(username, password, isTeacher);

            if (authenticated) {
                // Get the student ID and name from the database
                String studentId = getStudentId(username);
                String studentName = getStudentName(username);

                // Log the retrieved student ID for debugging
                System.out.println("Retrieved student ID: " + studentId);
                System.out.println("Retrieved student name: " + studentName);

                // Verify the student ID exists in the database
                if (studentId == null || studentId.isEmpty() || !verifyStudentIdExists(studentId)) {
                    statusLabel.setText("Error: Student ID not found in database");
                    return;
                }

                // Open the student dashboard directly without showing any alert
                openStudentDashboard(studentId, studentName);
            } else {
                statusLabel.setText("Invalid username or password");
            }
        } catch (Exception e) {
            statusLabel.setText("Login error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getStudentId(String username) {
        // This method should query the database to get the student ID based on the username
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            // Get database connection details from a config file or environment variables in a real app
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/exam_system", "root", "");
            String sql = "SELECT id FROM students WHERE username = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            rs = stmt.executeQuery();

            if (rs.next()) {
                String id = rs.getString("id");
                System.out.println("Database query returned student ID: " + id);
                return id;
            } else {
                System.out.println("No student ID found for username: " + username);
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving student ID: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close resources
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
                e.printStackTrace();
            }
        }

        // If we can't get the ID from the database, return the username as a fallback
        System.out.println("Using username as fallback student ID: " + username);
        return username;
    }

    private String getStudentName(String username) {
        // This method should query the database to get the student name based on the username
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            // Get database connection details from a config file or environment variables in a real app
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/exam_system", "root", "");
            String sql = "SELECT name FROM students WHERE username = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("name");
            }
        } catch (SQLException e) {
            System.err.println("Error retrieving student name: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Close resources
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
                e.printStackTrace();
            }
        }

        // If we can't get the name from the database, return the username as a fallback
        return username;
    }

    private boolean verifyStudentIdExists(String studentId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/exam_system", "root", "");
            String sql = "SELECT 1 FROM students WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setString(1, studentId);
            rs = stmt.executeQuery();

            boolean exists = rs.next();
            System.out.println("Student ID " + studentId + " exists in database: " + exists);
            return exists;
        } catch (SQLException e) {
            System.err.println("Error verifying student ID: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // Close resources
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
                e.printStackTrace();
            }
        }
    }

    private void openStudentDashboard(String studentId, String studentName) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/fxml/StudentDashboard.fxml"));
            Parent root = loader.load();

            StudentDashboardController controller = loader.getController();
            controller.setExamService(examService);
            controller.setStudentInfo(studentId, studentName);

            Stage stage = new Stage();
            stage.setTitle("Student Dashboard");
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.show();

            // Close the login window
            Stage loginStage = (Stage) usernameField.getScene().getWindow();
            loginStage.close();
        } catch (Exception e) {
            statusLabel.setText("Error opening dashboard: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
