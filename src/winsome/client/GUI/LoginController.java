package winsome.client.GUI;

import javafx.event.ActionEvent;
import winsome.WinGUIClientMain;
import winsome.RESTfulUtility.HttpResponse;
import winsome.client.APIClient;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import winsome.jsonUtility.JsonMessageBuilder;

import java.io.IOException;
import java.text.ParseException;

public class LoginController {
    //*****RIFERIMENTI PRELEVATI DAL FILE .fxml ASSOCIATO ALLA SCENE DA GESTIRE*****//
    @FXML
    private TextField usernameField;
    @FXML
    private TextField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Text errorText;
    @FXML
    private Button goBack;

    //riferimento all'istanza di APIClient per eseguire chiamate al server
    private final APIClient apiClient;

    //riferimento alla finestra/stage relativa all'applicazione
    private final Stage stage;

    public LoginController(Stage stage, APIClient apiClient){
        this.apiClient = apiClient;
        this.stage = stage;
    }

    @FXML
    public void initialize(){
        //faccio si che quando l'utente clicca il bottone goBack si cambi Scene e Controller tornando alla startScene
        goBack.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                FXMLLoader fxmlLoader = new FXMLLoader();
                fxmlLoader.setLocation(WinGUIClientMain.class.getResource("client/GUI/resource/xmlFile/startScene.fxml"));
                fxmlLoader.setController(new StartController(stage, apiClient));
                Scene scene = null;
                try {
                    scene = new Scene(fxmlLoader.load());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                stage.setScene(scene);
                stage.show();
            }
        });
        //faccio si che quando l'utente clicca il bottone login venga effettuata una chiamata al metodo login di apiClient
        loginButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                try {
                    HttpResponse response = apiClient.login(usernameField.getText(), passwordField.getText());
                    if (response.getStatusCode().equals("201")) {
                        //in caso di login effettuato con successo cambio Scene e controller e passo alla blogScene
                        FXMLLoader fxmlLoader = new FXMLLoader();
                        fxmlLoader.setLocation(WinGUIClientMain.class.getResource("client/GUI/resource/xmlFile/blogScene.fxml"));
                        fxmlLoader.setController(new BlogController(stage, apiClient));
                        Scene scene = new Scene(fxmlLoader.load());
                        stage.setScene(scene);
                        stage.show();
                    } else {
                        //stampo messaggio errore se login non va a buon fine
                        errorText.setFill(Color.RED);
                        ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                        errorText.setText("Error: " + errorJSONObject.get("description").asText());
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
