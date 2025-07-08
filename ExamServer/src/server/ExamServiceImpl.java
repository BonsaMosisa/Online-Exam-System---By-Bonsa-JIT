package server;

import java.rmi.RemoteException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import shared.*;

public class ExamServiceImpl implements RemoteExamService {

    private static final Logger LOGGER = Logger.getLogger(ExamServiceImpl.class.getName());

    private final DatabaseManager dbManager;
    private final ServerMainController controller;

    // Thread pool for handling concurrent student requests
    private final ExecutorService threadPool;

    // Track active exam sessions - using ConcurrentHashMap for thread safety
    private final Map<String, ActiveExamSession> activeExams = new ConcurrentHashMap<>();

    public ExamServiceImpl(ServerMainController controller) {
        this.dbManager = new DatabaseManager();
        this.controller = controller;

        // Create a thread pool with a fixed number of threads
        // Adjust the number based on expected concurrent users
        this.threadPool = Executors.newFixedThreadPool(20);

        controller.logActivity("Thread pool initialized with 20 threads for concurrent student sessions");
    }

    @Override
    public boolean authenticateUser(String username, String password, boolean isTeacher) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            String table = isTeacher ? "teachers" : "students";
            String sql = "SELECT * FROM " + table + " WHERE username = ? AND password = ?";

            stmt = conn.prepareStatement(sql);
            stmt.setString(1, username);
            stmt.setString(2, password); // In a real app, use password hashing

            rs = stmt.executeQuery();
            boolean authenticated = rs.next();

            // Log the authentication attempt
            controller.logActivity(username + " (" + (isTeacher ? "teacher" : "student")
                    + ") authentication " + (authenticated ? "successful" : "failed"));

            return authenticated;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Authentication error", e);
            controller.logActivity("Authentication error: " + e.getMessage());
            throw new RemoteException("Authentication failed", e);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public List<Exam> getAvailableExams(String studentId) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<Exam> exams = new ArrayList<>();

