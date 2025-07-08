package client;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import shared.RemoteExamService;

public class ExamClient extends Application {

    private static final String SERVER_HOST = "localhost";
    private static final int RMI_PORT = 1099;
    private static final String SERVICE_NAME = "ExamService";

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Connect to the RMI service
        Registry registry = LocateRegistry.getRegistry(SERVER_HOST, RMI_PORT);
        RemoteExamService examService = (RemoteExamService) registry.lookup(SERVICE_NAME);

        System.out.println("Connected to exam server.");

        // Load the login screen
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/client/fxml/Login.fxml"));
        Parent root = loader.load();

        // Get the controller and set the exam service
        LoginController controller = loader.getController();
        controller.setExamService(examService);

        // Set up and show the JavaFX stage
        primaryStage.setTitle("Online Exam System - Student Client");
        primaryStage.setScene(new Scene(root, 400, 300));
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
