package winsome.server;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.rmi.RemoteException;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import winsome.client.IntRMIClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import winsome.RESTfulUtility.*;
import winsome.jsonUtility.JsonMessageBuilder;
import winsome.resourseRappresentation.Post;
import winsome.resourseRappresentation.Reaction;
import winsome.resourseRappresentation.User;

public class RequestHandler implements Runnable {
    //riferimento al selettore da svegliare dopo aver preparato la risposta ad una richiesta
    private final Selector selector;
    //la SelectionKey associata al channel dal quale arriva la richiesta
    private final SelectionKey keyToServe;

    //riferimento alla lista di SelectionKey pronte in scrittura: il thread worker aggiungerà keyToServe a tale lista quando formulerà
    //la risposta da inviare al client e sveglierà il selector per avvertirlo
    private final ArrayList<SelectionKey> readyToWrite;
    //rifermenti alle strutture dati condivise
    private final ConcurrentHashMap<String, User> registeredUser;
    final private ConcurrentHashMap<Token, User> loggedUser;
    private final ConcurrentHashMap<String, IntRMIClient> userToNotify;
    private final ServerConfiguration serverConfiguration;

    //stringa che contiene la richiesta http da gestire
    private final String request;


    public RequestHandler(Selector selector, SelectionKey keyToServe, WinSomeServer server, String request) {
        this.selector = selector;
        this.keyToServe = keyToServe;
        this.readyToWrite = server.getReadyToWrite();
        this.request = request;
        this.registeredUser = server.getRegisteredUser();
        this.loggedUser = server.getLoggedUser();
        this.userToNotify = server.getUserToNotify();
        this.serverConfiguration = server.getServerConfiguration();
    }

