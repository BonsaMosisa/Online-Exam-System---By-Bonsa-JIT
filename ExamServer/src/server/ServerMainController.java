package server;

import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;
import shared.Exam;
import shared.ExamResult;

public class ServerMainController implements Initializable {

    @FXML
    private Label statusLabel;
    @FXML
    private TableView<Exam> examsTable;
    @FXML
    private TableColumn<Exam, Integer> examIdColumn;
    @FXML
    private TableColumn<Exam, String> examTitleColumn;
    @FXML
    private TableColumn<Exam, String> examDescriptionColumn;
    @FXML
    private TableColumn<Exam, Integer> examDurationColumn;
    @FXML
    private TableColumn<Exam, Boolean> examResultsVisibleColumn;
    @FXML
    private TableColumn<Exam, Boolean> examActiveColumn;

    @FXML
    private ComboBox<Exam> examSelector;
    @FXML
    private TableView<ExamResult> resultsTable;
    @FXML
    private TableColumn<ExamResult, String> resultStudentIdColumn;
    @FXML
    private TableColumn<ExamResult, String> resultStudentNameColumn;
    @FXML
    private TableColumn<ExamResult, Integer> resultScoreColumn;
    @FXML
    private TableColumn<ExamResult, Integer> resultTotalColumn;
    @FXML
    private TableColumn<ExamResult, Double> resultPercentageColumn;
    @FXML
    private TableColumn<ExamResult, Date> resultSubmissionTimeColumn;

    @FXML
    private TableView<ActiveSessionDisplay> sessionsTable;
    @FXML
    private TableColumn<ActiveSessionDisplay, String> sessionStudentIdColumn;
    @FXML
    private TableColumn<ActiveSessionDisplay, Integer> sessionExamIdColumn;
    @FXML
    private TableColumn<ActiveSessionDisplay, String> sessionExamTitleColumn;
    @FXML
    private TableColumn<ActiveSessionDisplay, String> sessionStartTimeColumn;
    @FXML
    private TableColumn<ActiveSessionDisplay, String> sessionTimeRemainingColumn;

    @FXML
    private TextArea logTextArea;

    private ExamServiceImpl examService;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private String loggedInTeacher;
    private DatabaseManager dbManager;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Initialize database manager
        dbManager = new DatabaseManager();

        // Initialize table columns
        examIdColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        examTitleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        examDescriptionColumn.setCellValueFactory(new PropertyValueFactory<>("description"));
        examDurationColumn.setCellValueFactory(new PropertyValueFactory<>("durationMinutes"));
        examResultsVisibleColumn.setCellValueFactory(new PropertyValueFactory<>("resultsVisible"));
        examActiveColumn.setCellValueFactory(new PropertyValueFactory<>("active"));

        resultStudentIdColumn.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        resultStudentNameColumn.setCellValueFactory(new PropertyValueFactory<>("studentName"));
        resultScoreColumn.setCellValueFactory(new PropertyValueFactory<>("score"));
        resultTotalColumn.setCellValueFactory(new PropertyValueFactory<>("totalPossible"));
        resultPercentageColumn.setCellValueFactory(new PropertyValueFactory<>("percentage"));
        resultSubmissionTimeColumn.setCellValueFactory(new PropertyValueFactory<>("submissionTime"));

        sessionStudentIdColumn.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        sessionExamIdColumn.setCellValueFactory(new PropertyValueFactory<>("examId"));
        sessionExamTitleColumn.setCellValueFactory(new PropertyValueFactory<>("examTitle"));
        sessionStartTimeColumn.setCellValueFactory(new PropertyValueFactory<>("startTime"));
        sessionTimeRemainingColumn.setCellValueFactory(new PropertyValueFactory<>("timeRemaining"));

        // Log server start
        logActivity("Server started");

