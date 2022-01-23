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
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import winsome.jsonUtility.JsonMessageBuilder;
import java.io.IOException;
import java.text.ParseException;

public class FeedController {
    //*****RIFERIMENTI PRELEVATI DAL FILE .fxml ASSOCIATO ALLA SCENE DA GESTIRE*****//

    @FXML
    private Button accountButton;

    @FXML
    private Button blogButton;

    @FXML
    private VBox feedPostVbox;

    @FXML
    private Text followResult;

    @FXML
    private VBox listUsersVbox;

    @FXML
    private Button refreshFeedButton;

    @FXML
    private Text usernameTagText;


    //riferimento all'istanza di APIClient per eseguire chiamate al server
    private final APIClient apiClient;

    //riferimento alla finestra/stage relativa all'applicazione
    private final Stage stage;

    public FeedController(Stage stage, APIClient apiClient){
        this.stage = stage;
        this.apiClient = apiClient;
    }

    @FXML
    public void initialize(){
        //faccio si che quando l'utente clicca il bottone account si cambi Scene e Controller passando alla accountScene
        accountButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent event) {
                FXMLLoader fxmlLoader = new FXMLLoader();
                fxmlLoader.setLocation(WinGUIClientMain.class.getResource("client/GUI/resource/xmlFile/accountScene.fxml"));
                fxmlLoader.setController(new AccountController(stage, apiClient));
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

        //faccio si che quando l'utente clicca il bottone feed venga reffettuato il refresh del feed e della list users
        refreshFeedButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    refreshFeed();
                    refreshListUsers();
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            //all'inizializzazione effettuo refresh del feed e ottengo completo anche il Vbox contente tutti gli utenti con i miei interessi
            refreshFeed();
            usernameTagText.setText(String.format("%-20s%s\n", "Username", "Tags"));
            refreshListUsers();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    //effects: effettua una chiamata al metodo viewFeed di apiClient e aggiunge tutte le informazioni sui post ottenute alla VBOX feedPostVbox
    public void refreshFeed() throws IOException, ParseException {
        HttpResponse response = apiClient.viewFeed();
        if (response.getStatusCode().equals("200")) {
            feedPostVbox.getChildren().clear();
            ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
            for (JsonNode post : rootNode.get("feed posts")) {
                //aggiungo textArea che contiene id, titolo e autore del post
                feedPostVbox.getChildren().add(createNewPost(post));
                //creo un bottone che, una volta cliccato mi fa cambiare scene e controller e passare alla feedPostScene
                Button button = new Button("click to show full post");
                button.setOnAction(new EventHandler<>() {
                    @Override
                    public void handle(ActionEvent event) {
                        FXMLLoader fxmlLoader = new FXMLLoader();
                        fxmlLoader.setLocation(WinGUIClientMain.class.getResource("client/GUI/resource/xmlFile/feedPostScene.fxml"));
                        //il nuovo controller FeedPostController si occuperà del post con id che gli passo come argomento
                        fxmlLoader.setController(new FeedPostController(stage, post.get("post").get("idPost").asInt(), apiClient));
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
                //aggiungo bottone alla Vbox feedPostVbox
                feedPostVbox.getChildren().add(button);
                //aggiungo spazio vuoto tra un post e l'altro
                feedPostVbox.getChildren().add(new Text(" "));
            }
        }
    }

    //crea una nuova TextArea non modificabile che contiene id, titolo e autore del post a partire da un JsonNode
    public TextArea createNewPost(JsonNode jsonResponse){
        String stringBuilder = "id: " + jsonResponse.get("post").get("idPost").asText() + "\n" +
                "Title: " + jsonResponse.get("post").get("title").asText() + "\n" +
                "Author: " + jsonResponse.get("post").get("author-username").asText();
        TextArea textArea = new TextArea(stringBuilder);
        textArea.setMaxHeight(80);
        textArea.setMinHeight(80);
        //faccio si che la textArea non sia editabile
        textArea.setEditable(false);
        return textArea;
    }
    //effects: effettua una chiamta al metodo listUsers per effettuare il refresh della Vbox listUsersVbox
    public void refreshListUsers() throws IOException, ParseException {
        HttpResponse response = apiClient.listUsers();
        if(response.getStatusCode().equals("200")){
            ObjectNode messageObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
            JsonNode userList = messageObject.get("user list");
            listUsersVbox.getChildren().clear();
            for(JsonNode user : userList){
                //scorro la lista di utenti per aggiungere ciascun utente alla VBbox listUsersVbox
                String username = user.get("username").asText();
                listUsersVbox.getChildren().add(new Text(String.format("%-25s%s", username, user.get("tags"))));
                //creo un nuovo bottone che, una volta cliccato chiama il metodo followUser di apiClient
                Button unfollowButton = new Button("follow");
                unfollowButton.setOnAction(new EventHandler<>() {
                    @Override
                    public void handle(ActionEvent event) {
                        try {
                            HttpResponse followResponse = apiClient.followUser(username);
                            if(followResponse.getStatusCode().equals("201")){
                                followResult.setText("User "+ username + " followed");
                                followResult.setFill(Color.GREEN);
                                refreshFeed();
                            }
                            else{
                                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(followResponse.getBody());
                                followResult.setText("Error: " + errorJSONObject.get("description").asText());
                                followResult.setFill(Color.RED);
                            }
                        } catch (IOException | ParseException e) {
                            e.printStackTrace();
                        }
                    }
                });
                //aggiungo il bottone alla Vbox listUsersVbox
                listUsersVbox.getChildren().add(unfollowButton);
                //spazio tra un utente e l'altro
                listUsersVbox.getChildren().add(new Text(" "));
            }
        }
    }
}