    public void run() {
        HttpRequest requestFromClient;
        //stampo la richiesta per mostrarla su terminale
        System.out.println(request);
        try {
            //parso la stringa ottenuta in un oggetto HttpRequest
            requestFromClient = HttpRequest.newHttpRequestFromString(request);
        } catch (ParseException e) {
            //il messaggio inviato non rispetta la sintassi HTTP
            HttpResponse response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error400);
            try {
                //preparo risposta che contiene messaggio d'errore
                String jsonMessage = JsonMessageBuilder.newErrorMessage("400", "Malformed request syntax", "Your request does not comply with the HTTP standard");
                response.setBody(jsonMessage);
                //prelevo il ByteBuffer che contiene la risposta Http
                ByteBuffer buf = response.getRelateByteBuffer();
                //stampo risposta per mostrarla su terminale
                System.out.println(response.getRelateString());
                //lo associo alla SelectionKey che sto gestendo
                keyToServe.attach(buf);
                //avverto il thread dispatcher che ho terminato di gestire la richiesta e ho preparato la risposta
                advertiseSelector();
                return;
            } catch (JsonProcessingException | UnsupportedEncodingException ex) {
                //queste eccezioni non verranno mai sollevate
                ex.printStackTrace();
                return;
            }
        }
        HttpResponse response = null;
        try {
            String URL = requestFromClient.getURL();
            //INDIVIDUO RISORSA SU CUI L'UTENTE VUOLE OPERARE
            String resourceRef = URL.substring(URL.indexOf("/") + 1);
            if (resourceRef.startsWith("winsome/tokens")) {
                //utente vuole accedere alla risorsa winsome/tokens
                response = requestOntokensResourse(requestFromClient);
            }
            else {
                String[] tokens = resourceRef.split("/");
                switch (tokens.length) {
                    case 2:
                        //utente vuole accedere alla risorsa winsome/users
                        response = requestOnUsers(requestFromClient);
                        break;
                    case 3:
                        //utente vuole accedere alla risorsa winsome/users/{username}
                        response = requestOnUserAccount(requestFromClient);
                        break;
                    case 4:
                        switch (tokens[3]) {
                            case "blog":
                                //utente vuole accedere alla risorsa winsome/users/{username}/blog
                                response = requestOnUserBlogResourse(requestFromClient);
                                break;
                            case "feed":
                                //utente vuole accedere alla risorsa winsome/users/{username}/feed
                                response = requestOnUserFeedResourse(requestFromClient);
                                break;
                            case "following":
                                //utente vuole accedere alla risorsa winsome/users/{username}/following
                                response = requestOnFollowingResourse(requestFromClient);
                                break;
                        }
                        break;
                    case 5:
                        switch (tokens[3]) {
                            case "blog":
                                //utente vuole accedere alla risorsa winsome/users/{username}/blog/{id-post}
                                response = requestOnBlogPost(requestFromClient);
                                break;
                            case "feed":
                                //utente vuole accedere alla risorsa winsome/users/{username}/feed/{id-post}
                                response = requestOnFeedPost(requestFromClient);
                                break;
                            case "following":
                                //utente vuole accedere alla risorsa winsome/users/{username}/following/{username}
                                response = requestOnFollowingUser(requestFromClient);
                                break;
                        }
                        break;
                    default:
                        break;
                }
            }
            if(response == null){
                //nessun caso precedente è stato coperto: l'utente ha indicato una risorsa non disponibile nel server
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error404);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("404", "Resource not found", "The resource " + resourceRef + " was not found");
                response.setBody(jsonMessage);
            }
            //prelevo il ByteBuffer che contiene la risposta Http
            ByteBuffer buf = response.getRelateByteBuffer();
            //stampo risposta per mostrarla su terminale
            System.out.println(response.getRelateString());
            //lo associo alla SelectionKey che sto gestendo
            keyToServe.attach(buf);
            //avverto il thread dispatcher che ho terminato di gestire la richiesta e ho preparato la risposta
            advertiseSelector();
        } catch (Exception e) {
            //una qualsiasi eccezione sollevata dai metodi chiamati è dovuta a richieste utente malformulate (ad esempio corpi non in JSON o comunque con campi errati)
            e.printStackTrace();
            try {
                //avverto il client con un messaggio d'errore
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error400);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("400", "Malformed request syntax", "Your request cannot be served due to a syntax error. Read the documentation to form a correct request");
                response.setBody(jsonMessage);
                ByteBuffer buf = response.getRelateByteBuffer();
                keyToServe.attach(buf);
                advertiseSelector();
            } catch (JsonProcessingException | UnsupportedEncodingException ex) {
                ex.printStackTrace();
            }
        }
    }

    //effects: aggiunge la chiave da servire alla lista di chiavi pronte in scrittura e sveglia il thread in eventuale attesa sulla select
    private void advertiseSelector() {
        synchronized (readyToWrite) {
            //acquisisco la lock visto che la lista è condivisa con il thread dispatcher
            readyToWrite.add(keyToServe);
        }
        selector.wakeup();
    }

    //effects: ritorna la risposta Http generata in seguito alla richiesta requestFromClient sulla risorsa winsome/tokens
    private HttpResponse requestOntokensResourse(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        if (requestFromClient.getMethod().equals("POST")) {
            response = POSTonTokens(requestFromClient);
        } else if (requestFromClient.getMethod().equals("DELETE")) {
            response = DELETEonTokens(requestFromClient);
        } else {
            //posso solo eseguire una POST o una DELETE sulla risorsa winsome/tokens: ritorno una risposta d'errore
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error405);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("405", "Not allowed", "You are not allowed to execute " + requestFromClient.getMethod() + " on " + requestFromClient.getURL() + " resource");
            response.setBody(jsonMessage);
        }
        return response;
    }


    //effects: ritorna la risposta Http generata in seguito ad una richiesta POST sulla risorsa winsome/tokens. La risposta avrà uno status code pari a:
    //status code = 201 in caso di successo
    //staus code = 406 se l'utente è già loggato
    //status code = 401 se username e password sono errate
    private HttpResponse POSTonTokens(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        //prelevo username e password dal body della richiesta
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        String jsonBody = requestFromClient.getBody();
        ObjectNode rootNode = mapper.readValue(jsonBody, ObjectNode.class);
        String username = rootNode.get("Credentials").get("username").asText();
        String password = rootNode.get("Credentials").get("password").asText();
        //ottengo riferimento all'utente che si vuole loggare sulla base dell'username
        User userToLogin = registeredUser.get(username);
        if (userToLogin != null && userToLogin.getPassword().equals(password)) {
            //l'utente è registrato e ha inserito la password corretta: può effettuare login
            Token newToken = new Token(username);
            //provo ad inserire il token nella struttura dati loggedUser
            if(loggedUser.putIfAbsent(newToken, userToLogin) != null){
                //l'utente era già loggato: ritorno un messaggio d'errore
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error406);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("406", "Already logged", "You are already logged from another session. Please logout before to login");
                response.setBody(jsonMessage);
            }
            else{
                //l'utente non era loggato
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success201);
                String jsonMessage = JsonMessageBuilder.getLoginInfoJsonString(userToLogin, serverConfiguration, newToken);
                response.setBody(jsonMessage);
            }
        } else {
            //l'utente ha inserito username o password scorretti
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Wrong username or password", "Username and password do not match any user");
            response.setBody(jsonMessage);
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta DELETE sulla risorsa winsome/tokens/{idToken}. La risposta avrà uno status code pari a:
    //status code = 200 in caso di successo
    //status code = 401 se l'utente non era loggato
    private HttpResponse DELETEonTokens(HttpRequest requestFromClient) throws JsonProcessingException {
        HttpResponse response;
        String apiKey = requestFromClient.getHeaderValue("Authorization");
        Token supposedToken = Token.createFakeToken(apiKey);
        //l'utente da cui arriva la richiesta sulla base dell'api-key
        User userFromToken = loggedUser.remove(supposedToken);
        if (userFromToken == null){
            //l'utente non era loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else {
            //l'utente era loggato e è autorizzato ad eseguire logout
            userToNotify.remove(userFromToken.getUsername());//non devo più notificare utente
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            ObjectNode rootNode = mapper.createObjectNode();
            ObjectNode childNode = rootNode.putObject("token");
            childNode.put("id", supposedToken.getId());
            childNode.put("api-key", supposedToken.getApiKey());
            response.setBody(mapper.writeValueAsString(rootNode));
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito alla richiesta requestFromClient sulla risorsa winsome/users
    private HttpResponse requestOnUsers(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        if ("GET".equals(requestFromClient.getMethod())) {
            response = GETonUsers(requestFromClient);
        } else {
            //l'unico metodo consentito sulla risorsa è la GET
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error405);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("405", "Not allowed", "You are not allowed to execute " + requestFromClient.getMethod() + " on " + requestFromClient.getURL() + " resource");
            response.setBody(jsonMessage);
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta GET sulla risorsa winsome/users. La risposta avrà uno status code pari a:
    //status code = 200 in caso di successo
    //status code = 401 se l'utente non era loggato
    private HttpResponse GETonUsers(HttpRequest requestFromClient) throws JsonProcessingException {
        HttpResponse response;
        String apiKey = requestFromClient.getHeaderValue("Authorization");
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));
        if(userFromApiKey == null){
            //l'utente richiedente non è loggato: l'apiKey inserita non si riferisce ad alcun token attivo
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged because your api-key does not match any of ours. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else{
            ArrayList<String> tags = new ArrayList<>();
            int indexOfStartQuery = requestFromClient.getURL().indexOf("?");
            if (indexOfStartQuery > 0) {
                //l'URL contiene una query
                String query = requestFromClient.getURL().substring(indexOfStartQuery + 1);
                String[] parameters = query.split("&");
                for (String parameter : parameters) {
                    if (parameter.startsWith("tag"))
                        //aggiungo tutti i tag della query alla lista
                        tags.add(parameter.split("=")[1]);
                }
            }
            HashSet<User> userList = new HashSet<>();
            if(tags.isEmpty()){
                //nessuna query inserita: ritorno la lista di tutti gli utenti iscritti
                userList.addAll(registeredUser.values());
            }
            else{
                //era presente una query: ottengo la lista dei soli utenti con i tag specificati nella query
                for (User user : registeredUser.values()) {
                    for (String tag : tags) {
                        if ((user.getTags().contains(tag) && !user.equals(userFromApiKey))) {
                            //ottengo lista utenti con tag in comune: ovviamente non aggiungo utente che ha effettuato richiesta
                            userList.add(user);
                        }
                    }
                }
            }
            //creo body da inviare come risposta
            String jsonMessage = JsonMessageBuilder.getUserListInJsonString(userList);
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
            response.setBody(jsonMessage);
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito alla richiesta requestFromClient sulla risorsa winsome/users/{username}
    private HttpResponse requestOnUserAccount(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        if ("GET".equals(requestFromClient.getMethod())) {
            response = GETonUserAccount(requestFromClient);
        } else {
            //solo il metodo GET è consentito su tale risorsa
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error405);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("405", "Not allowed", "You are not allowed to execute " + requestFromClient.getMethod() + " on " + requestFromClient.getURL() + " resource");
            response.setBody(jsonMessage);
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta GET sulla risorsa winsome/users/{username}. La risposta avrà uno status code pari a:
    //status code = 200 in caso di successo
    //status code = 401 se l'utente non è loggato o non è registrato o non è autorizzato
    private HttpResponse GETonUserAccount(HttpRequest requestFromClient) throws JsonProcessingException {
        HttpResponse response;
        //vogliamo accedere alle risorse di userFromUrl
        String usernameFromUrl = requestFromClient.getURL().split("/")[3];
        String apiKey = requestFromClient.getHeaderValue("Authorization");

        //riferimento all'utente sulle cui risorse la richiesta vuole agire
        User userFromUrl = registeredUser.get(usernameFromUrl);

        //riferimento all'utente associato all'apiKey: l'apiKey inserita permette di accedere alle risorse di userFromApiKey
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));

        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a userFromUrl <->
        //l'utente userFromUrl è uguale all'utente userFromApiKey
        if(userFromUrl == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + usernameFromUrl + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userFromUrl)){
            //l'utente è loggato ma è autorizzato con l'apiKey che ha inserito a compiere l'operazione che richiede
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else{
            //l'utente è autorizzato: è loggato e ha inserito il token corretto
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
            String jsonMessage = JsonMessageBuilder.getFullUserInfoJsonString(userFromApiKey);
            response.setBody(jsonMessage);
        }
        return response;

    }

    //effects: ritorna la risposta Http generata in seguito alla richiesta requestFromClient sulla risorsa winsome/users/{username}/blog
    private HttpResponse requestOnUserBlogResourse(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        switch(requestFromClient.getMethod()){
            case "GET":
                response = GETonUserBlog(requestFromClient);
                break;
            case "POST":
                response = POSTonUserBlog(requestFromClient);
                break;
            default:
                //solo i metodi GET e PUT sono consentiti sulla risorsa winsome/users/{username}/blog
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error405);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("405", "Not allowed", "You are not allowed to execute " + requestFromClient.getMethod() + " on " + requestFromClient.getURL() + " resource");
                response.setBody(jsonMessage);
                break;
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta GET sulla risorsa winsome/users/{username}/blog. La risposta avrà uno status code pari a:
    //status code = 200 in caso di successo
    //status code = 401 se l'utente non è loggato o non è registrato o non è autorizzato
    private HttpResponse GETonUserBlog(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        //vogliamo accedere alle risorse di userFromUrl
        String usernameFromUrl = requestFromClient.getURL().split("/")[3];
        String apiKey = requestFromClient.getHeaderValue("Authorization");

        //riferimento all'utente sulle cui risorse la richiesta vuole agire
        User userFromUrl = registeredUser.get(usernameFromUrl);

        //riferimento all'utente associato all'apiKey: l'apiKey inserita permette di accedere alle risorse di userFromApiKey
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));

        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a userFromUrl <->
        //l'utente userFromUrl è uguale all'utente userFromApiKey
        if(userFromUrl == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + usernameFromUrl + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userFromUrl)){
            //l'utente è loggato ma è autorizzato con l'apiKey che ha inserito a compiere l'operazione che richiede
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else{
            //l'utente è autorizzato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
            String jsonMessage = JsonMessageBuilder.getBlogInJsonString(userFromUrl.getBlog());
            response.setBody(jsonMessage);
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta POST sulla risorsa winsome/users/{username}/blog. La risposta avrà uno status code pari a:
    //status code = 201 in caso di successo
    //status code = 401 se l'utente non è loggato o non è registrato o non è autorizzato
    //status code = 406 se l'utente vuole effettuare il rewin di un post su cui ha già effettuato rewin o il titolo e il contenuto del nuovo post che voglio caricare sono vuoti
    //status code = 413 se il titolo o il contenuto sono troppo grandi
    private  HttpResponse POSTonUserBlog(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        //vogliamo accedere alle risorse di userFromUrl
        String usernameFromUrl = requestFromClient.getURL().split("/")[3];
        String apiKey = requestFromClient.getHeaderValue("Authorization");

        //riferimento all'utente sulle cui risorse la richiesta vuole agire
        User userFromUrl = registeredUser.get(usernameFromUrl);

        //riferimento all'utente associato all'apiKey: l'apiKey inserita permette di accedere alle risorse di userFromApiKey
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));

        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a userFromUrl <->
        //l'utente userFromUrl è uguale all'utente userFromApiKey
        if(userFromUrl == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + usernameFromUrl + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userFromUrl)){
            //l'utente è loggato ma è autorizzato con l'apiKey che ha inserito a compiere l'operazione che richiede
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else{
            //l'utente è stato identificato ed è autorizzato ad eseguire la richiesta
            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            String jsonBody = requestFromClient.getBody();
            ObjectNode rootNode = mapper.readValue(jsonBody, ObjectNode.class);
            ObjectNode leafNode = (ObjectNode) rootNode.get("newPost");
            if( leafNode == null ){
                //è un rewind in quanto non è stato trovato il campo newPost
                //ottengo id del post
                long idPost = rootNode.get("rewinPost").get("idPost").asLong();

                //ottengo feed dell'utente e vedo se contiene il post in questione
                HashMap<Long, Post> userFeed = userFromApiKey.getFeed();
                Post postToRewin = userFeed.get(idPost);
                if(postToRewin != null){
                    //il post è presente nel feed dell'utente
                    if(userFromApiKey.getBlog().putIfAbsent(postToRewin.getId(), postToRewin) == null){
                        //non aveva ancora effettuato il rewin del post
                        response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success201);
                        String jsonMessage = JsonMessageBuilder.getHeaderPostInJsonString(postToRewin);
                        response.setBody(jsonMessage);
                    }
                    else{
                        //aveva già effettuato il rewin del post
                        response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error406);
                        String jsonMessage = JsonMessageBuilder.newErrorMessage("406", "You have already rewin this post", "The post " + idPost + " is already on your blog.");
                        response.setBody(jsonMessage);
                    }
                }
                else{
                    //il post non è presente nel feed dell'utente
                    response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error404);
                    String jsonMessage = JsonMessageBuilder.newErrorMessage("404", "Post not found on your feed", "No post id = " + idPost + " was found on your feed");
                    response.setBody(jsonMessage);
                }
            }
            else{
                //si tratta della richiesta di creazione di un nuovo post

                //ottengo titolo e contenuto
                String titolo = leafNode.get("title").asText();
                String contenuto = leafNode.get("content").asText();
                if(contenuto.length()<=500 && titolo.length() <=20 && titolo.length()>0 && contenuto.length()>0){
                    //titolo e contenuto rispettano i canoni imposti: creo un nuovo post e lo aggiungo al blog dell'utente
                    Post newPost = new Post(userFromApiKey, titolo, contenuto);
                    userFromApiKey.getBlog().put(newPost.getId(), newPost);
                    response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success201);
                    String jsonMessage = JsonMessageBuilder.getHeaderPostInJsonString(newPost);
                    response.setBody(jsonMessage);
                }
                else if(titolo.length() > 20){
                    //titolo troppo grande
                    response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error413);
                    String jsonMessage = JsonMessageBuilder.newErrorMessage("413", "Title too large", "The title of your Post is too large: maximum amount is 20 character");
                    response.setBody(jsonMessage);
                }
                else if(contenuto.length()>500){
                    //post troppo grande
                    response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error413);
                    String jsonMessage = JsonMessageBuilder.newErrorMessage("413", "Post content too large", "The content of your Post is too large: maximum amount is 500 character");
                    response.setBody(jsonMessage);
                }
                else{
                    //titolo o contenuto vuoti
                    response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error406);
                    String jsonMessage = JsonMessageBuilder.newErrorMessage("406", "Empty title or content", "You can not post a new post with an empty title or content");
                    response.setBody(jsonMessage);
                }
            }
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito alla richiesta requestFromClient sulla risorsa winsome/users/{username}/feed
    private HttpResponse requestOnUserFeedResourse( HttpRequest requestFromClient) throws JsonProcessingException {
        HttpResponse response;
        if ("GET".equals(requestFromClient.getMethod())) {
            response = GETonUserFeed(requestFromClient);
        } else {
            //solo la GET è consentita sulla risorsa winsome/users/{username}/feed
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error405);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("405", "Not allowed", "You are not allowed to execute " + requestFromClient.getMethod() + " on " + requestFromClient.getURL() + " resource");
            response.setBody(jsonMessage);
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta GET sulla risorsa winsome/users/{username}/feed. La risposta avrà uno status code pari a:
    //status code = 200 in caso di successo
    //status code = 401 se l'utente non è loggato o non è registrato o non è autorizzato
    private HttpResponse GETonUserFeed(HttpRequest requestFromClient) throws JsonProcessingException {
        HttpResponse response;
        //vogliamo accedere alle risorse di userFromUrl
        String usernameFromUrl = requestFromClient.getURL().split("/")[3];
        String apiKey = requestFromClient.getHeaderValue("Authorization");

        //riferimento all'utente sulle cui risorse la richiesta vuole agire
        User userFromUrl = registeredUser.get(usernameFromUrl);

        //riferimento all'utente associato all'apiKey: l'apiKey inserita permette di accedere alle risorse di userFromApiKey
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));

        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a userFromUrl <->
        //l'utente userFromUrl è uguale all'utente userFromApiKey
        if(userFromUrl == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + usernameFromUrl + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userFromUrl)){
            //l'utente è loggato ma è autorizzato con l'apiKey che ha inserito a compiere l'operazione che richiede
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else{
            //l'utente è autorizzato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
            String jsonMessage = JsonMessageBuilder.getFeedInJsonString(userFromUrl.getFeed());
            response.setBody(jsonMessage);
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito alla richiesta requestFromClient sulla risorsa winsome/users/{username}/following
    private HttpResponse requestOnFollowingResourse(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        switch(requestFromClient.getMethod()){
            case "GET":
                response = GETonFollowingResourse(requestFromClient);
                break;
            case "POST":
                response = POSTonFollowingResourse(requestFromClient);
                break;
            default:
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error405);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("405", "Not allowed", "You are not allowed to execute " + requestFromClient.getMethod() + " on following resource");
                response.setBody(jsonMessage);
                break;
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta GET sulla risorsa winsome/users/{username}/following. La risposta avrà uno status code pari a:
    //status code = 200 in caso di successo
    //status code = 401 se l'utente non è loggato o non è registrato o non è autorizzato
    private HttpResponse GETonFollowingResourse(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        //vogliamo accedere alle risorse di userFromUrl
        String usernameFromUrl = requestFromClient.getURL().split("/")[3];
        String apiKey = requestFromClient.getHeaderValue("Authorization");

        //riferimento all'utente sulle cui risorse la richiesta vuole agire
        User userFromUrl = registeredUser.get(usernameFromUrl);

        //riferimento all'utente associato all'apiKey: l'apiKey inserita permette di accedere alle risorse di userFromApiKey
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));

        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a userFromUrl <->
        //l'utente userFromUrl è uguale all'utente userFromApiKey
        if(userFromUrl == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + usernameFromUrl + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userFromUrl)){
            //l'utente è loggato ma è autorizzato con l'apiKey che ha inserito a compiere l'operazione che richiede
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else{
            //l'utente è autorizzato
            String jsonMessage = JsonMessageBuilder.getUserListInJsonString(userFromApiKey.getFollowedUsers().getHashSet());
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
            response.setBody(jsonMessage);
        }
        return response;
    }


    //effects: ritorna la risposta Http generata in seguito ad una richiesta POST sulla risorsa winsome/users/{username}/following. La risposta avrà uno status code pari a:
    //status code = 201 in caso di successo
    //status code = 401 se l'utente non è loggato o non è registrato o non è autorizzato
    //status code = 406 se l'utente vuole seguire se stesso
    //status code = 304 se l'utente già segue l'utente che vuole seguire
    //status code = 404 se l'utente prova a seguire un utente non registrato
    private HttpResponse POSTonFollowingResourse(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        //vogliamo accedere alle risorse di userFromUrl
        String usernameFromUrl = requestFromClient.getURL().split("/")[3];
        String apiKey = requestFromClient.getHeaderValue("Authorization");

        //riferimento all'utente sulle cui risorse la richiesta vuole agire
        User userFromUrl = registeredUser.get(usernameFromUrl);

        //riferimento all'utente associato all'apiKey: l'apiKey inserita permette di accedere alle risorse di userFromApiKey
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));

        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a userFromUrl <->
        //l'utente userFromUrl è uguale all'utente userFromApiKey
        if(userFromUrl == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + usernameFromUrl + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userFromUrl)){
            //l'utente è loggato ma è autorizzato con l'apiKey che ha inserito a compiere l'operazione che richiede
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else{
            //l'utente è autorizzato
            String requestBody = requestFromClient.getBody();
            String usernameToFollow = JsonMessageBuilder.getJsonObjectNode(requestBody).get("userToFollow").get("username").asText();
            if(usernameToFollow.equals(userFromApiKey.getUsername())){
                //l'utente ha richiesto di seguire se stesso
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error406);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("406", "You can not follow yourself", "You can only follow other users: follow other users to see their posts on your feed");
                response.setBody(jsonMessage);
                return response;
            }
            User userToFollow = registeredUser.get(usernameToFollow);
            if(userToFollow != null){
                //voglio seguire un utente registrato
                if(userFromApiKey.getFollowedUsers().add(userToFollow)){
                    //non seguivo l'utente
                    IntRMIClient callbackUser = userToNotify.get(usernameToFollow);
                    if(callbackUser != null){
                        //l'utente che ho seguito era registrato per le callback sugli utenti
                        //lo notifico
                        callbackUser.notifyNewFollower(usernameFromUrl);
                    }
                    //aggiungo user al set dei follower di userToFollow
                    userToFollow.getFollowers().add(userFromApiKey);
                    response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success201);
                    ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
                    ObjectNode rootNode = mapper.createObjectNode();
                    ObjectNode childNode = rootNode.putObject("userFollowed");
                    childNode.put("username", userToFollow.getUsername());
                    childNode.set("tags", mapper.valueToTree(userToFollow.getTags()));
                    response.setBody(mapper.writeValueAsString(rootNode));
                }
                else{
                    //già seguivo l'utente che intendo seguire
                    response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.redirection304);
                    String jsonMessage = JsonMessageBuilder.newErrorMessage("304", "Already followed", "You already follow " + usernameToFollow);
                    response.setBody(jsonMessage);
                }
            }
            else{
                //l'utente che voglo seguire non è registrato
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error404);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("404", usernameToFollow + " is not registered", "The user you are trying to follow is not registered");
                response.setBody(jsonMessage);
            }
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito alla richiesta requestFromClient sulla risorsa winsome/users/{username}/blog/{idPost}
    private HttpResponse requestOnBlogPost(HttpRequest requestFromClient) throws JsonProcessingException {
        HttpResponse response;
        switch(requestFromClient.getMethod()){
            case "GET":
                response = GETonBlogPost(requestFromClient);
                break;
            case "DELETE":
                response = DELETEonBlogPost(requestFromClient);
                break;
            default:
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error405);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("405", "Not allowed", "You are not allowed to execute " + requestFromClient.getMethod() + " on " + requestFromClient.getURL() + " resource");
                response.setBody(jsonMessage);
                break;
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta GET sulla risorsa winsome/users/{username}/blog/{idPost}. La risposta avrà uno status code pari a:
    //status code = 200 in caso di successo
    //status code = 401 se l'utente non è loggato o non è registrato o non è autorizzato
    //status code = 404 se il post non è presente nel blog dell'utente
    private HttpResponse GETonBlogPost(HttpRequest requestFromClient) throws JsonProcessingException {
        HttpResponse response;
        //vogliamo accedere alle risorse di userFromUrl
        String usernameFromUrl = requestFromClient.getURL().split("/")[3];
        String apiKey = requestFromClient.getHeaderValue("Authorization");

        //riferimento all'utente sulle cui risorse la richiesta vuole agire
        User userFromUrl = registeredUser.get(usernameFromUrl);

        //riferimento all'utente associato all'apiKey: l'apiKey inserita permette di accedere alle risorse di userFromApiKey
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));

        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a userFromUrl <->
        //l'utente userFromUrl è uguale all'utente userFromApiKey
        if(userFromUrl == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + usernameFromUrl + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userFromUrl)){
            //l'utente è loggato ma è autorizzato con l'apiKey che ha inserito a compiere l'operazione che richiede
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else {
            //l'utente è stato identificato ed è autorizzato ad eseguire la richiesta
            String URL = requestFromClient.getURL();
            long idPost = Integer.parseInt(URL.split("/")[5]);
            Post post = userFromApiKey.getBlog().get(idPost);
            if(post != null){
                //il post esiste nel blog dell'utente---> lo invio
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
                String jsonMessage = JsonMessageBuilder.getFullPostInJsonString(post);
                response.setBody(jsonMessage);
            }
            else{
                //il post non esiste nel blog dell'utente
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error404);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("404", "Post not found on this blog", "No post id= " + idPost + " was found on your blog");
                response.setBody(jsonMessage);
            }
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta DELETE sulla risorsa winsome/users/{username}/blog/{idPost}. La risposta avrà uno status code pari a:
    //status code = 200 in caso di successo
    //status code = 401 se l'utente non è loggato o non è registrato o non è autorizzato
    //status code = 404 se il post non è presente nel blog dell'utente
    private HttpResponse DELETEonBlogPost(HttpRequest requestFromClient) throws JsonProcessingException {
        HttpResponse response;
        //vogliamo accedere alle risorse di userFromUrl
        String usernameFromUrl = requestFromClient.getURL().split("/")[3];
        String apiKey = requestFromClient.getHeaderValue("Authorization");

        //riferimento all'utente sulle cui risorse la richiesta vuole agire
        User userFromUrl = registeredUser.get(usernameFromUrl);

        //riferimento all'utente associato all'apiKey: l'apiKey inserita permette di accedere alle risorse di userFromApiKey
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));

        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a userFromUrl <->
        //l'utente userFromUrl è uguale all'utente userFromApiKey
        if(userFromUrl == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + usernameFromUrl + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userFromUrl)){
            //l'utente è loggato ma è autorizzato con l'apiKey che ha inserito a compiere l'operazione che richiede
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else{
            //l'utente è stato identificato ed è autorizzato ad eseguire la richiesta
            String URL = requestFromClient.getURL();
            long idPost = Integer.parseInt(URL.split("/")[5]);
            Post post = userFromApiKey.getBlog().remove(idPost);
            if(post != null){
                //il post esiste nel blog dell'utente ed è stato rimosso ---> invio il contenuto
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
                String jsonMessage = JsonMessageBuilder.getHeaderPostInJsonString(post);
                response.setBody(jsonMessage);
                if(post.getAuthor().equals(userFromApiKey)){
                    //l'utente che sta cancellando il post è colui che lo ha creato: devo rimuovere tale post dai blog di tutti gli utenti che ne
                    //hanno fatto il rewin
                    for(User user : registeredUser.values()){
                        user.getBlog().remove(idPost);
                    }
                }
            }
            else{
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error404);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("404", "Post " + idPost +" not found on this blog", "No post id= " + idPost + " was found on your blog");
                response.setBody(jsonMessage);
            }
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito alla richiesta requestFromClient sulla risorsa winsome/users/{username}/feed/{idPost}
    private HttpResponse requestOnFeedPost(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        switch(requestFromClient.getMethod()){
            case "GET":
                response = GETonFeedPost(requestFromClient);
                break;
            case "POST":
                response = POSTonFeedPost(requestFromClient);
                break;
            default:
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error405);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("405", "Not allowed", "You are not allowed to execute " + requestFromClient.getMethod() + " on " + requestFromClient.getURL() + " resource");
                response.setBody(jsonMessage);
                break;
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta GET sulla risorsa winsome/users/{username}/feed/{idPost}. La risposta avrà uno status code pari a:
    //status code = 200 in caso di successo
    //status code = 401 se l'utente non è loggato o non è registrato o non è autorizzato
    //status code = 404 se il post non è presente nel feed dell'utente
    private HttpResponse GETonFeedPost(HttpRequest requestFromClient) throws JsonProcessingException {
        HttpResponse response;
        //vogliamo accedere alle risorse di userFromUrl
        String usernameFromUrl = requestFromClient.getURL().split("/")[3];
        String apiKey = requestFromClient.getHeaderValue("Authorization");

        //riferimento all'utente sulle cui risorse la richiesta vuole agire
        User userFromUrl = registeredUser.get(usernameFromUrl);

        //riferimento all'utente associato all'apiKey: l'apiKey inserita permette di accedere alle risorse di userFromApiKey
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));

        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a userFromUrl <->
        //l'utente userFromUrl è uguale all'utente userFromApiKey
        if(userFromUrl == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + usernameFromUrl + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userFromUrl)){
            //l'utente è loggato ma è autorizzato con l'apiKey che ha inserito a compiere l'operazione che richiede
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else {
            //l'utente è stato identificato ed è autorizzato ad eseguire la richiesta
            String URL = requestFromClient.getURL();
            long idPost = Integer.parseInt(URL.split("/")[5]);
            HashMap<Long, Post> feed = userFromApiKey.getFeed();
            Post post = feed.get(idPost);
            if(post != null){
                //il post esiste nel feed dell'utente---> lo invio
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
                String jsonMessage = JsonMessageBuilder.getFullPostInJsonString(post);
                response.setBody(jsonMessage);
            }
            else{
                //il post non è presente nel feed dell'utente
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error404);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("404", "Post not found on this feed", "No post id = " + idPost + " was found on your feed");
                response.setBody(jsonMessage);
            }
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta POST sulla risorsa winsome/users/{username}/feed/{idPost}. La risposta avrà uno status code pari a:
    //status code = 201 in caso di successo
    //status code = 401 se l'utente non è loggato o non è registrato o non è autorizzato
    //status code = 404 se il post non è presente nel feed dell'utente
    //status code = 406 se l'utente ha già votato il post o se il commento è vuoto
    private HttpResponse POSTonFeedPost(HttpRequest requestFromClient) throws IOException {
        HttpResponse response;
        //vogliamo accedere alle risorse di userFromUrl
        String usernameFromUrl = requestFromClient.getURL().split("/")[3];
        String apiKey = requestFromClient.getHeaderValue("Authorization");

        //riferimento all'utente sulle cui risorse la richiesta vuole agire
        User userFromUrl = registeredUser.get(usernameFromUrl);

        //riferimento all'utente associato all'apiKey: l'apiKey inserita permette di accedere alle risorse di userFromApiKey
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));

        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a userFromUrl <->
        //l'utente userFromUrl è uguale all'utente userFromApiKey
        if(userFromUrl == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + usernameFromUrl + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userFromUrl)){
            //l'utente è loggato ma è autorizzato con l'apiKey che ha inserito a compiere l'operazione che richiede
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else {
            //l'utente è stato identificato ed è autorizzato ad eseguire la richiesta
            String URL = requestFromClient.getURL();
            long idPost = Integer.parseInt(URL.split("/")[5]);
            HashMap<Long, Post> feed = userFromApiKey.getFeed();
            Post post = feed.get(idPost);
            if(post != null){
                //il post esiste nel feed dell'utente---> posso aggiungere una reaction
                //aggiungo la reaction alla struttura dati thread safe
                ObjectNode rootNode = JsonMessageBuilder.getJsonObjectNode(requestFromClient.getBody());
                if(rootNode.get("reaction").get("type").asText().equals("0")){
                    //voglio aggiungere un voto
                    int rate = Integer.parseInt(rootNode.get("reaction").get("rate").asText());
                    Reaction reaction = Reaction.newVote(userFromApiKey, rate, true);
                    if(!post.getReaction().add(reaction)){
                        //hai già votato il post
                        response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error406);
                        String jsonMessage = JsonMessageBuilder.newErrorMessage("406", "You have already voted this post", "You can not vote a post multiple times");
                        response.setBody(jsonMessage);
                        return  response;
                    }
                }
                else{
                    //voglio aggiungere un commento
                    if(rootNode.get("reaction").get("content").asText().isEmpty()){
                        //sto provando ad aggiugner un commento vuoto
                        System.out.println(rootNode.get("reaction").get("content").asText().length());
                        response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error406);
                        String jsonMessage = JsonMessageBuilder.newErrorMessage("406", "Empty comment", "You can not post an empty comment");
                        response.setBody(jsonMessage);
                        return response;
                    }
                    else{
                        post.getReaction().add(Reaction.newComment(userFromApiKey, rootNode.get("reaction").get("content").asText(), true));
                    }
                }
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success201);
                response.setBody(requestFromClient.getBody());
            }
            else{
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error404);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("404", "Post not found on this feed", "No post id = " + idPost + " was found on your feed");
                response.setBody(jsonMessage);
            }
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito alla richiesta requestFromClient sulla risorsa winsome/users/{username}/following/{usernameToUnfollow}
    private HttpResponse requestOnFollowingUser(HttpRequest requestFromClient) throws JsonProcessingException, RemoteException {
        HttpResponse response;
        if (requestFromClient.getMethod().equals("DELETE"))
            response = DELETEEonFollowingUser(requestFromClient);
        else{
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error405);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("405", "Not allowed", "You are not allowed to execute " + requestFromClient.getMethod() + " on " + requestFromClient.getURL() + " resource");
            response.setBody(jsonMessage);
        }
        return response;
    }

    //effects: ritorna la risposta Http generata in seguito ad una richiesta POST sulla risorsa winsome/users/{username}/following/{usernameToUnfollow}. La risposta avrà uno status code pari a:
    //status code = 200 in caso di successo
    //status code = 401 se l'utente non è loggato o non è registrato o non è autorizzato
    //status code = 404 se usernameToUnfollow non è nella lista di following

    private HttpResponse DELETEEonFollowingUser(HttpRequest requestFromClient) throws JsonProcessingException, RemoteException {
        HttpResponse response;
        //vogliamo accedere alle risorse di userFromUrl
        String usernameFromUrl = requestFromClient.getURL().split("/")[3];
        String apiKey = requestFromClient.getHeaderValue("Authorization");

        //riferimento all'utente sulle cui risorse la richiesta vuole agire
        User userFromUrl = registeredUser.get(usernameFromUrl);

        //riferimento all'utente associato all'apiKey: l'apiKey inserita permette di accedere alle risorse di userFromApiKey
        User userFromApiKey = loggedUser.get(Token.createFakeToken(apiKey));

        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a userFromUrl <->
        //l'utente userFromUrl è uguale all'utente userFromApiKey
        if(userFromUrl == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + usernameFromUrl + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userFromUrl)){
            //l'utente è loggato ma è autorizzato con l'apiKey che ha inserito a compiere l'operazione che richiede
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else{
            //l'utente è stato identificato ed è autorizzato a eseguire la richiesta
            String URL = requestFromClient.getURL();
            String usernameToUnfollow = URL.split("/")[5];
            User userToUnfollow = registeredUser.get(usernameToUnfollow);
            if(userFromApiKey.getFollowedUsers().remove(userToUnfollow)){
                //l'utente seguiva l'utente che vuole unfolloware e ora non lo segue più
                IntRMIClient callbackUser = userToNotify.get(usernameToUnfollow);
                if(callbackUser != null){
                    //l'utente che ho seguito era registrato per le callback sugli utenti
                    //lo notifico della perdita di un follower
                    callbackUser.notifyLostFollower(userFromApiKey.getUsername());
                }
                //lo rimuovo dal set dell'utente unfollowato
                userToUnfollow.getFollowers().remove(userFromApiKey);
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
                String jsonMessage = JsonMessageBuilder.getBasicUserInfoInJsonString(userToUnfollow);
                response.setBody(jsonMessage);
            }
            else{
                //l'utente non seguiva l'utente che vuole unfolloware
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error404);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("404", "You do not follow " + usernameToUnfollow, usernameToUnfollow + "is not in your following list");
                response.setBody(jsonMessage);
            }
        }
        return response;
    }
}
