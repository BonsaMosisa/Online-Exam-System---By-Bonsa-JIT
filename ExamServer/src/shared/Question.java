package shared;

import java.io.Serializable;
import java.util.List;

public class Question implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private String text;
    private List<String> options;
    private int correctOptionIndex;
    private int points;

    // Constructors, getters, and setters
    public Question() {
    }

    public Question(int id, String text, List<String> options, int correctOptionIndex, int points) {
        this.id = id;
        this.text = text;
        this.options = options;
        this.correctOptionIndex = correctOptionIndex;
        this.points = points;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public int getCorrectOptionIndex() {
        return correctOptionIndex;
    }

    public void setCorrectOptionIndex(int correctOptionIndex) {
        this.correctOptionIndex = correctOptionIndex;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    @Override
    public String toString() {
        return text;
    }
}
