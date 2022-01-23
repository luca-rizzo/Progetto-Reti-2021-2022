package winsome.client;

import winsome.RESTfulUtility.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import winsome.jsonUtility.JsonMessageBuilder;
import winsome.resourseRappresentation.RWHashSet;
import winsome.server.IntRMIServer;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashSet;

public class APIClient implements APIClientInterface{
    final private ClientConfiguration clientConfiguration;
    final private String clientHostname;
    final private String tokensURI;
    private Socket clientSocket;
    private String serverHostname;
    private String apiKey;
    private String mytokenURI;
    private String multicastAddress;
    private String accountURI;
    private String blogURI;
    private String feedURI;
    private String followingURI;
    private String allAccountURI;
    private BufferedReader reader;
    private boolean logged;
    private BufferedOutputStream writeSide;
    private ArrayList<String> tags;
    private RWHashSet<String> followers;
    private ImplRMIClient callbackObject;
    private IntRMIClient stub;
    private final IntRMIServer remoteServerObject;
    private receiveNotificationThread receiveNotification;

    public APIClient(String pathConfigurationFile) throws IOException, NotBoundException {
        //inizializzo istanza APIClient con il file di configurazione
        ObjectMapper mapper = new ObjectMapper();
        clientConfiguration = mapper.readValue(Paths.get(pathConfigurationFile).toFile(), ClientConfiguration.class);
        this.clientHostname = InetAddress.getLocalHost().getHostName();
        this.followers = new RWHashSet<>();
        this.tags = new ArrayList<>();
        this.tokensURI = "/winsome/tokens";
        this.serverHostname = "";
        this.multicastAddress = "";
        this.apiKey = "";
        this.blogURI = "/";
        this.feedURI = "/";
        this.followingURI = "/";
        this.allAccountURI = "/";
        this.mytokenURI = "/";
        this.accountURI = "/";
        this.writeSide = null;
        this.logged = false;
        //ottengo un riferimento all'oggetto server remoto esportato per poter effettuare registrazione a WINSOME e al servizio di callback
        Registry r = LocateRegistry.getRegistry(clientConfiguration.getServerRegistryPort());
        this.remoteServerObject = (IntRMIServer) r.lookup(clientConfiguration.getRMIObjectbindingName());
    }
    //effects: effettua una richiesta di registrazione al server. Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla
    //nostra richiesta
    public HttpResponse register(String username, String password, ArrayList<String> tags) throws RemoteException, NotBoundException, ParseException, UnsupportedEncodingException {
        //chiama il metodo registerUser esposto dall'oggetto esportato
        return HttpResponse.newHttpResponseFromString(remoteServerObject.registerUser(username, password, tags));
    }

