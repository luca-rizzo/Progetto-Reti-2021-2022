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

public class FeedPostController {
    //*****RIFERIMENTI PRELEVATI DAL FILE .fxml ASSOCIATO ALLA SCENE DA GESTIRE*****//

    @FXML
    private TextArea commentTextArea;

    @FXML
    private Button goBack;

    @FXML
    private Text interactionResult;

    @FXML
    private Button newCommentButton;

    @FXML
    private TextArea postArea;

    @FXML
    private Button rateNegativeButton;

    @FXML
    private Button ratePositiveButton;

    @FXML
    private Button rewinButton;

    @FXML
    private VBox vBoxComment;

    @FXML
    private Text votiNegativi;

    @FXML
    private Text votiPositivi;

    //riferimento all'istanza di APIClient per eseguire chiamate al server
    private final APIClient apiClient;

    //riferimento alla finestra/stage relativa all'applicazione
    private final Stage stage;

    //id del post che il controller deve stampare
    private final long idPost;

    public FeedPostController(Stage stage, long idPost, APIClient apiClient){
        this.stage = stage;
        this.idPost = idPost;
        this.apiClient = apiClient;
    }

    public void initialize(){
        //imposto ritorno a capo automatico nella commentTextArea
        commentTextArea.setWrapText(true);

        //faccio si che quando l'utente clicca il bottone goBack si cambi Scene e Controller tornando alla feedScene
        goBack.setOnAction(new EventHandler<>() {
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

        //faccio si che quando l'utente clicca il bottone rewin venga effettuata una chiamata al metodo rewin di apiClient
        rewinButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    HttpResponse response = apiClient.rewinPost(String.valueOf(idPost));
                    if (response.getStatusCode().equals("201")) {
                        interactionResult.setText("You have rewin this post");
                        interactionResult.setFill(Color.GREEN);
                    } else {
                        ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                        interactionResult.setText("Error: " + errorJSONObject.get("description").asText());
                        interactionResult.setFill(Color.RED);
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        });
        //faccio si che quando l'utente clicca il bottone +1 venga effettuata una chiamata al metodo rate di apiClient e venga effettuato refresh
        //delle reazioni del post in caso di successo per rendere visibile il nuovo voto aggiunto
        ratePositiveButton.setOnAction(new EventHandler<>() {
            @Override
            public void handle(ActionEvent event) {
                try {
                    HttpResponse response = apiClient.ratePost(String.valueOf(idPost), String.valueOf(1));
                    if (response.getStatusCode().equals("201")) {
                        refreshPost();
                        interactionResult.setText("You have rated the post correctly");
                        interactionResult.setFill(Color.GREEN);
                    } else {
                        ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                        interactionResult.setText("Error: " + errorJSONObject.get("description").asText());
                        interactionResult.setFill(Color.RED);
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        });

        //faccio si che quando l'utente clicca il bottone -1 venga effettuata una chiamata al metodo rate di apiClient e venga effettuato refresh
        //delle reazioni del post in caso di successo per rendere visibile il nuovo voto aggiunto
        rateNegativeButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try{
                    HttpResponse response = apiClient.ratePost(String.valueOf(idPost), String.valueOf(1));
                    if(response.getStatusCode().equals("201")){
                        refreshPost();
                        interactionResult.setText("You have rated the post correctly");
                        interactionResult.setFill(Color.GREEN);
                    }
                    else{
                        ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                        interactionResult.setText("Error: " + errorJSONObject.get("description").asText());
                        interactionResult.setFill(Color.RED);
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        });

        //faccio si che quando l'utente clicca il bottone newComment venga effettuata una chiamata al metodo addComment di apiClient usando il contenuto di commentTextArea come argomento
        //e venga effettuato refresh delle reazioni del post in caso di successo per rendere visibile il nuovo commento aggiunto
        newCommentButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                try{
                    HttpResponse response = apiClient.addComment(String.valueOf(idPost), commentTextArea.getText());
                    if(response.getStatusCode().equals("201")){
                        refreshPost();
                        interactionResult.setText("You have commented the post correctly");
                        interactionResult.setFill(Color.GREEN);
                    }
                    else{
                        ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                        interactionResult.setText("Error: " + errorJSONObject.get("description").asText());
                        interactionResult.setFill(Color.RED);
                    }
                } catch (IOException | ParseException e) {
                    e.printStackTrace();
                }
            }
        });
        try{
            refreshPost();
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
    }

    //effects: effettua una chiamata al metodo showPost di apiClient e aggiorna titolo, contenuto post e reazioni
    public void refreshPost() throws IOException, ParseException {
        HttpResponse response = apiClient.showPost(String.valueOf(idPost));
        if (response.getStatusCode().equals("200")) {
            //ripulisco zona commenti
            vBoxComment.getChildren().clear();
            String messageBody = response.getBody();
            ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(messageBody);
            setPostContent(rootNode);
            votiNegativi.setText("Voti negativi: " + rootNode.get("post").get("downVotes").asText());
            votiPositivi.setText("Voti positivi: " + rootNode.get("post").get("upVotes").asText());
            for (JsonNode comment : rootNode.get("post").get("comment")) {
                //aggiungo i commenti alla vBox
                vBoxComment.getChildren().add(createNewComment(comment));
            }
            //setto lo spazio tra un commento e l'altro
            vBoxComment.setSpacing(5);
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
