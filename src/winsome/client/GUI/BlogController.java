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
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import winsome.jsonUtility.JsonMessageBuilder;

import java.io.IOException;
import java.text.ParseException;

public class BlogController {
    //*****RIFERIMENTI PRELEVATI DAL FILE .fxml ASSOCIATO ALLA SCENE DA GESTIRE*****//
    @FXML
    private Button accountButton;

    @FXML
    private VBox blogPostVbox;

    @FXML
    private TextArea contentTextArea;

    @FXML
    private Button feedButton;

    @FXML
    private Button publicButton;

    @FXML
    private TextField titleTextField;

    @FXML
    private Text postResult;

    //riferimento all'istanza di APIClient per eseguire chiamate al server
    private final APIClient apiClient;

    //riferimento alla finestra/stage relativa all'applicazione
    private final Stage stage;

    public BlogController(Stage stage, APIClient apiClient){
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
        //faccio si che quando utente clicca publicButton venga chiamato il metodo createPost usando il contenuto di titleTextField e contentTextArea come argomenti
        //e venga effettuato refresh della VBOX rappresentante il blog( per far comparire il nuovo post pubblicato) in caso di successo
        publicButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent event) {
                try{
                    HttpResponse response = apiClient.createPost(titleTextField.getText(),contentTextArea.getText());
                    if(response.getStatusCode().equals("201")){
                        postResult.setFill(Color.GREEN);
                        postResult.setText("Post created");
                        refreshBlog();
                    }
                    else{
                        ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                        postResult.setFill(Color.RED);
                        postResult.setText("Error: " + errorJSONObject.get("description").asText());
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        });
        try {
            contentTextArea.setWrapText(true);
            refreshBlog();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }


    //effects: effettua una chiamata al metodo viewBlog di apiClient per aggiornare Vbox blogPostVbox
    public void refreshBlog() throws IOException, ParseException {
        HttpResponse response = apiClient.viewBlog();
        if(response.getStatusCode().equals("200")){
            blogPostVbox.getChildren().clear();
            ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
            for (JsonNode post : rootNode.get("blog posts")) {
                //aggiungo textArea che contiene id, titolo e autore del post
                blogPostVbox.getChildren().add(createNewPost(post));
                //creo un bottone che, una volta cliccato mi fa cambiare scene e controller e passare alla blogPostScene
                Button button = new Button("click to show full post");
                button.setOnAction(new EventHandler<>() {
                    @Override
                    public void handle(ActionEvent event) {
                        FXMLLoader fxmlLoader = new FXMLLoader();
                        fxmlLoader.setLocation(WinGUIClientMain.class.getResource("client/GUI/resource/xmlFile/blogPostScene.fxml"));
                        //il nuovo controller BlogPostController si occuperà del post con id che gli passo come argomento
                        fxmlLoader.setController(new BlogPostController(stage, post.get("post").get("idPost").asInt(),apiClient));
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
                //aggiungo bottone alla Vbox blogPostVbox
                blogPostVbox.getChildren().add(button);
                //aggiungo spazio vuoto tra un post e l'altro
                blogPostVbox.getChildren().add(new Text(" "));
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
}
