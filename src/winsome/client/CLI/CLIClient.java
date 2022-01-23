package winsome.client.CLI;

import winsome.RESTfulUtility.HttpResponse;
import winsome.client.APIClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import winsome.jsonUtility.JsonMessageBuilder;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.text.ParseException;
import java.util.*;


public class CLIClient implements Runnable{
    //path che idetifica il file di configurazione che sarà usato dall'API client
    final private String pathConfigFile;
    //istanza di APIClient usata per effettuare chiamate al server senza badare ai meccanismi di comunicazione
    private APIClient apiClient;

    public CLIClient(String pathConfigFile) {
        this.pathConfigFile = pathConfigFile;
    }

    @Override
    public void run() {
        try{
            Scanner cmdLineScanner = new Scanner(System.in);
            String line;
            //creo un'istanza di apiClient che userò per ottenere dati dal server
            this.apiClient = new APIClient(pathConfigFile);
            //leggo comandi dal terminale fin quando non inserisco il comando exit
            while(! (line = cmdLineScanner.nextLine()).equals("exit")){
                String[] tokens = line.split(" ");
                int numberOfToken = tokens.length;
                if( numberOfToken == 0){
                    System.out.println("Please enter some command\n");
                    System.out.println(stringUsage());
                    continue;
                }
                String command = tokens[0].toLowerCase(Locale.ROOT);
                switch (command){
                    case "register":{
                        //utente ha digitato da linea di comando register
                        handleRegistration(tokens);
                        break;
                    }
                    case "login":{
                        //utente ha digitato da linea di comando login
                        handleLogin(tokens);
                        break;
                    }
                    case "logout":{
                        //utente ha digitato da linea di comando logout
                        handleLogout(tokens);
                        break;
                    }
                    case "blog":{
                        //utente ha digitato da linea di comando blog
                        handleBlog(tokens);
                        break;
                    }
                    case "post":{
                        //utente ha digitato da linea di comando post
                        handlePost(line);
                        break;
                    }
                    case "delete":{
                        //utente ha digitato da linea di comando delete
                        handleDelete(tokens);
                        break;
                    }
                    case "rewin":{
                        //utente ha digitato da linea di comando rewin
                        handleRewin(tokens);
                        break;
                    }
                    case "follow":{
                        //utente ha digitato da linea di comando follow
                        handleFollow(tokens);
                        break;
                    }
                    case "unfollow":{
                        //utente ha digitato da linea di comando unfollow
                        handleUnfollow(tokens);
                        break;
                    }
                    case "rate":{
                        //utente ha digitato da linea di comando rate
                        handleRate(tokens);
                        break;
                    }
                    case "comment":{
                        //utente ha digitato da linea di comando comment
                        handleComment(tokens, line);
                        break;
                    }
                    case "show":{
                        if(numberOfToken < 2 || !(tokens[1].equals("post") || tokens[1].equals("feed"))){
                            System.out.println("List of command\n");
                            System.out.println(stringUsage());
                        }
                        else if(tokens[1].equals("feed")){
                            //utente ha digitato da linea di comando show feed
                            handleShowFeed(tokens);
                        }
                        else {
                            //utente ha digitato da linea di comando show post
                            handleShowPost(tokens);
                        }
                        break;
                    }
                    case "list":{
                        if(numberOfToken < 2 || !(tokens[1].equals("users") || tokens[1].equals("following") || tokens[1].equals("followers"))){
                            System.out.println("List of command\n");
                            System.out.println(stringUsage());
                        }
                        else if(tokens[1].equals("users")){
                            //utente ha digitato da linea di comando list users
                            handleListUsers(tokens);
                        }
                        else if(tokens[1].equals("following")){
                            //utente ha digitato da linea di comando list following
                            handleListFollowing(tokens);
                        }
                        else{
                            //utente ha digitato da linea di comando list followers
                            handleListFollowers(tokens);
                        }
                        break;
                    }
                    case "wallet":{
                        //utente ha digitato da linea di comando wallet
                        handleWallet(tokens);
                        break;
                    }
                    default:{
                        System.out.println("List of command\n");
                        System.out.println(stringUsage());
                    }
                }
            }
            //utente ha digitato da linea di comando exit
            
            //se ero loggato eseguo logout prima di uscire
            if(apiClient.isLogged()){
                apiClient.logout();
            }
        } catch (IOException | NotBoundException | ParseException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    
    //effects: gestisce il comando register
    //se gli argomenti sono validi effettua una chiamata al metodo register di apiClient
    private void handleRegistration(String[] tokens) throws NotBoundException, IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken >= 3 && numberOfToken <= 8){
            //posso avere massimo 5 tags
            //dopo username e password è presente la lista di tag
            ArrayList<String> tags = new ArrayList<>(Arrays.asList(tokens).subList(3, numberOfToken));
            HttpResponse response = apiClient.register(tokens[1], tokens[2], tags);
            if(response.getStatusCode().equals("201")){
                System.out.println("Registration completed: below you will find the information relating to the new account");
                ObjectNode userNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                System.out.println("Username: " + userNode.get("username").asText());
                System.out.println("Password: " + userNode.get("password").asText());
                System.out.println("Tags: " + userNode.get("tags"));
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else if(numberOfToken<3){
            System.out.println("Some arguments are missing. Usage: register <username> <password> <tags>");
        }
        else{
            System.out.println("Too many argument. Usage: register <username> <password> <tags>");
        }
    }

    //effects: gestisce il comando login
    //se gli argomenti sono validi effettua una chiamata al metodo login di apiClient
    private void handleLogin(String[] tokens) throws IOException, ParseException, NotBoundException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 3){
            if(apiClient.isLogged()){
                System.out.println("You are already logged in. Please log out first");
                return;
            }
            //il secondo e terzo token rappresentano username e password
            HttpResponse response = apiClient.login(tokens[1], tokens[2]);
            if(response.getStatusCode().equals("201")){
                System.out.println("Successfully logged in");
            }
            else{
                //si è verificato un errore: lo stampo sul terminale
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else if( numberOfToken < 3){
            System.out.println("Some arguments are missing. Usage: login <username> <password>");
        }
        else{
            System.out.println("Too many argument. Usage: login <username> <password>");
        }
    }
    
    //effects: gestisce il comando blog
    //se gli argomenti sono validi effettua una chiamata al metodo viewBlog di apiClient
    private void handleBlog(String[] tokens) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 1){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            HttpResponse response = apiClient.viewBlog();
            if(response.getStatusCode().equals("200")) {
                //stampo le informazioni ottenute dal server sul terminale
                ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                System.out.println("Your blog posts:\n");
                for (JsonNode post : rootNode.get("blog posts")) {
                    System.out.println("id: " + post.get("post").get("idPost"));
                    System.out.println("Title: " + post.get("post").get("title"));
                    System.out.println("Author: " + post.get("post").get("author-username") + "\n");
                }
            }
            else{
                //si è verificato un errore: lo stampo sul terminale
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else{
            System.out.println("Too many argument. Usage: blog");
        }
    }

    //effects: gestisce il comando post
    //se gli argomenti sono validi effettua una chiamata al metodo createPost di apiClient
    private void handlePost(String line) throws IOException, ParseException {
        String[] tokens = line.split("\"");
        //tokens conterrà qualcosa tipo {"post ";"titolo"; " ";"contenuto"}
        int numberOfToken = tokens.length;
        if(numberOfToken == 4){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            HttpResponse response = apiClient.createPost(tokens[1], tokens[3]);
            if(response.getStatusCode().equals("201")){
                ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                System.out.println("Post created: id " + rootNode.get("post").get("idPost"));
            }
            else{
                //si è verificato un errore: lo stampo sul terminale
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else if( numberOfToken < 4){
            System.out.println("Some arguments are missing. Usage: post \"<content>\" \"<content>\"");
        }
        else{
            System.out.println("Too many argument. Usage: post \"<content>\" \"<content>\"");
        }
    }
    
    //effects: gestisce il comando delete
    //se gli argomenti sono validi effettua una chiamata al metodo deletePost di apiClient
    private void handleDelete(String[] tokens) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 2){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }

            //controllo che argomento sia un intero per evitare di fare richieste inutili al server
            try{
                Long.parseLong(tokens[1]);
            }catch (NumberFormatException e){
                System.out.println("The argument is wrong. Please enter an Integer as <idPost> argument. Usage: delete <idPost>(integer)");
                return;
            }
            HttpResponse response = apiClient.deletePost(tokens[1]);
            if(response.getStatusCode().equals("200")){
                System.out.println("Post " + tokens[1] + " deleted");
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else if( numberOfToken < 2){
            System.out.println("Some arguments are missing. Usage: delete <idPost>");
        }
        else{
            System.out.println("Too many argument. Usage: delete <idPost>");
        }
    }

    //effects: gestisce il comando logout
    //se gli argomenti sono validi effettua una chiamata al metodo logout di apiClient
    private void handleLogout(String[] tokens) throws IOException, ParseException, InterruptedException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 1){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            HttpResponse response = apiClient.logout();
            if(response.getStatusCode().equals("200")){
                System.out.println("You have logged out from " + apiClient.getAccountURI().split("/")[3] + " account");
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else{
            System.out.println("Too many argument. Usage: logout");
        }
    }

    //effects: gestisce il comando follow
    //se gli argomenti sono validi effettua una chiamata al metodo followUser di apiClient
    private void handleFollow(String[] tokens) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 2){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            HttpResponse response = apiClient.followUser(tokens[1]);
            if(response.getStatusCode().equals("201")){
                System.out.println("User " + tokens[1] + " followed");
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else if( numberOfToken < 2){
            System.out.println("Some arguments are missing. Usage: follow <username>");
        }
        else{
            System.out.println("Too many argument. Usage: follow <username>");
        }
    }
    
    //effects: gestisce il comando show feed
    //se gli argomenti sono validi effettua una chiamata al metodo viewFeed di apiClient
    private void handleShowFeed(String[] tokens) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 2){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            HttpResponse response = apiClient.viewFeed();
            if(response.getStatusCode().equals("200")) {
                //stampo le informazioni ottenute dal server sul terminale
                ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                System.out.println("Your feed posts:\n");
                for (JsonNode post : rootNode.get("feed posts")) {
                    System.out.println("id: " + post.get("post").get("idPost"));
                    System.out.println("Title: " + post.get("post").get("title"));
                    System.out.println("Author: " + post.get("post").get("author-username") + "\n");
                }
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else{
            System.out.println("Too many argument. Usage: show feed");
        }
    }

    //effects: gestisce il comando rate
    //se gli argomenti sono validi effettua una chiamata al metodo ratePost di apiClient
    private void handleRate(String[] tokens) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 3){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            //controllo argomenti per evitare di fare richieste inutili al server
            try{
                Long.parseLong(tokens[1]);
                int rate = Integer.parseInt(tokens[2]);
                if (!(rate == -1  ||  rate == 1)){
                    System.out.println("Rate argument must be +1 or -1");
                    return;
                }
            }catch (NumberFormatException e){
                System.out.println("The argument is wrong. Usage: rate <idPost>(integer) <rate> (+1,-1)");
                return;
            }
            HttpResponse response = apiClient.ratePost(tokens[1], tokens[2]);

            if(response.getStatusCode().equals("201")){
                System.out.println("You have rated the post " + tokens[1]);
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else if( numberOfToken < 3){
            System.out.println("Some arguments are missing. Usage: rate <idPost> <rate> (+1,-1)");
        }
        else{
            System.out.println("Too many argument. Usage: rate <idPost> <rate> (+1,-1)");
        }
    }
    
    //gestisce il comando comment
    //se gli argomenti sono validi effettua una chiamata al metodo addComment di apiClient
    private void handleComment(String[] tokens, String line) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken >= 3){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            //controllo argomenti per evitare di fare richieste inutili al winsome.server
            String comment;
            try{
                Long.parseLong(tokens[1]);
                //il comando line.split("\"") restituisce qualcosa di questo tipo: {"comment <idPost> ", "commentContent"}
                comment = line.split("\"")[1];
            }catch (NumberFormatException | IndexOutOfBoundsException e){
                System.out.println("The argument is wrong. Usage: comment <idPost>(integer) \"<content>\"");
                return;
            }
            HttpResponse response = apiClient.addComment(tokens[1], comment);
            if(response.getStatusCode().equals("201")){
                System.out.println("You have commented the post " + tokens[1]);
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else{
            System.out.println("Some arguments are missing. Usage: comment <idPost> \"<content>\"");
        }
    }

    //gestisce il comando show post
    //se gli argomenti sono validi effettua una chiamata al metodo showPost di apiClient
    private void handleShowPost(String[] tokens) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 3){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            //controllo che argomento sia un intero per evitare di fare richieste inutili al server
            try{
                Integer.parseInt(tokens[2]);
            }catch (NumberFormatException e){
                System.out.println("The argument is wrong. Please enter an Integer as <idPost> argument. Usage: show post <idPost>(integer)");
                return;
            }
            HttpResponse response = apiClient.showPost(tokens[2]);
            if(response.getStatusCode().equals("200")){
                //stampo informazioni inviate dal server sul terminale
                String messageBody = response.getBody();
                ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(messageBody);
                System.out.println("Titolo: " + rootNode.get("post").get("title").asText());
                System.out.println("Contenuto: " + rootNode.get("post").get("content").asText());
                System.out.println("Voti: " + rootNode.get("post").get("upVotes").asText() + " positivi, " + rootNode.get("post").get("downVotes").asText() + " negativi");
                System.out.println("Commenti: ");
                for(JsonNode comment : rootNode.get("post").get("comment")){
                    System.out.println("\t" + comment.get("author").asText() + ": " + comment.get("content").asText());
                }
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else if( numberOfToken < 3){
            System.out.println("Some arguments are missing. Usage: show post <idPost>");
        }
        else{
            System.out.println("Too many argument. Usage: show post <idPost>");
        }
    }

    //gestisce il comando list users
    //se gli argomenti sono validi effettua una chiamata al metodo listUsers di apiClient
    private void handleListUsers(String[] tokens) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 2){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            HttpResponse response = apiClient.listUsers();
            if(response.getStatusCode().equals("200")) {
                //stampo informazioni inviate dal server sul terminale
                System.out.printf("%-20s%s\n", "Username", "Tags");
                ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                JsonNode childNode = rootNode.get("user list");
                for(JsonNode elem : childNode){
                    System.out.printf("%-20s%s\n", elem.get("username"), elem.get("tags"));
                }
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else{
            System.out.println("Too many argument. Usage: list users");
        }
    }

    //gestisce il comando list following
    //se gli argomenti sono validi effettua una chiamata al metodo listFollowing di apiClient
    private void handleListFollowing(String[] tokens) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 2){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            HttpResponse response = apiClient.listFollowing();
            if(response.getStatusCode().equals("200")) {
                //stampo informazioni inviate dal server sul terminale
                System.out.printf("%-20s%s\n", "Username", "Tags");
                ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                JsonNode childNode = rootNode.get("user list");
                for(JsonNode elem : childNode){
                    System.out.printf("%-20s%s\n", elem.get("username").asText(), elem.get("tags"));
                }
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else{
            System.out.println("Too many argument. Usage: list users");
        }
    }
    
    //gestisce il comando list following
    //se gli argomenti sono validi effettua una chiamata al metodo listFollowers di apiClient
    private void handleListFollowers(String[] tokens) {
        int numberOfToken = tokens.length;
        if(numberOfToken == 2){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            HashSet<String> followers = apiClient.listFollowers();
            //stampa lista utenti mantenuta localmente dall'apiKey e aggiornata tramite il meccanismo di callback
            System.out.println("List of your followers: \n");
            for(String username: followers){
                System.out.println(username);
            }
        }
        else{
            System.out.println("Too many argument. Usage: list followers");
        }
    }

    //gestisce il comando list following
    //se gli argomenti sono validi effettua una chiamata al metodo unfollowUser di apiClient
    private void handleUnfollow(String[] tokens) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 2){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            HttpResponse response = apiClient.unfollowUser(tokens[1]);
            if(response.getStatusCode().equals("200")){
                System.out.println("User " + tokens[1] + " unfollowed");
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else if( numberOfToken < 2){
            System.out.println("Some arguments are missing. Usage: follow <username>");
        }
        else{
            System.out.println("Too many argument. Usage: follow <username>");
        }
    }
    //gestisce il comando wallet e wallet btc
    //se gli argomenti sono validi effettua una chiamata al metodo accountPrivateInfo di apiClient
    private void handleWallet(String[] tokens) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if((numberOfToken == 2 && tokens[1].equals("btc")) || numberOfToken == 1){
            //il comando inserito è wallet o wallet btc
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            HttpResponse response = apiClient.accountPrivateInfo();
            if(response.getStatusCode().equals("200")){
                ObjectNode walletNode = (ObjectNode) JsonMessageBuilder.getJsonObjectNode(response.getBody()).get("wallet");
                System.out.println("Amount in steem: " + walletNode.get("wallet amount in steem"));
                if(numberOfToken == 1){
                    //il comando inserito è wallet
                    System.out.println("Transaction: ");
                    for(JsonNode transaction : walletNode.get("transaction")){
                        System.out.println(transaction.asText());
                    }
                }
                else {
                    //il comando inserito è wallet btc
                    System.out.println("Amount in bitcoin: " + walletNode.get("wallet amount in bitcoin"));
                }
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else{
            System.out.println("Too many argument. Usage: wallet or wallet btc");
        }
    }
    
    //effects: gestisce i comando rewin
    //se gli argomenti sono validi effettua una chiamata al metodo rewinPost di apiClient
    private void handleRewin(String[] tokens) throws IOException, ParseException {
        int numberOfToken = tokens.length;
        if(numberOfToken == 2){
            if(!apiClient.isLogged()){
                System.out.println("You need to be logged to perform this action");
                return;
            }
            //controllo che argomento sia un intero per evitare di fare richieste inutili al server
            try{
                Long.parseLong(tokens[1]);
            }catch (NumberFormatException e){
                System.out.println("The argument is wrong. Please enter an Integer as <idPost> argument. Usage: rewin <idPost>(integer)");
                return;
            }

            HttpResponse response = apiClient.rewinPost(tokens[1]);
            if(response.getStatusCode().equals("201")){
                ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
                System.out.println("You successfully rewin the post: " + rootNode.get("post").get("idPost"));
            }
            else{
                //si è verificato un errore
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode( response.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
        }
        else if( numberOfToken < 2){
            System.out.println("Some arguments are missing. Usage: rewin <idPost>");
        }
        else{
            System.out.println("Too many argument. Usage: rewin <idPost>");
        }
    }

    private String stringUsage(){
        return  "register <username> <password> <tag separati da spazio>: per registrarsi\n" +
                "login <username> <password>: per effettuare login\n" +
                "logout: per eseguire il logout\n" +
                "blog: per ottenere la lista di post del tuo blog. Per ciascun post viene mostrato id del post, titolo e autore\n" +
                "post \"<title>\" \"<content>\": per pubblicare un nuovo post sul tuo blog\n" +
                "show feed: per ottenere la lista di post del tuo feed\n" +
                "show post <idPost>: per ottenere titolo,contenuto,autore,reazioni del post <idPost>\n" +
                "delete <idPost>: per eliminare <idPost> dal tuo blog\n" +
                "rewin <idPost>: per pubblicare il <idPost> post presente nel tuo feed nel tuo blog\n" +
                "comment <idPost> <content>: per commentare con <content> il post <idPost>\n" +
                "follow <usernameUtente>: per followare <usernameUtente>\n" +
                "unfollow <usernameUtente>: per unfolloware <usernameUtente>\n" +
                "rate <idPost> <vote>: per votare il post <idPost>. Il valore di <vote> può essere o 1 o –1\n" +
                "list followers: per ottenere la lista dei propri followers\n" +
                "list following: per ottenere lista utenti che segui\n" +
                "list users: per ottenere lista utenti con I tuoi stessi tags\n" +
                "wallet: per ottenere il valore del proprio wallet in winsome e la storia delle transizioni del portafoglio\n" +
                "wallet btc: per ottenere il valore del portafoglio in winsome e in BTC";
    }
}
