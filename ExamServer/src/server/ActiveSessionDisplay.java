package server;

public class ActiveSessionDisplay {
    private final String studentId;
    private final int examId;
    private final String examTitle;
    private final String startTime;
    private final String timeRemaining;

    public ActiveSessionDisplay(String studentId, int examId, String examTitle,
            String startTime, String timeRemaining) {
        this.studentId = studentId;
        this.examId = examId;
        this.examTitle = examTitle;
        this.startTime = startTime;
        this.timeRemaining = timeRemaining;
    }

    public String getStudentId() {
        return studentId;
    }

    public int getExamId() {
        return examId;
    }

    public String getExamTitle() {
        return examTitle;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getTimeRemaining() {
        return timeRemaining;
    }
}