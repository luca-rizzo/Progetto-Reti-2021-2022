package winsome.resourseRappresentation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

public class User {
    //mantiene le relazioni <idPost, Post con tale idPost>: rappresenta il blog associato all'utente
    final private ConcurrentHashMap<Long, Post> blog;
    //HashSet concorrente che mantiene il riferimento al set di followers
    final private RWHashSet<User> followedUsers;
    //HashSet concorrente che mantiene il rifermento ai following/utenti che segui
    final private RWHashSet<User> followers;
    //username univoco dell'utente
    final private String username;
    //password associata all'utente
    final private String password;
    //lista di tags associati all'utente
    final private ArrayList<String> tags;
    //Oggetto concorrente che mantiene le informazioni relative al portafoglio di un utente
    final private Wallet wallet;

    public User(String username, String password, ArrayList<String> tags) {
        this.username = username;
        this.tags = tags;
        this.password = password;
        this.followedUsers = new RWHashSet<>();
        this.followers = new RWHashSet<>();
        this.blog = new ConcurrentHashMap<>();
        this.wallet = new Wallet();
    }

    //due utenti sono uguali se hanno lo stesso username
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        User user = (User) o;

        return username.equals(user.username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }
    //*****GETTERS AND SETTERS*****//
    public RWHashSet<User> getFollowers() {
        return followers;
    }
    public RWHashSet<User> getFollowedUsers() {
        return followedUsers;
    }
    public String getUsername() {
        return username;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public String getPassword() {
        return password;
    }

    public ArrayList<String> getTags() {
        return tags;
    }

    public ConcurrentHashMap<Long, Post> getBlog() {
        return blog;
    }

    //effects: scorre il set di utenti seguiti e aggiunge ad una HashMap locale tutte le entry relative al blog dell'utente seguito
    //(la get sulle entry della ConcurrentHashMap è thread-safe)
    public HashMap<Long, Post> getFeed(){
        HashMap<Long, Post> feed = new HashMap<>();
        for(User user: followedUsers.getHashSet())
            feed.putAll(user.getBlog());
        return feed;
    }
}