        try {
            conn = dbManager.getConnection();

            // First, get exams the student has already taken
            String takenSql = "SELECT exam_id FROM exam_results WHERE student_id = ?";
            stmt = conn.prepareStatement(takenSql);
            stmt.setString(1, studentId);
            rs = stmt.executeQuery();

            Set<Integer> takenExamIds = new HashSet<>();
            while (rs.next()) {
                takenExamIds.add(rs.getInt("exam_id"));
            }

            // Close previous resources
            dbManager.closeResources(null, stmt, rs);

            // Get all active exams
            String sql = "SELECT * FROM exams WHERE active = 1";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();

            while (rs.next()) {
                int examId = rs.getInt("id");

                // Skip exams the student has already taken
                if (takenExamIds.contains(examId)) {
                    continue;
                }

                Exam exam = new Exam(
                        examId,
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("duration_minutes"),
                        rs.getBoolean("results_visible")
                );
                exam.setActive(rs.getBoolean("active"));
                exams.add(exam);
            }

            controller.logActivity("Student " + studentId + " retrieved available exams - found " + exams.size() + " exams");
            return exams;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving available exams", e);
            controller.logActivity("Error retrieving exams: " + e.getMessage());
            throw new RemoteException("Failed to retrieve exams: " + e.getMessage(), e);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public Exam getExamQuestions(int examId, String studentId) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            // First check if the student has already taken this exam
            conn = dbManager.getConnection();
            String checkSql = "SELECT * FROM exam_results WHERE exam_id = ? AND student_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, examId);
            stmt.setString(2, studentId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                controller.logActivity("Student " + studentId + " attempted to retake exam " + examId);
                throw new RemoteException("You have already taken this exam");
            }

            // Close previous resources
            dbManager.closeResources(null, stmt, rs);

            // Get the exam details
            String examSql = "SELECT * FROM exams WHERE id = ?";
            stmt = conn.prepareStatement(examSql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new RemoteException("Exam not found");
            }

            Exam exam = new Exam(
                    rs.getInt("id"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getInt("duration_minutes"),
                    rs.getBoolean("results_visible")
            );
            exam.setActive(rs.getBoolean("active"));

            // Close previous resources
            dbManager.closeResources(null, stmt, rs);

            // Get the questions for this exam
            String questionsSql = "SELECT q.* FROM questions q "
                    + "JOIN exam_questions eq ON q.id = eq.question_id "
                    + "WHERE eq.exam_id = ?";
            stmt = conn.prepareStatement(questionsSql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            List<Question> questions = new ArrayList<>();
            while (rs.next()) {
                int questionId = rs.getInt("id");
                Question question = new Question(
                        questionId,
                        rs.getString("text"),
                        getOptionsForQuestion(questionId),
                        rs.getInt("correct_option"),
                        rs.getInt("points")
                );
                questions.add(question);
            }

            exam.setQuestions(questions);

            // Create an active exam session
            ActiveExamSession session = new ActiveExamSession(
                    examId,
                    studentId,
                    System.currentTimeMillis(),
                    exam.getDurationMinutes() * 60 * 1000
            );
            activeExams.put(studentId + "-" + examId, session);

            controller.logActivity("Student " + studentId + " started exam " + examId);

            // Update active sessions in the UI
            controller.refreshSessions();

            return exam;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving exam questions", e);
            controller.logActivity("Error retrieving exam questions: " + e.getMessage());
            throw new RemoteException("Failed to retrieve exam questions: " + e.getMessage(), e);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    private List<String> getOptionsForQuestion(int questionId) throws SQLException {
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
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public boolean submitExam(int examId, String studentId, List<Answer> answers) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            // First verify that the student exists in the database
            conn = dbManager.getConnection();
            String checkStudentSql = "SELECT id FROM students WHERE id = ?";
            stmt = conn.prepareStatement(checkStudentSql);
            stmt.setString(1, studentId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                controller.logActivity("Error: Student ID " + studentId + " not found in database");
                throw new RemoteException("Student ID not found in database. Please contact your administrator.");
            }

            // Close previous resources
            dbManager.closeResources(null, stmt, rs);

            // Check if the student has already submitted this exam
            String checkSql = "SELECT COUNT(*) FROM exam_results WHERE exam_id = ? AND student_id = ?";
            stmt = conn.prepareStatement(checkSql);
            stmt.setInt(1, examId);
            stmt.setString(2, studentId);
            rs = stmt.executeQuery();

            if (rs.next() && rs.getInt(1) > 0) {
                controller.logActivity("Student " + studentId + " attempted to resubmit exam " + examId);
                throw new RemoteException("You have already submitted this exam");
            }

            // Close previous resources
            dbManager.closeResources(null, stmt, rs);

            // Check if the exam exists
            String checkExamSql = "SELECT id FROM exams WHERE id = ?";
            stmt = conn.prepareStatement(checkExamSql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                controller.logActivity("Error: Exam ID " + examId + " not found in database");
                throw new RemoteException("Exam ID not found in database. Please contact your administrator.");
            }

            // Close previous resources
            dbManager.closeResources(null, stmt, rs);

            // Get a connection and disable auto-commit for transaction
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);

            try {
                // Calculate the score
                int score = 0;
                int totalPossible = 0;

                Map<Integer, Integer> questionPoints = new HashMap<>();
                Map<Integer, Integer> correctAnswers = new HashMap<>();

                // Get all questions and their correct answers
                String questionsSql = "SELECT id, correct_option, points FROM questions "
                        + "WHERE id IN (SELECT question_id FROM exam_questions WHERE exam_id = ?)";
                stmt = conn.prepareStatement(questionsSql);
                stmt.setInt(1, examId);
                rs = stmt.executeQuery();

                while (rs.next()) {
                    int questionId = rs.getInt("id");
                    int correctOption = rs.getInt("correct_option");
                    int points = rs.getInt("points");

                    questionPoints.put(questionId, points);
                    correctAnswers.put(questionId, correctOption);
                    totalPossible += points;
                }

                // Calculate score based on answers
                for (Answer answer : answers) {
                    int questionId = answer.getQuestionId();
                    int selectedOption = answer.getSelectedOptionIndex();

                    if (correctAnswers.containsKey(questionId)
                            && correctAnswers.get(questionId) == selectedOption) {
                        score += questionPoints.get(questionId);
                    }
                }

                // Save the result to the database
                dbManager.closeResources(null, stmt, rs);

                String resultSql = "INSERT INTO exam_results (exam_id, student_id, score, total_possible, submission_time) "
                        + "VALUES (?, ?, ?, ?, NOW())";
                stmt = conn.prepareStatement(resultSql);
                stmt.setInt(1, examId);
                stmt.setString(2, studentId);
                stmt.setInt(3, score);
                stmt.setInt(4, totalPossible);

                controller.logActivity("Inserting exam result for student " + studentId + " with exam " + examId);
                int resultRows = stmt.executeUpdate();

                if (resultRows != 1) {
                    throw new SQLException("Failed to insert exam result");
                }

                // Save individual answers
                dbManager.closeResources(null, stmt, null);

                String answerSql = "INSERT INTO student_answers (exam_id, student_id, question_id, selected_option) "
                        + "VALUES (?, ?, ?, ?)";
                stmt = conn.prepareStatement(answerSql);

                for (Answer answer : answers) {
                    stmt.setInt(1, examId);
                    stmt.setString(2, studentId);
                    stmt.setInt(3, answer.getQuestionId());
                    stmt.setInt(4, answer.getSelectedOptionIndex());
                    stmt.addBatch();
                }

                int[] answerResults = stmt.executeBatch();

                // Commit the transaction
                conn.commit();

                controller.logActivity("Student " + studentId + " submitted exam " + examId
                        + " with score " + score + "/" + totalPossible);

                return true;
            } catch (SQLException e) {
                // Rollback the transaction in case of error
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        controller.logActivity("Error rolling back transaction: " + ex.getMessage());
                    }
                }
                controller.logActivity("Error submitting exam: " + e.getMessage());
                throw new RemoteException("Failed to submit exam: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            controller.logActivity("Database error during exam submission: " + e.getMessage());
            throw new RemoteException("Failed to submit exam: " + e.getMessage(), e);
        } finally {
            // Restore auto-commit
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    controller.logActivity("Error restoring auto-commit: " + e.getMessage());
                }
            }
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public ExamResult getExamResult(int examId, String studentId) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();

            // First check if results are visible for this exam
            String examSql = "SELECT results_visible FROM exams WHERE id = ?";
            stmt = conn.prepareStatement(examSql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new RemoteException("Exam not found");
            }

            boolean resultsVisible = rs.getBoolean("results_visible");

            if (!resultsVisible) {
                controller.logActivity("Student " + studentId + " attempted to view results for exam "
                        + examId + " but results are not visible");
                throw new RemoteException("Results are not available for viewing yet");
            }

            // Close previous resources
            dbManager.closeResources(null, stmt, rs);

            // Get the exam result
            String resultSql = "SELECT er.*, s.name as student_name, e.title as exam_title "
                    + "FROM exam_results er "
                    + "JOIN students s ON er.student_id = s.id "
                    + "JOIN exams e ON er.exam_id = e.id "
                    + "WHERE er.exam_id = ? AND er.student_id = ?";
            stmt = conn.prepareStatement(resultSql);
            stmt.setInt(1, examId);
            stmt.setString(2, studentId);
            rs = stmt.executeQuery();

            if (!rs.next()) {
                throw new RemoteException("No result found for this exam");
            }

            ExamResult result = new ExamResult(
                    rs.getInt("id"),
                    rs.getInt("exam_id"),
                    rs.getString("student_id"),
                    rs.getString("student_name"),
                    rs.getInt("score"),
                    rs.getInt("total_possible"),
                    rs.getTimestamp("submission_time")
            );
            result.setExamTitle(rs.getString("exam_title"));

            controller.logActivity("Student " + studentId + " viewed results for exam " + examId);
            return result;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving exam result", e);
            controller.logActivity("Error retrieving exam result: " + e.getMessage());
            throw new RemoteException("Failed to retrieve exam result: " + e.getMessage(), e);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public List<ExamResult> getExamResults(int examId) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<ExamResult> results = new ArrayList<>();

        try {
            conn = dbManager.getConnection();
            String sql = "SELECT er.*, s.name as student_name, e.title as exam_title "
                    + "FROM exam_results er "
                    + "JOIN students s ON er.student_id = s.id "
                    + "JOIN exams e ON er.exam_id = e.id "
                    + "WHERE er.exam_id = ? "
                    + "ORDER BY er.score DESC";

            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            while (rs.next()) {
                ExamResult result = new ExamResult(
                        rs.getInt("id"),
                        rs.getInt("exam_id"),
                        rs.getString("student_id"),
                        rs.getString("student_name"),
                        rs.getInt("score"),
                        rs.getInt("total_possible"),
                        rs.getTimestamp("submission_time")
                );
                result.setExamTitle(rs.getString("exam_title"));
                results.add(result);
            }

            controller.logActivity("Retrieved results for exam " + examId);
            return results;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error retrieving exam results", e);
            controller.logActivity("Error retrieving exam results: " + e.getMessage());
            throw new RemoteException("Failed to retrieve exam results: " + e.getMessage(), e);
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public boolean createExam(Exam exam) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);

            try {
                // Insert the exam
                String examSql = "INSERT INTO exams (title, description, duration_minutes, results_visible, active) "
                        + "VALUES (?, ?, ?, ?, ?)";
                stmt = conn.prepareStatement(examSql, Statement.RETURN_GENERATED_KEYS);
                stmt.setString(1, exam.getTitle());
                stmt.setString(2, exam.getDescription());
                stmt.setInt(3, exam.getDurationMinutes());
                stmt.setBoolean(4, exam.isResultsVisible());
                stmt.setBoolean(5, exam.isActive());

                int examRows = stmt.executeUpdate();
                if (examRows != 1) {
                    throw new SQLException("Failed to insert exam");
                }

                // Get the generated exam ID
                rs = stmt.getGeneratedKeys();
                if (!rs.next()) {
                    throw new SQLException("Failed to get generated exam ID");
                }
                int examId = rs.getInt(1);
                exam.setId(examId);

                // Insert the questions
                if (exam.getQuestions() != null && !exam.getQuestions().isEmpty()) {
                    for (Question question : exam.getQuestions()) {
                        // Insert the question
                        dbManager.closeResources(null, stmt, rs);
                        String questionSql = "INSERT INTO questions (text, correct_option, points) "
                                + "VALUES (?, ?, ?)";
                        stmt = conn.prepareStatement(questionSql, Statement.RETURN_GENERATED_KEYS);
                        stmt.setString(1, question.getText());
                        stmt.setInt(2, question.getCorrectOptionIndex());
                        stmt.setInt(3, question.getPoints());

                        int questionRows = stmt.executeUpdate();
                        if (questionRows != 1) {
                            throw new SQLException("Failed to insert question");
                        }

                        // Get the generated question ID
                        rs = stmt.getGeneratedKeys();
                        if (!rs.next()) {
                            throw new SQLException("Failed to get generated question ID");
                        }
                        int questionId = rs.getInt(1);
                        question.setId(questionId);

                        // Link the question to the exam
                        dbManager.closeResources(null, stmt, rs);
                        String linkSql = "INSERT INTO exam_questions (exam_id, question_id) VALUES (?, ?)";
                        stmt = conn.prepareStatement(linkSql);
                        stmt.setInt(1, examId);
                        stmt.setInt(2, questionId);

                        int linkRows = stmt.executeUpdate();
                        if (linkRows != 1) {
                            throw new SQLException("Failed to link question to exam");
                        }

                        // Insert the options
                        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
                            dbManager.closeResources(null, stmt, null);
                            String optionSql = "INSERT INTO question_options (question_id, option_text, option_order) "
                                    + "VALUES (?, ?, ?)";
                            stmt = conn.prepareStatement(optionSql);

                            for (int i = 0; i < question.getOptions().size(); i++) {
                                stmt.setInt(1, questionId);
                                stmt.setString(2, question.getOptions().get(i));
                                stmt.setInt(3, i);
                                stmt.addBatch();
                            }

                            int[] optionResults = stmt.executeBatch();
                            for (int result : optionResults) {
                                if (result != 1) {
                                    throw new SQLException("Failed to insert question option");
                                }
                            }
                        }
                    }
                }

                // Commit the transaction
                conn.commit();
                controller.logActivity("Created exam: " + exam.getTitle());

                // Refresh the UI
                controller.refreshExams();

                return true;
            } catch (SQLException e) {
                // Rollback the transaction in case of error
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        LOGGER.log(Level.SEVERE, "Error rolling back transaction", ex);
                        controller.logActivity("Error rolling back transaction: " + ex.getMessage());
                    }
                }
                LOGGER.log(Level.SEVERE, "Error creating exam", e);
                controller.logActivity("Error creating exam: " + e.getMessage());
                throw new RemoteException("Failed to create exam: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during exam creation", e);
            controller.logActivity("Database error during exam creation: " + e.getMessage());
            throw new RemoteException("Failed to create exam: " + e.getMessage(), e);
        } finally {
            // Restore auto-commit
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Error restoring auto-commit", e);
                    controller.logActivity("Error restoring auto-commit: " + e.getMessage());
                }
            }
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public boolean updateExam(Exam exam) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);

            try {
                // Update the exam
                String examSql = "UPDATE exams SET title = ?, description = ?, duration_minutes = ?, "
                        + "results_visible = ?, active = ? WHERE id = ?";
                stmt = conn.prepareStatement(examSql);
                stmt.setString(1, exam.getTitle());
                stmt.setString(2, exam.getDescription());
                stmt.setInt(3, exam.getDurationMinutes());
                stmt.setBoolean(4, exam.isResultsVisible());
                stmt.setBoolean(5, exam.isActive());
                stmt.setInt(6, exam.getId());

                int examRows = stmt.executeUpdate();
                if (examRows != 1) {
                    throw new SQLException("Failed to update exam");
                }

                // Delete existing questions and options
                dbManager.closeResources(null, stmt, null);

                // First get all question IDs for this exam
                String getQuestionsSql = "SELECT question_id FROM exam_questions WHERE exam_id = ?";
                stmt = conn.prepareStatement(getQuestionsSql);
                stmt.setInt(1, exam.getId());
                rs = stmt.executeQuery();

                List<Integer> questionIds = new ArrayList<>();
                while (rs.next()) {
                    questionIds.add(rs.getInt("question_id"));
                }

                // Delete options for each question
                dbManager.closeResources(null, stmt, rs);
                if (!questionIds.isEmpty()) {
                    for (int questionId : questionIds) {
                        String deleteOptionsSql = "DELETE FROM question_options WHERE question_id = ?";
                        stmt = conn.prepareStatement(deleteOptionsSql);
                        stmt.setInt(1, questionId);
                        stmt.executeUpdate();
                    }
                }

                // Delete exam_questions links
                dbManager.closeResources(null, stmt, null);
                String deleteLinksSql = "DELETE FROM exam_questions WHERE exam_id = ?";
                stmt = conn.prepareStatement(deleteLinksSql);
                stmt.setInt(1, exam.getId());
                stmt.executeUpdate();

                // Delete questions
                dbManager.closeResources(null, stmt, null);
                if (!questionIds.isEmpty()) {
                    for (int questionId : questionIds) {
                        String deleteQuestionSql = "DELETE FROM questions WHERE id = ?";
                        stmt = conn.prepareStatement(deleteQuestionSql);
                        stmt.setInt(1, questionId);
                        stmt.executeUpdate();
                    }
                }

                // Insert the new questions
                if (exam.getQuestions() != null && !exam.getQuestions().isEmpty()) {
                    for (Question question : exam.getQuestions()) {
                        // Insert the question
                        dbManager.closeResources(null, stmt, null);
                        String questionSql = "INSERT INTO questions (text, correct_option, points) "
                                + "VALUES (?, ?, ?)";
                        stmt = conn.prepareStatement(questionSql, Statement.RETURN_GENERATED_KEYS);
                        stmt.setString(1, question.getText());
                        stmt.setInt(2, question.getCorrectOptionIndex());
                        stmt.setInt(3, question.getPoints());

                        int questionRows = stmt.executeUpdate();
                        if (questionRows != 1) {
                            throw new SQLException("Failed to insert question");
                        }

                        // Get the generated question ID
                        rs = stmt.getGeneratedKeys();
                        if (!rs.next()) {
                            throw new SQLException("Failed to get generated question ID");
                        }
                        int questionId = rs.getInt(1);
                        question.setId(questionId);

                        // Link the question to the exam
                        dbManager.closeResources(null, stmt, rs);
                        String linkSql = "INSERT INTO exam_questions (exam_id, question_id) VALUES (?, ?)";
                        stmt = conn.prepareStatement(linkSql);
                        stmt.setInt(1, exam.getId());
                        stmt.setInt(2, questionId);

                        int linkRows = stmt.executeUpdate();
                        if (linkRows != 1) {
                            throw new SQLException("Failed to link question to exam");
                        }

                        // Insert the options
                        if (question.getOptions() != null && !question.getOptions().isEmpty()) {
                            dbManager.closeResources(null, stmt, null);
                            String optionSql = "INSERT INTO question_options (question_id, option_text, option_order) "
                                    + "VALUES (?, ?, ?)";
                            stmt = conn.prepareStatement(optionSql);

                            for (int i = 0; i < question.getOptions().size(); i++) {
                                stmt.setInt(1, questionId);
                                stmt.setString(2, question.getOptions().get(i));
                                stmt.setInt(3, i);
                                stmt.addBatch();
                            }

                            int[] optionResults = stmt.executeBatch();
                            for (int result : optionResults) {
                                if (result != 1) {
                                    throw new SQLException("Failed to insert question option");
                                }
                            }
                        }
                    }
                }

                // Commit the transaction
                conn.commit();
                controller.logActivity("Updated exam: " + exam.getTitle());

                // Refresh the UI
                controller.refreshExams();

                return true;
            } catch (SQLException e) {
                // Rollback the transaction in case of error
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        LOGGER.log(Level.SEVERE, "Error rolling back transaction", ex);
                        controller.logActivity("Error rolling back transaction: " + ex.getMessage());
                    }
                }
                LOGGER.log(Level.SEVERE, "Error updating exam", e);
                controller.logActivity("Error updating exam: " + e.getMessage());
                throw new RemoteException("Failed to update exam: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during exam update", e);
            controller.logActivity("Database error during exam update: " + e.getMessage());
            throw new RemoteException("Failed to update exam: " + e.getMessage(), e);
        } finally {
            // Restore auto-commit
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Error restoring auto-commit", e);
                    controller.logActivity("Error restoring auto-commit: " + e.getMessage());
                }
            }
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public boolean deleteExam(int examId) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            conn.setAutoCommit(false);

            try {
                // First get all question IDs for this exam
                String getQuestionsSql = "SELECT question_id FROM exam_questions WHERE exam_id = ?";
                stmt = conn.prepareStatement(getQuestionsSql);
                stmt.setInt(1, examId);
                rs = stmt.executeQuery();

                List<Integer> questionIds = new ArrayList<>();
                while (rs.next()) {
                    questionIds.add(rs.getInt("question_id"));
                }

                // Delete student answers
                dbManager.closeResources(null, stmt, rs);
                String deleteAnswersSql = "DELETE FROM student_answers WHERE exam_id = ?";
                stmt = conn.prepareStatement(deleteAnswersSql);
                stmt.setInt(1, examId);
                stmt.executeUpdate();

                // Delete exam results
                dbManager.closeResources(null, stmt, null);
                String deleteResultsSql = "DELETE FROM exam_results WHERE exam_id = ?";
                stmt = conn.prepareStatement(deleteResultsSql);
                stmt.setInt(1, examId);
                stmt.executeUpdate();

                // Delete options for each question
                if (!questionIds.isEmpty()) {
                    for (int questionId : questionIds) {
                        dbManager.closeResources(null, stmt, null);
                        String deleteOptionsSql = "DELETE FROM question_options WHERE question_id = ?";
                        stmt = conn.prepareStatement(deleteOptionsSql);
                        stmt.setInt(1, questionId);
                        stmt.executeUpdate();
                    }
                }

                // Delete exam_questions links
                dbManager.closeResources(null, stmt, null);
                String deleteLinksSql = "DELETE FROM exam_questions WHERE exam_id = ?";
                stmt = conn.prepareStatement(deleteLinksSql);
                stmt.setInt(1, examId);
                stmt.executeUpdate();

                // Delete questions
                if (!questionIds.isEmpty()) {
                    for (int questionId : questionIds) {
                        dbManager.closeResources(null, stmt, null);
                        String deleteQuestionSql = "DELETE FROM questions WHERE id = ?";
                        stmt = conn.prepareStatement(deleteQuestionSql);
                        stmt.setInt(1, questionId);
                        stmt.executeUpdate();
                    }
                }

                // Delete the exam
                dbManager.closeResources(null, stmt, null);
                String deleteExamSql = "DELETE FROM exams WHERE id = ?";
                stmt = conn.prepareStatement(deleteExamSql);
                stmt.setInt(1, examId);

                int examRows = stmt.executeUpdate();
                if (examRows != 1) {
                    throw new SQLException("Failed to delete exam");
                }

                // Commit the transaction
                conn.commit();
                controller.logActivity("Deleted exam with ID: " + examId);

                // Refresh the UI
                controller.refreshExams();

                return true;
            } catch (SQLException e) {
                // Rollback the transaction in case of error
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ex) {
                        LOGGER.log(Level.SEVERE, "Error rolling back transaction", ex);
                        controller.logActivity("Error rolling back transaction: " + ex.getMessage());
                    }
                }
                LOGGER.log(Level.SEVERE, "Error deleting exam", e);
                controller.logActivity("Error deleting exam: " + e.getMessage());
                throw new RemoteException("Failed to delete exam: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Database error during exam deletion", e);
            controller.logActivity("Database error during exam deletion: " + e.getMessage());
            throw new RemoteException("Failed to delete exam: " + e.getMessage(), e);
        } finally {
            // Restore auto-commit
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "Error restoring auto-commit", e);
                    controller.logActivity("Error restoring auto-commit: " + e.getMessage());
                }
            }
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    @Override
    public boolean setResultVisibility(int examId, boolean visible) throws RemoteException {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = dbManager.getConnection();
            String sql = "UPDATE exams SET results_visible = ? WHERE id = ?";

            stmt = conn.prepareStatement(sql);
            stmt.setBoolean(1, visible);
            stmt.setInt(2, examId);

            int rows = stmt.executeUpdate();
            if (rows != 1) {
                throw new SQLException("Failed to update exam result visibility");
            }

            controller.logActivity("Set result visibility for exam " + examId + " to " + visible);

            // Refresh the UI
            controller.refreshExams();

            return true;
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error setting result visibility", e);
            controller.logActivity("Error setting result visibility: " + e.getMessage());
            throw new RemoteException("Failed to set result visibility: " + e.getMessage(), e);
        } finally {
            dbManager.closeResources(conn, stmt, null);
        }
    }

    // Method to get all active exam sessions for display in the UI
    public List<ActiveSessionDisplay> getActiveSessions() {
        List<ActiveSessionDisplay> sessions = new ArrayList<>();

        for (Map.Entry<String, ActiveExamSession> entry : activeExams.entrySet()) {
            ActiveExamSession session = entry.getValue();

            // Calculate time remaining
            long currentTime = System.currentTimeMillis();
            long endTime = session.getStartTime() + session.getDurationMillis();
            long remainingMillis = Math.max(0, endTime - currentTime);

            // Format time remaining as mm:ss
            long minutes = remainingMillis / (60 * 1000);
            long seconds = (remainingMillis % (60 * 1000)) / 1000;
            String timeRemaining = String.format("%02d:%02d", minutes, seconds);

            // Format start time
            String startTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                    .format(new java.util.Date(session.getStartTime()));

            // Get exam title from database
            String examTitle = getExamTitle(session.getExamId());

            sessions.add(new ActiveSessionDisplay(
                    session.getStudentId(),
                    session.getExamId(),
                    examTitle,
                    startTime,
                    timeRemaining
            ));
        }

        return sessions;
    }

    private String getExamTitle(int examId) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dbManager.getConnection();
            String sql = "SELECT title FROM exams WHERE id = ?";
            stmt = conn.prepareStatement(sql);
            stmt.setInt(1, examId);
            rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getString("title");
            } else {
                return "Unknown Exam";
            }
        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "Error getting exam title", e);
            controller.logActivity("Error getting exam title: " + e.getMessage());
            return "Unknown Exam";
        } finally {
            dbManager.closeResources(conn, stmt, rs);
        }
    }

    // Shutdown the thread pool when the application closes
    public void shutdown() {
        threadPool.shutdown();
        controller.logActivity("Thread pool shutdown initiated");
    }

    // Inner class to track active exam sessions
    private static class ActiveExamSession {

        private final int examId;
        private final String studentId;
        private final long startTime;
        private final long durationMillis;

        public ActiveExamSession(int examId, String studentId, long startTime, long durationMillis) {
            this.examId = examId;
            this.studentId = studentId;
            this.startTime = startTime;
            this.durationMillis = durationMillis;
        }

        public int getExamId() {
            return examId;
        }

        public String getStudentId() {
            return studentId;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getDurationMillis() {
            return durationMillis;
        }
    }
}
