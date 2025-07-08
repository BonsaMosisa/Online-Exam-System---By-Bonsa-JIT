package client;

import java.util.List;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import shared.Exam;
import shared.ExamResult;
import shared.RemoteExamService;

public class StudentDashboardController {

    @FXML
    private Label studentInfoLabel;

    @FXML
    private TableView<Exam> availableExamsTable;

    @FXML
    private TableColumn<Exam, Integer> examIdColumn;

    @FXML
    private TableColumn<Exam, String> examTitleColumn;

    @FXML
    private TableColumn<Exam, String> examDescriptionColumn;

    @FXML
    private TableColumn<Exam, Integer> examDurationColumn;

    @FXML
    private TableView<ExamResult> resultsTable;

    @FXML
    private TableColumn<ExamResult, Integer> resultExamIdColumn;

    @FXML
    private TableColumn<ExamResult, String> resultExamTitleColumn;

    @FXML
    private TableColumn<ExamResult, Integer> resultScoreColumn;

    @FXML
    private TableColumn<ExamResult, Integer> resultTotalColumn;

    @FXML
    private TableColumn<ExamResult, Double> resultPercentageColumn;

    @FXML
    private TableColumn<ExamResult, java.util.Date> resultSubmissionTimeColumn;

    private RemoteExamService examService;
    private String studentId;
    private String studentName;

    public void initialize() {
        // Set up the available exams table columns
        examIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        examTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        examDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        examDurationColumn.setCellValueFactory(new PropertyValueFactory<>("durationMinutes"));

        // Set up the results table columns
        resultExamIdColumn.setCellValueFactory(new PropertyValueFactory<>("examId"));
        resultExamTitleColumn.setCellValueFactory(new PropertyValueFactory<>("examTitle"));
        resultScoreColumn.setCellValueFactory(new PropertyValueFactory<>("score"));
        resultTotalColumn.setCellValueFactory(new PropertyValueFactory<>("totalPossible"));
        resultPercentageColumn.setCellValueFactory(new PropertyValueFactory<>("percentage"));
        resultSubmissionTimeColumn.setCellValueFactory(new PropertyValueFactory<>("submissionTime"));
    }

    public void setExamService(RemoteExamService examService) {
        this.examService = examService;
    }

    public void setStudentInfo(String studentId, String studentName) {
        this.studentId = studentId;
        this.studentName = studentName;
        studentInfoLabel.setText("Student: " + studentName + " (ID: " + studentId + ")");

        // Log the student ID for debugging
        System.out.println("Student dashboard initialized with ID: " + studentId);

        // Load available exams and results
        handleRefreshExams(null);
        handleRefreshResults(null);
    }

