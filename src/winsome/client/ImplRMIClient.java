package winsome.client;

import winsome.resourseRappresentation.RWHashSet;
import java.rmi.RemoteException;
import java.rmi.server.RemoteObject;

public class ImplRMIClient extends RemoteObject implements IntRMIClient{
    //riferimento alla struttura dati concorrente che mantiene set di followers
    private final RWHashSet<String> followers;

    public ImplRMIClient(RWHashSet<String> followers) throws RemoteException {
        super();
        this.followers = followers;
    }
    //effects: stampa un messaggio di notifca nuovo follower e aggiunge la stringa usernameNewFollower al RWHashSet locale nel client
    @Override
    public void notifyNewFollower(String usernameNewFollower) throws RemoteException {
        System.out.println("You have a new follower: " + usernameNewFollower);
        followers.add(usernameNewFollower);
    }
    //effects: stampa un messaggio di notifca follower perso e rimuove la stringa usernameLostFollower dal RWHashSet locale nel client
    @Override
    public void notifyLostFollower(String usernameLostFollower) throws RemoteException {
        System.out.println("You have lost a follower: " + usernameLostFollower);
        followers.remove(usernameLostFollower);
    }
}