        // Set up a timer to refresh active sessions every 5 seconds
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(5000); // 5 seconds
                    Platform.runLater(() -> {
                        if (examService != null) {
                            refreshSessions();
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }

    public void setExamService(ExamServiceImpl examService) {
        this.examService = examService;
        refreshExams();
    }

    public void setLoggedInTeacher(String username) {
        this.loggedInTeacher = username;
        logActivity("Teacher " + username + " logged in");
        statusLabel.setText("Logged in as: " + username);
    }

    public void logActivity(String message) {
        Platform.runLater(() -> {
            String timestamp = timeFormat.format(new Date());
            logTextArea.appendText("[" + timestamp + "] " + message + "\n");
        });
    }

    @FXML
    private void handleCreateExam(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/server/fxml/ExamEditor.fxml"));
            Parent root = loader.load();

            ExamEditorController controller = loader.getController();
            controller.setExamService(examService);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Create New Exam");
            stage.setScene(new Scene(root));
            stage.showAndWait();

            refreshExams();
        } catch (Exception e) {
            logActivity("Error opening exam editor: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open exam editor", e.getMessage());
        }
    }

    @FXML
    private void handleEditExam(ActionEvent event) {
        Exam selectedExam = examsTable.getSelectionModel().getSelectedItem();
        if (selectedExam == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "No Exam Selected",
                    "Please select an exam to edit.");
            return;
        }

        try {
            // Load the full exam with questions
            Exam fullExam = loadExamWithQuestions(selectedExam.getId());

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/server/fxml/ExamEditor.fxml"));
            Parent root = loader.load();

            ExamEditorController controller = loader.getController();
            controller.setExamService(examService);
            controller.loadExam(fullExam);

            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Edit Exam");
            stage.setScene(new Scene(root));
            stage.showAndWait();

            refreshExams();
        } catch (Exception e) {
            logActivity("Error opening exam editor: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open exam editor", e.getMessage());
        }
    }