    //effects: effettua una richiesta di login al server. Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla
    //nostra richiesta
    public HttpResponse login(String username, String password) throws IOException, ParseException{
        //attivo connessione TCP con i server attraverso i riferimenti ottenuti dal file di configurazione
        clientSocket = new Socket(InetAddress.getByName(clientConfiguration.getServerIP()), clientConfiguration.getListenSocketPort());
        this.serverHostname = clientSocket.getInetAddress().getHostName();
        this.writeSide = new BufferedOutputStream(clientSocket.getOutputStream());
        this.reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        //creo una richiesta HTTP POST sulla risorsa serverHostname/winsome/tokens che contiene come body le credenziali passate come argomento
        HttpRequest request = HttpRequest.newRequest(serverHostname  + tokensURI, HttpRequest.Method.POST);
        request.addHeader("Host", clientHostname);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode childNode = rootNode.putObject("Credentials");
        childNode.put("username", username);
        childNode.put("password", password);
        request.setBody(mapper.writeValueAsString(rootNode));
        byte[] requestBytes = request.getRelateByteBuffer().array();
        //invio la richiesta al server attraverso la socket TCP
        writeSide.write(requestBytes);
        writeSide.flush();
        //ottengo la risposta
        HttpResponse response = HttpResponse.newHttpResponseFromStream(reader);
        if(response.getStatusCode().equals("201")){
            //creo un nuovo oggetto per il callback
            this.callbackObject = new ImplRMIClient(followers);
            //ottengo lo stub
            this.stub = (IntRMIClient) UnicastRemoteObject.exportObject(callbackObject,0);
            this.logged = true;
            //salvo tutte le informazioni inviate dal server necessarie per il corretto funzionamento del meccanismo di comunicazione

            ObjectNode objectNode = JsonMessageBuilder.getJsonObjectNode(response.getBody());
            //salvo apiKey per autenticare richieste successive alla risorsa
            this.apiKey = objectNode.get("newToken").get("api-key").asText();
            //mantengo riferimento ad URI del mio toen per poter successivamente eseguire logout
            this.mytokenURI = objectNode.get("newToken").get("href").asText();
            //salvo la lista dei tag di mio interesse da usare per creare query nel metodo listUsers()
            for(JsonNode tag : objectNode.get("your tags"))
                this.tags.add(tag.asText());
            //inizializzo la struttura dati locale followers con la lista di follower inviata dal server
            JsonNode arrayFollowers = objectNode.get("your followers");
            for(JsonNode follower : arrayFollowers){
                followers.add(follower.get("username").asText());
            }
            JsonNode linkNode = objectNode.get("links");
            //salvo URI di riferimento  alla risorsa che identifica la lista di utenti WINSOME
            this.allAccountURI = linkNode.get(0).get("href").asText();
            //salvo URI di riferimento alla risorsa che identifica il mio account
            this.accountURI = linkNode.get(1).get("href").asText();
            //salvo URI di riferimento alla risorsa che identifica il mio blog
            this.blogURI = linkNode.get(2).get("href").asText();
            //salvo URI di riferimento alla risorsa che identifica il mio feed
            this.feedURI = linkNode.get(3).get("href").asText();
            //salvo URI di riferimento alla risorsa che identifica la lista following
            this.followingURI = linkNode.get(4).get("href").asText();
            //ottengo indirizzo IP e porta del gruppo multicast al quale devo iscrivermi per ottenere notifiche sul calcolo delle ricompense
            this.multicastAddress = objectNode.get("multicast-Address").asText();
            int multicastPort = objectNode.get("multicast-Port").asInt();
            //mi registro al meccanismo di callback passando lo stub al metodo dell'oggetto esportato
            HttpResponse registerCallbackResponse = HttpResponse.newHttpResponseFromString(remoteServerObject.registerForCallbackFollower(username, apiKey, this.stub));
            if(registerCallbackResponse.getStatusCode().equals("200")){
                System.out.println("You have registered for the callback service");
            }
            else{
                System.out.println("Error in registration for callback service");
                ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(registerCallbackResponse.getBody());
                System.out.println("Error: " + errorJSONObject.get("message").asText());
                System.out.println("Description: " + errorJSONObject.get("description").asText());
            }
            //creo un InetSocketAddress che identifica il gruppo multicast al quale il thread deve iscriversi
            InetSocketAddress multicastGroup = new InetSocketAddress(InetAddress.getByName(multicastAddress), multicastPort);
            receiveNotification = new receiveNotificationThread(multicastGroup, clientConfiguration.getNameOfNetworkInterfaceDevice());
            receiveNotification.start();
        }
        return response;
    }

