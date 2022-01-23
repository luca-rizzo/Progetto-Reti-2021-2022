package winsome.client.GUI;

import javafx.event.ActionEvent;
import winsome.WinGUIClientMain;
import winsome.client.APIClient;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import java.io.IOException;

public class StartController {
    //*****RIFERIMENTI PRELEVATI DAL FILE .fxml ASSOCIATO ALLA SCENE DA GESTIRE*****//
    @FXML
    private Button loginButton;

    @FXML
    private Button registerButton;

    //riferimento all'istanza di APIClient per eseguire chiamate al server
    private final APIClient apiClient;

    //riferimento alla finestra/stage relativa all'applicazione
    private final Stage stage;

    public StartController(Stage stage, APIClient apiClient){
        this.apiClient = apiClient;
        this.stage = stage;
    }
    @FXML
    public void initialize(){
        //faccio si che quando l'utente clicca il bottone login si cambi Scene e Controller passando alla loginScene
        loginButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                FXMLLoader fxmlLoader = new FXMLLoader();
                fxmlLoader.setLocation(WinGUIClientMain.class.getResource("client/GUI/resource/xmlFile/loginScene.fxml"));
                fxmlLoader.setController(new LoginController(stage, apiClient));
                Scene scene = null;
                try {
                    //ottengo scene corrispondente al file .fxml passato a fxmlLoader
                    scene = new Scene(fxmlLoader.load());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //setto la scene ottenuta nello Stage relativo all'applicazione
                stage.setScene(scene);
                stage.show();
            }
        });

        //faccio si che quando l'utente clicca il bottone register si cambi Scene e Controller passando alla registerScene
        registerButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                FXMLLoader fxmlLoader = new FXMLLoader();
                fxmlLoader.setLocation(WinGUIClientMain.class.getResource("client/GUI/resource/xmlFile/registerScene.fxml"));
                fxmlLoader.setController(new RegisterController(stage, apiClient));
                Scene scene = null;
                try {
                    //ottengo scene corrispondente al file .fxml passato a fxmlLoader
                    scene = new Scene(fxmlLoader.load());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //setto la scene ottenuta nello Stage relativo all'applicazione
                stage.setScene(scene);
                stage.show();
            }
        });
    }
}
