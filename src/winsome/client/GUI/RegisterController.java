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
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import winsome.jsonUtility.JsonMessageBuilder;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.text.ParseException;
import java.util.ArrayList;

public class RegisterController {
    //*****RIFERIMENTI PRELEVATI DAL FILE .fxml ASSOCIATO ALLA SCENE DA GESTIRE*****//
    @FXML
    private TextField tagsField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button registerButton;

    @FXML
    private Text registerResult;

    @FXML
    private TextField usernameField;

    @FXML
    private Button goBack;

    //riferimento all'istanza di APIClient per eseguire chiamate al server
    private final APIClient apiClient;

    //riferimento alla finestra/stage relativa all'applicazione
    private final Stage stage;

    public RegisterController(Stage stage, APIClient apiClient){
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
        //faccio si che quando l'utente clicca il bottone register venga effettuata una chiamata al metodo register di apiClient
        registerButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent actionEvent) {
                try {
                    ArrayList<String> tags = new ArrayList<>();
                    for(String tag : tagsField.getText().split(";")){
                        if(!tag.isEmpty())
                            tags.add(tag);
                    }
                    //effettuo chiamata all'apiClient
                    HttpResponse response = apiClient.register(usernameField.getText(), passwordField.getText(), tags);
                    if(response.getStatusCode().equals("201")){
                        registerResult.setText("Registration complete. You can now login");
                        registerResult.setFill(Color.GREEN);
                    }
                    else{
                        ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                        registerResult.setFill(Color.RED);
                        registerResult.setText("Error: " + errorJSONObject.get("description").asText());
                    }
                } catch (IOException | ParseException | NotBoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