    private Exam loadExamWithQuestions(int examId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();

            // Get exam details
            String examSql = "SELECT * FROM exams WHERE id = ?";
            stmt = conn.prepareStatement(examSql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new Exception("Exam not found");
            }

            Exam exam = new Exam(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getInt("duration_minutes"),
                    rs.getBoolean("results_visible")
            );

            // Get questions
            dbManager.closeResources(null, stmt, rs);
            String questionsSql = "SELECT q.* FROM questions q "
                    + "JOIN exam_questions eq ON q.id = eq.question_id "
                    + "WHERE eq.exam_id = ?";
            stmt = conn.prepareStatement(questionsSql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            List<shared.Question> questions = new ArrayList<>();
            while (rs.next()) {
                int questionId = rs.getInt("id");
                shared.Question question = new shared.Question(
                        questionId,
                        rs.getString("text"),
                        getOptionsForQuestion(questionId),
                        rs.getInt("correct_option"),
                        rs.getInt("points")
                );
                questions.add(question);
            }

            exam.setQuestions(questions);
            return exam;
        } catch (Exception e) {
            logActivity("Error loading exam with questions: " + e.getMessage());
            e.printStackTrace();
            return new Exam(examId, "Error loading exam", "", 0, false);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    private List<String> getOptionsForQuestion(int questionId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<String> options = new ArrayList<>();

        try {
            conn = dbManager.getConnection();
            String sql = "SELECT * FROM question_options WHERE question_id = ? ORDER BY option_order";

            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, questionId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                options.add(rs.getString("option_text"));
            }

            return options;
        } catch (Exception e) {
            logActivity("Error getting options for question: " + e.getMessage());
            e.printStackTrace();
            return options;
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @FXML
    private void handleDeleteExam(ActionEvent event) {
        Exam selectedExam = examsTable.getSelectionModel().getSelectedItem();
        if (selectedExam == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "No Exam Selected",
                    "Please select an exam to delete.");
            return;
        }

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Delete");
        alert.setHeaderText("Delete Exam");
        alert.setContentText("Are you sure you want to delete the exam: " + selectedExam.getTitle() + "?");

        if (alert.showAndWait().get() == ButtonType.OK) {
            try {
                examService.deleteExam(selectedExam.getId());
                refreshExams();
            } catch (Exception e) {
                logActivity("Error deleting exam: " + e.getMessage());
                showAlert(Alert.AlertType.ERROR, "Error", "Could not delete exam", e.getMessage());
            }
        }
    }

    @FXML
    private void handleRefreshExams(ActionEvent event) {
        refreshExams();
    }

    @FXML
    private void handleExamSelected(ActionEvent event) {
        refreshResults();
    }

    @FXML
    private void handleToggleResultsVisibility(ActionEvent event) {
        Exam selectedExam = examSelector.getValue();
        if (selectedExam == null) {
            showAlert(Alert.AlertType.WARNING, "No Selection", "No Exam Selected",
                    "Please select an exam to toggle results visibility.");
            return;
        }

        try {
            boolean newVisibility = !selectedExam.isResultsVisible();
            examService.setResultVisibility(selectedExam.getId(), newVisibility);
            selectedExam.setResultsVisible(newVisibility);

            refreshExams();
            refreshResults();

            String message = "Results for exam '" + selectedExam.getTitle() + "' are now "
                    + (newVisibility ? "visible" : "hidden") + " to students.";
            showAlert(Alert.AlertType.INFORMATION, "Results Visibility", "Visibility Updated", message);
        } catch (Exception e) {
            logActivity("Error toggling results visibility: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Could not toggle results visibility", e.getMessage());
        }
    }

    @FXML
    private void handleRefreshResults(ActionEvent event) {
        refreshResults();
    }

    @FXML
    private void handleRefreshSessions(ActionEvent event) {
        refreshSessions();
    }

    @FXML
    private void handleClearLog(ActionEvent event) {
        logTextArea.clear();
        logActivity("Log cleared");
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        try {
            // Shutdown the thread pool
            if (examService != null) {
                examService.shutdown();
            }

            // Load the login screen
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/server/fxml/TeacherLogin.fxml"));
            Parent root = loader.load();

            // Get the current stage and set the new scene
            Stage stage = (Stage) statusLabel.getScene().getWindow();
            stage.setTitle("Online Exam System - Teacher Login");
            stage.setScene(new Scene(root, 400, 300));
            stage.centerOnScreen();

            // Log the logout
            logActivity("Teacher " + loggedInTeacher + " logged out");
        } catch (Exception e) {
            logActivity("Error during logout: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Could not logout", e.getMessage());
        }
    }

    public void refreshExams() {
        try {
            Connection conn = null;
            PreparedStatement stmt = null;
            ResultSet rs = null;

            try {
                conn = dbManager.getConnection();
                // Modified SQL to get ALL exams, not just active ones
                String sql = "SELECT * FROM exams ORDER BY id DESC";

                stmt = conn.prepareStatement(sql);
                rs = stmt.executeQuery();

                ObservableList<Exam> exams = FXCollections.observableArrayList();

                while (rs.next()) {
                    Exam exam = new Exam(
                            rs.getInt("id"),
                            rs.getString("title"),
                            rs.getString("description"),
                            rs.getInt("duration_minutes"),
                            rs.getBoolean("results_visible")
                    );
                    exam.setActive(rs.getBoolean("active"));
                    exams.add(exam);
                }

                examsTable.setItems(exams);
                examSelector.setItems(exams);

                logActivity("Refreshed exams list - found " + exams.size() + " exams");
            } finally {
                dbManager.closeResources(conn, stmt, rs);
            }
        } catch (Exception e) {
            logActivity("Error refreshing exams: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Could not refresh exams", e.getMessage());
        }
    }

    private void refreshResults() {
        Exam selectedExam = examSelector.getValue();
        if (selectedExam == null) {
            resultsTable.setItems(FXCollections.observableArrayList());
            return;
        }

        try {
            List<ExamResult> results = examService.getExamResults(selectedExam.getId());
            resultsTable.setItems(FXCollections.observableArrayList(results));

            logActivity("Refreshed results for exam: " + selectedExam.getTitle());
        } catch (Exception e) {
            logActivity("Error refreshing results: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Error", "Could not refresh results", e.getMessage());
        }
    }

    public void refreshSessions() {
        try {
            if (examService != null) {
                List<ActiveSessionDisplay> sessions = examService.getActiveSessions();
                sessionsTable.setItems(FXCollections.observableArrayList(sessions));

                logActivity("Refreshed active sessions");
            }
        } catch (Exception e) {
            logActivity("Error refreshing sessions: " + e.getMessage());
            // Don't show alert for automatic refreshes
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
