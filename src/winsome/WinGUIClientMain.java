package winsome;

import javafx.stage.WindowEvent;
import winsome.client.APIClient;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import winsome.client.GUI.StartController;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.text.ParseException;
import java.util.Objects;


public class WinGUIClientMain extends Application {
    @Override
    public void start(Stage stage) throws IOException, NotBoundException {
        //creo l'istanza di apiClient che verrà passata da controller a controller
        APIClient apiClient = new APIClient("./resource/configClient.json");
        FXMLLoader fxmlLoader = new FXMLLoader();
        fxmlLoader.setLocation(WinGUIClientMain.class.getResource("client/GUI/resource/xmlFile/startScene.fxml"));
        //setto controller iniziale
        fxmlLoader.setController(new StartController(stage, apiClient));
        //ottengo scene corrispondente al file .fxml passato a fxmlLoader
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("WinsomeClient");
        //setto la scene ottenuta
        stage.setScene(scene);
        //imposto la chiamata del metodo logout una volche che l'utente chiude la finestra
        stage.setOnCloseRequest(new EventHandler<>() {
            @Override
            public void handle(WindowEvent windowEvent) {
                try {
                    if(apiClient.isLogged())
                        apiClient.logout();
                } catch (IOException | ParseException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        //faccio si che le dimensioni della finestra siano fisse
        stage.setResizable(false);
        //aggiungo icona
        Image icon = new Image(Objects.requireNonNull(WinGUIClientMain.class.getResourceAsStream("client/GUI/resource/image/logo.jpg")));
        stage.getIcons().add(icon);
        //mostro lo stage
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}

