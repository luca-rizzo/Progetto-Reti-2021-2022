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
import javafx.scene.text.Text;
import javafx.stage.Stage;
import winsome.jsonUtility.JsonMessageBuilder;

import java.io.IOException;
import java.text.ParseException;

public class BlogPostController {
    //*****RIFERIMENTI PRELEVATI DAL FILE .fxml ASSOCIATO ALLA SCENE DA GESTIRE*****//
    @FXML
    private Text votiPositivi;
    @FXML
    private Text votiNegativi;
    @FXML
    private VBox vBoxComment;
    @FXML
    private Button goBack;
    @FXML
    private TextArea postArea;
    @FXML
    private Button deleteButton;
    @FXML
    private Text deleteText;

    //riferimento all'istanza di APIClient per eseguire chiamate al server
    private final APIClient apiClient;

    //riferimento alla finestra/stage relativa all'applicazione
    private final Stage stage;

    //id del post che il controller deve stampare
    private final long idPost;

    public BlogPostController(Stage stage, long idPost, APIClient apiClient){
        this.stage = stage;
        this.idPost = idPost;
        this.apiClient = apiClient;
    }

    public void initialize(){
        //faccio si che quando l'utente clicca il bottone goBack si cambi Scene e Controller tornando alla blogScene
        goBack.setOnAction(new EventHandler<>() {
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
        //faccio si che quando l'utente clicca il bottone delete si esegua una chiamata al metodo deletePost di apiClient.
        //in caso di successo si cambierà Scene e controller e si tornerà alla blogScene
        deleteButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent event) {
                try{
                    HttpResponse response = apiClient.deletePost(String.valueOf(idPost));
                    if(response.getStatusCode().equals("200")){
                        //come se cliccassi bottone goBack
                        goBack.fire();
                    }
                    else{
                        ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                        deleteText.setText("Error: " + errorJSONObject.get("description").asText());
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        });
        try {
            //ottengo informazioni riguardo il post attraverso la chiamata al metodo showPost di apiClient
            HttpResponse response = apiClient.showPost(String.valueOf(idPost));
            if(response.getStatusCode().equals("200")){
                //utilizzo il contenuto della risposta per riempire la schermata
                String messageBody = response.getBody();
                ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(messageBody);
                setPostContent(rootNode);
                //imposto Text relativi a voti positivi e nagativi
                votiNegativi.setText("Voti negativi: " + rootNode.get("post").get("downVotes").asText());
                votiPositivi.setText("Voti positivi: " + rootNode.get("post").get("upVotes").asText());
                for(JsonNode comment : rootNode.get("post").get("comment")){
                    vBoxComment.getChildren().add(createNewComment(comment));
                }
                vBoxComment.setSpacing(5);
            }

        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }
    //effects: dato un JsonNode che contiene un post in formato JSON, aggiunge alla postArea il contenuto del post
    public void setPostContent(JsonNode jsonResponse){
        String stringBuilder = "id: " + jsonResponse.get("post").get("idPost").asText() + "\n" +
                "Title: " + jsonResponse.get("post").get("title").asText() + "\n" +
                "Author: " + jsonResponse.get("post").get("author-username").asText() + "\n" +
                "Content: " + jsonResponse.get("post").get("content").asText();
        postArea.setText(stringBuilder);
        //setto altezza massima della textArea
        postArea.setMaxHeight(250);
        postArea.setMinHeight(150);
        //faccio si che la textArea non sia modificabile
        postArea.setEditable(false);
    }

    //effects: dato un JsonNode che contiene un commento in formato JSON, ritorna una textArea che contiene autore e contenuto del commento
    public TextArea createNewComment(JsonNode comment){
        TextArea textArea = new TextArea(comment.get("author").asText() + ": " + comment.get("content").asText());
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxHeight(60);
        textArea.setMinHeight(40);
        return textArea;
    }
}
