package winsome.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import winsome.client.IntRMIClient;
import java.io.UnsupportedEncodingException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;

public interface IntRMIServer extends Remote {
    String registerUser(String username, String password, ArrayList<String> tags) throws RemoteException, UnsupportedEncodingException;
    String registerForCallbackFollower(String username, String apiKey, IntRMIClient client) throws RemoteException, JsonProcessingException, UnsupportedEncodingException;
    String unregisterForCallbackFollower(String username, String apiKey, IntRMIClient client) throws RemoteException, JsonProcessingException, UnsupportedEncodingException;
}
