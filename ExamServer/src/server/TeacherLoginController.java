package server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class TeacherLoginController {

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label statusLabel;

    private DatabaseManager dbManager;

    public void initialize() {
        dbManager = new DatabaseManager();
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
            if (authenticateTeacher(username, password)) {
                // Load the server main interface
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/server/fxml/ServerMain.fxml"));
                Parent root = loader.load();

                ServerMainController controller = loader.getController();
                controller.setLoggedInTeacher(username);

                // Create and start the exam service
                ExamServiceImpl examService = new ExamServiceImpl(controller);
                controller.setExamService(examService);

                // Get the current stage and set the new scene
                Stage stage = (Stage) usernameField.getScene().getWindow();
                stage.setTitle("Online Exam System - Server Admin");
                stage.setScene(new Scene(root, 900, 600));
                stage.centerOnScreen();

                // Start the RMI service
                ExamServer.startRMIService(examService);
            } else {
                statusLabel.setText("Invalid username or password");
            }
        } catch (Exception e) {
            statusLabel.setText("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean authenticateTeacher(String username, String password) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            String sql = "SELECT * FROM teachers WHERE username = ? AND password = ?";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            stmt.setString(2, password); // In a real app, use password hashing

            rs = stmt.executeQuery();
            return rs.next(); // Returns true if a matching teacher is found
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }
}