    @FXML
    private void handleRefreshExams(ActionEvent event) {
        try {
            List<Exam> exams = examService.getAvailableExams(studentId);
            availableExamsTable.setItems(FXCollections.observableArrayList(exams));

            if (exams.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No Exams", "No Available Exams",
                        "There are no available exams for you at this time.");
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not refresh exams", e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleRefreshResults(ActionEvent event) {
        try {
            // Create a list to store results
            java.util.List<ExamResult> allResults = new java.util.ArrayList<>();

            // Get completed exams
            List<Exam> completedExams = getCompletedExams();

            if (completedExams.isEmpty()) {
                showAlert(Alert.AlertType.INFORMATION, "No Results", "No Completed Exams",
                        "You haven't completed any exams yet.");
                return;
            }

            // For each completed exam, try to get the result if results are visible
            boolean hasHiddenResults = false;
            for (Exam exam : completedExams) {
                try {
                    // Check if results are visible for this exam
                    if (exam.isResultsVisible()) {
                        ExamResult result = examService.getExamResult(exam.getId(), studentId);
                        if (result != null) {
                            allResults.add(result);
                        }
                    } else {
                        // Results exist but are not visible yet
                        hasHiddenResults = true;
                        System.out.println("Results for exam " + exam.getId() + " are not yet released by the teacher");
                    }
                } catch (Exception e) {
                    System.out.println("Error getting result for exam " + exam.getId() + ": " + e.getMessage());
                    // Only count as hidden if it's specifically about visibility
                    if (e.getMessage() != null && e.getMessage().contains("not available for viewing")) {
                        hasHiddenResults = true;
                    }
                }
            }

            // Update the table with all visible results
            resultsTable.setItems(FXCollections.observableArrayList(allResults));

            if (allResults.isEmpty()) {
                if (hasHiddenResults) {
                    showAlert(Alert.AlertType.INFORMATION, "Results Not Available", "Results Pending Release",
                            "You have completed exams, but the results have not been released by the teacher yet.");
                } else {
                    showAlert(Alert.AlertType.INFORMATION, "No Results", "No Results Available",
                            "There are no exam results available for you at this time.");
                }
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not refresh results", e.getMessage());
            e.printStackTrace();
        }
    }

    private List<Exam> getCompletedExams() {
        try {
            java.util.List<Exam> completedExams = new java.util.ArrayList<>();

            java.sql.Connection conn = null;
            java.sql.PreparedStatement stmt = null;
            java.sql.ResultSet rs = null;

            try {
                // Connect to the database
                conn = java.sql.DriverManager.getConnection("jdbc:mysql://localhost:3306/exam_system", "root", "");

                // Query to find exams that have results for this student
                // This means the student has completed these exams
                String sql = "SELECT e.id, e.title, e.description, e.duration_minutes, e.results_visible "
                        + "FROM exams e "
                        + "JOIN exam_results r ON e.id = r.exam_id "
                        + "WHERE r.student_id = ?";

                stmt = conn.prepareStatement(sql);
                stmt.setString(1, studentId);
                rs = stmt.executeQuery();

                while (rs.next()) {
                    int examId = rs.getInt("id");
                    boolean resultsVisible = rs.getBoolean("results_visible");

                    // Create an Exam object with the results_visible flag
                    Exam exam = new Exam(
                            examId,
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getInt("duration_minutes"),
                            resultsVisible
                    );

                    completedExams.add(exam);
                }
            } finally {
                // Close resources
                if (rs != null) {
                    try {
                        rs.close();
                    } catch (Exception e) {
                    }
                }
                if (stmt != null) {
                    try {
                        stmt.close();
                    } catch (Exception e) {
                    }
                }
                if (conn != null) {
                    try {
                        conn.close();
                    } catch (Exception e) {
                    }
                }
            }

            return completedExams;
        } catch (Exception e) {
            e.printStackTrace();
            return new java.util.ArrayList<>();
        }
    }

    @FXML
    private void handleStartExam(ActionEvent event) {
        Exam selectedExam = availableExamsTable.getSelectionModel().getSelectedItem();
        if (selectedExam == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "No Exam Selected",
                    "Please select an exam to start.");
            return;
        }

        try {
            // Verify student ID before starting exam
            if (studentId == null || studentId.trim().isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "Error", "Invalid Student ID",
                        "Your student ID is invalid. Please log in again.");
                return;
            }

            System.out.println("Starting exam with student ID: " + studentId);

            // Get the exam with questions
            Exam examWithQuestions = examService.getExamQuestions(selectedExam.getId(), studentId);

            // Open the exam session window
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/fxml/ExamSession.fxml"));
            Parent root = loader.load();

            ExamSessionController controller = loader.getController();
            controller.setExamService(examService);
            controller.setStudentId(studentId);
            controller.setExam(examWithQuestions);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Exam: " + examWithQuestions.getTitle());
            stage.setScene(new Scene(root));
            stage.setMaximized(true);
            stage.setOnShown(e -> controller.startExam());

            // Use showAndWait with try-catch to handle potential exceptions
            try {
                stage.showAndWait();
            } catch (Exception e) {
                System.err.println("Error in showAndWait: " + e.getMessage());
                e.printStackTrace();
            }

            // Refresh exams after the exam session is closed
            handleRefreshExams(null);
            handleRefreshResults(null);
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not start exam", e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            // Close the current window
            Stage stage = (Stage) studentInfoLabel.getScene().getWindow();
            stage.close();

            // Open the login window
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/fxml/Login.fxml"));
            Parent root = loader.load();

            LoginController controller = loader.getController();
            controller.setExamService(examService);

            Stage loginStage = new Stage();
            loginStage.setTitle("Online Exam System - Login");
            loginStage.setScene(new Scene(root));
            loginStage.show();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not logout", e.getMessage());
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
