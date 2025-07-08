package server;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import shared.RemoteExamService;

public class ExamServer extends Application {

    private static final int RMI_PORT = 1099;
    private static final String SERVICE_NAME = "ExamService";
    private static Registry registry;
    private static ExamServiceImpl examService;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Load the login screen first
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/server/fxml/TeacherLogin.fxml"));
        Parent root = loader.load();

        // Set up and show the JavaFX stage
        primaryStage.setTitle("Online Exam System - Teacher Login");
        primaryStage.setScene(new Scene(root, 400, 300));
        primaryStage.show();
    }

    public static void startRMIService(ExamServiceImpl service) {
        try {
            // Store the service reference
            examService = service;

            // Start the RMI service
            RemoteExamService stub = (RemoteExamService) UnicastRemoteObject.exportObject(examService, 0);

            // Create and start the RMI registry
            registry = LocateRegistry.createRegistry(RMI_PORT);
            registry.rebind(SERVICE_NAME, stub);

            System.out.println("Exam Server started. RMI service bound to registry.");
        } catch (Exception e) {
            System.err.println("Error starting RMI service: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void stop() {
        // Clean up resources when the application closes
        try {
            if (registry != null) {
                try {
                    registry.unbind(SERVICE_NAME);
                    System.out.println("Server stopped, service unbound.");
                } catch (Exception e) {
                    // Registry or service not found, which is fine during shutdown
                    System.out.println("No service to unbind or registry not available.");
                }
            }

            // Shutdown the thread pool
            if (examService != null) {
                examService.shutdown();
            }
        } catch (Exception e) {
            System.err.println("Error stopping server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
