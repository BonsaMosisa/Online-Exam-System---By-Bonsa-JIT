package server;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import shared.Exam;
import shared.Question;

public class ExamEditorController implements Initializable {

    @FXML
    private Label titleHeader;

    // Exam details
    @FXML
    private TextField examTitleField;
    @FXML
    private TextArea examDescriptionField;
    @FXML
    private TextField examDurationField;
    @FXML
    private CheckBox resultsVisibleCheckbox;

    // Questions table
    @FXML
    private TableView<Question> questionsTable;
    @FXML
    private TableColumn<Question, String> questionTextColumn;
    @FXML
    private TableColumn<Question, Integer> questionPointsColumn;

    // Question editor
    @FXML
    private TitledPane questionEditorPane;
    @FXML
    private TextArea questionTextField;
    @FXML
    private TextField questionPointsField;
    @FXML
    private VBox optionsContainer;

    private ExamServiceImpl examService;
    private Exam currentExam;
    private Question currentQuestion;
    private ObservableList<Question> questions = FXCollections.observableArrayList();
    private List<RadioButton> optionRadioButtons = new ArrayList<>();
    private ToggleGroup optionsGroup = new ToggleGroup();
    private boolean isEditMode = false;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        // Initialize the questions table
        questionTextColumn.setCellValueFactory(new PropertyValueFactory<>("text"));
        questionPointsColumn.setCellValueFactory(new PropertyValueFactory<>("points"));
        questionsTable.setItems(questions);

