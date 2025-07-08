package shared;

import java.io.Serializable;

public class Answer implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private int questionId;
    private int selectedOptionIndex;
    
    // Constructors, getters, and setters
    public Answer() {}
    
    public Answer(int questionId, int selectedOptionIndex) {
        this.questionId = questionId;
        this.selectedOptionIndex = selectedOptionIndex;
    }
    
    // Getters and setters
    public int getQuestionId() { return questionId; }
    public void setQuestionId(int questionId) { this.questionId = questionId; }
    
    public int getSelectedOptionIndex() { return selectedOptionIndex; }
    public void setSelectedOptionIndex(int selectedOptionIndex) { this.selectedOptionIndex = selectedOptionIndex; }
}