package shared;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface RemoteExamService extends Remote {
    // Authentication
    boolean authenticateUser(String username, String password, boolean isTeacher) throws RemoteException;
    
    // Student methods
    List<Exam> getAvailableExams(String studentId) throws RemoteException;
    Exam getExamQuestions(int examId, String studentId) throws RemoteException;
    boolean submitExam(int examId, String studentId, List<Answer> answers) throws RemoteException;
    ExamResult getExamResult(int examId, String studentId) throws RemoteException;
    
    // Teacher methods
    boolean createExam(Exam exam) throws RemoteException;
    boolean updateExam(Exam exam) throws RemoteException;
    boolean deleteExam(int examId) throws RemoteException;
    List<ExamResult> getExamResults(int examId) throws RemoteException;
    boolean setResultVisibility(int examId, boolean visible) throws RemoteException;
}