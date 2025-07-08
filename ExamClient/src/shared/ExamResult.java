package shared;

import java.io.Serializable;
import java.util.Date;

public class ExamResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private int id;
    private int examId;
    private String examTitle; // Added exam title
    private String studentId;
    private String studentName;
    private int score;
    private int totalPossible;
    private Date submissionTime;

    // Constructors, getters, and setters
    public ExamResult() {
    }

    public ExamResult(int id, int examId, String studentId, String studentName,
            int score, int totalPossible, Date submissionTime) {
        this.id = id;
        this.examId = examId;
        this.studentId = studentId;
        this.studentName = studentName;
        this.score = score;
        this.totalPossible = totalPossible;
        this.submissionTime = submissionTime;
    }

    // Getters and setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getExamId() {
        return examId;
    }

    public void setExamId(int examId) {
        this.examId = examId;
    }

    public String getExamTitle() {
        return examTitle;
    }

    public void setExamTitle(String examTitle) {
        this.examTitle = examTitle;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getTotalPossible() {
        return totalPossible;
    }

    public void setTotalPossible(int totalPossible) {
        this.totalPossible = totalPossible;
    }

    public Date getSubmissionTime() {
        return submissionTime;
    }

    public void setSubmissionTime(Date submissionTime) {
        this.submissionTime = submissionTime;
    }

    public double getPercentage() {
        return totalPossible > 0 ? (double) score / totalPossible * 100 : 0;
    }
}