        // Add listener for question selection
        questionsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> {
                    if (newValue != null) {
                        loadQuestionForEditing(newValue);
                    }
                }
        );

        // Initialize with a blank question editor
        resetQuestionEditor();
    }

    public void setExamService(ExamServiceImpl examService) {
        this.examService = examService;
    }

    public void loadExam(Exam exam) {
        this.currentExam = exam;
        this.isEditMode = true;

        titleHeader.setText("Edit Exam");

        // Load exam details
        examTitleField.setText(exam.getTitle());
        examDescriptionField.setText(exam.getDescription());
        examDurationField.setText(String.valueOf(exam.getDurationMinutes()));
        resultsVisibleCheckbox.setSelected(exam.isResultsVisible());

        // Load questions
        questions.clear();
        if (exam.getQuestions() != null) {
            questions.addAll(exam.getQuestions());
        }
    }

    @FXML
    private void handleAddQuestion(ActionEvent event) {
        // Save current question if any
        saveCurrentQuestion();

        // Create a new blank question
        currentQuestion = new Question();
        currentQuestion.setId(generateTempId()); // Generate a temporary ID
        currentQuestion.setPoints(1);
        currentQuestion.setOptions(new ArrayList<>());
        currentQuestion.getOptions().add("Option 1");
        currentQuestion.getOptions().add("Option 2");
        currentQuestion.setCorrectOptionIndex(0); // Default to first option
        currentQuestion.setText(""); // Empty text

        // Reset the editor and load the new question
        questionTextField.setText("");
        questionPointsField.setText("1");
        updateOptionsUI();

        // Add to the list
        questions.add(currentQuestion);
        questionsTable.getSelectionModel().select(currentQuestion);
    }

    private int generateTempId() {
        // Generate a temporary negative ID to avoid conflicts with database IDs
        return -1 * (questions.size() + 1);
    }

    @FXML
    private void handleRemoveQuestion(ActionEvent event) {
        Question selectedQuestion = questionsTable.getSelectionModel().getSelectedItem();
        if (selectedQuestion != null) {
            questions.remove(selectedQuestion);
            if (selectedQuestion == currentQuestion) {
                resetQuestionEditor();
            }
        }
    }

    @FXML
    private void handleAddOption(ActionEvent event) {
        if (currentQuestion != null) {
            List<String> options = currentQuestion.getOptions();
            if (options == null) {
                options = new ArrayList<>();
                currentQuestion.setOptions(options);
            }

            options.add("New Option");
            updateOptionsUI();
        } else {
            showAlert(Alert.AlertType.WARNING, "No Question Selected",
                    "No Question", "Please select or add a question first.");
        }
    }

    @FXML
    private void handleApplyQuestionChanges(ActionEvent event) {
        saveCurrentQuestion();
        showAlert(Alert.AlertType.INFORMATION, "Changes Applied",
                "Question Updated", "The question has been updated.");
    }

    @FXML
    private void handleCancel(ActionEvent event) {
        Stage stage = (Stage) examTitleField.getScene().getWindow();
        stage.close();
    }

    @FXML
    private void handleSaveExam(ActionEvent event) {
        // Save the current question being edited
        saveCurrentQuestion();

        // Validate the exam
        if (!validateExam()) {
            return;
        }

        try {
            // Create or update the exam
            if (currentExam == null) {
                currentExam = new Exam();
            }

            // Set the exam properties
            currentExam.setTitle(examTitleField.getText().trim());
            currentExam.setDescription(examDescriptionField.getText().trim());
            currentExam.setDurationMinutes(Integer.parseInt(examDurationField.getText().trim()));
            currentExam.setResultsVisible(resultsVisibleCheckbox.isSelected());
            currentExam.setQuestions(new ArrayList<>(questions));

            // Save to the database
            if (isEditMode) {
                examService.updateExam(currentExam);
            } else {
                examService.createExam(currentExam);
            }

            // Close the window
            Stage stage = (Stage) examTitleField.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "Error", "Could not save exam", e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadQuestionForEditing(Question question) {
        // Save the current question first
        saveCurrentQuestion();

        // Load the selected question
        currentQuestion = question;

        // Update the UI
        questionTextField.setText(question.getText());
        questionPointsField.setText(String.valueOf(question.getPoints()));

        // Update options
        updateOptionsUI();
    }

    private void saveCurrentQuestion() {
        if (currentQuestion != null) {
            // Get values from UI
            currentQuestion.setText(questionTextField.getText().trim());

            try {
                currentQuestion.setPoints(Integer.parseInt(questionPointsField.getText().trim()));
            } catch (NumberFormatException e) {
                currentQuestion.setPoints(1);
            }

            // Get the selected correct option
            Toggle selectedToggle = optionsGroup.getSelectedToggle();
            if (selectedToggle != null) {
                currentQuestion.setCorrectOptionIndex((int) selectedToggle.getUserData());
            } else {
                // Default to first option if none selected
                currentQuestion.setCorrectOptionIndex(0);
            }

            // Update the options from text fields
            List<String> options = new ArrayList<>();
            for (int i = 0; i < optionsContainer.getChildren().size(); i++) {
                HBox optionBox = (HBox) optionsContainer.getChildren().get(i);
                TextField optionField = (TextField) optionBox.getChildren().get(1);
                options.add(optionField.getText().trim());
            }
            currentQuestion.setOptions(options);

            // Refresh the table
            questionsTable.refresh();
        }
    }

    private void resetQuestionEditor() {
        questionTextField.clear();
        questionPointsField.setText("1");
        currentQuestion = null;
        optionsContainer.getChildren().clear();
    }

    private void updateOptionsUI() {
        optionsContainer.getChildren().clear();
        optionRadioButtons.clear();

        if (currentQuestion != null && currentQuestion.getOptions() != null) {
            List<String> options = currentQuestion.getOptions();
            int correctOption = currentQuestion.getCorrectOptionIndex();

            for (int i = 0; i < options.size(); i++) {
                final int optionIndex = i;

                HBox optionBox = new HBox(10);
                optionBox.setPadding(new Insets(5));

                RadioButton radioButton = new RadioButton();
                radioButton.setToggleGroup(optionsGroup);
                radioButton.setUserData(optionIndex);
                optionRadioButtons.add(radioButton);

                if (optionIndex == correctOption) {
                    radioButton.setSelected(true);
                }

                TextField optionField = new TextField(options.get(i));
                HBox.setHgrow(optionField, Priority.ALWAYS);

                Button removeButton = new Button("Remove");
                removeButton.setOnAction(e -> handleRemoveOption(optionIndex));

                optionBox.getChildren().addAll(radioButton, optionField, removeButton);
                optionsContainer.getChildren().add(optionBox);
            }
        }
    }

    private void handleRemoveOption(int optionIndex) {
        if (currentQuestion != null && currentQuestion.getOptions() != null) {
            List<String> options = currentQuestion.getOptions();

            if (options.size() > 2) { // Ensure at least 2 options remain
                options.remove(optionIndex);

                // If the correct option was removed or is after the removed option, update it
                int correctOption = currentQuestion.getCorrectOptionIndex();
                if (correctOption == optionIndex) {
                    currentQuestion.setCorrectOptionIndex(0); // Default to first option
                } else if (correctOption > optionIndex) {
                    currentQuestion.setCorrectOptionIndex(correctOption - 1);
                }

                updateOptionsUI();
            } else {
                showAlert(Alert.AlertType.WARNING, "Cannot Remove",
                        "Minimum Options", "A question must have at least 2 options.");
            }
        }
    }

    private boolean validateExam() {
        // Check exam title
        if (examTitleField.getText().trim().isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error",
                    "Missing Title", "Please enter an exam title.");
            return false;
        }

        // Check exam duration
        try {
            int duration = Integer.parseInt(examDurationField.getText().trim());
            if (duration <= 0) {
                showAlert(Alert.AlertType.WARNING, "Validation Error",
                        "Invalid Duration", "Duration must be a positive number.");
                return false;
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Validation Error",
                    "Invalid Duration", "Please enter a valid number for duration.");
            return false;
        }

        // Check if there are questions
        if (questions.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Validation Error",
                    "No Questions", "Please add at least one question to the exam.");
            return false;
        }

        // Validate each question
        for (Question question : questions) {
            if (question.getText() == null || question.getText().trim().isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Validation Error",
                        "Empty Question", "All questions must have text.");
                return false;
            }

            if (question.getOptions() == null || question.getOptions().size() < 2) {
                showAlert(Alert.AlertType.WARNING, "Validation Error",
                        "Insufficient Options", "All questions must have at least 2 options.");
                return false;
            }

            for (String option : question.getOptions()) {
                if (option == null || option.trim().isEmpty()) {
                    showAlert(Alert.AlertType.WARNING, "Validation Error",
                            "Empty Option", "All options must have text.");
                    return false;
                }
            }
        }

        return true;
    }

    private void showAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
