package winsome.server;

import winsome.RESTfulUtility.Token;
import winsome.client.IntRMIClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import winsome.RESTfulUtility.HttpResponse;
import winsome.jsonUtility.JsonMessageBuilder;
import winsome.resourseRappresentation.User;
import java.io.UnsupportedEncodingException;
import java.rmi.RemoteException;
import java.rmi.server.RemoteServer;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class ImplRMIServer extends RemoteServer implements  IntRMIServer {
    final private ConcurrentHashMap<String, User> registeredUser;
    final private ConcurrentHashMap<String, IntRMIClient> usersToNotify;
    final private  ConcurrentHashMap<Token, User> loggedUsers;

    //riferimenti prelevati da winsomeServer
    public ImplRMIServer(WinSomeServer winSomeServer){
        //prelevo i riferimenti da winsomeServer
        this.registeredUser = winSomeServer.getRegisteredUser();
        this.usersToNotify = winSomeServer.getUserToNotify();
        this.loggedUsers = winSomeServer.getLoggedUser();
    }

    //effects: gestisce la richiesta di iscrizione di un utentea WINSOME. Ritorna una stringa contenente una risposta HTTP con
    //status code = 201 in caso di successo
    //staus code = 412 se l'username o la password non sono validi
    //status code = 409 se l'username è già in uso
    public String registerUser(String username, String password, ArrayList<String> tags) throws RemoteException, UnsupportedEncodingException {
        HttpResponse response = null;
        try {
            if (username == null || password == null || username.isEmpty() || password.isEmpty()) {
                //password o username invalidi
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error412);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("412", "Invalid username or password", "The username or password entered are invalid: make sure you have entered a non-empty username or password");
                response.setBody(jsonMessage);
                return response.getRelateString();
            }
            //rendo lowercase tutti i tag passati
            for (String tag : tags) {
                String newValue = tag.toLowerCase(Locale.ROOT);
                tags.set(tags.indexOf(tag), newValue);
            }
            User newUser = new User(username, password, tags);
            //esiste già un utente con tale username?
            if (registeredUser.putIfAbsent(username, newUser) != null) { //accesso atomico alla struttura dati
                //l'utente esisteva già
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error409);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("409", "Already used username", "The username entered is already in use");
                response.setBody(jsonMessage);
            }
            else {
                //l'utente è stato registrato
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success201);
                String jsonMessage = JsonMessageBuilder.getRegistrationInfoJsonString(newUser);
                response.setBody(jsonMessage);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return response.getRelateString();
    }

    //effects: gestisce la richiesta di iscrizione al servizio di callback WINSOME. Ritorna una stringa contenente una risposta HTTP con
    //status code = 200 in caso di successo
    //staus code = 401 se l'utente non è registrato, non è loggato o non è autenticato
    //status code = 409 se l'utente è già registrato al servizio di callback
    public String registerForCallbackFollower(String username, String apiKey, IntRMIClient client) throws RemoteException, JsonProcessingException, UnsupportedEncodingException {
        User userToRegister = registeredUser.get(username);
        User userFromApiKey = loggedUsers.get(Token.createFakeToken(apiKey));
        HttpResponse response;
        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata all'utente userFromUrl eventualmente registrato e loggato
        if(userToRegister == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + username + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userToRegister)){
            //l'utente è loggato ma non ha inserito il token che gli permette di compiere l'operazione richiesta
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else{
            if(usersToNotify.putIfAbsent(username, client) != null){
                //l'utente era già registrato al servizio di callback
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error406);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("409", "Already registered", "You are already registered for callback message");
                response.setBody(jsonMessage);
            }
            else{
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
            }
        }
        return response.getRelateString();
    }

    //effects: gestisce la richiesta di annullamento dell'iscrizione al servizio di callback WINSOME. Ritorna una stringa contenente una risposta HTTP con
    //status code = 200 in caso di successo
    //staus code = 401 se l'utente non è registrato, non è loggato o non è autenticato
    //status code = 409 se l'utente non è già registrato al servizio di callback
    public String unregisterForCallbackFollower(String username, String apiKey, IntRMIClient client) throws RemoteException, JsonProcessingException, UnsupportedEncodingException {
        User userToRegister = registeredUser.get(username);
        User userFromApiKey = loggedUsers.get(Token.createFakeToken(apiKey));
        HttpResponse response;
        //permetto di compiere azione sulla risorsa di userFromUrl solo se la richiesta contiene l'api-key associata a tale utente
        if(userToRegister == null){
            //l'utente non è registrato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not registered", "The supposed user " + username + " is not register so the resource does not exist");
            response.setBody(jsonMessage);
        }
        else if(userFromApiKey == null){
            //l'api-key non corrisponde ad alcun utente: l'utente richiedente non è loggato
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "Not logged", "You are not logged. Please log in to perform this action");
            response.setBody(jsonMessage);
        }
        else if(!userFromApiKey.equals(userToRegister)){
            //l'utente è loggato ma non ha inserito il token che gli permette di compiere l'operazione richiesta
            response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error401);
            String jsonMessage = JsonMessageBuilder.newErrorMessage("401", "You are not authorized to perform this action", "The api-key of your request does not match our api-key. Please add correct api-key to perform the action");
            response.setBody(jsonMessage);
        }
        else{
            if(! usersToNotify.remove(username, client)){
                //l'utente non era registrato al servizio di callback
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.error409);
                String jsonMessage = JsonMessageBuilder.newErrorMessage("409", "Not register to RMI callback service", "You are not register to RMI callback service");
                response.setBody(jsonMessage);
            }
            else{
                response = HttpResponse.newHttpResponse(HttpResponse.StatusCode.success200);
            }
        }
        return response.getRelateString();
    }
}
