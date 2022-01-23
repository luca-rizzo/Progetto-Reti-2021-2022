package winsome.client;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface IntRMIClient extends Remote {
    void notifyNewFollower(String usernameNewFollower) throws RemoteException;
    void notifyLostFollower(String usernameLostFollower) throws RemoteException;
}