    //effects: effettua una richiesta al server per ottenere la lista delle informazioni private relative all'account loggato
    //crea una richiesta HTTP GET sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse accountPrivateInfo() throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(serverHostname  + accountURI, HttpRequest.Method.GET);
        //aggiungo api-Key per autenticare la richiesta
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        request.addHeader("Accept",  "application/json");
        byte[] requestBytes = request.getRelateByteBuffer().array();
        //invia una richiesta HTTP
        writeSide.write(requestBytes);
        writeSide.flush();
        //attende una risposta
        return HttpResponse.newHttpResponseFromStream(reader);
    }

    //effects: effettua una richiesta al server per ottenere il blog associato all'utente loggato
    //crea una richiesta HTTP GET sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/blog
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse viewBlog() throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(serverHostname  + blogURI, HttpRequest.Method.GET);
        //aggiungo api-Key per autenticare la richiesta
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        request.addHeader("Accept",  "application/json");
        byte[] requestBytes = request.getRelateByteBuffer().array();
        //invia una richiesta HTTP
        writeSide.write(requestBytes);
        //attende una risposta
        writeSide.flush();
        return HttpResponse.newHttpResponseFromStream(reader);
    }

    //effects: effettua una richiesta al server per creare un nuovo post nel blog dell'utente loggato
    //crea una richiesta HTTP POST sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/blog con title e content come body in JSON
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse createPost (String title, String content) throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(serverHostname  + blogURI, HttpRequest.Method.POST);
        //aggiungo api-Key per autenticare la richiesta
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode childNode = rootNode.putObject("newPost");
        childNode.put("title", title);
        childNode.put("content", content);
        request.setBody(mapper.writeValueAsString(rootNode));
        byte[] requestBytes = request.getRelateByteBuffer().array();
        //invia una richiesta HTTP
        writeSide.write(requestBytes);
        writeSide.flush();
        //attende una risposta
        return HttpResponse.newHttpResponseFromStream(reader);
    }

    //effects: effettua una richiesta al server per effettuare rewin di un post del feed nel blog dell'utente loggato
    //crea una richiesta HTTP POST sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/blog con idPost come body in JSON
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse rewinPost (String idPost) throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(serverHostname  + blogURI, HttpRequest.Method.POST);
        request.addHeader("Authorization", apiKey);
        //aggiungo api-Key per autenticare la richiesta
        request.addHeader("Host", clientHostname);
        request.addHeader("Content-type",  "application/json");
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode childNode = rootNode.putObject("rewinPost");
        childNode.put("idPost", idPost);
        request.setBody(mapper.writeValueAsString(rootNode));
        byte[] requestBytes = request.getRelateByteBuffer().array();
        //invia una richiesta HTTP
        writeSide.write(requestBytes);
        writeSide.flush();
        //attende una risposta
        return HttpResponse.newHttpResponseFromStream(reader);
    }

    //effects: effettua una richiesta al server per eliminare un post dal blog dell'utente loggato
    //crea una richiesta HTTP DELETE sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/blog/idPost
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse deletePost ( String idPost ) throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(serverHostname  + blogURI + "/" + idPost, HttpRequest.Method.DELETE);
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        byte[] requestBytes = request.getRelateByteBuffer().array();
        writeSide.write(requestBytes);
        writeSide.flush();
        return HttpResponse.newHttpResponseFromStream(reader);
    }

    //effects: effettua una richiesta al server per eseguire il logout. Interrompe il thread per la ricezione delle notifiche, si disiscrive al servizio di callback
    // e esegue "unexport" dell'oggetto esportato per il callback.
    //crea una richiesta HTTP DELETE sulla risorsa serverHostname/winsome/tokens/{idTokenSessioneAttuale}
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse logout() throws IOException, ParseException, InterruptedException {
        //mi disiscrivo al servizio di callback
        HttpResponse unregisterCallbackResponse = HttpResponse.newHttpResponseFromString(remoteServerObject.unregisterForCallbackFollower(accountURI.split("/")[3], apiKey, this.stub));
        if(unregisterCallbackResponse.getStatusCode().equals("200")){
            System.out.println("You are no longer registered for the callback service");
        }
        else{
            System.out.println("Error deleting the callback service registration");
            ObjectNode errorJSONObject = JsonMessageBuilder.getJsonObjectNode(unregisterCallbackResponse.getBody());
            System.out.println("Error: " + errorJSONObject.get("message").asText());
            System.out.println("Description: " + errorJSONObject.get("description").asText());
        }
        HttpRequest request = HttpRequest.newRequest(serverHostname  + mytokenURI , HttpRequest.Method.DELETE);
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        byte[] requestBytes = request.getRelateByteBuffer().array();
        writeSide.write(requestBytes);
        writeSide.flush();
        HttpResponse response = HttpResponse.newHttpResponseFromStream(reader);
        if(response.getStatusCode().equals("200")){
            resetAll();
        }
        return response;
    }
    //effects: chiude la connessione attiva con il server, esegue "unexport" dell'oggetto esportato per il callback,
    // interrompe ed esegue il join del thread in ascolto per la recezione delle notifiche e resetta tutti i parametri non final dell'oggetto
    private void resetAll() throws IOException, InterruptedException {
        if(clientSocket.isConnected()){
            clientSocket.close();
            this.reader.close();
            this.writeSide.close();
            this.writeSide=null;
            this.reader=null;
            clientSocket = null;
        }
        this.followers = new RWHashSet<>();
        this.tags = new ArrayList<>();
        this.serverHostname = "";
        this.multicastAddress = "";
        this.apiKey = "";
        this.blogURI = "/";
        this.feedURI = "/";
        this.followingURI = "/";
        this.allAccountURI = "/";
        this.mytokenURI = "/";
        this.writeSide = null;
        this.logged = false;
        UnicastRemoteObject.unexportObject(this.callbackObject,false);
        this.callbackObject = null;
        this.stub = null;
        receiveNotification.interrupt();
        receiveNotification.join();
    }

    //effects: effettua una richiesta al server far seguire l'utente username all'utente loggato
    //crea una richiesta HTTP POST sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/following che conterrà username in JSON come body
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse followUser(String username) throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(serverHostname  + followingURI, HttpRequest.Method.POST);
        //aggiungo api-Key per autenticare la richiesta
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        request.addHeader("Content-type",  "application/json");
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode childNode = rootNode.putObject("userToFollow");
        childNode.put("username", username);
        request.setBody(mapper.writeValueAsString(rootNode));
        byte[] requestBytes = request.getRelateByteBuffer().array();
        writeSide.write(requestBytes);
        writeSide.flush();
        return HttpResponse.newHttpResponseFromStream(reader);
    }
    //effects: effettua una richiesta al server per eliminare utente username dalla lista degli utenti seguiti dall'utente loggato
    //crea una richiesta HTTP DELETE sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/following/username
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse unfollowUser(String username) throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(serverHostname  + followingURI + "/" + username, HttpRequest.Method.DELETE);
        //aggiungo api-Key per autenticare la richiesta
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        byte[] requestBytes = request.getRelateByteBuffer().array();
        writeSide.write(requestBytes);
        writeSide.flush();
        return HttpResponse.newHttpResponseFromStream(reader);
    }

    //effects: effettua una richiesta al server per ottenere il feed associato all'utente loggato
    //crea una richiesta HTTP GET sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/feed
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse viewFeed() throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(serverHostname  + feedURI, HttpRequest.Method.GET);
        //aggiungo api-Key per autenticare la richiesta
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        request.addHeader("Accept",  "application/json");
        byte[] requestBytes = request.getRelateByteBuffer().array();
        writeSide.write(requestBytes);
        writeSide.flush();
        return HttpResponse.newHttpResponseFromStream(reader);
    }

    //effects: effettua una richiesta al server per aggiungere un voto ad un post del feed associato all'utente loggato
    //crea una richiesta HTTP POST sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/feed/idPost che conterrà il valore del voto in formato JSON come body
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse ratePost(String idPost, String rate) throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(serverHostname  + feedURI + "/" + idPost, HttpRequest.Method.POST);
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        request.addHeader("Accept",  "application/json");
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode childNode = rootNode.putObject("reaction");
        childNode.put("type", 0);
        childNode.put("rate", rate);
        request.setBody(mapper.writeValueAsString(rootNode));
        byte[] requestBytes = request.getRelateByteBuffer().array();
        writeSide.write(requestBytes);
        writeSide.flush();
        return HttpResponse.newHttpResponseFromStream(reader);
    }

    //effects: effettua una richiesta al server per aggiungere un commento ad un post del feed associato all'utente loggato
    //crea una richiesta HTTP POST sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/feed/idPost che conterrà il contenuto del commento in formato JSON come body
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse addComment(String idPost, String content) throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(serverHostname  + feedURI + "/" + idPost, HttpRequest.Method.POST);
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        request.addHeader("Accept",  "application/json");
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode childNode = rootNode.putObject("reaction");
        childNode.put("type", 1);
        childNode.put("content", content);
        request.setBody(mapper.writeValueAsString(rootNode));
        byte[] requestBytes = request.getRelateByteBuffer().array();
        writeSide.write(requestBytes);
        writeSide.flush();
        return HttpResponse.newHttpResponseFromStream(reader);
    }

    //effects: effettua una richiesta al server per ottenere un post appartenente al feed o al blog dell'utente all'utente loggato
    //crea due richiesta HTTP GET: una sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/feed/idPost, l'altra
    //sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/blog/idPost
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public  HttpResponse showPost(String idPost) throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(serverHostname  + feedURI + "/" + idPost, HttpRequest.Method.GET);
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        request.addHeader("Accept",  "application/json");
        byte[] requestBytes = request.getRelateByteBuffer().array();
        writeSide.write(requestBytes);
        writeSide.flush();
        HttpResponse response = HttpResponse.newHttpResponseFromStream(reader);
        if(response.getStatusCode().equals("404")){
            //il post non è presente nel feed
            request = HttpRequest.newRequest(serverHostname  + blogURI + "/" + idPost, HttpRequest.Method.GET);
            request.addHeader("Authorization", apiKey);
            request.addHeader("Host", clientHostname);
            request.addHeader("Accept",  "application/json");
            requestBytes = request.getRelateByteBuffer().array();
            writeSide.write(requestBytes);
            writeSide.flush();
            response = HttpResponse.newHttpResponseFromStream(reader);
            if(response.getStatusCode().equals("404")){
                //il post non è presente neanche nel blog
                String jsonMessage = JsonMessageBuilder.newErrorMessage("404", "Post not found", "Post id = " + idPost + " was not found in your blog nor in your feed");
                response.setBody(jsonMessage);
            }
        }
        return response;
    }

    //effects: effettua una richiesta al server per ottenere la lista di utenti iscritti a WINSOME con gli stessi tag di interesse dell'utente loggato
    //crea una richiesta HTTP GET sulla risorsa serverHostname/winsome/users con una query nell'URL che contiene la lista di tag dell'utente loggato
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse listUsers() throws IOException, ParseException {
        StringBuilder URL = new StringBuilder(serverHostname + allAccountURI);
        if(tags!= null && !tags.isEmpty()){
            //aggiungo tutti i tag di interesse nell'URL
            URL.append("?");
            for(String tag : tags){
                URL.append("tag=").append(tag).append("&");
            }
            //rimuovo ultimo &
            URL = new StringBuilder(URL.substring(0, URL.length() - 1));
        }
        else{
            //nessun tag di interesse
            URL.append("?tag=empty");
        }
        HttpRequest request = HttpRequest.newRequest(URL.toString(), HttpRequest.Method.GET);
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        request.addHeader("Accept",  "application/json");
        byte[] requestBytes = request.getRelateByteBuffer().array();
        writeSide.write(requestBytes);
        writeSide.flush();
        return HttpResponse.newHttpResponseFromStream(reader);
    }

    //effects: effettua una richiesta al server per ottenere la lista di utenti che l'utente loggato segue
    //crea una richiesta HTTP GET sulla risorsa serverHostname/winsome/users/{usernameUtenteLoggato}/following
    //Ritornerà un'istanza di HttpResponse che contiene la risposta del server alla nostra richiesta
    public HttpResponse listFollowing() throws IOException, ParseException {
        HttpRequest request = HttpRequest.newRequest(this.followingURI, HttpRequest.Method.GET);
        request.addHeader("Authorization", apiKey);
        request.addHeader("Host", clientHostname);
        request.addHeader("Accept",  "application/json");
        byte[] requestBytes = request.getRelateByteBuffer().array();
        writeSide.write(requestBytes);
        writeSide.flush();
        return HttpResponse.newHttpResponseFromStream(reader);
    }

    //effects: ritorna il set di followers inizializzato al momento del login e aggiornato tramite RMI callback
    public HashSet<String> listFollowers(){
        return this.followers.getHashSet();
    }

    public String getAccountURI() {
        return accountURI;
    }

    //effects: ritorna true se l'oggetto ha una connessione TCP attiva con un utente loggato
    public boolean isLogged() {
        return logged;
    }

}
