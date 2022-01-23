package winsome.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import winsome.jsonUtility.JsonMessageBuilder;
import winsome.resourseRappresentation.Post;
import winsome.resourseRappresentation.Reaction;
import winsome.resourseRappresentation.User;
import winsome.resourseRappresentation.Wallet;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class DataBackup implements Runnable {
    final private ConcurrentHashMap<String, User> registeredUsers;
    final private ServerConfiguration serverConfiguration;

    public DataBackup(WinSomeServer winSomeServer) {
        this.registeredUsers = winSomeServer.getRegisteredUser();
        this.serverConfiguration = winSomeServer.getServerConfiguration();
    }

    @Override
    public void run() {
        //scorre tutta la lista di utenti registrati e salva tutti i valori contenuti: non effettua alcuna strategia di caching.
        //ad ogni iterazione tutti i valori presenti in memoria principale saranno salvati su disco

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        ObjectNode rootNode = mapper.createObjectNode();
        //aggiungo coppia "users":<array di utenti> all'oggetto JSON
        ArrayNode usersList = rootNode.putArray("users");
        for(User user: registeredUsers.values()){
            //aggiungo nuovo utente alla lista e ne salvo tutte le informazioni rilevanti
            ObjectNode userNode = usersList.addObject();
            userNode.put("username", user.getUsername());
            userNode.put("password", user.getPassword());
            userNode.set("tags", mapper.valueToTree(user.getTags()));
            Wallet userWallet = user.getWallet();
            ArrayList<String> transaction;
            transaction = userWallet.getTransactions();
            ObjectNode walletNode = userNode.putObject("wallet");
            walletNode.set("transactions", mapper.valueToTree(transaction));
            userNode.set("followers", JsonMessageBuilder.getUserListInJsonArray(user.getFollowers().getHashSet()));
            userNode.set("following", JsonMessageBuilder.getUserListInJsonArray(user.getFollowedUsers().getHashSet()));
            //aggiungo la coppia "blog":<lista d post del blog>
            ArrayNode blogArrayNode = userNode.putArray("blog");
            for(Post post : user.getBlog().values()){
                //aggiungo nuovo post alla lista e ne salvo tutte le informazioni rilevanti
                ObjectNode postObjectNode = blogArrayNode.addObject();
                postObjectNode.put("idPost", post.getId());
                postObjectNode.put("title", post.getTitolo());
                postObjectNode.put("author-username", post.getAuthor().getUsername());
                postObjectNode.put("iteration", post.getIterazioni());
                postObjectNode.put("content", post.getContenuto());
                ArrayNode reactionArrayNode = postObjectNode.putArray("reactions");
                //aggiungo la coppia "reaction":<lista di reaction relative al post>
                for(Reaction reaction : post.getReaction().getHashSet()){
                    ObjectNode reactionObjectNode = reactionArrayNode.addObject();
                    if(reaction.getType() == 0){
                        reactionObjectNode.put("type", reaction.getType());
                        reactionObjectNode.put("rate", reaction.getRate());
                        reactionObjectNode.put("author", reaction.getAuthor().getUsername());
                    }
                    else{
                        reactionObjectNode.put("type", reaction.getType());
                        reactionObjectNode.put("content", reaction.getContent());
                        reactionObjectNode.put("author", reaction.getAuthor().getUsername());
                    }
                    reactionObjectNode.put("isNew", reaction.isNew());
                }
            }
        }
        try {
            //scrivo tutto sul file di backup ottenuto dalle configurazioni del server
            mapper.writeValue(Paths.get(serverConfiguration.getBackupJsonFile()).toFile(), rootNode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
