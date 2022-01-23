package winsome.jsonUtility;

import winsome.RESTfulUtility.LinkResourse;
import winsome.RESTfulUtility.Token;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import winsome.resourseRappresentation.*;
import winsome.server.ServerConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class JsonMessageBuilder {

    //effects: ritorna una stringa JSON che contiene informazioni su un particolare errore
    static public String newErrorMessage(String error, String message, String description) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootnode = mapper.createObjectNode();
        rootnode.put("error", error);
        rootnode.put("message", message);
        rootnode.put("description", description);
        return mapper.writeValueAsString(rootnode);
    }

    //effects: ritorna una stringa JSON che contiene tutte le informazioni relative al post passato come argomento
    static public String getFullPostInJsonString(Post post) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode childNode = rootNode.putObject("post");
        childNode.put("idPost", post.getId());
        childNode.put("title", post.getTitolo());
        childNode.put("author-username", post.getAuthor().getUsername());
        childNode.put("content", post.getContenuto());
        RWHashSet<Reaction> reactionList = post.getReaction();
        ArrayList<Reaction> comments = new ArrayList<>();
        int upVote = 0;
        int downVote = 0;
        //itero in maniera thread-safe lungo le reaction attraverso la copia restituita da reactionList.getHashSet()
        for(Reaction reaction: reactionList.getHashSet()){
            if(reaction.getType() == 0){
                //è un voto
                if(reaction.getRate() == 1)
                    upVote++;
                else
                    downVote++;
            }
            else{
                //è un commento
                comments.add(reaction);
            }
        }
        childNode.put("upVotes", upVote);
        childNode.put("downVotes", downVote);
        ArrayNode listComment = mapper.createArrayNode();
        for(Reaction comment : comments){
            ObjectNode commentObjectNode = listComment.addObject();
            commentObjectNode.put("content", comment.getContent());
            commentObjectNode.put("author", comment.getAuthor().getUsername());
        }
        childNode.set("comment", listComment);
        return mapper.writeValueAsString(rootNode);
    }

    //effecst: ritorna una stringa JSON che contiene idPost, titolo e autore relativo al post passato come argomento
    static public String getHeaderPostInJsonString(Post post) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.writeValueAsString(getHeaderPostInJsonObj(post));
    }

    //metodo privato per la creazione di un ObjectNode associato ad un post
    static private ObjectNode getHeaderPostInJsonObj(Post post){
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode childNode = rootNode.putObject("post");
        childNode.put("idPost", post.getId());
        childNode.put("title", post.getTitolo());
        childNode.put("author-username", post.getAuthor().getUsername());
        return rootNode;
    }

    //effects: ritorna una stringa Json che contiene il blog relativo ad un utente: viene ricreata la lista di post del blog.
    //Per ciascun post verranno inseriti idPost, titolo e autore
    static public String getBlogInJsonString(ConcurrentHashMap<Long,Post> blog) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        ArrayNode childNode = rootNode.putArray("blog posts");
        Collection<Post> blogPosts = blog.values();
        for(Post post : blogPosts){
            childNode.add(JsonMessageBuilder.getHeaderPostInJsonObj(post));
        }
        return mapper.writeValueAsString(rootNode);
    }

    //effects: ritorna una stringa Json che contiene il feed relativo ad un utente: viene ricreata la lista di post del feed.
    //Per ciascun post verranno inseriti idPost, titolo e autore
    static public String getFeedInJsonString(HashMap<Long,Post> feed) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        ArrayNode childNode = rootNode.putArray("feed posts");
        Collection<Post> feedPosts = feed.values();
        for(Post post : feedPosts){
            childNode.add(JsonMessageBuilder.getHeaderPostInJsonObj(post));
        }
        return mapper.writeValueAsString(rootNode);
    }

    //effects: ritorna la stringa JSON che contiene il messaggio da inviare ad un utente in seguito ad un login effettuato correttamente
    //contiene: id del token, api-key del token, riferimento al nuovo token, indirizzo e porta multicast,
    //lista followers che serve al client per inizializzare il set di follower che mantiene in locale,
    //lista tag relativi all'utente e link a tutte le URI associate all'utente loggato
    static public String getLoginInfoJsonString(User user, ServerConfiguration serverConfiguration, Token newToken) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode childNode = rootNode.putObject("newToken");
        childNode.put("id", newToken.getId());
        childNode.put("api-key", newToken.getApiKey());
        childNode.put("href", "/winsome/tokens/" + newToken.getId());
        rootNode.set("newToken", childNode);
        rootNode.put("multicast-Address", serverConfiguration.getMulticastAddress());
        rootNode.put("multicast-Port", serverConfiguration.getMulticastPort());
        rootNode.set("your tags", mapper.valueToTree(user.getTags()));
        rootNode.set("your followers", JsonMessageBuilder.getUserListInJsonArray(user.getFollowers().getHashSet()));
        ArrayList<LinkResourse> linkArray = new ArrayList<>();
        linkArray.add(new LinkResourse("/winsome/users", "user accounts", "A list of user accounts"));
        linkArray.add(new LinkResourse("/winsome/users/" + user.getUsername(), "your user account", "Your account information, including your wallet"));
        linkArray.add(new LinkResourse("/winsome/users/" + user.getUsername() + "/blog", "blog", "Your blog"));
        linkArray.add(new LinkResourse("/winsome/users/" + user.getUsername() + "/feed", "feed", "Your feed"));
        linkArray.add(new LinkResourse("/winsome/users/" + user.getUsername() + "/following", "following-list", "List of users you follow"));
        linkArray.add(new LinkResourse("/winsome/users/" + user.getUsername() + "/blog/{id-post}", "post-Blog", "A post in your blog"));
        linkArray.add(new LinkResourse("/winsome/users/" + user.getUsername() + "/feed/{id-post}", "post-Feed", "A post in your feed"));
        rootNode.set("links", mapper.valueToTree(linkArray));
        return mapper.writeValueAsString(rootNode);
    }

    //effects: ritorna la stringa JSON che contiene il messaggio da inviare ad un utente in seguito ad una registrazione effettuata correttamente
    //contiene: username, password e tag del nuovo utente e un riferimento alla risorsa con cui si può effettuare login
    public static String getRegistrationInfoJsonString(User user) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        rootNode.put("username", user.getUsername());
        rootNode.put("password", user.getPassword());
        rootNode.set("tags", mapper.valueToTree(user.getTags()));
        ArrayList<LinkResourse> linkArray = new ArrayList<>();
        linkArray.add(new LinkResourse("/winsome/tokens","tokens","Reference to log in. A PUT on this reference, with correct username and password as body (in JSON), will return a unique api-key that will allow you to perform all the actions allowed to you"));
        rootNode.set("links", mapper.valueToTree(linkArray));
        return mapper.writeValueAsString(rootNode);
    }

    //effects: ritorna un ArrayNode che contiene una lista di ObjectNode ciascuno associato ad un utente presente in userList.
    //Per ciascun utente contiene verrà inserito username e lista di tag a lui associati
    public static ArrayNode getUserListInJsonArray(HashSet<User> userList) {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ArrayNode rootNode = mapper.createArrayNode();
        for(User user : userList){
            ObjectNode childNode = rootNode.addObject();
            childNode.put("username", user.getUsername());
            childNode.set("tags", mapper.valueToTree(user.getTags()));
        }
        return rootNode;
    }

    //effects: ritorna una stringa in formato JSON che contiene la lista di utenti presenti in userList.
    //Per ciascun utente contiene verrà inserito username e lista di tag a lui associati
    public static String getUserListInJsonString(HashSet<User> userList) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        rootNode.set("user list", getUserListInJsonArray(userList));
        return mapper.writeValueAsString(rootNode);
    }

    //effects: ritorna una stringa JSON che contiene username e tag associati all'utente user.
    public static String getBasicUserInfoInJsonString(User user) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        rootNode.put("username", user.getUsername());
        rootNode.set("tags", mapper.valueToTree(user.getTags()));
        return mapper.writeValueAsString(rootNode);
    }

    //effects: ritorna una stringa JSON che contiene tutte le informazioni (anche quelle private come password e wallet)
    // associate all'utente user
    public static String getFullUserInfoJsonString(User user) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        rootNode.put("href", "winsome/users/" + user.getUsername());
        rootNode.put("username", user.getUsername());
        rootNode.put("password", user.getPassword());
        //per avere tutte le informazioni relative al wallet sincronizzate
        Wallet userWallet = user.getWallet().copyWallet();
        double steemAmount = userWallet.getAmount();
        double bitcoinAmount = userWallet.getAmountInBitcoin();
        ArrayList<String> transaction = userWallet.getTransactions();
        ObjectNode walletNode = rootNode.putObject("wallet");
        walletNode.put("wallet amount in steem", String.format("%.6f", steemAmount));
        walletNode.put("wallet amount in bitcoin", String.format("%.6f", bitcoinAmount));
        walletNode.set("transaction", mapper.valueToTree(transaction));
        rootNode.set("tags", mapper.valueToTree(user.getTags()));
        return  mapper.writeValueAsString(rootNode);
    }

    //effects: ritorna l'ObjectNode associato alla stringa message per poter parsare in modo semplice i messaggi JSON
    static public ObjectNode getJsonObjectNode(String message) throws IOException {
        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        return mapper.readValue(message, ObjectNode.class);
    }
}
