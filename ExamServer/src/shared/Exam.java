package shared;

import java.io.Serializable;
import java.util.List;

public class Exam implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String title;
    private String description;
    private int durationMinutes;
    private boolean resultsVisible;
    private boolean active = true;
    private List<Question> questions;

    // Constructors, getters, and setters
    public Exam() {
    }

    public Exam(int id, String title, String description, int durationMinutes, boolean resultsVisible) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.resultsVisible = resultsVisible;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public boolean isResultsVisible() {
        return resultsVisible;
    }

    public void setResultsVisible(boolean resultsVisible) {
        this.resultsVisible = resultsVisible;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<Question> questions) {
        this.questions = questions;
    }

    @Override
    public String toString() {
        return title;
    }
}
