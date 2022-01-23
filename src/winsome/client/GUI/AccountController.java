package winsome.client.GUI;

import javafx.event.ActionEvent;
import winsome.WinGUIClientMain;
import winsome.RESTfulUtility.HttpResponse;
import winsome.client.APIClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import winsome.jsonUtility.JsonMessageBuilder;

import java.io.IOException;
import java.text.ParseException;
import java.util.HashSet;

public class AccountController {
    //*****RIFERIMENTI PRELEVATI DAL FILE .fxml ASSOCIATO ALLA SCENE DA GESTIRE*****//

    @FXML
    private Text logoutResult;

    @FXML
    private Button logoutButton;

    @FXML
    private Text unfollowResult;

    @FXML
    private Button accountButton;

    @FXML
    private Button blogButton;

    @FXML
    private Button feedButton;

    @FXML
    private VBox followersVbox;

    @FXML
    private VBox followingVbox;

    @FXML
    private Text passwordText;

    @FXML
    private VBox transactionVbox;

    @FXML
    private Text usernameText;

    @FXML
    private Text walletBTCText;

    @FXML
    private Text walletWinText;

    //riferimento all'istanza di APIClient per eseguire chiamate al server
    private final APIClient apiClient;

    //riferimento alla finestra/stage relativa all'applicazione
    private final Stage stage;

    public AccountController(Stage stage, APIClient apiClient){
        this.stage = stage;
        this.apiClient = apiClient;
    }

    @FXML
    public void initialize(){
        //faccio si che quando l'utente clicca il bottone blog si cambi Scene e Controller passando alla blogScene
        blogButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent event) {
                FXMLLoader fxmlLoader = new FXMLLoader();
                fxmlLoader.setLocation(WinGUIClientMain.class.getResource("client/GUI/resource/xmlFile/blogScene.fxml"));
                fxmlLoader.setController(new BlogController(stage, apiClient));
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
        //faccio si che quando l'utente clicca il bottone feed si cambi Scene e Controller passando alla feedScene
        feedButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent event) {
                FXMLLoader fxmlLoader = new FXMLLoader();
                fxmlLoader.setLocation(WinGUIClientMain.class.getResource("client/GUI/resource/xmlFile/feedScene.fxml"));
                fxmlLoader.setController(new FeedController(stage, apiClient));
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

        //faccio si che quando l'utente clicca il bottone account vengano refreshate informazioni presenti nell'account
        accountButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    refreshAccount();
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        });

        //faccio si che quando l'utente clicca il bottone logout venga chiamato il metodo logout di apiClient. In caso di successo
        //si cambierà Scene e Controller passando alla startScene
        logoutButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    HttpResponse response = apiClient.logout();
                    if(response.getStatusCode().equals("200")){
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
                    else{
                        ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                        logoutResult.setFill(Color.RED);
                        logoutResult.setText("Error: " + errorJSONObject.get("description").asText());
                    }
                } catch (IOException | ParseException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        followersVbox.setSpacing(10);
        try {
            //all'inizializzazione effettuo il refresh dell'account
            refreshAccount();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }
    //effects: effettua il refresh di tutte le informazioni contenute nella schermata tramite le chiamate ai metodi accountPrivateInfo, listFollowers e listFollowing di apiClient
    public void refreshAccount() throws IOException, ParseException {
        //ottengo informazioni private
        HttpResponse response = apiClient.accountPrivateInfo();
        if (response.getStatusCode().equals("200")){
            transactionVbox.getChildren().clear();
            ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
            usernameText.setText("Username: " + rootNode.get("username").asText());
            passwordText.setText("Password: " + rootNode.get("password").asText());
            ObjectNode walletNode = (ObjectNode) rootNode.get("wallet");
            walletWinText.setText("Amount in steem: " + walletNode.get("wallet amount in steem").asText());
            walletBTCText.setText("Amount in bitcoin: " + walletNode.get("wallet amount in bitcoin").asText());
            //agguingo tutte le transazioni alla Vbox transactionVbox
            for(JsonNode transaction : walletNode.get("transaction")){
                transactionVbox.getChildren().add(new Text(transaction.asText()));
            }
        }
        //ottengo lista followers che agiungo alla Vbox followersVbox
        HashSet<String> followers = apiClient.listFollowers();
        followersVbox.getChildren().clear();
        for(String users : followers)
            followersVbox.getChildren().add(new Text("username: " + users));
        //effettuo il refresh della Vbox following
        refreshFollowing();
    }
    //effects: effettua refresh della Vbox followingVbox
    public void refreshFollowing() throws IOException, ParseException {
        HttpResponse response = apiClient.listFollowing();
        if(response.getStatusCode().equals("200")) {
            followingVbox.getChildren().clear();
            ObjectNode responseNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
            JsonNode userListNode = responseNode.get("user list");
            for(JsonNode userNode : userListNode){
                //aggiungo un nuovo Text che contiene username dell'utente seguito alla Vbox
                String username = userNode.get("username").asText();
                followingVbox.getChildren().add(new Text("username: " + username));
                //aggiungo bottone unfollow associato a ciascun utente che seguo: un click su tale bottone effettuerà la chiamata al metodo unfollowUser
                //di apiClient: in caso di successo verrà eseguito il refresh della Vbox followingVbox per non far comparire più utente unfollowato
                Button unfollowButton = new Button("Unfollow");
                unfollowButton.setOnAction(new EventHandler<>() {
                    @Override
                    public void handle(ActionEvent event) {
                        try {
                            HttpResponse unfollowResponse = apiClient.unfollowUser(username);
                            if(unfollowResponse.getStatusCode().equals("200")){
                                unfollowResult.setText("User "+ username + " unfollowed");
                                unfollowResult.setFill(Color.GREEN);
                                refreshFollowing();
                            }
                            else{
                                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(unfollowResponse.getBody());
                                unfollowResult.setText("Error: " + errorJSONObject.get("description").asText());
                                unfollowResult.setFill(Color.RED);
                            }
                        } catch (IOException | ParseException e) {
                            e.printStackTrace();
                        }
                    }
                });
                //aggiungo bottone alla Vbox
                followingVbox.getChildren().add(unfollowButton);
                //spazio tra un utente e l'altro
                followingVbox.getChildren().add(new Text(" "));
            }
        }
        else{
            ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
            followingVbox.getChildren().add(new Text("Error: " + errorJSONObject.get("description").asText()));
        }
    }
}